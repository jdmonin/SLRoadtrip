/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2013 Jeremy D Monin <jdmonin@nand.net>
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

import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.android.util.DBExport;
import org.shadowlands.roadtrip.db.GeoArea;
import org.shadowlands.roadtrip.db.Location;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.RDBVerifier;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;
import org.shadowlands.roadtrip.model.LogbookTableModel;
import org.shadowlands.roadtrip.util.RTRDateTimeFormatter;
import org.shadowlands.roadtrip.util.android.RTRAndroidDateTimeFormatter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Present an unformatted view of the current vehicle's trip log.
 *<P>
 * Optionally, can filter to show only trips that include a given {@link #EXTRAS_LOCID location ID}
 * (Location Mode, same as in {@link LogbookTableModel}).
 *<P>
 * Or, can set the starting date to {@link #EXTRAS_DATE}; this date and 2 weeks of newer trips are
 * shown, with buttons to show older and newer trips.
 *
 * @author jdmonin
 */
public class LogbookShow extends Activity
{
	/**
	 * Increment in weeks when loading newer/older trips from the database,
	 * or 0 to load all (This may run out of memory).
	 * @see #LOCID_TRIP_INCREMENT
	 */
	public static final int WEEK_INCREMENT = 2;

	/**
	 * For Location filtering mode, increment in trips when loading from the database.
	 * @see #WEEK_INCREMENT
	 */
	public static final int LOCID_TRIP_INCREMENT = 10;

	/**
	 * Location Mode: If added to intent extras, show only trips including
	 * this locID; for {@link Intent#putExtra(String, int)}.
	 * @see #EXTRAS_LOCMODE_ALLV
	 */
	public static final String EXTRAS_LOCID = "LogbokShow.locID";

	/**
	 * Location Mode: Optional flag for intent extras to indicate all vehicles' trips
	 * should be shown; for {@link Intent#putExtra(String, boolean)}.
	 * Requires {@link #EXTRAS_LOCID}.
	 */
	public static final String EXTRAS_LOCMODE_ALLV = "LogbookShow.locAllV";

	/**
	 * Go To Date: If added to intent extras, show trips starting at or after this time.
	 * Unix time format (seconds, same epoch as {@link System#currentTimeMillis()});
	 * use {@link Intent#putExtra(String, int)}.
	 * Ignored if {@link #EXTRAS_LOCID} is set.
	 * Optionally, can also use with {@link #EXTRAS_VEHICLE_ID}.
	 * @see #goToDate
	 */
	public static final String EXTRAS_DATE = "LogbookShow.dateUnix";

	/**
	 * Go To Date: If added to intent extras, show trips of this vehicle
	 * instead of the current vehicle.
	 * Requires {@link #EXTRAS_DATE}.
	 */
	public static final String EXTRAS_VEHICLE_ID = "LogbookShow.vid";

	/** tag for android logging */
	private static final String TAG = "RTR.LogbookShow";

	/** for use by {@link #onClick_BtnEarlier(View)}. Width <tt>FILL_PARENT</tt>, height <tt>WRAP_CONTENT</tt>. */
	private static ViewGroup.LayoutParams TS_ROW_LP = null;

	private RDBAdapter db = null;
	private TextView tvHeader;

	/** Message that no trips were found in {@link #onCreate(Bundle)}, or null */
	private TextView tvNoTripsFound = null;

	private ScrollView sv;
	/** see also {@link #showV} */
	private Vehicle currV;
	private LogbookTableModel ltm;

	/**
	 * Starting date for "Go To Date" mode ({@link #EXTRAS_DATE}); 0 otherwise.
	 */
	private int goToDate = 0;

	/**
	 * Vehicle for "Go To Date" mode ({@link #EXTRAS_DATE}, {@link #EXTRAS_VEHICLE_ID});
	 * or {@link #currV}.
	 */
	private Vehicle showV = null;

	/**
	 * If true, the most recent "earlier trips" or "later trips" button
	 * click was on "earlier trips" ({@link #onClick_BtnEarlier(View)}).
	 * If false, {@link #onClick_BtnLater(View)} or neither was clicked.
	 * Used with {@link #onCreateGoToDateVehicleDialog(boolean)} to show the
	 * most recently viewed date range for another vehicle.
	 */
	private boolean rangeEarlierClicked = false;

	/** Cached verifier object, for successive manual calls from {@link #doDBValidation()} */
	private RDBVerifier verifCache = null;

	/** Non-null if currently running. Set and cleared in {@link ValidateDBTDataTask#doInBackground(String...)}. */
	private ValidateDBTDataTask verifTask = null;

	/** Used by {@link #onClick_BtnEarlier(View)}, {@link #onClick_BtnLater(View)} */
	private LinearLayout tripListParentLayout = null;

	/** Used by {@link #onClick_BtnEarlier(View)} */
	private int tripListBtnEarlierPosition = -1;

	/** Used by {@link #onClick_BtnLater(View)} */
	private int tripListBtnLaterPosition = -1;

	/** Used by {@link #askLocationAndShow(int, Activity, RDBAdapter)}; null if no location selected. */
	private static Location askLocationAndShow_locObj = null;

	/** Used by {@link #askLocationAndShow(int, Activity, RDBAdapter)}; show trips for all vehicles? */
	private static boolean askLocationAndShow_allV = false;

	/** Used by {@link #askLocationAndShow(int, Activity, RDBAdapter)}; geoarea */
	private static int askLocationAndShow_areaID = -1;

	/**
	 * Logbook in Location Mode: Show a popup with the current GeoArea's locations, the user picks
	 * one and the LogbookShow activity is launched for it.
	 * @param vID  A specific vehicle ID to show, or 0 for current vehicle
	 * @param fromActivity  Current activity; will call {@link Activity#startActivity(Intent)} on it
	 * @param db  Connection to use
	 * @see #showTripsForLocation(int, boolean, int, Activity)
	 */
	public static final void askLocationAndShow(final int vID, final Activity fromActivity, final RDBAdapter db)
	{
		if (askLocationAndShow_areaID == -1)
		{
			final GeoArea currA = Settings.getCurrentArea(db, false);
			if (currA == null)
				return;
			askLocationAndShow_areaID = currA.getID();
		}

		/** Find all locations in the current area, or null */
		Location[] areaLocs = Location.getAll(db, askLocationAndShow_areaID);
		if (areaLocs == null)
		{
			Toast.makeText(fromActivity, R.string.logbook_show__no_locs_in_area, Toast.LENGTH_SHORT).show();
			return;
		}

		final View askItems = fromActivity.getLayoutInflater().inflate(R.layout.logbook_show_popup_locsearch, null);
		final AutoCompleteTextView loc =
			(AutoCompleteTextView) askItems.findViewById(R.id.logbook_show_popup_locs_loc);

		if (askLocationAndShow_locObj != null)
		{
			if (askLocationAndShow_locObj.getAreaID() == askLocationAndShow_areaID)
				loc.setText(askLocationAndShow_locObj.toString());
			else
				askLocationAndShow_locObj = null;
		}
		if (askLocationAndShow_allV)
		{
			CheckBox cb = (CheckBox) askItems.findViewById(R.id.logbook_show_popup_locs_allv);
			if (cb != null)
				cb.setChecked(true);
		}

		ArrayAdapter<Location> adapter = new ArrayAdapter<Location>(fromActivity, R.layout.list_item, areaLocs);
		loc.setAdapter(adapter);
		loc.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View clickedOn, int position, long rowID)
			{
				ListAdapter la = loc.getAdapter();
				if (la == null)
					return;
				askLocationAndShow_locObj = (Location) la.getItem(position);
			}
			});

		if (askLocationAndShow_allV)
		{
			CheckBox cb = (CheckBox) askItems.findViewById(R.id.logbook_show_popup_locs_allv);
			if (cb != null)
				cb.setChecked(askLocationAndShow_allV);
		}

		/** When GeoArea spinner selection changes, query for locations in that area: */
		final Spinner areas = (Spinner) askItems.findViewById(R.id.logbook_show_popup_locs_areas);
		SpinnerDataFactory.setupGeoAreasSpinner(db, fromActivity, areas, askLocationAndShow_areaID, true);
		areas.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			public void onItemSelected
				(AdapterView<?> ctx, View view, int pos, long id)
			{
				final int newAreaID = ((GeoArea) areas.getSelectedItem()).getID();
				if (newAreaID == askLocationAndShow_areaID)
					return;
				askLocationAndShow_areaID = newAreaID;
				if ((askLocationAndShow_locObj != null)
					&& (newAreaID != askLocationAndShow_locObj.getAreaID()))
				{
					loc.setText("");
					askLocationAndShow_locObj = null;
				}
				Location[] areaLocs = Location.getAll(db, newAreaID);
				if (areaLocs == null)
				{
					Toast.makeText(fromActivity, R.string.logbook_show__no_locs_in_area, Toast.LENGTH_SHORT).show();
					loc.setAdapter((ArrayAdapter<Location>) null);
					return;
				}
				loc.setAdapter(new ArrayAdapter<Location>(fromActivity, R.layout.list_item, areaLocs));
			}

			public void onNothingSelected(AdapterView<?> parent) { } // Required stub
		});

		AlertDialog.Builder alert = new AlertDialog.Builder(fromActivity);
		alert.setTitle(R.string.logbook_show__trips_to_location);
		alert.setMessage(R.string.logbook_show__enter_location_to_search_trips);
		alert.setView(askItems);
		alert.setPositiveButton(android.R.string.search_go, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton)
			{
				final Vehicle showV = null;
				CheckBox cb = (CheckBox) askItems.findViewById(R.id.logbook_show_popup_locs_allv);
				if (cb != null)
					askLocationAndShow_allV = cb.isChecked();

				if (askLocationAndShow_locObj != null)
				{
					showTripsForLocation
						(askLocationAndShow_locObj.getID(), askLocationAndShow_allV, vID, fromActivity);
				} else {
					final int text;
					if (loc.getText().length() == 0)
						text = R.string.please_enter_the_location;
					else
						text = R.string.please_choose_existing_location;

					Toast.makeText(fromActivity, text, Toast.LENGTH_SHORT).show();
				}
			}
		});
		alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton)
			{
				askLocationAndShow_locObj = null;
			}
		});
		areaLocs = null;  // free the reference
		alert.show();
	}

	/**
	 * Start this activity in Location Mode: Only show trips including a given location.
	 * @param locID  Location ID
	 * @param allV  If true, show all vehicles' trips, not just the current vehicle
	 * @param vID   If not <tt>allV</tt>, a specific vehicle ID to show, or 0 for current vehicle
	 * @param fromActivity  Current activity; will call {@link Activity#startActivity(Intent)} on it
	 */
	public static final void showTripsForLocation(final int locID, final boolean allV, final int vID, final Activity fromActivity)
	{
		Intent i = new Intent(fromActivity, LogbookShow.class);
		i.putExtra(EXTRAS_LOCID, locID);
		if (allV)
			i.putExtra(EXTRAS_LOCMODE_ALLV, true);
		else if (vID != 0)
			i.putExtra(EXTRAS_VEHICLE_ID, vID);
		fromActivity.startActivity(i);
	}

	/**
	 * Export some of this logbook's data from {@link #ltm}.
	 * Called from the dialog created in {@link #onCreateExportDialog()}.
	 * Calls {@link DBExport#exportTripData(android.content.Context, LogbookTableModel, String)}.
	 *<P>
	 * If {@link #ltm} is empty, does nothing.
	 * @param fname  Filename for export (not a path)
	 * @since 0.9.20
	 */
	private void doExport(final String fname)
	{
		if (ltm.getRangeCount() == 0)
			return;

		// TODO bg task, like ValidateDBTDataTask
		try
		{
			final boolean prevMode = LogbookTableModel.trip_simple_mode;
			LogbookTableModel.trip_simple_mode = true;
			// TODO copy constructor for LTM with trip data?
			LogbookTableModel expLTM = new LogbookTableModel
				(showV, ltm.getRange(0).timeStart, ltm.getWeekIncrement(), true,
				 new RTRAndroidDateTimeFormatter(getApplicationContext()), db);
			DBExport.exportTripData(this, expLTM, fname);
			LogbookTableModel.trip_simple_mode = prevMode;
			Toast.makeText(this, R.string.logbook_show__export_complete, Toast.LENGTH_SHORT).show();
		}
		catch (Throwable th)
		{
	    	AlertDialog.Builder alert = new AlertDialog.Builder(this);
	    	alert.setTitle(R.string.error);
	    	alert.setMessage("Error during export: " + th);
	    	alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
		    	  public void onClick(DialogInterface dialog, int whichButton) { }
		    	});
	    	alert.show();
		}
	}

	/**
	 * Perform all levels of DB validation, including transactional data
	 *     ({@link RDBVerifier#LEVEL_TDATA}).
	 */
	private void doDBValidation()
	{
		int res = 0;

		/**
		 * Level for successive calls on {@link #verifCache}
		 */
		int verifiedLevel = 0;

		/** Cached verifier object, for successive level calls */
		if (verifCache == null)
			verifCache = new RDBVerifier(db);

		// do "quick validation" levels (below LEVEL_TDATA) first
		int chkLevel;
		for (chkLevel = RDBVerifier.LEVEL_PHYS; chkLevel < RDBVerifier.LEVEL_TDATA; ++chkLevel)
		{			
			if (verifiedLevel < chkLevel)
			{
				res = verifCache.verify(chkLevel);
				if (res == 0)
					verifiedLevel = chkLevel;
				else
					break;
			}
		}

		if (chkLevel == RDBVerifier.LEVEL_TDATA)
		{
			// completed all "quick" levels successfully
			new ValidateDBTDataTask().execute();
			return;  // <--- Early return: Verify DB in bg task ---
		}

		String vLevel;
		switch(chkLevel)
		{
		case RDBVerifier.LEVEL_PHYS:
			vLevel = "Physical (level 1)";
			break;
		case RDBVerifier.LEVEL_MDATA:
			vLevel = "Master-data (level 2)";
			break;
		// case RDBVerifier.LEVEL_TDATA happens in background task
		default:
			vLevel = "";  // to satisfy compiler
		}
		if (res != 0)
		{
			vLevel += " validation failed at level " + chkLevel;
		} else {
			vLevel += " validation successful.";
		}

		Toast.makeText(this, vLevel, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.logbook_show);

	    tvHeader = (TextView) findViewById(R.id.logbook_show_header);  // TODO show date range or location
	    TextView tvContent = (TextView) findViewById(R.id.logbook_show_textview);
		db = new RDBOpenHelper(this);

		if ( ! checkCurrentVehicleSetting())
		{
        	Toast.makeText(getApplicationContext(),
                "Current vehicle not found in db",
                Toast.LENGTH_SHORT).show();
	    	finish();
	    	return;  // <--- early ret: no vehicle ---
		}

		// Read and semi-format the trips for this vehicle.
		// (The LogbookTableModel constructor calls ltm.addRowsFromTrips.)
		int locID = -1;
		boolean locMode_allV = false;
		{
			Intent i = getIntent();
			if (i != null)
			{
				locID = i.getIntExtra(EXTRAS_LOCID, -1);
				if (locID != -1)
					locMode_allV = i.getBooleanExtra(EXTRAS_LOCMODE_ALLV, false);
				else if (i.hasExtra(EXTRAS_DATE))
				{
					goToDate = i.getIntExtra(EXTRAS_DATE, 0);
				}

				if (! locMode_allV)
				{
					final int vid = i.getIntExtra(EXTRAS_VEHICLE_ID, 0);
					if (vid != 0)
					{
						try
						{
							showV = new Vehicle(db, vid);
						}
						catch (Throwable e) { }
					}
				}
			}
		}

		if (showV == null)
			showV = currV;
		if (! locMode_allV)
			setTitle(getTitle() + ": " + showV.toString());
		else
			setTitle(getTitle() + ": " + getResources().getString(R.string.all_vehicles));

		// Choose an LTM mode based on our intent extras
		final RTRDateTimeFormatter dtf = new RTRAndroidDateTimeFormatter(getApplicationContext());
		if (locID == -1)
		{
			if (goToDate == 0)
				ltm = new LogbookTableModel(showV, WEEK_INCREMENT, dtf, db);
			else
				ltm = new LogbookTableModel(showV, goToDate, WEEK_INCREMENT, true, dtf, db);
		} else {
			ltm = new LogbookTableModel(showV, locMode_allV, locID, LOCID_TRIP_INCREMENT, dtf, db);
		}
		StringBuffer sbTrips = new StringBuffer();
		if (ltm.getRangeCount() > 0)
			ltm.getRange(0).appendRowsAsTabbedString(sbTrips);
		if (ltm.hasCurrentTrip())
		{
			sbTrips.append("\n\t\t(Current Trip in progress)");
		}
		else if (sbTrips.length() < 5)
		{
			tvNoTripsFound = tvContent;
			if (locID != -1)
				sbTrips.append("\nNo trips found to that Location for this Vehicle.");
			else if (goToDate != 0)
				sbTrips.append("\nNo trips on or after that date for this vehicle.");
			else
				sbTrips.append("\nNo trips found for this Vehicle.");
		}

		tvContent.setText(sbTrips);

		// Find the trip layout linearlayout, for onClick_BtnEarlier's benefit.
		tripListParentLayout = (LinearLayout) findViewById(R.id.logbook_show_triplist_parent);
		if (tripListParentLayout != null)
		{
			View v = findViewById(R.id.logbook_show_btn_earlier);
			if (v != null)
				tripListBtnEarlierPosition = tripListParentLayout.indexOfChild(v);
			v = findViewById(R.id.logbook_show_btn_later);
			if (v != null)
				tripListBtnLaterPosition = tripListParentLayout.indexOfChild(v);
		}
		if ((tripListBtnEarlierPosition == -1) || (tripListParentLayout == null))
		{
			// shouldn't happen, but just in case
			Log.e(TAG, "layout items not found");
			Toast.makeText(this, "L110: internal error: layout items not found", Toast.LENGTH_SHORT).show();
			View btnEarlier = findViewById(R.id.logbook_show_btn_earlier);
			if (btnEarlier != null)
				btnEarlier.setVisibility(View.GONE);
		}

		// If we're in Go To Date Mode, show the hidden "newer trips" button.
		if ((goToDate != 0) && (ltm.getRangeCount() > 0))
		{
			View btnLater = findViewById(R.id.logbook_show_btn_later);
			if (btnLater != null)
				btnLater.setVisibility(View.VISIBLE);
		}

		// Scroll to bottom (most recent), unless in Go To Date mode.
		if (goToDate == 0)
		{
			sv = (ScrollView) findViewById(R.id.logbook_show_triplist_scroll);
			if (sv != null)
				sv.post(new Runnable() {
					public void run() {
						sv.fullScroll(ScrollView.FOCUS_DOWN);
					}
				});
		}
	}

	/** Show the DatePickerDialog or "Other Vehicle" dialog, leading to "Go To Date" mode. Or, show Export dialog. */
	@Override
	protected Dialog onCreateDialog(int id)
	{
	    switch (id) {
		    case R.id.menu_logbook_go_to_date:
				return onCreateGoToDateVehicleDialog(false);

		    case R.id.menu_logbook_other_veh:
				return onCreateGoToDateVehicleDialog(true);

		    case R.id.menu_logbook_export:
		    	return onCreateExportDialog();
	    }
	    return null;
	}

	/**
	 * Show the DatePickerDialog and Vehicle chooser leading to "Go To Date" mode.
	 * @param vehicleOnly  If true, show the vehicle chooser
	 *            but hide the datepicker (keep current date)
	 */
	private Dialog onCreateGoToDateVehicleDialog(final boolean vehicleOnly)
	{
		final Calendar cal = Calendar.getInstance();
		if (goToDate != 0)
			cal.setTimeInMillis(1000L * goToDate);
		else
			cal.setTimeInMillis(System.currentTimeMillis());

		final View askItems = getLayoutInflater().inflate(R.layout.logbook_show_popup_date_veh, null);
		final Spinner vehs =
			(Spinner) askItems.findViewById(R.id.logbook_show_popup_date_vehs);
		SpinnerDataFactory.setupVehiclesSpinner(db, this, vehs, showV.getID());
		final DatePicker dpick =
			(DatePicker) askItems.findViewById(R.id.logbook_show_popup_date_picker);
		if (vehicleOnly)
			dpick.setVisibility(View.GONE);
		else
			dpick.updateDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));

		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle(vehicleOnly
				? R.string.logbook_show__other_vehicle
				: R.string.logbook_show__go_to_date);
		alert.setView(askItems);
		alert.setPositiveButton( (vehicleOnly
				? android.R.string.ok
				: android.R.string.search_go ),
				new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int whichButton)
			{
				final int calGoToDate;
				if (vehicleOnly)
				{
					final int rcount = ltm.getRangeCount();
					if (rcount == 0)
						calGoToDate = goToDate;  // keep current date
					else if (rangeEarlierClicked)
						calGoToDate = ltm.getRange(0).timeStart;
					else						
						calGoToDate = ltm.getRange(rcount-1).timeStart;
				} else {
		        	Calendar cal = Calendar.getInstance();
		        	cal.setTimeInMillis(System.currentTimeMillis());
		        	cal.set(Calendar.YEAR, dpick.getYear());
		        	cal.set(Calendar.MONTH, dpick.getMonth());
		        	cal.set(Calendar.DAY_OF_MONTH, dpick.getDayOfMonth());
		        	cal.set(Calendar.HOUR_OF_DAY, 0);
		        	cal.set(Calendar.MINUTE, 0);
		        	calGoToDate = (int) (cal.getTimeInMillis() / 1000L);
				}

	        	// TODO consider re-use this one, instead of a new activity, if same vehicle
	    		Vehicle v = (Vehicle) vehs.getSelectedItem();
	    		if (v == null)
	    			return;  // shouldn't happen
	    		Intent i = new Intent(LogbookShow.this, LogbookShow.class);
	    		i.putExtra(EXTRAS_DATE, calGoToDate);
	    		i.putExtra(EXTRAS_VEHICLE_ID, v.getID());
	    		LogbookShow.this.startActivity(i);
			}
		});
		alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {}
		});

		return alert.create();
	}

	/**
	 * Set up and show the Export dialog.
	 * This dialog will call {@link #doExport(String)}.
	 */
	private Dialog onCreateExportDialog()
	{
		final View exportLayout = getLayoutInflater().inflate(R.layout.logbook_show_popup_export, null);
		final EditText etFname = (EditText) exportLayout.findViewById(R.id.logbook_show_popup_export_fname);

		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle(R.string.export);
		alert.setView(exportLayout);
		alert.setMessage("Testing only -- this feature is nowhere near complete");
		alert.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int whichButton)
			{
				String fname = etFname.getText().toString().trim();
				if (fname.length() > 0)
				{
					if (! fname.contains("."))
						fname = fname + DBExport.DBEXPORT_FILENAME_SUFFIX;  // ".csv"
					doExport(fname);
				} else {
					Toast.makeText(LogbookShow.this, R.string.please_enter_the_filename, Toast.LENGTH_SHORT).show();
				}
			}
		});
		alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {}
		});

		return alert.create();
	}

	/**
	 * Check Settings table for <tt>CURRENT_VEHICLE</tt>.
	 * Set {@link #currV}.
	 *
	 * @return true if settings exist and are OK, false otherwise.
	 */
	private boolean checkCurrentVehicleSetting()
	{
		currV = Settings.getCurrentVehicle(db, false);
		return (currV != null);
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.logbook_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    case R.id.menu_logbook_recent_gas:
	    	startActivity(new Intent(this, LogbookRecentGas.class));
	        return true;

		case R.id.menu_logbook_filter_location:
			askLocationAndShow(((showV != null) ? showV.getID() : 0), this, db);
			return true;

		case R.id.menu_logbook_go_to_date:
			showDialog(R.id.menu_logbook_go_to_date);
			return true;

		case R.id.menu_logbook_other_veh:
			showDialog(R.id.menu_logbook_other_veh);
			return true;

		case R.id.menu_logbook_validate:
			doDBValidation();
			return true;

		case R.id.menu_logbook_export:
			showDialog(R.id.menu_logbook_export);
			return true;

		default:
	        return super.onOptionsItemSelected(item);
	    }
	}

	/**
	 * Load a few weeks of earlier trips from the database.
	 * @param v  ignored
	 */
	public void onClick_BtnEarlier(View v)
	{
		// TODO if too many ranges loaded, consider clear out most recent ones
		if (! ltm.addEarlierTrips(db))
		{
			View btnEarlier = findViewById(R.id.logbook_show_btn_earlier);
			if (btnEarlier != null)
				btnEarlier.setVisibility(View.GONE);
			Toast.makeText
				(this, R.string.no_earlier_trips_found, Toast.LENGTH_SHORT).show();
			return;
		}
		rangeEarlierClicked = true;
		StringBuffer sbTrips = new StringBuffer();
		ltm.getRange(0).appendRowsAsTabbedString(sbTrips);

		final TextView tv;
		if (tvNoTripsFound != null)
		{
			// replace that with the trip text
			tv = tvNoTripsFound;
			tv.setText(sbTrips);
			tvNoTripsFound = null;
		} else {
			// create a new textview
			if (TS_ROW_LP == null)
				TS_ROW_LP = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			tv = new TextView(this);
			tv.setLayoutParams(TS_ROW_LP);
			tv.setText(sbTrips);
			tripListParentLayout.addView(tv, tripListBtnEarlierPosition + 1);
		}

		// Once layout is done, scroll to the bottom of the newly added text
		// so that what's currently visible, stays visible.
		if (sv != null)
			sv.post(new Runnable() {
				public void run() {
					sv.scrollTo(0, tv.getMeasuredHeight());
				}
			});
	}

	/**
	 * Load a few weeks of later trips from the database.
	 * @param v  ignored
	 */
	public void onClick_BtnLater(View v)
	{
		// TODO if too many ranges loaded, consider clear out oldest ones
		if (! ltm.addLaterTrips(db))
		{
			View btnLater = findViewById(R.id.logbook_show_btn_later);
			if (btnLater != null)
				btnLater.setVisibility(View.GONE);
			Toast.makeText
				(this, R.string.no_later_trips_found, Toast.LENGTH_SHORT).show();
			return;
		}
		rangeEarlierClicked = false;
		StringBuffer sbTrips = new StringBuffer();
		ltm.getRange(ltm.getRangeCount()-1).appendRowsAsTabbedString(sbTrips);

		if (TS_ROW_LP == null)
			TS_ROW_LP = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		final TextView tv = new TextView(this);
		tv.setLayoutParams(TS_ROW_LP);
		tv.setText(sbTrips);
		tripListParentLayout.addView(tv, tripListBtnLaterPosition);
		++tripListBtnLaterPosition;
	}


	@Override
	public void onPause()
	{
		super.onPause();
		if ((verifCache != null) && (verifTask == null))
		{
			verifCache.release();
			verifCache = null;
		}
		if (db != null)
			db.close();
	}

	// TODO javadoc
	@Override
	public void onResume()
	{
		super.onResume();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		if (verifCache != null)
		{
			verifCache.release();
			verifCache = null;
		}
		if (db != null)
			db.close();
	}

	/**
	 * Run db validation level {@link RDBVerifier#LEVEL_TDATA} in a separate thread.
	 * Uses {@link LogbookShow#verifCache}, which must not be null.
	 * Calls {@link RDBVerifier#verify(int)}.
	 * If OK, sets {@link LogbookShow#verifiedLevel} and clears {@link LogbookShow#verifCache}.
	 * @see LogbookShow#doDBValidation(boolean)
	 * @see BackupsRestore.ValidateDBTask
	 */
	private class ValidateDBTDataTask extends AsyncTask<Void, Integer, Boolean>
	{
		ProgressDialog dia;

		@Override
		protected Boolean doInBackground(final Void... unusedParam)
		{
			final boolean ok = (verifCache.verify(RDBVerifier.LEVEL_TDATA) == 0);
			if (ok)
			{
				if (verifCache != null)
				{
					verifCache.release();
					verifCache = null;
				}
			}
			verifTask = null;
			return ok ? Boolean.TRUE : Boolean.FALSE;
		}

		@Override
		protected void onPreExecute()
		{
			verifTask = this;

			dia = new ProgressDialog(LogbookShow.this);
			// dia.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);  TODO progress bar
			dia.setMessage(getResources().getString(R.string.logbook_show__validating_db));
			dia.setIndeterminate(true);
			dia.setCancelable(false);
			dia.show();
		}

		@Override
		protected void onProgressUpdate(Integer... progress) { }

		@Override
		protected void onPostExecute(final Boolean ok)
		{
			if (dia.isShowing())
				dia.dismiss();
			Toast.makeText(getApplicationContext(),
				( ok
					? R.string.logbook_show__validation_successful_3
					: R.string.logbook_show__validation_failed )
				, Toast.LENGTH_SHORT)
				.show();
		}
	}

}
