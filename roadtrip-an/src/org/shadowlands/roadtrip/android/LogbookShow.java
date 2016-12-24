/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2016 Jeremy D Monin <jdmonin@nand.net>
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
import org.shadowlands.roadtrip.db.RDBKeyNotFoundException;
import org.shadowlands.roadtrip.db.RDBVerifier;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.VehSettings;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.ViaRoute;
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
import android.widget.Button;
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
			LogbookTableModel expLTM = new LogbookTableModel(ltm, db);
			DBExport.exportTripData(this, expLTM, fname);
			LogbookTableModel.trip_simple_mode = prevMode;
			Toast.makeText(this, R.string.logbook_show__export_complete, Toast.LENGTH_SHORT).show();
			/*
			 * For a selected date range, instead would use:
			LogbookTableModel expLTM = new LogbookTableModel
				(showV, ltm.getRange(0).timeStart, ... , db);
			 */
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
			Toast.makeText
				(getApplicationContext(), "Current vehicle not found in db",
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

	/** Show a logbook-related dialog. */
	@Override
	protected Dialog onCreateDialog(int id)
	{
	    switch (id) {
		case R.id.menu_logbook_go_to_date:
			return onCreateGoToDateVehicleDialog(false);

		case R.id.menu_logbook_filter_location:
			return new SearchLocationPopup(((showV != null) ? showV.getID() : 0), this, db).getDialog();

		case R.id.menu_logbook_other_veh:
			return onCreateGoToDateVehicleDialog(true);

		case R.id.menu_logbook_search_vias:
			return new SearchViasPopup(((showV != null) ? showV.getID() : 0), this, db).getDialog();

		case R.id.menu_logbook_export:
			return onCreateExportDialog();
	    }

	    return null;
	}

	/**
	 * Show a new {@link LogbookShow} when a vehicle and/or date are selected
	 * from the dialog created in {@link #onCreateGoToDateVehicleDialog(boolean)}.
	 * @param vehicleOnly  True if not also showing date ({@code dpick} == null)
	 * @param selV  Selected vehicle from dialog's spinner
	 * @param cal   Calendar object from dialog
	 * @param dpick  Calendar picker, or null if {@code vehicleOnly}
	 * @since 0.9.50
	 */
	private void onClickDateVehDialogOK
		(final boolean vehicleOnly, final Vehicle selV, final Calendar cal, final DatePicker dpick)
	{
		final int calGoToDate;  // unix time for EXTRAS_DATE
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
			cal.setTimeInMillis(System.currentTimeMillis());
			cal.set(Calendar.YEAR, dpick.getYear());
			cal.set(Calendar.MONTH, dpick.getMonth());
			cal.set(Calendar.DAY_OF_MONTH, dpick.getDayOfMonth());
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			calGoToDate = (int) (cal.getTimeInMillis() / 1000L);
		}

		// TODO consider re-use this one, instead of a new activity, if same vehicle
		Intent i = new Intent(LogbookShow.this, LogbookShow.class);
		i.putExtra(EXTRAS_DATE, calGoToDate);
		i.putExtra(EXTRAS_VEHICLE_ID, selV.getID());
		LogbookShow.this.startActivity(i);
	}

	/**
	 * Show the DatePickerDialog and Vehicle chooser leading to "Go To Date" mode.
	 * The vehicle spinner shows Active or Inactive vehicles (same state as {@link #showV})
	 * with an "Other..." item at the end of the list.  Choosing Other causes the spinner contents
	 * to change to all vehicles with the opposite Active flag.
	 *
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
		SpinnerDataFactory.setupVehiclesSpinner
			(db, Vehicle.FLAG_WITH_OTHER |
			    ((showV.isActive()) ? Vehicle.FLAG_ONLY_ACTIVE : Vehicle.FLAG_ONLY_INACTIVE),
			 this, vehs, showV.getID());
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
				Vehicle v = (Vehicle) vehs.getSelectedItem();
				if (v == null)
					return;  // shouldn't happen

				onClickDateVehDialogOK(vehicleOnly, v, cal, dpick);
			}
		});
		alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {}
		});

		final AlertDialog dia = alert.create();

		// In vehicleOnly mode, when a vehicle is selected in the spinner,
		// immediately show it (without needing to also hit OK).  Also
		// handle "Other Vehicle..." selection:
		// show the same dialog but switch active/inactive
		SpinnerDataFactory.SpinnerItemSelectedListener vehSpinListener
			= new SpinnerDataFactory.SpinnerItemSelectedListener(showV.getID())
		{
			private long createdAt = System.currentTimeMillis();
			boolean vehsActive = showV.isActive();

			public void onItemSelected
				(AdapterView<?> ctx, View view, int pos, long id)
			{
				Object o = vehs.getSelectedItem();
				if ((o == null) || (System.currentTimeMillis() - createdAt < 300))
					return;  // too early, this call is likely from initial setup

				if (o != Vehicle.OTHER_VEHICLE)
				{
					if (vehicleOnly)
					{
						Vehicle v = (Vehicle) vehs.getSelectedItem();
						if ((v == null) || (v.getID() == itemID_default))
							return;  // shouldn't happen

						// immediately show the new vehicle
						onClickDateVehDialogOK(vehicleOnly, v, cal, dpick);
						if (dia != null)
							dia.dismiss();
					}
					return;
				}

				vehsActive = ! vehsActive;
				SpinnerDataFactory.setupVehiclesSpinner
					(db, Vehicle.FLAG_WITH_OTHER |
					    ((vehsActive) ? Vehicle.FLAG_ONLY_ACTIVE : Vehicle.FLAG_ONLY_INACTIVE),
					 LogbookShow.this, vehs, showV.getID());
				createdAt = System.currentTimeMillis();  // ignore onItemSelected call on spinner re-setup

				// open the spinner to show the new vehicles, so the user
				// won't need to tap it open to get to where they were
				vehs.postDelayed(new Runnable() {
						public void run() { vehs.performClick(); }
					}, 100);
			}
		};
		vehSpinListener.dia = dia;
		vehs.setOnItemSelectedListener(vehSpinListener);

		return dia;
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
			{
				Intent i = new Intent(this, LogbookRecentGas.class);
				if (showV != null)
					i.putExtra(LogbookRecentGas.EXTRAS_VEHICLE_ID, showV.getID());
				startActivity(i);
			}
			return true;

		case R.id.menu_logbook_filter_location:
			showDialog(R.id.menu_logbook_filter_location);
			return true;

		case R.id.menu_logbook_go_to_date:
			showDialog(R.id.menu_logbook_go_to_date);
			return true;

		case R.id.menu_logbook_other_veh:
			showDialog(R.id.menu_logbook_other_veh);
			return true;

		case R.id.menu_logbook_search_vias:
			showDialog(R.id.menu_logbook_search_vias);
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
				TS_ROW_LP = new ViewGroup.LayoutParams
					(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
			TS_ROW_LP = new ViewGroup.LayoutParams
				(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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

			new AlertDialog.Builder(LogbookShow.this)
				.setMessage( (ok)
					? R.string.logbook_show__validation_no_problems
					: R.string.logbook_show__validation_failed )
				.setNeutralButton(android.R.string.ok, null)
				.show();
		}
	}

	/**
	 * Logbook in Location Mode: Show a popup with the current GeoArea's locations.
	 * The user will pick one and a new LogbookShow activity is launched for it.
	 *<P>
	 * Before v0.9.51 this was {@code LogbookShow.askLocationAndShow(..)}.
	 * @since 0.9.51
	 */
	public static final class SearchLocationPopup
	{
		private AlertDialog aDia;

		/** Location to search for; null if no location selected. */
		private static Location locObj = null;

		/** Show trips for all vehicles? */
		private static boolean allV = false;

		/** ID of {@link GeoArea} selected in spinner. */
		private static int areaID = -1;

		/**
		 * Create a new {@link SearchLocationPopup}, ready to show.
		 * Remember to call {@link #getDialog()} from the UI thread.
		 * If this constructor fails to find a required item in the database,
		 * it will show a Toast and {@code getDialog()} will do nothing when called.
		 * @param vID  To get current area, a specific vehicle ID or 0 for current vehicle
		 * @param fromActivity  Current activity; will call {@link Activity#startActivity(Intent)} on it
		 * @param db  Connection to use
		 * @see #showTripsForLocation(int, boolean, int, Activity)
		 * @see SearchViasPopup
		 */
		public SearchLocationPopup(final int vID, final Activity fromActivity, final RDBAdapter db)
		{
			if (areaID == -1)
			{
				final Vehicle av;
				if (vID == 0) {
					av = Settings.getCurrentVehicle(db, false);
				} else {
					try {
						av = new Vehicle(db, vID);
					} catch (RDBKeyNotFoundException e) {
						return;
					}
				}
				if (av == null)
					return;

				final GeoArea currA = VehSettings.getCurrentArea(db, av, false);
				if (currA == null)
					return;

				areaID = currA.getID();
			}

			/** Find all locations in the current area, or null */
			Location[] areaLocs = Location.getAll(db, areaID);
			if (areaLocs == null)
			{
				Toast.makeText(fromActivity, R.string.logbook_show__no_locs_in_area, Toast.LENGTH_SHORT).show();
				return;
			}

			final View askItems =
				fromActivity.getLayoutInflater().inflate(R.layout.logbook_show_popup_locsearch, null);
			final AutoCompleteTextView loc =
				(AutoCompleteTextView) askItems.findViewById(R.id.logbook_show_popup_locs_loc);

			if (locObj != null)
			{
				if (locObj.getAreaID() == areaID)
					loc.setText(locObj.toString());
				else
					locObj = null;
			}
			if (allV)
			{
				CheckBox cb = (CheckBox) askItems.findViewById(R.id.logbook_show_popup_locs_allv);
				if (cb != null)
					cb.setChecked(true);
			}

			ArrayAdapter<Location> adapter
				= new ArrayAdapter<Location>(fromActivity, R.layout.list_item, areaLocs);
			loc.setAdapter(adapter);
			loc.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				public void onItemClick(AdapterView<?> parent, View clickedOn, int position, long rowID)
				{
					ListAdapter la = loc.getAdapter();
					if (la == null)
						return;

					locObj = (position != -1) ? (Location) la.getItem(position) : null;
				}
				});

			if (allV)
			{
				CheckBox cb = (CheckBox) askItems.findViewById(R.id.logbook_show_popup_locs_allv);
				if (cb != null)
					cb.setChecked(allV);
			}

			/** When GeoArea spinner selection changes, query for locations in that area: */
			final Spinner areas = (Spinner) askItems.findViewById(R.id.logbook_show_popup_locs_areas);
			SpinnerDataFactory.setupGeoAreasSpinner(db, fromActivity, areas, areaID, true, -1);
			areas.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				public void onItemSelected
					(AdapterView<?> ctx, View view, int pos, long id)
				{
					final int newAreaID = ((GeoArea) areas.getSelectedItem()).getID();
					if (newAreaID == areaID)
						return;
					areaID = newAreaID;
					if ((locObj != null)
						&& (newAreaID != locObj.getAreaID()))
					{
						loc.setText("");
						locObj = null;
					}
					Location[] areaLocs = Location.getAll(db, newAreaID);
					if (areaLocs == null)
					{
						Toast.makeText
							(fromActivity, R.string.logbook_show__no_locs_in_area,
							 Toast.LENGTH_SHORT).show();
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
					final String locText = loc.getText().toString().trim();
					CheckBox cb = (CheckBox) askItems.findViewById(R.id.logbook_show_popup_locs_allv);
					if (cb != null)
						allV = cb.isChecked();

					if ((locObj != null) && ! locText.equalsIgnoreCase(locObj.getLocation()))
						locObj = null;  // picked from autocomplete, then changed text
					if ((locObj == null) && (locText.length() > 0))
					{
						// Typed location description, instead of picked from autocomplete
						try
						{
							locObj = Location.getByDescr(db, areaID, locText);
						} catch (IllegalStateException e) {}
					}

					if (locObj != null)
					{
						showTripsForLocation
							(locObj.getID(), allV, vID, fromActivity);
					} else {
						final int msg = (locText.length() == 0)
							? R.string.please_enter_the_location
							: R.string.please_choose_existing_location;

						Toast.makeText(fromActivity, msg, Toast.LENGTH_SHORT).show();
					}
				}
			});
			alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton)
				{
					locObj = null;
				}
			});

			aDia = alert.create();
		}

		/**
		 * Get the built search dialog. Call this method from {@code onCreateDialog(..)}
		 * instead of calling {@link #show()}.
		 * @return the AlertDialog, or {@code null} if it couldn't be initialized in the constructor
		 * @see #show()
		 */
		public AlertDialog getDialog()
		{
			return aDia;
		}
	}

	/**
	 * Show {@link ViaRoute}s between two locations:
	 * A popup where two locations can be chosen by the user from the current
	 * or other GeoAreas, then search and show a list of ViaRoutes between them.
	 * Call the {@link #SearchViasPopup(int, Activity, RDBAdapter) constructor}
	 * and then either {@link #getDialog()} or {@link #show()}.
	 * @see SearchLocationPopup
	 * @since 0.9.51
	 */
	public static final class SearchViasPopup
	{
		private AlertDialog aDia;

		/** Location selected in text field A or B; null if no location selected. */
		private Location locObj_A = null, locObj_B = null;

		/** {@link GeoArea} ID of {@link #locObj_A} or {@link #locObj_B}. */
		private int areaID_A, areaID_B;

		/**
		 * Create a new {@link SearchViasPopup}, ready to show.
		 * Remember to call {@link #getDialog()} or {@link #show()} from the UI thread.
		 * If this constructor fails to find a required item in the database,
		 * it will show a Toast and {@code show()} will return false when called.
		 * @param vID  To get current area, a specific vehicle ID or 0 for current vehicle
		 * @param fromActivity  Current activity, for resources
		 * @param db  Connection to use
		 * @see SearchLocationPopup
		 */
		public SearchViasPopup
			(final int vID, final Activity fromActivity, final RDBAdapter db)
		{
			final Vehicle av;
			if (vID == 0) {
				av = Settings.getCurrentVehicle(db, false);
			} else {
				try {
					av = new Vehicle(db, vID);
				} catch (RDBKeyNotFoundException e) {
					return;
				}
			}
			if (av == null)
				return;

			final GeoArea currA = VehSettings.getCurrentArea(db, av, false);
			if (currA == null)
				return;

			final int aID = currA.getID();
			areaID_A = aID;
			areaID_B = aID;

			/** Find all locations in the current area, or null */
			Location[] areaLocs = Location.getAll(db, aID);
			if (areaLocs == null)
			{
				Toast.makeText(fromActivity, R.string.logbook_show__no_locs_in_area, Toast.LENGTH_SHORT).show();
				return;
			}

			final View askItems = fromActivity.getLayoutInflater().inflate(R.layout.logbook_loc_vias_search, null);

			final AutoCompleteTextView locA =
				(AutoCompleteTextView) askItems.findViewById(R.id.logbook_loc_vias_locA);
			final AutoCompleteTextView locB =
				(AutoCompleteTextView) askItems.findViewById(R.id.logbook_loc_vias_locB);

			if (locObj_A != null)
				if (locObj_A.getAreaID() == areaID_A)
					locA.setText(locObj_A.toString());
				else
					locObj_A = null;

			if (locObj_B != null)
				if (locObj_B.getAreaID() == areaID_B)
					locA.setText(locObj_B.toString());
				else
					locObj_B = null;

			ArrayAdapter<Location> adapter = new ArrayAdapter<Location>(fromActivity, R.layout.list_item, areaLocs);
			locA.setAdapter(adapter);
			locB.setAdapter(adapter);
			locA.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				public void onItemClick(AdapterView<?> parent, View clickedOn, int position, long rowID)
				{
					ListAdapter la = locA.getAdapter();
					if (la == null)
						return;

					locObj_A = (position != -1) ? (Location) la.getItem(position) : null;
				}
				});
			locB.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				public void onItemClick(AdapterView<?> parent, View clickedOn, int position, long rowID)
				{
					ListAdapter la = locB.getAdapter();
					if (la == null)
						return;

					locObj_B = (position != -1) ? (Location) la.getItem(position) : null;
				}
				});

			/** When GeoArea spinner selection changes, query for locations in that area: */
			final Spinner areasA = (Spinner) askItems.findViewById(R.id.logbook_loc_vias_locA_areas),
			              areasB = (Spinner) askItems.findViewById(R.id.logbook_loc_vias_locB_areas);
			SpinnerDataFactory.setupGeoAreasSpinner(db, fromActivity, areasA, areaID_A, true, -1);
			SpinnerDataFactory.setupGeoAreasSpinner(db, fromActivity, areasB, areaID_B, true, -1);
			areasA.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				public void onItemSelected
					(AdapterView<?> ctx, View view, int pos, long id)
				{
					final int newAreaID = ((GeoArea) areasA.getSelectedItem()).getID();
					if (newAreaID == areaID_A)
						return;

					areaID_A = newAreaID;
					if ((locObj_A != null) && (newAreaID != locObj_A.getAreaID()))
					{
						locA.setText("");
						locObj_A = null;
					}
					Location[] areaLocs = Location.getAll(db, newAreaID);
					if (areaLocs == null)
					{
						Toast.makeText
							(fromActivity, R.string.logbook_show__no_locs_in_area,
							 Toast.LENGTH_SHORT).show();
						locA.setAdapter((ArrayAdapter<Location>) null);
						return;
					}

					locA.setAdapter(new ArrayAdapter<Location>(fromActivity, R.layout.list_item, areaLocs));
				}

				public void onNothingSelected(AdapterView<?> parent) { } // Required stub
			});
			areasB.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				public void onItemSelected
					(AdapterView<?> ctx, View view, int pos, long id)
				{
					final int newAreaID = ((GeoArea) areasB.getSelectedItem()).getID();
					if (newAreaID == areaID_B)
						return;

					areaID_B = newAreaID;
					if ((locObj_B != null) && (newAreaID != locObj_B.getAreaID()))
					{
						locB.setText("");
						locObj_B = null;
					}
					Location[] areaLocs = Location.getAll(db, newAreaID);
					if (areaLocs == null)
					{
						Toast.makeText
							(fromActivity, R.string.logbook_show__no_locs_in_area,
							 Toast.LENGTH_SHORT).show();
						locB.setAdapter((ArrayAdapter<Location>) null);
						return;
					}

					locB.setAdapter(new ArrayAdapter<Location>(fromActivity, R.layout.list_item, areaLocs));
				}

				public void onNothingSelected(AdapterView<?> parent) { } // Required stub
			});

			AlertDialog.Builder alert = new AlertDialog.Builder(fromActivity);
			alert.setMessage(R.string.logbook_show__search_via_routes__desc);
			alert.setView(askItems);
			alert.setPositiveButton(android.R.string.search_go, null);  // will override to prevent auto-close
			alert.setNegativeButton(android.R.string.cancel, null);

			aDia = alert.create();

			// To prevent auto-close when button is clicked, and allow the user to re-enter
			// any missing data if validation fails, override the positive button's listener:
			aDia.setOnShowListener(new DialogInterface.OnShowListener()
			{
				@Override
				public void onShow(DialogInterface dialog)
				{
					final Button bSearch = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
					bSearch.setOnClickListener(new View.OnClickListener()
					{
				public void onClick(final View unused)
				{
					String locText = locA.getText().toString().trim();
					if (locObj_A != null)
						if (! locText.equalsIgnoreCase(locObj_A.getLocation()))
							locObj_A = null;  // tapped loc in dropdown, then changed it
					if ((locObj_A == null) && (locText.length() > 0))
						// Typed location description, instead of picked from autocomplete
						try
						{
							locObj_A = Location.getByDescr(db, areaID_A, locText);
						} catch (IllegalStateException e) {}
					if (locObj_A != null)
					{
						locText = locB.getText().toString().trim();
						if (locObj_B != null)
							if (! locText.equalsIgnoreCase(locObj_B.getLocation()))
								locObj_B = null;
						if ((locObj_B == null) && (locText.length() > 0))
							try
							{
								locObj_B = Location.getByDescr(db, areaID_B, locText);
							} catch (IllegalStateException e) {}
					}

					if ((locObj_A != null) && (locObj_B != null))
					{
						aDia.dismiss();

						// TODO encapsulate this and make it look better than an alertdialog
						// TODO i18n

						final int locID_A = locObj_A.getID(), locID_B = locObj_B.getID();
						final ViaRoute[] vias = ViaRoute.getAll(db, locID_A, locID_B, true);
						StringBuilder sb = new StringBuilder();
						if (vias == null)
							sb.append("None found");
						else
						{
							final int locID_FromFirst = vias[0].getLocID_From();
							boolean didDirSwitch = false;
							if (locID_FromFirst == locID_A)
								sb.append("From " + locObj_A.getLocation()
									  + " to " + locObj_B.getLocation() + ":\n");
							else
								sb.append("From " + locObj_B.getLocation()
									  + " to " + locObj_A.getLocation() + ":\n");
							for (final ViaRoute via : vias)
							{
								Log.d(TAG, "from " + via.getLocID_From() + " to " + via.getLocID_To() + ": " + via.getDescr());
								if ((! didDirSwitch) && (via.getLocID_From() != locID_FromFirst))
								{
									didDirSwitch = true;
									sb.append('\n');
									if (locID_FromFirst == locID_B)  // now show the other
										sb.append("From " + locObj_A.getLocation()
											  + " to " + locObj_B.getLocation() + ":\n");
									else
										sb.append("From " + locObj_B.getLocation()
											  + " to " + locObj_A.getLocation() + ":\n");
								}

								final int dist = via.getOdoDist();
								if (dist != 0)
								{
									sb.append(Integer.toString(dist / 10));
									sb.append('.');
									sb.append(Integer.toString(dist % 10));
									// TODO unit name from prefs? convert?
									sb.append(" mi ");
								}
								sb.append("via ");
								sb.append(via.getDescr());
								sb.append("\n");
							}
						}
						new AlertDialog.Builder(fromActivity)
							.setMessage(sb)
							.setNeutralButton(android.R.string.ok, null)
							.show();
					} else {
						if (locObj_A == null)
							locA.requestFocus();
						else
							locB.requestFocus();
						final int msg = (locText.length() == 0)
							? R.string.please_enter_the_location
							: R.string.please_choose_existing_location;

						Toast.makeText(fromActivity, msg, Toast.LENGTH_SHORT).show();
					}
				}
			});
				}
			});
		}

		/**
		 * Get the built search dialog. Call this method from {@code onCreateDialog(..)}
		 * instead of calling {@link #show()}.
		 * @return the AlertDialog, or {@code null} if it couldn't be initialized in the constructor
		 * @see #show()
		 */
		public AlertDialog getDialog()
		{
			return aDia;
		}

		/**
		 * Show this search dialog. Call this method from the UI thread.
		 * @return true if alert was shown, false if it couldn't be initialized in the constructor
		 * @see #getDialog()
		 */
		public boolean show()
		{
			if (aDia == null)
				return false;

			aDia.show();
			return true;
		}
	}

}
