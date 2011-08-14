/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  Copyright (C) 2010-2011 Jeremy D Monin <jdmonin@nand.net>
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

import org.shadowlands.roadtrip.AndroidStartup;
import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.android.util.Misc;
import org.shadowlands.roadtrip.db.FreqTrip;
import org.shadowlands.roadtrip.db.FreqTripTStop;
import org.shadowlands.roadtrip.db.GasBrandGrade;
import org.shadowlands.roadtrip.db.GeoArea;
import org.shadowlands.roadtrip.db.Location;
import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.RDBKeyNotFoundException;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.TStop;
import org.shadowlands.roadtrip.db.TStopGas;
import org.shadowlands.roadtrip.db.Trip;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.ViaRoute;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

/**
 * Confirm a trip stop during a trip, or end the trip, from Main activity.
 * Called when the stop begins (no {@link Settings#CURRENT_TSTOP} in the database),
 * when resuming the trip from the current stop (has <tt>CURRENT_TSTOP</tt>), and when
 * ending the trip (show this with the boolean intent extra
 * {@link #EXTRAS_FLAG_ENDTRIP}).
 *<P>
 * Assumes CURRENT_DRIVER, CURRENT_VEHICLE, CURRENT_TRIP are set.
 * If not, it will jump to the {@link AndroidStartup} activity
 * to enter the driver/vehicle data.
 *<P>
 * If we're not ending the trip right now, and we're on a frequent trip
 * with stops ({@link Settings#CURRENT_FREQTRIP_TSTOPLIST} is set),
 * then {@link #onCreate(Bundle)} will bring up another activity to ask the
 * user if we've stopped at one of those {@link FreqTripTStop frequent TStops}.
 * If that activity is canceled, this one will be too.
 *<P>
 * If stopping here creates a new {@link Location} or {@link ViaRoute}, and the
 * text is changed when resuming the trip, make sure the new item's text is updated
 * in the database.
 *
 * @author jdmonin
 */
public class TripTStopEntry extends Activity
	implements OnDateSetListener, OnItemClickListener, TextWatcher
{
	/** Flag for ending the entire trip (not just stopping), for {@link Intent#putExtra(String, boolean)} */
	public static final String EXTRAS_FLAG_ENDTRIP = "endtrip";

	/** Historical Mode threshold is 24 hours, in milliseconds. */
	private static final long TIMEDIFF_HISTORICAL_MILLIS = 24 * 60 * 60 * 1000L;

	/** Determines if {@link #onCreate(Bundle)} should call {@link #onRestoreInstanceState(Bundle)} */
	private static final String TSTOP_BUNDLE_SAVED_MARKER = "tripTStopEntrySavedState";

	/** tag for Log debugs */
	@SuppressWarnings("unused")
	private static final String TAG = "Roadtrip.TripTStopEntry";

	private RDBAdapter db = null;
	private GeoArea currA;
	private Vehicle currV;
	private Person currD;
	private Trip currT;
	private TStop currTS;

	/** all locations in the area, or null; set from {@link #currA} in {@link #onCreate(Bundle)} */
	private Location[] areaLocs;

	/**
	 * the areaID of locations in {@link #areaLocs}, or -1.
	 * For roadtrips, also the currently selected area ID of
	 * {@link #btnRoadtripArea_chosen}.
	 * 0 is used for all local trips.
	 * 0 OK for tstops/locations within a roadtrip, but not
	 * for the start or end tstop/location.
	 */
	private int areaLocs_areaID;

	/**
	 * true if {@link #EXTRAS_FLAG_ENDTRIP} was set when creating the activity.
	 * @see #isCurrentlyStopped
	 */
	private boolean stopEndsTrip;

	/**
	 * When this activity was created, were we already at a TStop?
	 * True if <tt>{@link #currTS} != null</tt>.
	 * @see #stopEndsTrip
	 */
	private boolean isCurrentlyStopped;

	/**
	 * Frequent TStop chosen by user, if we'e on a freq trip and
	 * {@link Settings#CURRENT_FREQTRIP_TSTOPLIST} isn't empty.
	 * Null otherwise.
	 */
	private FreqTripTStop wantsFTS;

	/** Gas info for {@link #currTS}, or null */
	private TStopGas stopGas;

	/** Button to enter info for gas.  Has a green light when {@link #stopGas} != null. */
	private Button btnGas;

	/**
	 * Gas-stop info entered via {@link TripTStopGas}, or null.
	 * When this is non-null, the "Gas" button has a green light,
	 * and gas is associated with the TStop.
	 */
	private Bundle bundleGas;

	private OdometerNumberPicker odo_total, odo_trip;
	/** odometer value before it was changed within this activity; set in onCreate via {@link #updateTextAndButtons()} */
	private int odoTotalOrig, odoTripOrig;
	/** if true, the odometer values were adjusted using {@link #viaRouteObj} data. */
	private boolean odosAreSetFromVia = false;
	/** if true, the odometer values were adjusted using FreqTrip data. */
	private boolean odosAreSetFromFreq = false;

	/**
	 * If {@link #odosAreSetFromVia} or {@link #odosAreSetFromFreq}, the value when they were set;
	 * read in {@link #onClick_BtnEnterTStop(View)} to determine if the calculated total is "drifting"
	 * because of rounding.  Otherwise 0.  If total is 0, ignore trip.  For vias, the total is set
	 * non-zero only if its checkbox wasn't checked when the via was selected.
	 */
	private int odosAreSetFrom_total = 0, odosAreSetFrom_trip = 0;

	private CheckBox odo_total_chk, odo_trip_chk, tp_time_stop_chk, tp_time_cont_chk;
	private TimePicker tp_time_stop, tp_time_cont;

	/** location; uses, sets {@link #locObj} */
	private AutoCompleteTextView loc;
	/** via_route; see {@link #updateViaRouteAutocomplete(ViaRoute, boolean)} */
	private AutoCompleteTextView via;

	/**
	 * previous via destination-locID; helps {@link #updateViaRouteAutocomplete(ViaRoute, boolean)}
	 * determine whether it needs to query certain data.
	 */
	private int via_lastLocIDTo = -1;

	private ViaRouteListenerWatcher viaListener;

	/**
	 * When stopping at a stop, selection from the {@link #via} dropdown for selecting a ViaRoute.
	 * Changing {@link #via}'s text clears {@link #viaRouteObj},
	 * unless <tt>viaRouteObj</tt> was created for this stop
	 * ({@link #viaRouteObjCreatedHere} != null).
	 * @see #prevLocObj
	 * @see #locObj
	 * @see #viaRouteObjCreatedHere
	 * @see ViaRouteOnItemClickListener#onItemClick(AdapterView, View, int, long)
	 */
	private ViaRoute viaRouteObj;

	/**
	 * if non-null, then <tt>currTS != null</tt>, and
	 * {@link #viaRouteObj} was created for this TStop.
	 */
	private ViaRoute viaRouteObjCreatedHere = null;

	/**
	 * When at a stop, the previous stop's location ID; for trip's first stop, the trip start location;
	 * from {@link Settings#getPreviousLocation(RDBAdapter, boolean)}.
	 * Used for {@link #viaRouteObj}.
	 */
	private Location prevLocObj;

	/**
	 * when at a stop, the location we stopped at.
	 * Used for {@link #loc} and {@link #viaRouteObj}.
	 * Changing {@link #loc}'s text clears {@link #locObj},
	 * unless <tt>locObj</tt> was created for this stop
	 * ({@link #locObjCreatedHere} is true).
	 */
	private Location locObj;

	/**
	 * if non-null, then <tt>currTS != null</tt>, and
	 * {@link #viaRouteObj} was created for this TStop.
	 */
	private Location locObjCreatedHere = null;

	/**
	 * if true, then <tt>currTS != null</tt>, and <tt>stopGas.gas_brandgrade</tt>
	 * was created for this TStop.
	 *<P>
	 * Remember that <tt>stopGas</tt> isn't loaded from the db until
	 * {@link #onClick_BtnGas(View)} or {@link #onClick_BtnEnterTStop(View)}.
	 */
	private boolean gbgCreatedHere = false;

	/** TStop's date-time for time_stop, time_continue */
	private Calendar stopTime, contTime;

	/** Which date to update? Set in {@link #onCreateDialog(int)}, checked in {@link #onDateSet(DatePicker, int, int, int)}. */
	private Calendar currentDateToPick;

	/**
	 * date formatter for use by {@link DateFormat#format(CharSequence, Calendar)},
	 * initialized once in {@link #updateDateButtons(int)}.
	 */
	private StringBuffer fmt_dow_shortdate;
	private Button btnStopTimeDate, btnContTimeDate;

	/** null unless currT.isRoadtrip */
	private Button btnRoadtripAreaStart, btnRoadtripAreaNone, btnRoadtripAreaEnd;

	/**
	 * For roadtrip, the currently hilighted geoarea button.
	 * Null, or one of {@link #btnRoadtripAreaStart},
	 * {@link #btnRoadtripAreaNone} or {@link #btnRoadtripAreaEnd}.
	 * @see #hilightRoadtripAreaButton(int, String, boolean, int)
	 * @see #areaLocs_areaID
	 * @see #onClick_BtnAreaStart(View)
	 * @see #onClick_BtnAreaNone(View)
	 * @see #onClick_BtnAreaEnd(View)
	 */
	private Button btnRoadtripArea_chosen;

	/** Called when the activity is first created.
	 * See {@link #updateTextAndButtons()} for remainder of init work,
	 * which includes checking the current driver/vehicle/trip
	 * and hiding/showing buttons as appropriate.
	 * Also calls {@link #onRestoreInstanceState(Bundle)} if
	 * our state was saved.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
	    super.onCreate(savedInstanceState);
	    db = new RDBOpenHelper(this);
	    setContentView(R.layout.trip_tstop_entry);

		odo_total_chk = (CheckBox) findViewById(R.id.trip_tstop_odo_total_chk);
		odo_total = (OdometerNumberPicker) findViewById(R.id.trip_tstop_odo_total);
		odo_total.setCheckboxOnChanges(odo_total_chk);
		odo_total.setTenthsVisibility(false);

		odo_trip_chk = (CheckBox) findViewById(R.id.trip_tstop_odo_trip_chk);
		odo_trip = (OdometerNumberPicker) findViewById(R.id.trip_tstop_odo_trip);
		odo_trip.setCheckboxOnChanges(odo_trip_chk);

		odo_total.setRelatedUncheckedOdoOnChanges(odo_trip, odo_trip_chk);
		odo_trip.setRelatedUncheckedOdoOnChanges(odo_total, odo_total_chk);

		tp_time_stop = (TimePicker) findViewById(R.id.trip_tstop_time_stop);
		tp_time_cont = (TimePicker) findViewById(R.id.trip_tstop_time_cont);
		tp_time_stop_chk = (CheckBox) findViewById(R.id.trip_tstop_time_stop_chk);
		tp_time_cont_chk = (CheckBox) findViewById(R.id.trip_tstop_time_cont_chk);
		{
			final boolean pref24hr = DateFormat.is24HourFormat(this);
			tp_time_stop.setIs24HourView(pref24hr);
			tp_time_cont.setIs24HourView(pref24hr);
		}
		btnStopTimeDate = (Button) findViewById(R.id.trip_tstop_btn_stop_date);
		btnContTimeDate = (Button) findViewById(R.id.trip_tstop_btn_cont_date);
		btnGas = (Button) findViewById(R.id.trip_tstop_btn_gas);

		// get currA, currV, currT, maybe currTS and prevLocObj
		if (! checkCurrentDriverVehicleTripSettings())
		{
			// Internal error: Current area/driver/vehicle/trip not found in db
        	Toast.makeText(getApplicationContext(),
                R.string.internal__current_notfound_area_driver_veh_trip,
                Toast.LENGTH_SHORT).show();
	    	startActivity(new Intent(TripTStopEntry.this, AndroidStartup.class));
	    	finish();
	    	return;
		}
		isCurrentlyStopped = (currTS != null);

		// if currTS != null, we'll read stopGas in updateTextAndButtons,
		// and set btnGas's green light.
		stopGas = null;

		Intent i = getIntent();
		if (i != null)
		{
			stopEndsTrip = i.getBooleanExtra(EXTRAS_FLAG_ENDTRIP, false);
			if (stopEndsTrip)
			{
				Button eb = (Button) findViewById(R.id.trip_tstop_btn_enter);
				if (eb != null)
					eb.setText(R.string.end_trip);
				setTitle(R.string.end_trip);
				TextView tv = (TextView) findViewById(R.id.trip_tstop_loc_label);
				if (tv != null)
				{
					if (0 == currT.getRoadtripEndAreaID())  // 0 for local trips
						tv.setText(R.string.destination);
					else
						tv.setText(R.string.destination_within_the_area);
				}
				tv = (TextView) findViewById(R.id.trip_tstop_prompt);
				if (tv != null)
					tv.setVisibility(View.GONE);

				if (currT.isFrequent())
				{
					tv = (TextView) findViewById(R.id.trip_tstop_end_mk_freq_text);
					if (tv != null)
						tv.setText(R.string.make_this_another_frequent_trip);
				}
			}
		} // else, stopEndsTrip is false

		if (! stopEndsTrip)
		{
			View vrow = findViewById(R.id.trip_tstop_row_end_mk_freq);
			if (vrow != null)
				vrow.setVisibility(View.GONE);

			if (currTS != null)
			{
				Button eb = (Button) findViewById(R.id.trip_tstop_btn_enter);
				if (eb != null)
					eb.setText(R.string.continu);
			}
		}

		loc = (AutoCompleteTextView) findViewById(R.id.trip_tstop_loc);
		loc.addTextChangedListener(this);
		areaLocs_areaID = -1;
		areaLocs = null;
		// loc, areaLocs, areaLocs_areaID will be filled soon.

		via = (AutoCompleteTextView) findViewById(R.id.trip_tstop_via);
		viaListener = new ViaRouteListenerWatcher();
		via.addTextChangedListener(viaListener);

		// adjust date/time fields, now that we know if we have currTS
		// and know if stopEndsTrip.
		final long timeNow = System.currentTimeMillis();

		// Continue Time:
		if (stopEndsTrip || (currTS == null))
		{
			contTime = null;
			tp_time_cont.setVisibility(View.GONE);
			tp_time_cont_chk.setVisibility(View.GONE);
			btnContTimeDate.setVisibility(View.GONE);
			findViewById(R.id.trip_tstop_time_cont_label).setVisibility(View.GONE);
		} else {
			contTime = Calendar.getInstance();
			contTime.setTimeInMillis(timeNow);
			tp_time_cont_chk.setChecked(true);
		}

		// Stop Time:
		stopTime = Calendar.getInstance();
		boolean setTimeStopCheckbox;
		if (isCurrentlyStopped)
		{
			int stoptime_sec = currTS.getTime_stop();
			if (stoptime_sec != 0)
			{
				setTimeStopCheckbox = true;
				stopTime.setTimeInMillis(1000L * stoptime_sec);

				if ((contTime != null)
					&& (Math.abs(timeNow - (1000L * stoptime_sec)) >= TIMEDIFF_HISTORICAL_MILLIS))
				{
					// Historical Mode: continue from that date & time, not from today
					contTime.setTimeInMillis(1000L * (stoptime_sec + 60));  // 1 minute later
				}

			} else {
				setTimeStopCheckbox = false;
				stopTime.setTimeInMillis(timeNow);
			}

			// Focus on continue-time, to scroll the screen down
			tp_time_cont.requestFocus();
		} else {
			// It's a new stop.
			// How recent was that vehicle's most recent trip? (Historical Mode)
			{
				long latestVehTime = 1000L * currV.readLatestTime(currT);
				if ((latestVehTime != 0L)
				    && (Math.abs(latestVehTime - timeNow) >= TIMEDIFF_HISTORICAL_MILLIS))
				{
					Toast.makeText(this,
						R.string.using_old_date_due_to_previous,
						Toast.LENGTH_SHORT).show();
					setTimeStopCheckbox = false;
				} else {
					latestVehTime = timeNow;
					setTimeStopCheckbox = true;
				}
				stopTime.setTimeInMillis(latestVehTime);
			}
		}
		tp_time_stop_chk.setChecked(setTimeStopCheckbox);
		updateDateButtons(0);
		initTimePicker(stopTime, tp_time_stop);
		if (contTime != null) 
			initTimePicker(contTime, tp_time_cont);
		btnRoadtripArea_chosen = null;  // Will soon be set by calling hilightRoadtripAreaButton

		// Give status, read odometer, etc;
		// if currTS != null, fill fields from it.
		updateTextAndButtons();
		if ((savedInstanceState != null) && savedInstanceState.containsKey(TSTOP_BUNDLE_SAVED_MARKER))
			onRestoreInstanceState(savedInstanceState);

		// If needed, determine current area.
		// Based on current area, set up Location auto-complete
		if (areaLocs_areaID == -1)
		{
			if (currTS != null)
			{
				areaLocs_areaID = currTS.getAreaID();
			}
			else if ((prevLocObj != null) && currT.isRoadtrip() && ! stopEndsTrip)
			{
				final int pArea = prevLocObj.getAreaID();
				if ((pArea == 0)
					|| (pArea == currT.getAreaID())
					|| (pArea == currT.getRoadtripEndAreaID()))
					areaLocs_areaID = pArea;
			}
		}
		if (areaLocs_areaID == -1)
		{
			if (! stopEndsTrip)
			{
				areaLocs_areaID = currA.getID();
			} else {
				areaLocs_areaID = currT.getRoadtripEndAreaID();
				if (areaLocs_areaID == 0)  // rtrEndAreaID is 0 for local trips
					areaLocs_areaID = currA.getID();
			}
		}
		areaLocs = Location.getAll(db, areaLocs_areaID);
		if (areaLocs != null)
		{
			ArrayAdapter<Location> adapter = new ArrayAdapter<Location>(this, R.layout.list_item, areaLocs);
			loc.setAdapter(adapter);
			loc.setOnItemClickListener(this);
		}

		// See if we're stopping on a frequent trip:
		if ((! isCurrentlyStopped) && currT.isFrequent())
		{
			FreqTrip ft = Settings.getCurrentFreqTrip(db, false);
			if (stopEndsTrip)
			{
				// Ending frequent trip. Copy default field values from FreqTrip.
				copyValuesFromFreqTrip(ft);
			}
			else if (ft != null)
			{
				// Not ending trip yet. Should ask the user to choose a FreqTripTStop, if available.
				try {
					Settings cTSL = new Settings(db, Settings.CURRENT_FREQTRIP_TSTOPLIST);
					if (cTSL.getStrValue() != null)
					{
						startActivityForResult
							(new Intent(this, TripTStopChooseFreq.class),
							 R.id.main_btn_freq_local);
						// When it returns with the result, its intent should contain
						// an int extra "_id" that's the chosen
						// FreqTripTStop ID, or 0 for a new non-freq TStop.
						// (see onActivityResult)
					}
				} catch (Throwable e) { } // RDBKeyNotFoundException
			}
		}

		// If not roadtrip, hide the area buttons;
		// otherwise, load their values and hilight if stopped.
		if (currT.isRoadtrip() && ! stopEndsTrip)
		{
			final int gaID_s = currT.getAreaID(),
				gaID_e = currT.getRoadtripEndAreaID();
			final GeoArea ga_s, ga_e;
			try {
				ga_s = new GeoArea(db, gaID_s);
				ga_e = new GeoArea(db, gaID_e);
			} catch (Throwable e) {
				// TODO not found, inconsistency
				return;
			}
			btnRoadtripAreaStart = (Button) findViewById(R.id.trip_tstop_btn_area_start);
			btnRoadtripAreaNone = (Button) findViewById(R.id.trip_tstop_btn_area_none);
			btnRoadtripAreaEnd = (Button) findViewById(R.id.trip_tstop_btn_area_end);
			btnRoadtripAreaStart.setText(ga_s.getName());
			btnRoadtripAreaEnd.setText(ga_e.getName());
			hilightRoadtripAreaButton(areaLocs_areaID, null, false, 0);
		} else {
			View v = findViewById(R.id.trip_tstop_area_label);
			if (v != null)
				v.setVisibility(View.GONE);

			v = findViewById(R.id.trip_tstop_area_buttons);
			if (v != null)
				v.setVisibility(View.GONE);
		}		
	}

	/** set a timepicker's hour and minute, based on a calendar's current time */
	private final static void initTimePicker(Calendar c, TimePicker tp)
	{
		tp.setCurrentHour(c.get(Calendar.HOUR_OF_DAY));
		tp.setCurrentMinute(c.get(Calendar.MINUTE));
	}

	/**
	 * Hilight the matching button, update {@link #areaLocs_areaID},
	 * and optionally update related data.
	 * @param areaID  GeoArea ID to hilight
	 * @param newAreaText  New GeoArea's name, or null for "none" (no area).
	 *   Not needed unless <tt>alsoUpdateData</tt>.
	 * @param alsoUpdateData If true, also update currTS,
	 *   and re-query location fields.  If false, only change the
	 *   checkmark, {@link #areaLocs_areaID}, and {@link #btnRoadtripArea_chosen}.
	 * @param confirmChange  User-confirm action, if <tt>alsoUpdateData</tt>,
	 *   if they've already chosen a Location in another geoarea:
	 *   <UL>
	 *   <LI> 0: Ask the user in a popup AlertDialog
	 *   <LI> 1: Confirm changing to this location in the new area
	 *   <LI> 2: Clear the Location and ViaRoute fields
	 *   </UL>
	 *   The buttons of the popup in choice 0 will either call this method again,
	 *   with <tt>confirmChange</tt> 1 or 2, or cancel changing the GeoArea.
	 */
	private void hilightRoadtripAreaButton
		(final int areaID, String newAreaText, final boolean alsoUpdateData, final int confirmChange)
	{
		if ((areaLocs_areaID == areaID) && alsoUpdateData)
			return;

		final boolean locObjIsDifferentArea = alsoUpdateData
			&& (locObj != null) && (areaID != locObj.getAreaID())
			&& ((locObjCreatedHere == null) || (locObj.getID() != locObjCreatedHere.getID()));
		if (locObjIsDifferentArea && (confirmChange == 0))
		{
			// popup to confirm changing it; see confirmChange javadoc
			if (newAreaText == null)
				newAreaText = getResources().getString(R.string.none);
			showRoadtripAreaButtonConfirmDialog
				(areaID, locObj.toString(), newAreaText, via.getText().toString());

			return;  // <--- Early return: Popup to confirm ---
		}

		if (btnRoadtripArea_chosen != null)
			// un-hilight previous
			btnRoadtripArea_chosen.setCompoundDrawablesWithIntrinsicBounds
			  (0, 0, 0, 0);

		Button toChg;
		if (areaID == currT.getAreaID())
			toChg = btnRoadtripAreaStart;
		else if (areaID == currT.getRoadtripEndAreaID())
			toChg = btnRoadtripAreaEnd;
		else if (areaID == 0)
			toChg = btnRoadtripAreaNone;
		else
			toChg = null;

		areaLocs_areaID = areaID;
		btnRoadtripArea_chosen = toChg;

		if (toChg != null)
			toChg.setCompoundDrawablesWithIntrinsicBounds
			  (R.drawable.checkmark_green19, 0, 0, 0);

		if (! alsoUpdateData)
		{
			return;   // <--- Early return: No data changes ---
		}

		final Editable prevLocText;
		if (locObjIsDifferentArea && (confirmChange == 2))
		{
			prevLocText = null;  // clear
			locObj = null;
			updateViaRouteAutocomplete(null, false);
		} else {
			prevLocText = loc.getText();  // ok to keep text & obj
		}
		areaLocs = Location.getAll(db, areaLocs_areaID);
		if (areaLocs != null)
		{
			ArrayAdapter<Location> adapter = new ArrayAdapter<Location>(this, R.layout.list_item, areaLocs);
			loc.setAdapter(adapter);
		} else {
			loc.setAdapter( (ArrayAdapter<Location>) null);
		}
		if (prevLocText != null)
			loc.setText(prevLocText);
		else
			loc.setText("");
	}

	/**
	 * Called from {@link #hilightRoadtripAreaButton(int, String, boolean, int)}
	 * when the user should confirm changing the GeoArea.
	 * @param areaID  GeoArea ID to confirm changing to
	 * @param locText  Location text currently entered, or null
	 * @param newAreaText  New GeoArea's name
	 * @param viaText  ViaRoute text currently entered, or null
	 */
	private void showRoadtripAreaButtonConfirmDialog
		(final int areaID, final String locText, final String newAreaText, final String viaText)
	{
    	AlertDialog.Builder alert = new AlertDialog.Builder(this);
    	alert.setTitle(R.string.confirm);
    	// Build popup message, including texts passed in
    	{
			String txt = getResources().getString(R.string.trip_tstop_entry_prompt_geoarea_confirm);
        	StringBuffer sb = new StringBuffer(txt);
        	if ((locText != null) && (locText.length() > 0))
        	{
        		sb.append("\n");
        		sb.append(getResources().getString(R.string.location));
        		sb.append(": ");
        		sb.append(locText);
        	}
    		sb.append("\n");
    		sb.append(getResources().getString(R.string.new_area));
    		sb.append(": ");
    		sb.append(newAreaText);        		
        	if ((viaText != null) && (viaText.length() > 0))
        	{
        		sb.append("\n");
        		sb.append(getResources().getString(R.string.via_route));
        		sb.append(": ");
        		sb.append(viaText);
        	}
        	alert.setMessage(sb);
    	}
    	alert.setPositiveButton(R.string.trip_tstop_entry_keep_location, new DialogInterface.OnClickListener() {
	    	  public void onClick(DialogInterface dialog, int whichButton)
	    	  {
	    		  hilightRoadtripAreaButton(areaID, newAreaText, true, 1);  // keep location, change area
	    	  }
	    	});
    	alert.setNegativeButton(R.string.trip_tstop_entry_clear_location, new DialogInterface.OnClickListener() {
	    	  public void onClick(DialogInterface dialog, int whichButton)
	    	  {
	    		  hilightRoadtripAreaButton(areaID, newAreaText, true, 2);  // clear location, change area
	    	  }
	    	});
    	alert.setNeutralButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
	    	  public void onClick(DialogInterface dialog, int whichButton)
	    	  {
	    		  // don't change the area, do nothing
	    	  }
	    	});
    	alert.show();
	}

	/**
	 * Check Settings table for <tt>CURRENT_DRIVER</tt>, <tt>CURRENT_VEHICLE</tt>,
	 * <tt>CURRENT_TRIP</tt>.
	 * Set {@link #currA}, {@link #currD}, {@link #currV} and {@link #currT}.
	 * Set {@link #currTS} if <tt>CURRENT_TSTOP</tt> is set.
	 * Set {#link {@link #prevLocObj}} if <tt>PREV_LOCATION</tt> is set.
	 *<P>
	 * If there's an inconsistency between Settings and GeoArea/Vehicle/Person tables, don't fix it,
	 * but don't load objects either.
	 *
	 * @return true if settings exist and are OK, false otherwise.
	 */
	private boolean checkCurrentDriverVehicleTripSettings()  // TODO refactor common
	{
		currA = Settings.getCurrentArea(db, false);
		if (currA == null)
		{
    		final String homearea = getResources().getString(R.string.home_area);
    		currA = new GeoArea(homearea);
    		currA.insert(db);
    		Settings.setCurrentArea(db, currA);
		}
		currD = Settings.getCurrentDriver(db, false);
		currV = Settings.getCurrentVehicle(db, false);
		currT = Settings.getCurrentTrip(db, true);
		currTS = Settings.getCurrentTStop(db, false);
		prevLocObj = Settings.getPreviousLocation(db, false);

		return ((currA != null) && (currD != null) && (currV != null) && (currT != null));
		// null prevTS OK, null prevLocObj OK
	}

	/**
	 * Update the text about current driver, vehicle and trip;
	 * update odometers from {@link Trip#readHighestOdometers()}.
	 * If <tt>{@link #currTS} != null</tt>, fill fields from that instead.
	 * Called as an ending part of {@link #onCreate(Bundle)}.
	 */
	private void updateTextAndButtons()
	{
		int[] odos;
		if (currTS == null)
		{
			odos = currT.readHighestOdometers();
			odoTotalOrig = odos[0];
			odoTripOrig = odos[1];
			odo_total.setCurrent10d(odos[0], false);
			odo_trip.setCurrent10d(odos[1], false);
			return;  // <--- Early return: no current tstop ---
		}

		int odo = currTS.getOdo_total();
		if (odo != 0)
		{
			odoTotalOrig = odo;
			odo_total.setCurrent10d(odo, false);
			odo_total_chk.setChecked(true);
			odos = null;
		} else {
			odos = currT.readHighestOdometers();
			odoTotalOrig = odos[0];
			odo_total.setCurrent10d(odos[0], false);
		}
		odo = currTS.getOdo_trip();
		if (odo != 0)
		{
			odoTripOrig = odo;
			odo_trip.setCurrent10d(odo, false);
			odo_trip_chk.setChecked(true);
		} else {
			if (odos == null)
				odos = currT.readHighestOdometers();
			odoTripOrig = odos[1];
			odo_trip.setCurrent10d(odos[1], false);
		}

		// fill text fields, unless null or 0-length
		setEditText(currTS.readLocationText(), R.id.trip_tstop_loc);
		setEditText(currTS.getVia_route(), R.id.trip_tstop_via);
		setEditText(currTS.getComment(), R.id.trip_tstop_comment);
		locObj = null;
		if (currTS.getLocationID() > 0)
		{
			try
			{
				locObj = new Location(db, currTS.getLocationID());
				if (currTS.isSingleFlagSet(TStop.TEMPFLAG_CREATED_LOCATION))
				{
					locObjCreatedHere = locObj;
				}
			} catch (RDBKeyNotFoundException e)
			{ }
		}

		updateViaRouteAutocomplete(null, true);  // sets viaRouteObj from currTS.via_id;
			// also sets viaRouteObjCreatedHere if applicable.

		if (via.getText().length() == 0)
		{
			if (viaRouteObj != null)
				via.setText(viaRouteObj.toString());
			else if ((currTS != null) && (currTS.getVia_route() != null))
				via.setText(currTS.getVia_route());
		}

		try
		{
			stopGas = new TStopGas(db, currTS.getID());
			btnGas.setCompoundDrawablesWithIntrinsicBounds
			  ((stopGas != null) ? android.R.drawable.presence_online
				: android.R.drawable.presence_invisible,
				0, 0, 0);
			if ((stopGas.gas_brandgrade_id != 0) && currTS.isSingleFlagSet(TStop.TEMPFLAG_CREATED_GASBRANDGRADE))
				gbgCreatedHere = true;

		} catch (RDBKeyNotFoundException e) {
			stopGas = null;
		}
	}

	/**
	 * Set the stop-date/continue-date button text based on
	 * {@link #stopTime}'s, {@link #contTime}'s value.
	 *
	 * @param which  1 for stoptime, 2 for conttime, 0 for both.
	 *   If {@link #contTime} is <tt>null</tt>, its button text is not changed.
	 */
	private void updateDateButtons(final int which)
	{
		if (fmt_dow_shortdate == null)
			fmt_dow_shortdate = Misc.buildDateFormatDOWShort(this, true);

		// update btn text to current times:
		if (which != 2)
			btnStopTimeDate.setText(DateFormat.format(fmt_dow_shortdate, stopTime));
		if ((which != 1) && (contTime != null))
			btnContTimeDate.setText(DateFormat.format(fmt_dow_shortdate, contTime));
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

	/** For roadtrips, update GUI and data from a click on the 'starting geoarea' button. */
	public void onClick_BtnAreaStart(View v)
	{
		hilightRoadtripAreaButton
			(currT.getAreaID(), btnRoadtripAreaStart.getText().toString(), true, 0);
	}

	/** For roadtrips, update GUI and data from a click on the 'no geoarea' button. */
	public void onClick_BtnAreaNone(View v)
	{
		hilightRoadtripAreaButton(0, null, true, 0);
	}

	/** For roadtrips, update GUI and data from a click on the 'ending geoarea' button. */
	public void onClick_BtnAreaEnd(View v)
	{
		hilightRoadtripAreaButton
			(currT.getRoadtripEndAreaID(), btnRoadtripAreaEnd.getText().toString(), true, 0);
	}

	/** Show or hide the Via dropdown if available */
	public void onClick_BtnViaDropdown(View v)
	{
		if (loc.getText().length() == 0)
		{
			Toast.makeText(this, R.string.please_enter_the_location, Toast.LENGTH_SHORT).show();
			return;
		}

		if (via.getAdapter() == null)
		{
			Toast.makeText(this, R.string.trip_tstop_entry_no_vias_entered, Toast.LENGTH_SHORT).show();
			return;
		}

		if (via.isPopupShowing())
			via.dismissDropDown();
		else
			via.showDropDown();
	}

	/**
	 * Read fields, and record this TStop in the database.
	 * If continuing from the stop, update {@link Settings#PREV_LOCATION}.
	 * If {@link #EXTRAS_FLAG_ENDTRIP}, end the Trip too.
	 * Finish this Activity.
	 *<P>
	 * Checks for required fields, such as description.
	 * If missing, prompt for it and don't end the Activity yet.
	 * IF ending the trip, the total odometer is required
	 * for this stop.  If creating a Frequent Trip, the
	 * ending trip odometer is also required.
	 */
	public void onClick_BtnEnterTStop(View v)
	{
		String locat = null, via_route = null, comment = null;
		boolean createdLoc = false, createdVia = false;
		int locID = 0;

		CheckBox b;

		int stopTimeSec = 0;  // optional stop-time
		b = (CheckBox) findViewById (R.id.trip_tstop_time_stop_chk);
		if ((b != null) && b.isChecked())
		{			
			if (tp_time_stop != null)
			{
				stopTime.set(Calendar.HOUR_OF_DAY, tp_time_stop.getCurrentHour());
				stopTime.set(Calendar.MINUTE, tp_time_stop.getCurrentMinute());
				stopTimeSec = (int) (stopTime.getTimeInMillis() / 1000L);
			}
		}

		locat = textIfEntered(R.id.trip_tstop_loc);
		if (locat == null)
		{
			loc.requestFocus();
        	Toast.makeText(this,
    			R.string.please_enter_the_location,
                Toast.LENGTH_SHORT).show();
        	return;  // <--- Early return: missing field ---
		}
		via_route = textIfEntered(R.id.trip_tstop_via);
		comment = textIfEntered(R.id.trip_tstop_comment);

		int odoTotal = 0, odoTrip = 0;
		if ((odo_total_chk != null) && odo_total_chk.isChecked())
			odoTotal = odo_total.getCurrent10d();
		if ((odo_trip_chk != null) && odo_trip_chk.isChecked())
			odoTrip = odo_trip.getCurrent10d();
		if ((odoTotal == 0) &&
			(stopEndsTrip || (bundleGas != null)))
		{
			odo_total.requestFocus();
        	Toast.makeText(this,
    			R.string.please_check_the_total_odometer,
                Toast.LENGTH_SHORT).show();
        	return;  // <--- Early return: missing required field ---
		}

		final boolean mkFreqTrip;
		if (! stopEndsTrip)
		{
			mkFreqTrip = false;
		} else {
			CheckBox et = (CheckBox) findViewById(R.id.trip_tstop_end_mk_freq);
			mkFreqTrip = (et != null) && et.isChecked();
		}

		if (mkFreqTrip && (odoTrip == 0))
		{
			odo_trip.requestFocus();
        	Toast.makeText(this,
    			R.string.please_check_the_trip_odometer,
                Toast.LENGTH_SHORT).show();
        	return;  // <--- Early return: missing required field ---
		}

		// Make sure odometers don't run backwards
		if ((odoTrip > 0) || (odoTotal > 0))
		{
			View focusStopHere = null;
			String toastText = null;
			final int[] odos = currT.readHighestOdometers(currTS);

			if ((odoTotal > 0) && (odoTotal < odos[0]))
			{
				// check if it's only the hidden tenths digit
				final int wholeOdo0 = odos[0] / 10;
				if (wholeOdo0 == (odoTotal/10))
				{
					// visually the same; update hidden tenths to keep the db consistent
					odoTotal = odos[0];
					odo_total.setCurrent10d(odoTotal, false);
				} else {
					// visually different
					focusStopHere = odo_total;
					toastText = getResources().getString(R.string.trip_tstop_entry_totalodo_low, wholeOdo0);  // %1$d
				}
			}
			else if ((odoTrip > 0) && (odoTrip < odos[1]))
			{
				focusStopHere = odo_trip;
				toastText = getResources().getString(R.string.trip_tstop_entry_tripodo_low, odos[1] / 10.0);  // %1$.1f
			}
			
			if (focusStopHere != null)
			{
				focusStopHere.requestFocus();
				Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show();
				return;  // <--- Early return: Odometer is too low ---
			}
		}

		// continue-time
		int contTimeSec = 0;
		if (isCurrentlyStopped && (! stopEndsTrip) && tp_time_cont_chk.isChecked() && (contTime != null))
		{
			// Convert to unix time:
			contTime.set(Calendar.HOUR_OF_DAY, tp_time_cont.getCurrentHour());
			contTime.set(Calendar.MINUTE, tp_time_cont.getCurrentMinute());
			contTimeSec = (int) (contTime.getTimeInMillis() / 1000L);

			// Validate continue-time:
			if (stopTimeSec == 0)
				stopTimeSec = currT.getTime_start();
			if ((stopTimeSec != 0) && (contTimeSec < stopTimeSec)) 
			{
				tp_time_cont.requestFocus();
	        	Toast.makeText(this,
        			R.string.this_time_must_be_no_earlier_than,
                    Toast.LENGTH_LONG).show();
				return;  // <--- inconsistent time ---
			}
		}

		// area ID (roadtrips)
		if (stopEndsTrip && currT.isRoadtrip()
			&& (areaLocs_areaID != currT.getRoadtripEndAreaID()))
		{
			// Scroll to top and show toast
			final ScrollView sv = (ScrollView) findViewById(R.id.trip_tstop_scrollview);
			if (sv != null)
				sv.post(new Runnable() {
					public void run() {
						sv.fullScroll(ScrollView.FOCUS_DOWN);
					}
				});
			Toast.makeText(this, R.string.trip_tstop_entry_roadtrip_end_geoarea, Toast.LENGTH_SHORT).show();
			return;  // <--- Early return: Wrong area ID ---
		}
		// areaID is set in onCreate, but check just in case.
		if ((areaLocs_areaID <= 0) && (stopEndsTrip || ! currT.isRoadtrip()))
		{
			if (! stopEndsTrip)
			{
				areaLocs_areaID = currA.getID();
			} else {
				areaLocs_areaID = currT.getRoadtripEndAreaID();
				if (areaLocs_areaID == 0)  // rtrEndAreaID is 0 for local trips
					areaLocs_areaID = currA.getID();
			}
		}

		/**
		 * Done checking field contents, time to update the db.
		 * tsid is the TStop ID we'll create or update here.
		 */
		final int tsid;

		// If our total-odo was calculated by a via's distance, or by a freqtrip's distance,
		// see if the user changed it from the calculated value.
		// Adjust the hidden tenths digit value if needed.
		// This should help avoid "rounding error" accumulating from the hidden tenths digit
		// of the total odometer, which should mean fewer such manual changes.
		if ((odosAreSetFrom_total != 0)
		    && (odoTotal > odosAreSetFrom_total)
		    && ((odoTotal % 10) >= 5))
		{
			// Since odoTotal != 0, the user set the total checkmark or manually changed it.
			// Also check if they also changed the trip-odo.
			final int tripdiff;
			if ((odoTrip != 0) && (odosAreSetFrom_trip != 0))
				tripdiff = odoTrip - odosAreSetFrom_trip;
			else
				tripdiff = 0;

			if ((odoTotal / 10) != ((odosAreSetFrom_total + tripdiff) / 10))
			{
				// If possible, make the adjustment.
				// Since we know odoTotal's tenths >= 5, this subtraction only affects
				// the hidden tenths digit, not the visible whole part of odoTotal.
				// To avoid inconsistencies when stops are close together,
				// first check the previous highest recorded total-odo for this trip.

				final int odoHighest = currT.readHighestOdoTotal();
				final int odoAdjTotal = odoTotal - 5;
				if (odoAdjTotal > odoHighest)
					// assert: (odoTotal - odoHighest) > 5
					odoTotal = odoAdjTotal;
				else if ((odoTotal - odoHighest) > 1)
					// assert: (odoTotal - odoHighest) <= 5 and odoTotal > odoHighest
					odoTotal = odoHighest + 1;  // gives -4 -3 -2 or -1 to odoTotal
				Toast.makeText(this, "L1087 odoTotal drift corrected to " + odoTotal, Toast.LENGTH_LONG).show();
			}
		}

		// Get or create the Location db record,
		// if we don't already have it
		if ((locObj == null)
			|| (! locObj.getLocation().equalsIgnoreCase(locat))
			|| ((areaLocs_areaID != locObj.getAreaID())
				&& ((locObjCreatedHere == null) || (locObj.getID() != locObjCreatedHere.getID()))))
		{
			final int locatIdx = loc.getListSelection();
			ListAdapter la = loc.getAdapter();
			if ((locatIdx != ListView.INVALID_POSITION) && (locatIdx != ListAdapter.NO_SELECTION) && (la != null))
			{
				locObj = (Location) la.getItem(locatIdx);
				if (locObj != null)
					locID = locObj.getID();
			}
			if (locObj == null)
			{
				if (locObjCreatedHere == null)
				{
					locObj = new Location(areaLocs_areaID, null, null, locat);
					locID = locObj.insert(db);
					createdLoc = true;
				} else {
					// re-use it
					locObj = locObjCreatedHere;
					locID = locObj.getID();
					locObj.setAreaID(areaLocs_areaID);
					locObj.setLocation(locat);
					locObj.commit();
				}
			}
		} else {
			// not null, and text matches: use it
			locID = locObj.getID();

			if ((locObjCreatedHere != null) && (locID == locObjCreatedHere.getID())
				&& (areaLocs_areaID != locObjCreatedHere.getAreaID()))
			{
				locObjCreatedHere.setAreaID(areaLocs_areaID);
				locObjCreatedHere.commit();

				// no need to update locObj.areaid field too, because
				// we're resuming from this stop, and won't be at
				// locObj next time this activity is called.
			}
					
		}
		if ((locObjCreatedHere != null) && (locID != locObjCreatedHere.getID()))
		{
			// record created here wasn't used, so remove it from db
			locObjCreatedHere.delete();
			locObjCreatedHere = null;
		}

		// Get or create the ViaRoute db record,
		// if we don't already have it
		int viaID;
		if ((locID == 0) || (via_route == null) || (prevLocObj == null))
		{
			viaID = 0;
			viaRouteObj = null;  // it's probably already null
		} else if ((viaRouteObj != null) && viaRouteObj.getDescr().equalsIgnoreCase(via_route))
		{
			viaID = viaRouteObj.getID();

			// if isCurrentlyStopped, or ending the trip,
			// and we don't yet have odo_dist for
			// this ViaRoute, set it from this tstop:
			if (isCurrentlyStopped || stopEndsTrip)
			{
				if (odo_trip_chk.isChecked())
				{
					if ((0 == viaRouteObj.getOdoDist())
						|| ((viaRouteObjCreatedHere != null)
							&& (viaID == viaRouteObjCreatedHere.getID())))
					{
						final int prev_tripOdo = TStop.tripReadPrevTStopOdo(currT, prevLocObj, currTS);
						if (prev_tripOdo != -1)
						{
							int odo_dist = odo_trip.getCurrent10d() - prev_tripOdo;
							viaRouteObj.setOdoDist(odo_dist);  // if unchanged, does nothing
							viaRouteObj.commit();              // if unchanged, does nothing
						}
					}
				}
				else if ((viaRouteObjCreatedHere != null) && (viaID == viaRouteObjCreatedHere.getID()))
				{
					// odo_trip_chk not checked, but we created ViaRoute for this TStop;
					// clear that via's odo_dist
					if (0 != viaRouteObjCreatedHere.getOdoDist())
					{
						viaRouteObjCreatedHere.setOdoDist(0);
						viaRouteObjCreatedHere.commit();
					}
				}
			}
		} else {
			// via-route text doesn't match, create new ViaRoute
			int odo_dist = 0;
			if (odo_trip_chk.isChecked())
			{
				final int prev_tripOdo = TStop.tripReadPrevTStopOdo(currT, prevLocObj, currTS);
				if (prev_tripOdo != -1)
					odo_dist = odo_trip.getCurrent10d() - prev_tripOdo;
			}
			if (viaRouteObjCreatedHere == null)
			{
				viaRouteObj = new ViaRoute(prevLocObj.getID(), locID, odo_dist, via_route);
				viaID = viaRouteObj.insert(db);
				createdVia = true;
			} else {
				// re-use it
				viaRouteObj = viaRouteObjCreatedHere;
				viaID = viaRouteObj.getID();
				viaRouteObj.set(locID, odo_dist, via_route);
				viaRouteObj.commit();
			}
		}
		if ((viaRouteObjCreatedHere != null) && (viaID != viaRouteObjCreatedHere.getID()))
		{
			// record created here wasn't used, so remove it from db
			viaRouteObjCreatedHere.delete();
			viaRouteObjCreatedHere = null;
		}

		// If we've chosen a frequent tstop, remove
		// it from the list of unused ones.
		if ((wantsFTS != null) && (locID == wantsFTS.getLocationID()) && ! stopEndsTrip)
		{
			Settings.reduceCurrentFreqTripTStops(db, wantsFTS);
		}

		// If the stop has gas, check for a new GasBrandGrade.
		boolean createdGasBrandGrade = false;
		if ((bundleGas != null)
			&& bundleGas.containsKey(TripTStopGas.EXTRAS_FIELD_BRANDGRADE))
		{
			int bgid = bundleGas.getInt(TripTStopGas.EXTRAS_FIELD_BRANDGRADE_ID);
			String gbName = bundleGas.getString(TripTStopGas.EXTRAS_FIELD_BRANDGRADE);
			if (gbName != null)
			{
				gbName = gbName.trim();
				if (gbName.length() == 0)
					gbName = null;
			}

			createdGasBrandGrade = (0 == bgid);
			if (createdGasBrandGrade)
			{
				if (gbName != null)
				{
					GasBrandGrade bg = new GasBrandGrade(gbName);
					bgid = bg.insert(db);
					bundleGas.putInt(TripTStopGas.EXTRAS_FIELD_BRANDGRADE_ID, bgid);
					if (stopGas != null)
					{
						stopGas.gas_brandgrade_id = bgid;
						stopGas.gas_brandgrade = bg;
					}
				} else {
					createdGasBrandGrade = false;  // null or 0-length name
					if (gbgCreatedHere)
					{
						// TODO delete the one created, since we aren't using it
					}
				}
			}
			else if (gbgCreatedHere && (0 != bgid))
			{
				// brand was created here when we stopped, now we're
				// continuing; see if the created name was changed.
				if (stopGas.gas_brandgrade == null)
				{
					try
					{
						stopGas.gas_brandgrade = new GasBrandGrade(db, bgid);
					}
					catch (Throwable th) {}					
				}

				if ((stopGas.gas_brandgrade != null)
					&& ! gbName.equalsIgnoreCase(stopGas.gas_brandgrade.getName()))
				{
					stopGas.gas_brandgrade.setName(gbName);
					stopGas.gas_brandgrade.commit();
				}
			}

		}  // if (bundle contains gas brand)

		if (! isCurrentlyStopped)
		{
			// Create a new TStop; set tsid (not currTS).
			int areaID;
			if (stopEndsTrip)
				areaID = currT.getRoadtripEndAreaID();  // will be 0 if local trip
			else if (currT.isRoadtrip() && (areaLocs_areaID != -1))
				areaID = areaLocs_areaID;  // db contents note: unless stopEndsTrip, tstop.a_id always 0 before March 2011
			else
				areaID = 0;  // unused in local trip tstops

			int flags = 0;
			if (! stopEndsTrip)
			{
				if (createdLoc)
					flags |= TStop.TEMPFLAG_CREATED_LOCATION;
				if (createdVia)
					flags |= TStop.TEMPFLAG_CREATED_VIAROUTE;
				if (createdGasBrandGrade)
					flags |= TStop.TEMPFLAG_CREATED_GASBRANDGRADE;
			}
			TStop newStop = new TStop(currT, odoTotal, odoTrip, stopTimeSec, 0, locID, areaID, null, null, flags, viaID, comment);
			tsid = newStop.insert(db);
			currT.addCommittedTStop(newStop);  // add it to the Trip's list
			if (! stopEndsTrip)
				Settings.setCurrentTStop(db, newStop);
			// Don't set currTS field yet, it needs to be null for code here.

			// Now set the gas info, if any:
			if (bundleGas != null)
			{
				stopGas = TripTStopGas.saveDBObjFromBundle(bundleGas, null);
				if (stopGas != null)
				{
					stopGas.setTStop(newStop);
					stopGas.insert(db);
					newStop.setFlagSingle(TStop.FLAG_GAS);
					newStop.commit();
				}
			}
		} else {
			// Currently stopped; resuming from stop, or ending trip.
			tsid = currTS.getID();
			currTS.setOdos(odoTotal, odoTrip);
			// text fields, info fields
			currTS.setLocationID(locID);
			currTS.setVia_id(viaID);
			currTS.setComment(comment);
			if (currT.isRoadtrip() && (areaLocs_areaID != -1)
				&& (areaLocs_areaID != currTS.getAreaID()))
				currTS.setAreaID(areaLocs_areaID);
			currTS.clearTempFlags();
			// continue-time
			if ((! stopEndsTrip) && (contTimeSec != 0))
				currTS.setTime_continue(contTimeSec, false);
			if (stopEndsTrip)
			{
				final int areaID = currT.getRoadtripEndAreaID();  // will be 0 if local trip
				if (areaID != 0)
					currTS.setAreaID(areaID);
			}

			currTS.commit();

			// Now set the gas info, if any:
			if (bundleGas != null)
			{
				stopGas = TripTStopGas.saveDBObjFromBundle(bundleGas, stopGas);
				if (stopGas != null)
				{
					if (stopGas.getID() > 0)
					{
						stopGas.commit();
						if (! currTS.isSingleFlagSet(TStop.FLAG_GAS))
						{
							currTS.setFlagSingle(TStop.FLAG_GAS);
							currTS.commit();
						}
					} else {
						stopGas.setTStop(currTS);
						stopGas.insert(db);
						currTS.setFlagSingle(TStop.FLAG_GAS);
						currTS.commit();
					}
				}
				// TODO else delete?
			}
		}  // if (! currently stopped)

		if ((stopGas != null) && (bundleGas != null))
		{
			// For gas, update Location's latest_gas_brandgrade_id
			if (stopGas.gas_brandgrade_id != 0)
			{
				locObj.setLatestGasBrandGradeID(stopGas.gas_brandgrade_id);
				locObj.commit();  // does nothing if unchanged from location's previous bgid
			}
		}

		if (stopEndsTrip)
			endCurrentTrip(tsid, odo_total.getCurrent10d(), mkFreqTrip);

		if (currTS != null)  // if we were stopped already...
		{
			Settings.setCurrentTStop(db, null);  // clear it
			Settings.setPreviousLocation(db, locObj); // update prev_loc
		}

		finish();
	}

	/**
	 * Copy field values from this {@link FreqTripTStop} ID
	 * (and also set {@link #wantsFTS}):
	 * odo_trip (if greater than current setting), locID, viaID.
	 * @see #copyValuesFromFreqTStop(int)
	 */
	private void copyValuesFromFreqTStop(final int ftsID)
	{
		try {
			wantsFTS = new FreqTripTStop(db, ftsID);
		} catch (Throwable e) {  // RDBKeyNotFoundException
			return;
		}

		copyValuesFromFreq
			(wantsFTS.getOdo_trip(), wantsFTS.getLocationID(), wantsFTS.getViaID());
	}

	/**
	 * Copy field values from this {@link FreqTrip}:
	 * odo_trip (if greater than current setting), locID, viaID.
	 * @see #copyValuesFromFreqTStop(int)
	 */
	private void copyValuesFromFreqTrip(final FreqTrip ft)
	{
		if (ft == null)
			return;

		copyValuesFromFreq
			(ft.getEnd_odoTrip(), ft.getEnd_locID(), ft.getEnd_ViaRouteID());
	}

	/**
	 * Copy field values from this freqtrip or freqtripstop:
	 * odo_trip (if greater than current setting), odo_total, locID, viaID.
	 */
	private void copyValuesFromFreq
		(final int odoStop, final int locID, final int viaID)
	{
		final int odoDiff = odoStop - odo_trip.getCurrent10d();
		if (odoDiff > 0)
		{
			odosAreSetFrom_trip = odoStop;
			odosAreSetFrom_total = odo_total.getCurrent10d() + odoDiff;
			odo_trip.setCurrent10d(odoStop, false);
			odo_trip_chk.setChecked(true);
			odo_total.setCurrent10d(odosAreSetFrom_total, false);
			odosAreSetFromFreq = true;
		} else {
			odosAreSetFromFreq = false;
			odosAreSetFrom_total = 0;
		}

		// Set location from this, and requery/re-filter via IDs
		Location loc = null;
		if ((areaLocs != null) && (areaLocs.length < 100))
		{
			for (int i = 0; i < areaLocs.length; ++i)
				if (locID == areaLocs[i].getID())
				{
					loc = areaLocs[i];
					break;
				}
		}
		if (loc == null)
		{
			try {
				loc = new Location(db, locID);
			} catch (Throwable e) { }  // RDBKeyNotFoundException, shouldn't happen
		}

		ViaRoute vr = null;
		if ((loc != null) && (viaID != 0))
		{
			try {
				vr = new ViaRoute(db, viaID);
			} catch (Throwable e) { } // RDBKeyNotFoundException
		}
		setLocObjUpdateVias(loc, vr, true);
	}

	/**
	 * Show the {@link DatePickerDialog} when the stop-date button is clicked.
	 * @see #onCreateDialog(int)
	 */
	public void onClick_BtnStopDate(View v)
	{
		showDialog(R.id.trip_tstop_btn_stop_date);
	}

	/**
	 * Show the {@link DatePickerDialog} when the continue-date button is clicked.
	 * @see #onCreateDialog(int)
	 */
	public void onClick_BtnContDate(View v)
	{
		showDialog(R.id.trip_tstop_btn_cont_date);
	}

	public void onClick_BtnGas(View v)
	{
    	Intent i = new Intent(this, TripTStopGas.class);
    	if ((bundleGas == null) && (stopGas != null))
    	{
    		bundleGas = new Bundle();
        	if (stopGas != null)
        	{
        		final int bgid = stopGas.gas_brandgrade_id;
        		if ((bgid != 0) && (stopGas.gas_brandgrade == null))
        		{
        			try
        			{
        				stopGas.gas_brandgrade = new GasBrandGrade(db, bgid);
        			}
        			catch (Throwable th) {}
        		}
	    		TripTStopGas.saveBundleFromDBObj(stopGas, bundleGas, gbgCreatedHere);
        	}
    	}
    	if (bundleGas != null)
    		i.putExtras(bundleGas);
    	else if (locObj != null)
    	{
    		// If location has a previous GasBrandGrade ID,
    		// use that as the default.
    		final int bgid = locObj.getLatestGasBrandGradeID();
    		if (bgid != 0)
    		{
    			try
    			{
    				GasBrandGrade gbg = new GasBrandGrade(db, bgid);
    				Bundle bu = new Bundle();
    				bu.putInt(TripTStopGas.EXTRAS_FIELD_BRANDGRADE_ID, bgid);
    				bu.putCharSequence(TripTStopGas.EXTRAS_FIELD_BRANDGRADE, gbg.getName());
    				i.putExtras(bu);
    			}
    			catch (Throwable th) {}
    			
    		}
    	}	
		startActivityForResult(i, R.id.trip_tstop_btn_gas);
	}

	/**
	 * Callback from {@link TripTStopGas} or {@link TripTStopChooseFreq}.
	 * @param idata  intent which may contain gas-stop info, or a freq-tstop ID
	 */
	@Override
	public void onActivityResult(final int requestCode, final int resultCode, Intent idata)
	{
		if (resultCode == RESULT_CANCELED)
			return;

		switch(requestCode)
		{
		case R.id.trip_tstop_btn_gas:  // TripTStopGas
			bundleGas = idata.getExtras();
			btnGas.setCompoundDrawablesWithIntrinsicBounds
			  ((bundleGas != null) ? android.R.drawable.presence_online
				: android.R.drawable.presence_invisible,
				0, 0, 0);
			break;

		case R.id.main_btn_freq_local:  // TripTStopChooseFreq
			if (idata != null)
			{
				int id = idata.getIntExtra("_id", 0);
				if (id > 0)
					copyValuesFromFreqTStop(id);
			}
			break;
		}
	}

	/**
	 * Callback for displaying {@link DatePickerDialog} after {@link #onClick_BtnStartDate(View)}.
	 * @see #onDateSet(DatePicker, int, int, int)
	 */
	@Override
	protected Dialog onCreateDialog(final int id)
	{
		if (id == R.id.trip_tstop_btn_cont_date)
		{
			currentDateToPick = contTime;
		} else {
			currentDateToPick = stopTime;
		}
        return new DatePickerDialog
        	(this, this,
			currentDateToPick.get(Calendar.YEAR),
			currentDateToPick.get(Calendar.MONTH),
			currentDateToPick.get(Calendar.DAY_OF_MONTH));
	}

	/** Callback from {@link DatePickerDialog} for TStop stop-date or continue-date. */
	public void onDateSet(DatePicker dp, final int year, final int month, final int monthday)
	{
		if (currentDateToPick == null)
			return;  // shouldn't happen
		currentDateToPick.set(Calendar.YEAR, year);
		currentDateToPick.set(Calendar.MONTH, month);
		currentDateToPick.set(Calendar.DAY_OF_MONTH, monthday);

		if (currentDateToPick == stopTime)
			updateDateButtons(1);
		else
			updateDateButtons(2);
	}

	/**
	 * Get this field's text, if anything was entered.
	 * @param editTextID  field ID from <tt>R.id</tt>
	 * @return a nonzero-length trimmed String, or null
	 */
	private String textIfEntered(final int editTextID)
	{
		EditText et = (EditText) findViewById (editTextID);
		if (et == null)
			return null;
		String st = et.getText().toString().trim();
		if (st.length() > 0)
			return st;
		else
			return null;
	}

	/** Unless <tt>txt</tt> is <tt>null</tt>, set <tt>editTextID</tt>'s contents. */
	private void setEditText(final String txt, final int editTextID)
	{
		if ((txt == null) || (txt.length() == 0))
			return;
		EditText et = (EditText) findViewById (editTextID);
		et.setText(txt);
	}

	/**
	 * Finish the current trip in the database. (Completed trips have a time_end, odo_end).
	 * Clear CURRENT_TRIP.
	 * Update the Trip and Vehicle odometer.
	 * If ending a roadtrip, also update CURRENT_AREA.
	 * Assumes TStop already created or updated.
	 * Uses {@link #stopAtTime} to set trip's time_end.
	 *
	 * @param tsid  This trip stop ID, not 0
	 * @param odo_total  Total odometer at end of trip, not 0
	 * @param mkFreqTrip  If true, want to create a {@link FreqTrip} based on this trip's data.
	 */
	private void endCurrentTrip(final int tsid, final int odo_total, final boolean mkFreqTrip)
	{
		currT.setTime_end((int) (stopTime.getTimeInMillis() / 1000L));
		currT.setOdo_end(odo_total);
		currT.commit();

		currV.setOdometerCurrentAndLastTrip(odo_total, currT, true);
		  // that also calls currV.commit()

		Settings.setCurrentTrip(db, null);
		if (currT.isFrequent())
			Settings.setCurrentFreqTrip(db, null);

		// For roadtrip, set current geoarea too
		final int endAreaID = currT.getRoadtripEndAreaID();
		if (endAreaID != 0)
		{
			try {
				Settings.setCurrentArea(db, new GeoArea(db, endAreaID));
			}
			catch (IllegalStateException e) { }
			catch (IllegalArgumentException e) { }
			catch (RDBKeyNotFoundException e) { }
		}

		if (mkFreqTrip)
		{
			// make new intent, set "_id" to currT.id, call it.
			Intent i = new Intent(TripTStopEntry.this, TripCreateFreq.class);
			i.putExtra("_id", currT.getID());
			startActivity(i);
			// TripTStopEntry code outside of endCurrentTrip will soon call finish();
		}
	}

	/** Callback for {@link OnItemClickListener} for location autocomplete; read {@link #loc}, set {@link #locObj}. */
	public void onItemClick(AdapterView<?> parent, View clickedOn, int position, long rowID)
	{
		ListAdapter la = loc.getAdapter();
		if (la == null)
			return;
		setLocObjUpdateVias((Location) la.getItem(position), null, false);
	}

	/**
	 * Set {@link #locObj} to a new location; update {@link #currTS} if <tt>currTS</tt> != null;
	 * and update via-route autocomplete with {@link #updateViaRouteAutocomplete(ViaRoute, boolean)}.
	 * @param L  new locObj, or null
	 * @param vr  new currTS.ViaRoute if known, or null to not change <tt>currTS.via_id</tt>.
	 *           If this is set, check its location IDs against {@link #prevLocObj} and {@link #locObj}
	 *           before updating <tt>currTS.via_id</tt>.
	 * @param setLocText  If true, update {@link #loc}'s text contents after setting {@link #locObj}
	 */
	private void setLocObjUpdateVias(Location L, ViaRoute vr, final boolean setLocText)
	{
		locObj = L;
		if (locObj != null)
		{
			if (setLocText)
				loc.setText(locObj.toString());
			int locID = locObj.getID();
			if (currTS != null)
			{
				currTS.setLocationID(locID);
				if (vr != null)
				{
					if ((prevLocObj != null) && (prevLocObj.getID() == vr.getLocID_From())
						&& (locObj.getID() == vr.getLocID_To()))
						currTS.setVia_id(vr.getID());
					else
						currTS.setVia_id(0);
				}
			}
		} else {
			if (setLocText)
				loc.setText("");
			if (currTS != null)
			{
				currTS.setLocationID(0);
				currTS.setVia_id(0);
			}
			vr = null;
		}
		updateViaRouteAutocomplete(vr, false);
	}

	/**
	 * If new location text is typed into {@link #loc}, and {@link #locObj}
	 * no longer matches that text, clear <tt>locObj</tt>
	 * and call {@link #updateViaRouteAutocomplete(ViaRoute, boolean)}.
	 * (for addTextChangedListener / {@link TextWatcher}) 
	 */
	public void afterTextChanged(Editable arg0)
	{
		if (locObj == null)
			return;
		final String newText = arg0.toString().trim();
		final int newLen = newText.length(); 
		if ((newLen == 0) || ! locObj.toString().equalsIgnoreCase(newText))
		{
			// Mismatch: object no longer matches typed location
			locObj = null;
			updateViaRouteAutocomplete(null, false);
		}
	}

	/** required stub for {@link TextWatcher} */
	public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3)
	{ }

	/** required stub for {@link TextWatcher} */
	public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3)
	{ }

	/**
	 * Update contents of ViaRoute auto-complete {@link #via}, based on {@link #prevLocObj} and {@link #locObj}.
	 * Also clear {@link #viaRouteObj} and via's text contents, since its possible values are changing.
	 *<P>
	 * For {@link #onCreate(Bundle)}, {@link #currTS}, {@link #locObj},
	 * and {@link #prevLocObj} must be set before calling this method.
	 *<P>
	 * If {@link #currTS} != null, then if desired, update {@link TStop#getVia_id() currTS.getVia_id()}
	 * and {@link TStop#getLocationID() currTS.getLocationID()}
	 * before calling this method, in order to set {@link #viaRouteObj} from <tt>currTS.via_id</tt>.
	 * To do this, {@link #prevLocObj} and {@link #locObj} must not be null.
	 *<P>
	 * If {@link #currTS} != null, and {@link #locObj} matches its locID, and
	 * {@link TStop#getVia_id() currTS.getVia_id()} is set, then try to find and
	 * set {@link #viaRouteObj} from the ViaRoutes loaded for the auto-complete.
	 *<P>
	 * If locObj is unchanged, do nothing to the autocomplete.
	 * Update {@link #viaRouteObj} only if <tt>vr</tt> != null
	 * and (if <tt>currTS</tT> != null) <tt>vr</tt>'s ID == currTS.getVia_id().
	 *<P>
	 * Called after {@link #locObj} changes, and also as part of {@link #onCreate(Bundle)}.
	 *
	 * @param vr  if not null, use this to set {@link #viaRouteObj} here,
	 *     only if it matches {@link #prevLocObj} and {@link #locObj}.
	 *     If {@link #currTS} != null, then {@link TStop#getVia_id() currTS.getVia_id()}
	 *     must also match to do this.
	 * @param isFromOnCreate  Are we being called from {@link #onCreate(Bundle)}?
	 *     If so, update {@link #viaRouteObjCreatedHere}.
	 */
	private void updateViaRouteAutocomplete(ViaRoute vr, final boolean isFromOnCreate)
	{
		if (prevLocObj == null)
			return;

		// If isFromOnCreate, then viaRouteObjCreatedHere == null
		// until it's updated below;
		// if currTS == null, viaRouteObjCreatedHere remains null.

		if ((locObj != null) && (via_lastLocIDTo == locObj.getID()))
		{
			// No need to update the autocomplete, but
			// we'll set viaRouteObj if it's consistent.
			// If isFromOnCreate, via_lastLocIDTo == -1,
			// so we won't get here.
			if ((vr != null) && (vr != viaRouteObj))
			{
				if ((vr.getLocID_From() == prevLocObj.getID())
					&& (vr.getLocID_To() == via_lastLocIDTo)
					&& ((currTS == null)
						|| ((via_lastLocIDTo == currTS.getLocationID())
							&& (vr.getID() == currTS.getVia_id()))))
				{
					viaRouteObj = vr;
					via.setText(vr.getDescr());
				}
			}
			return;  // <--- early return; no change to autocomplete ---
		}

		if (viaRouteObj != null)
		{
			viaRouteObj = null;
			if (viaRouteObjCreatedHere == null)
				via.setText("");
		}
		ViaRoute[] vias;
		if (locObj != null)
		{
			vias = ViaRoute.getAll(db, prevLocObj, locObj);
			via_lastLocIDTo = locObj.getID();
		} else {
			vias = null;
			via_lastLocIDTo = -1;
		}
		if (vias != null)
		{
			// Assert: locObj != null, because vias != null.

			ArrayAdapter<ViaRoute> adapter = new ArrayAdapter<ViaRoute>(this, R.layout.list_item, vias);
			via.setAdapter(adapter);
			via.setOnItemClickListener(viaListener);

			if ((currTS != null) && (currTS.getLocationID() != locObj.getID()))
				return;  // <--- Can't update viaRouteObj: different currTS location

			if ((vr != null)
				&& (vr.getLocID_From() == prevLocObj.getID())
				&& (vr.getLocID_To() == locObj.getID())
				&& ((currTS == null) || (vr.getID() == currTS.getVia_id())))
			{
				viaRouteObj = vr;
				via.setText(vr.getDescr());
			}
			else if (currTS != null)
			{
				// Search the via array contents for currTS.viaID;
				// set viaRouteObj, maybe viaRouteObjCreatedHere,
				// if found.
				final int currTSVia = currTS.getVia_id();
				if (currTSVia != 0)
				{
					for (int i = vias.length - 1; i >= 0; --i)
					{
						if (currTSVia == vias[i].getID())
						{
							viaRouteObj = vias[i];
							if (isFromOnCreate && currTS.isSingleFlagSet(TStop.TEMPFLAG_CREATED_VIAROUTE))
							{
								viaRouteObjCreatedHere = viaRouteObj;
							}
							via.setText(viaRouteObj.getDescr());
							break;
						}
					}
				}
			}
		} else {
			via.setAdapter((ArrayAdapter<ViaRoute>) null);
		}
	}

	/**
	 * Save our state before an Android pause or stop.
	 * @see #onRestoreInstanceState(Bundle)
	 */
	@Override
	public void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		if (outState == null)
			return;
		outState.putBoolean(TSTOP_BUNDLE_SAVED_MARKER, true);
		odo_total.onSaveInstanceState(outState, "OTO");
		odo_trip.onSaveInstanceState(outState, "OTR");
		outState.putBoolean("OTOC", odo_total_chk.isChecked());
		outState.putBoolean("OTRC", odo_trip_chk.isChecked());
		outState.putBoolean("TSC", tp_time_stop_chk.isChecked());
		outState.putBoolean("TCC", tp_time_cont_chk.isChecked());
		outState.putInt("TSV", (tp_time_stop.getCurrentHour() << 8) | tp_time_stop.getCurrentMinute() );
		outState.putInt("TCV", (tp_time_cont.getCurrentHour() << 8) | tp_time_cont.getCurrentMinute() );
		outState.putInt("AID", areaLocs_areaID);
	}

	/**
	 * Restore our state after an Android pause or stop.
	 * Happens here (and not <tt>onCreate</tt>) to ensure the
	 * initialization is complete before this method is called.
	 *<P>
	 * Check {@link #areaLocs_areaID} before and after calling, to see if it changed,
	 * because this method won't reload the {@link #areaLocs} autocomplete list.
	 *
	 * @see #onSaveInstanceState(Bundle)
	 */
	@Override
	public void onRestoreInstanceState(Bundle inState)
	{
		super.onRestoreInstanceState(inState);
		if ((inState == null) || ! inState.containsKey(TSTOP_BUNDLE_SAVED_MARKER))
			return;
		odo_total.onRestoreInstanceState(inState, "OTO");
		odo_trip.onRestoreInstanceState(inState, "OTR");
		odo_total_chk.setChecked(inState.getBoolean("OTOC"));
		odo_trip_chk.setChecked(inState.getBoolean("OTRC")); 
		tp_time_stop_chk.setChecked(inState.getBoolean("TSC"));
		tp_time_cont_chk.setChecked(inState.getBoolean("TCC"));
		int hhmm = inState.getInt("TSV");
		tp_time_stop.setCurrentHour(hhmm >> 8);
		tp_time_stop.setCurrentMinute(hhmm & 0xFF);
		hhmm = inState.getInt("TCV");
		tp_time_cont.setCurrentHour(hhmm >> 8);
		tp_time_cont.setCurrentMinute(hhmm & 0xFF);
		areaLocs_areaID = inState.getInt("AID", -1);
	}

	/**
	 * For ViaRoute autocomplete ({@link TripTStopEntry#via}), the callbacks for {@link OnItemClickListener}
	 * and {@link TextWatcher}; sets or clears {@link TripTStopEntry#viaRouteObj},
	 * possibly updates trip_odo, total_odo.
	 */
	private class ViaRouteListenerWatcher implements OnItemClickListener, TextWatcher
	{
		/** For ViaRoute autocomplete, the callback for {@link OnItemClickListener}; sets {@link TripTStopEntry#viaRouteObj}. */
		public void onItemClick(AdapterView<?> parent, View clickedOn, int position, long rowID)
		{
			ListAdapter la = via.getAdapter();
			if (la == null)
				return;
			viaRouteObj = (ViaRoute) la.getItem(position);
			final int odo_dist = viaRouteObj.getOdoDist();
			if ((odo_dist != 0) && ! odosAreSetFromFreq)
			{
				odosAreSetFromVia = true;

				if (! odo_trip_chk.isChecked())
				{
					odosAreSetFrom_trip = odoTripOrig + odo_dist;
					odo_trip.setCurrent10d(odosAreSetFrom_trip, false);
				} else {
					odosAreSetFrom_trip = 0;
				}

				if (! odo_total_chk.isChecked())
				{
					odosAreSetFrom_total = odoTotalOrig + odo_dist;
					odo_total.setCurrent10d(odosAreSetFrom_total, false);
				} else {
					odosAreSetFrom_total = 0;
				}
			}
		}		

		/**
		 * If new via-route text is typed into {@link #via}, and {@link TripTStopEntry#viaRouteObj}
		 * no longer matches that text, clear <tt>viaRouteObj</tt>.
		 * (for addTextChangedListener / {@link TextWatcher}) 
		 */
		public void afterTextChanged(Editable arg0)
		{
			if (viaRouteObj == null)
				return;
			final String newText = arg0.toString().trim();
			final int newLen = newText.length(); 
			if ((newLen == 0) || ! viaRouteObj.toString().equalsIgnoreCase(newText))
			{
				viaRouteObj = null;  // Mismatch: object no longer matches typed ViaRoute description
				if (odosAreSetFromVia)
				{
					if (! odo_trip_chk.isChecked())
						odo_trip.setCurrent10d(odoTripOrig, false);
					if (! odo_total_chk.isChecked())
						odo_total.setCurrent10d(odoTotalOrig, false);
					odosAreSetFromVia = false;
					odosAreSetFrom_total = 0;
				}
			}
		}

		/** required stub for {@link TextWatcher} */
		public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3)
		{ }

		/** required stub for {@link TextWatcher} */
		public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3)
		{ }

	}

}
