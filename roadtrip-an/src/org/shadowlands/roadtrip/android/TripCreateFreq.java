/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  Copyright (C) 2010 Jeremy D Monin <jdmonin@nand.net>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.shadowlands.roadtrip.android;

import java.util.Calendar;
import java.util.Vector;

import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.db.FreqTrip;
import org.shadowlands.roadtrip.db.FreqTripTStop;
import org.shadowlands.roadtrip.db.Location;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.TStop;
import org.shadowlands.roadtrip.db.Trip;
import org.shadowlands.roadtrip.db.ViaRoute;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

/**
 * After the end of a trip, gather info and create a {@link FreqTrip frequent trip}.
 *<P>
 * Called from {@link TripTStopEntry} or {@link Main}, with an intent which
 * contains an int extra, with key = "_id", for the trip to copy from.
 *<P>
 * Has two modes: First, confirm the freqtrip details and TStops.
 * Second ({@link #modeConfirmVias}) confirm any changes to ViaRoutes
 * after removing TStop(s).
 *
 * @author jdmonin
 */
public class TripCreateFreq extends Activity
	implements OnItemClickListener
{
	/** tag for android logging */
	private static final String TAG = "RTR.TripCreateFreq";

	private RDBAdapter db = null;

	/**
	 * If true, user has already clicked "Create" once, and
	 * decided to not include some TStops in the FreqTrip.
	 * That invalidated some remaining ViaRoutes of the trip
	 * (held in {@link #stopsList}), so now they need to edit
	 * Via dropdowns, and then click "Create" again.
	 */
	private boolean modeConfirmVias = false;

	/**
	 * {@link TStop}s from the trip which will become {@link FreqTripTStop}s,
	 * or null if none.
	 *<P>
	 * Initialized in {@link #populateStopsList(RDBAdapter)},
	 * possibly updated in {@link #updateStopsListFromCheckboxes()}
	 * and {@link #updateStopsListNewVias()}.
	 * Views are managed via {@link #stopsListRows}.
	 */ 
	private Vector<TStop> stopsList;

	/**
	 * Members of {@link #stopsList} whose checkboxes are checked.
	 * Initialized in {@link #populateStopsList(RDBAdapter)}.
	 * <tt>stopsChosen[endingTStop]</tt> is always true.
	 */
	private boolean[] stopsChosen;

	/** Source trip, from ID passed into our bundle at create time. */
	private Trip srcT;

	/** Goes with {@link #tpAtTime}, setup in {@link #onCreate(Bundle)} */
	private Calendar calAtTime;

	/** Starting and ending location; setup in {@link #onCreate(Bundle)} */
	private Location startLoc, endLoc;

	/** Has the user confirmed they want to cancel creating it? */
	private boolean confirmedCancel;

	private CheckBox cbWeekdays, cbWeekends, cbAtTime;
	private TimePicker tpAtTime;

	private EditText etDescr;

	/**
	 * Holds the members of {@link #stopsList}, including the
	 * trip-ending TStop (for its location & via).
	 * Initialized in {@link #populateStopsList(RDBAdapter)}.
	 */
	private TStopRowController[] stopsListRows;

	/** Used by {@link TStopRowController} */
	private LinearLayout tstopListParentLayout = null;

	/** Used by {@link TStopRowController} */
	private int tstopListPosition = -1;

	/**
	 * Called when the activity is first created.
	 * Load our source trip, set flags, etc.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.trip_create_freq);

	    confirmedCancel = false;
	    etDescr = (EditText) findViewById(R.id.trip_createfreq_descr);
	    // lvTStopsList = (ListView) findViewById(R.id.trip_createfreq_tstop_list);
	    // lvTStopsList.setOnItemClickListener(this);
	    stopsList = null;
		stopsChosen = null;
	    cbWeekdays = (CheckBox) findViewById(R.id.trip_createfreq_cb_weekdays);
	    cbWeekends = (CheckBox) findViewById(R.id.trip_createfreq_cb_weekends);
	    cbAtTime = (CheckBox) findViewById(R.id.trip_createfreq_cb_atTime);
	    tpAtTime = (TimePicker) findViewById(R.id.trip_createfreq_timepicker);

	    // Contents of tpAtTime, etDescr will be set once we've
	    // read the trip data from the db.

	    db = new RDBOpenHelper(this);

		Intent i = getIntent();
		srcT = null;
		if (i != null)
		{
			try {
				srcT = new Trip(db, i.getIntExtra("_id", 0));
				startLoc = srcT.readStartTStop(true).readLocation();
				endLoc = srcT.readLatestTStop().readLocation();
			} catch (Throwable e) { } // RDBKeyNotFoundException or NullPointerException; shouldn't happen
		}
		if ((srcT == null) || (startLoc == null) || (endLoc == null))
		{
			Toast.makeText(this, R.string.not_found, Toast.LENGTH_SHORT).show();
			db.close();
			db = null;
			finish();
			return;  // <--- Early return: Trip somehow not found ---
		} else {
		    // TODO etDescr: fill 'hint text' (empty text) based on src,dest,via, or trip comments

			// Show starting, ending location
			TextView tv = (TextView) findViewById(R.id.trip_createfreq_from);
			tv.setText(tv.getText() + " " + startLoc.getLocation());
			tv = (TextView) findViewById(R.id.trip_createfreq_to);
			tv.setText(tv.getText() + " " + endLoc.getLocation());

			// Take trip start time, set tpAtTime from it
		    tpAtTime.setIs24HourView(DateFormat.is24HourFormat(this));		    
		    calAtTime = Calendar.getInstance();
		    if (0 != srcT.getTime_start())
		    	calAtTime.setTimeInMillis(1000L * srcT.getTime_start());
		    tpAtTime.setCurrentHour(calAtTime.get(Calendar.HOUR_OF_DAY));
		    tpAtTime.setCurrentMinute(calAtTime.get(Calendar.MINUTE));
		}

		if (! populateStopsList())
		{
			View v = findViewById(R.id.trip_createfreq_tstop_text);
			if (v != null)
				v.setVisibility(View.GONE);
		}
	}

	@Override
	public void onPause()
	{
		super.onPause();
		if (db != null)
			db.close();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		if (db != null)
			db.close();
	}

	/**
	 * During <tt>onCreate</tt>, list the trip stops that are part of the source trip.
	 * Populates {@link #stopsList}, {@link #stopsListRows}, and {@link #stopsChosen}.
	 * The starting stop is never included.
	 * If there are any intermediate stops, then the ending stop is included.
	 * @return true if intermediate {@link TStop}s found, false otherwise
	 * @see #updateStopsListFromCheckboxes()
	 */
	private boolean populateStopsList()
	{		
		stopsList = srcT.readAllTStops(false);  // include the finishing stop
		if (stopsList == null)
			return false;

		// Remove trip-starting stop, if it exists
		if (! srcT.isStartTStopFromPrevTrip())
		{
			TStop ts = stopsList.firstElement();
			if ((ts.getOdo_total() == srcT.getOdo_start()) && (ts.getOdo_trip() == 0))
			{
				stopsList.removeElementAt(0);
				if (stopsList.isEmpty())
				{
					// this shouldn't happen, we should have the finishing stop
					stopsList = null;
					return false;
				}
			}
		}

		final int L = stopsList.size();
		if (L == 1)
		{
			// Trip contains only the finishing stop,
			// there are no intermediate stops.
			stopsList = null;
			return false;
		}
		
		stopsChosen = new boolean[L];
		stopsListRows = new TStopRowController[L];
		tstopListPosition = -1;
		tstopListParentLayout = (LinearLayout) findViewById(R.id.trip_createfreq_tstoplist_parent);
		if (tstopListParentLayout != null)
		{
			View v = findViewById(R.id.trip_createfreq_desttext);
			if (v != null)
				tstopListPosition = tstopListParentLayout.indexOfChild(v);
		}
		if ((tstopListPosition == -1) || (tstopListParentLayout == null))
		{
			// shouldn't happen, but just in case
			Log.e(TAG, "populateStopsList layout items not found");
			Toast.makeText(this, "L170: internal error: populateStopsList layout items not found", Toast.LENGTH_SHORT).show();
			return false;
		}

		for (int i = 0; i < L; ++i)
		{
			stopsChosen[i] = true;
			TStopRowController row = new TStopRowController(i);  // inner class, implies 'this'
			stopsListRows[i] = row;
			row.addToOurLayout();
		}
		return true;
	}

	/**
	 * 'Cancel' button or 'back' button was clicked: popup an
	 * {@link AlertDialog} to confirm if wants to finish this activity,
	 * and not create the frequent trip.
	 */
	public void onClick_BtnCancel(View v)
	{
		if (confirmedCancel)
		{
			finish();
		} else {
	    	AlertDialog.Builder alert = new AlertDialog.Builder(this);

	    	alert.setTitle(R.string.confirm);
	    	alert.setMessage(R.string.trip_createfreq_cancel_are_you_sure);
	    	alert.setPositiveButton(R.string.create, new DialogInterface.OnClickListener() {
		    	  public void onClick(DialogInterface dialog, int whichButton) { }
		    	});
	    	alert.setNegativeButton(R.string.confirm, new DialogInterface.OnClickListener() {
	    	public void onClick(DialogInterface dialog, int whichButton) {
	    		confirmedCancel = true;
	    		TripCreateFreq.this.finish();
		    	  }
		    	});
	    	alert.show();
		}
	}

	/**
	 * 'Create' button was clicked: check fields, make the freqtrip if OK.
	 * The description is required if the trip has intermediate stops.
	 * If {@link #stopsList} != null, will first call
	 * {@link #updateStopsListFromCheckboxes()} or
	 * {@link #updateStopsListNewVias()}, depending on {@link #modeConfirmVias}.
	 */
	public void onClick_BtnCreate(View v)
	{
		String descr = etDescr.getText().toString().trim();
		if (descr.length() == 0)
		{
			descr = null;

			// Do we have any stops?  If so, description is required.
			if (stopsList != null)
			{
				for (int i = stopsChosen.length - 2; i >= 0; --i)  // skip ending tstop
				{
					if (stopsChosen[i])
					{
						etDescr.requestFocus();
						Toast.makeText(this, R.string.please_enter_the_description, Toast.LENGTH_SHORT).show();
						return;  // <--- Missing required description ---
					}
				}
			}
		}

		int atTime;
		if (! cbAtTime.isChecked())
		{
			atTime = -1;
		} else {
			atTime = tpAtTime.getCurrentHour() * 60 + tpAtTime.getCurrentMinute();
		}

		if (! modeConfirmVias)
		{
			if (! updateStopsListFromCheckboxes())
			{
				modeConfirmVias = true;

				// Prompt user to confirm any cleared or guessed ViaRoutes.
		    	AlertDialog.Builder alert = new AlertDialog.Builder(this);
		    	alert.setTitle(R.string.via_routes_changed);
		    	alert.setMessage(R.string.trip_createfreq_confirm_each_via);
		    	alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			    	  public void onClick(DialogInterface dialog, int whichButton) {}
			    	});
		    	alert.show();

		    	return;  // <-- after alert, user will hit Create button again ---
			}
		}
		else if (stopsListRows != null)
		{
			// We're in modeConfirmVias.
			// read any confirmed vias.
			// Insert any new vias into db.
			updateStopsListNewVias();
		}

		FreqTrip ft = FreqTrip.createFromTrip
			(srcT, stopsList, descr, atTime,
			 cbWeekends.isChecked(), cbWeekdays.isChecked());
		ft.insert(db);

		Log.i(TAG, "Created new FreqTrip id " + ft.getID());
		Toast.makeText(this, "Created new FreqTrip id " + ft.getID(), Toast.LENGTH_SHORT).show();

		finish();
	}

	/**
	 * See whether any stops were removed (checkboxes un-checked)
	 * from the trip, and remove them from {@link #stopsList} if so.
	 * Then, validate the ViaRoutes between the remaining ones
	 * (including to the trip-ending stop).
	 * Also disable the checkboxes to prevent further changes. 
	 *<P>
	 * Determines whether to begin {@link #modeConfirmVias},
	 * but doens't set that mode flag.
	 *<P>
	 * May change via fields of some {@link TStop}s within {@link #stopsList}.
	 * Called from {@link #onClick_BtnCreate(View)}.
	 *<UL>
	 * <LI> - If {@link #stopsList} is null, there were never any
	 *          intermediate stops. OK. (return true)
	 * <LI> - Get the trip start location
	 * <LI> - Iterate through all the original stops within {@link #stopsListRows}:
	 * <LI> If the current stop was deleted, skip it
	 * <LI> Keep track of previous, current location
	 * <LI> If the preceding stop wasn't deleted, OK (go to next stop)
	 * <LI> If it has no via_id, OK (go to next)
	 * <LI> If it's via locations match previous & current, OK (go to next)
	 * <LI> The user will have to decide this one. (Clear the 'All OK' flag)
	 * <LI> Clear its via_id
	 * <LI> Query the database for ViaRoutes between the previous & current location
	 * <LI> If there are none, we'll give the user a blank field to type a new ViaRoute into
	 * <LI> Call a {@link TStopRowController} method to populate the via autocomplete
	 * <LI> If there's only 1 possible ViaRoute, this method should suggest it as default to the user
	 * <LI> - After iterating, return the 'All OK' flag.
	 *</UL>
	 * @return true if vias are OK (some might have been cleared),
	 *         false if the user needs to be asked to confirm
	 */
	private boolean updateStopsListFromCheckboxes()
	{
		if (stopsList == null)
			return true;

		// First, were any stops removed?  If not, nothing to do
		{
			boolean anyRemoved = false;
			// remove unwanted from stopsList:
			// (length-2 is to skip the trip-ending tstop)
			for (int i = stopsChosen.length - 2; i >= 0; --i)
			{
				if (! stopsChosen[i])
				{
					stopsList.removeElementAt(i);
					anyRemoved = true;
				}
				if (stopsListRows[i].cb != null)
					stopsListRows[i].cb.setEnabled(false);
				// TODO instead of disable, if they change again
				// when in modeConfirmVias, go into another round
				// by calling this method again;
				// this would want vias editable from the start.
			}
	
			if (! anyRemoved)
				return true;  // <--- Early return: No stops were removed ---
		}

		boolean allOK = true;
		int prev_locID = startLoc.getID(), curr_locID;

		// We iterate through stopsListRows, not stopsList,
		// because some items were removed from stopsList.
    	for (int i = 0; i < stopsListRows.length; ++i)
    	{
    		if (! stopsChosen[i])
    			continue;

    		TStop ts = stopsListRows[i].ts;
    		curr_locID = ts.getLocationID();
    		if (((i == 0) || ! stopsChosen[i-1])
				&& (curr_locID != 0))
    		{
    			ViaRoute via = ts.readVia();
    			if ((via != null) && (prev_locID != via.getLocID_From()))
    			{
    				// Mismatch.
    				ts.setVia_id(0);
					allOK = false;
    				ViaRoute[] vrlist = ViaRoute.getAll(db, prev_locID, curr_locID);
    				stopsListRows[i].showPossibleVias(vrlist);
    			}
    		}
    		// Ready for next iteration:
    		prev_locID = curr_locID;
		}
    	return allOK;
	}

	/**
	 * When user has clicked 'Create' in {@link #modeConfirmVias},
	 * read the autocompletes for any confirmed vias, and update
	 * {@link #stopsList}'s via IDs.  Insert any new {@link ViaRoute}s into the db.
	 * @throws IllegalStateException if not in {@link #modeConfirmVias} when called
	 */
	private void updateStopsListNewVias()
		throws IllegalStateException
	{
		if (! modeConfirmVias)
			throw new IllegalStateException();

		int locID_curr = startLoc.getID();
		int locID_prev;
		for (int i = 0 ; i < stopsListRows.length; ++i)
		{
			if (! stopsChosen[i])
				continue;

			TStop ts = stopsListRows[i].ts;
			locID_prev = locID_curr;
			locID_curr = ts.getLocationID();
			AutoCompleteTextView tvVia = stopsListRows[i].via;
			if (tvVia == null)
				continue;
			if (stopsListRows[i].viaSel != null)
			{
				ts.setVia_id(stopsListRows[i].viaSel.getID());
			} else {
				String viaText = tvVia.getText().toString().trim();
				if (viaText.length() == 0)
				{
					ts.setVia_id(0);
				} else {
					// Create and insert a new ViaRoute
					final int stopOdoT = ts.getOdo_trip();
					final int odo_dist;  // distance from previous stop
					if ((i == 0) || (stopOdoT == 0))
					{
						odo_dist = stopOdoT;
					} else if (! stopsChosen[i-1]) {
						odo_dist = 0;  // new route may have new distance
					} else {
						final int prevOdoT = stopsListRows[i-1].ts.getOdo_trip();
						if (prevOdoT == 0)
							odo_dist = 0;
						else
							odo_dist = stopOdoT - prevOdoT;
					}
					ViaRoute vr = new ViaRoute(locID_prev, locID_curr, odo_dist, viaText);
					ts.setVia_id(vr.insert(db));
				}
			}
		}
	}

	/** Check with user for {@link KeyEvent#KEYCODE_BACK} */
	@Override
	public boolean onKeyDown(final int keyCode, KeyEvent event)
	{
	    if ((keyCode == KeyEvent.KEYCODE_BACK)
	    	&& (event.getRepeatCount() == 0))
	    {
	    	onClick_BtnCancel(null);
	        return true;  // Don't pass to next receiver
	    }

	    return super.onKeyDown(keyCode, event);
	}

	/** Check with user for {@link KeyEvent#KEYCODE_BACK} */
	@Override
	public boolean onKeyUp(final int keyCode, KeyEvent event)
	{
	    if (keyCode == KeyEvent.KEYCODE_BACK)
	    {
	    	// Deal with this key during onKeyDown, not onKeyUp.
	        return true;  // Don't pass to next receiver
	    }
	    return super.onKeyUp(keyCode, event);
	}

	/** When a FreqTrip is selected in the list, finish the activity with its ID. */
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		if ((stopsList == null) || (position >= stopsList.size()))
			return;  // unlikely, but just in case
		TStop ts = (TStop) stopsList.elementAt(position);
		Toast.makeText(this, "got tstop id " + ts.getID(),
			Toast.LENGTH_SHORT).show();
		// TODO something else
	}

	/** for use by {@link TStopRowController}. Width <tt>FILL_PARENT</tt>, height <tt>WRAP_CONTENT</tt>. */
	private static ViewGroup.LayoutParams TS_ROW_LP = null;

	/**
	 * Info and screen controls for one {@link TStop}, either an
	 * intermediate stop or the trip's ending stop.
	 * Added to the activity layout in {@link TripCreateFreq#populateStopsList(RDBAdapter)}.
	 *<P>
	 * If the user must confirm the ViaRoute, call
	 * {@link #showPossibleVias(ViaRoute[])} to set up that layout.
	 *<P>
	 * To read the via contents, use {@link #viaSel} if they selected a via
	 * which already exists; if that's null, {#link #via} for the text of a new one. 
	 */
	private class TStopRowController
		implements OnCheckedChangeListener, OnItemClickListener, TextWatcher
	{
		/** Our TripStop info; set in {@link #addToOurLayout()}. */
		public TStop ts;

		/** Is this the trip-ending TStop, not intermediate? Set in {@link #addToOurLayout()}. */
		public boolean isTripEndTStop;

		/** Position in the TStop array. See also {@link #isTripEndTStop} */
		private final int tstopIndex;

		/** holds {@link #cb} and maybe via-dropdown */
		private LinearLayout row;

		/** checkbox with TStop.toString; not used if {@link #isTripEndTStop} */
		private CheckBox cb;

		/** dropdown for ViaRoutes to this stop from previous stop, or free-text entry for via route, or null */
		public AutoCompleteTextView via;

		/** the ViaRoute selected within {@link #via}, if any */
		public ViaRoute viaSel;

		/**
		 * Before this can be used, call {@link #addToOurLayout()}.
		 * @param tstopIndex  The stop's index within {@link TripCreateFreq#stopsList}
		 */
		public TStopRowController(final int tstopIndex)
		{
			this.tstopIndex = tstopIndex;
			via = null;
		}

		/**
		 * Dynamically create a CheckBox with info about this TStop,
		 * and add to the activity's layout.
		 *<P>
		 * Assumes that {@link TripCreateFreq#stopsList} is initialized,
		 * and the final stop in that list is the destination, not an
		 * intermediate stop.
		 *<P>
		 * Initially, the TStop text will include the via route.  This can be cleared later.
		 *<P>
		 * Please set {@link TripCreateFreq#tstopListParentLayout}
		 * and {@link TripCreateFreq#tstopListPosition} before calling.
		 * {@link TripCreateFreq#tstopListPosition tstopListPosition} will
		 * be incremented after this method calls <tt>addView</tt>.
		 */
		public void addToOurLayout()
		{
			if (TS_ROW_LP == null)
				TS_ROW_LP = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

			ts = stopsList.elementAt(tstopIndex);
			isTripEndTStop = (tstopIndex == stopsList.size() - 1);

			row = new LinearLayout(TripCreateFreq.this);
			row.setLayoutParams(TS_ROW_LP);
			row.setOrientation(LinearLayout.HORIZONTAL);

			if (! isTripEndTStop)
			{
				cb = new CheckBox(TripCreateFreq.this);
	    		cb.setText(ts.toString(true, true));
	        	cb.setChecked(true);
	        	cb.setOnCheckedChangeListener(this);	
	        	row.addView(cb);
			} else {
				TextView tv = new TextView(TripCreateFreq.this);
				Resources res = getResources();
				StringBuffer sb = new StringBuffer(res.getString(R.string.destination));
				sb.append(": ");
				sb.append(ts.toString(true, true));
	    		tv.setText(sb);
	        	row.addView(tv);
			}
			tstopListParentLayout.addView(row, tstopListPosition);
			++tstopListPosition;
		}

        /** Update {@link TripCreateFreq#stopsChosen} when the checkbox is set or cleared */
		public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked)
		{
			stopsChosen[tstopIndex] = isChecked;
		}

		/**
		 * Create a ViaRoute AutoComplete, assigned to {@link #via}.
		 * Do not call this method more than once.
		 *<P>
		 * To read the selected ViaRoute later, use {@link #viaSel}.
		 * if {@link #viaSel} is blank, read {@link #via}'s text.
		 * @param vrlist  Vias which are valid for {@link #ts}, or null if none.
		 *    If null, will create a textview with no autocomplete adapter,
		 *    into which a new via's text can be typed.
		 */
		public void showPossibleVias(ViaRoute[] vrlist)
		{
			if (via != null)
				return;
			// TODO if via != null, should we discard its current adapter?

			// remove uneditable "via" text; CheckBox extends TextView
			if (! isTripEndTStop)
			{
				((CheckBox) row.getChildAt(0)).setText(ts.toString(true, false));				
			} else {
				Resources res = getResources();
				StringBuffer sb = new StringBuffer(res.getString(R.string.destination));
				sb.append(": ");
				sb.append(ts.toString(true, false));
				((TextView) row.getChildAt(0)).setText(sb);				
			}

			// Add a textview and a via dropdown/autocomplete
			TextView tv = new TextView(TripCreateFreq.this);
			tv.setText(R.string.via);
			tv.setPadding(3, 0, 1, 0);  // left 3 pixels, right 1 pixel
			row.addView(tv);

			viaSel = null;
			via = new AutoCompleteTextView(TripCreateFreq.this);
			row.addView(via, TS_ROW_LP);  // width FILL_PARENT
			if (vrlist != null)
			{
				ArrayAdapter<ViaRoute> adapter = new ArrayAdapter<ViaRoute>(TripCreateFreq.this, R.layout.list_item, vrlist);
				via.setAdapter(adapter);
				via.setOnItemClickListener(this);
				via.addTextChangedListener(this);
				if (vrlist.length == 1)
				{
					viaSel = vrlist[0];
					via.setText(viaSel.toString());
				}
			}
		}

		/**
		 * If new via-route text is typed into {@link #via}, and {@link #viaSel}
		 * no longer matches that text, clear <tt>via</tt>.
		 * (for addTextChangedListener / {@link TextWatcher}) 
		 */
		public void afterTextChanged(Editable arg0)
		{
			if (viaSel == null)
				return;
			final String newText = arg0.toString().trim();
			final int newLen = newText.length(); 
			if ((newLen == 0) || ! viaSel.toString().equalsIgnoreCase(newText))
			{
				viaSel = null;  // Mismatch: object no longer matches typed ViaRoute description
			}
		}

		/** required stub for {@link TextWatcher} */
		public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3)
		{ }

		/** required stub for {@link TextWatcher} */
		public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3)
		{ }

		/** For ViaRoute autocomplete, the callback for {@link OnItemClickListener}; sets {@link #viaSel}. */
		public void onItemClick(AdapterView<?> parent, View clickedOn, int position, long rowID)
		{
			ListAdapter la = via.getAdapter();
			if (la == null)
				return;
			viaSel = (ViaRoute) la.getItem(position);
		}		

	}  // inner class TStopRowAdapter

}
