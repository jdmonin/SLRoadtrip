/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2015 Jeremy D Monin <jdmonin@nand.net>
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
import org.shadowlands.roadtrip.db.TripCategory;
import org.shadowlands.roadtrip.db.VehSettings;
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
import android.os.Handler;
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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.TimePicker.OnTimeChangedListener;
import android.widget.Toast;

/**
 * Confirm a trip stop during a trip, or end the trip, from Main activity.
 * Called when the stop begins (no {@link VehSettings#CURRENT_TSTOP} in the database for current vehicle),
 * when resuming the trip from the current stop (has <tt>CURRENT_TSTOP</tt>), and when
 * ending the trip (show this with the boolean intent extra
 * {@link #EXTRAS_FLAG_ENDTRIP}).
 *<P>
 * Assumes CURRENT_DRIVER, CURRENT_VEHICLE, CURRENT_TRIP are set.
 * If not, it will jump to the {@link AndroidStartup} activity
 * to enter the driver/vehicle data.
 *<P>
 * If we're not ending the trip right now, and we're on a frequent trip
 * with stops ({@link VehSettings#CURRENT_FREQTRIP_TSTOPLIST} is set),
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
	implements OnDateSetListener, OnItemClickListener, TextWatcher, OnTimeChangedListener
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

	/** all locations in the {@link #areaLocs_areaID} area, or null; set from {@link #currA} in {@link #onCreate(Bundle)} */
	private Location[] areaLocs;

	/**
	 * the areaID of locations in {@link #areaLocs}, or -1.
	 * For roadtrips, also the currently selected area ID of
	 * {@link #btnRoadtripArea_chosen}.
	 * 0 is used for all local trips.
	 * Geoarea id 0 is OK for tstops/locations within a roadtrip, but not
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

	/** If true, not paused yet; used by onResume to prevent initializing things twice right after onCreate. */
	private boolean neverPaused;

	/**
	 * If the {@link #tp_time_cont Continue Time} is the current time, and
	 * we're updating it as the time changes, the hour and minute of
	 * the last update. <BR>
	 * Format is -1 if unused, or (Hour << 8) | Minute.
	 * @see #contTimeRunningHandler
	 * @see #initContTimeRunning(Calendar, long)
	 */
	private int contTimeRunningHourMinute = -1;

	/**
	 * If we're updating {@link #tp_time_cont} as the time changes, the handler for that.
	 * Otherwise null.
	 * @see #contTimeRunningHourMinute
	 * @see #initContTimeRunning(Calendar, long)
	 * @see #contTimeRunningRunnable
	 * @see http://developer.android.com/resources/articles/timed-ui-updates.html
	 */
	private Handler contTimeRunningHandler = null;

	/**
	 * If we're updating {@link #tp_time_cont} as the time changes, the Runnable for that.
	 * Otherwise null.
	 * @see #contTimeRunningHandler
	 */
	private Runnable contTimeRunningRunnable = null;

	/**
	 * If we're updating {@link #tp_time_cont} as the time changes, true if we've already
	 * put up a Toast with the <tt>trip_tstop_entry_time_cont_update</tt> string.
	 * @see #contTimeRunningHandler
	 */
	private boolean contTimeRunningAlreadyToasted = false;

	/**
	 * Frequent TStop chosen by user, if we'e on a freq trip and
	 * {@link VehSettings#CURRENT_FREQTRIP_TSTOPLIST} isn't empty.
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

	/** if true, {@link #onClick_BtnEnterTStop(View)} has already asked the user to
	 *  confirm continuing from the stop without entering any odometer.
	 */
	private boolean askedConfirmEmptyOdos = false;

	private CheckBox odo_total_chk, odo_trip_chk, tp_time_stop_chk, tp_time_cont_chk;
	private TimePicker tp_time_stop, tp_time_cont;
	/** Ignore {@link #onTimeChanged(TimePicker, int, int)} until this is true */
	private boolean tp_time_stop_init;

	/** optional {@link TripCategory}; null unless {@link #stopEndsTrip} */
	private Spinner spTripCat;

	/** location; uses, sets {@link #locObj}. Autocomplete list is {@link #areaLocs}.
	 *  During a roadtrip, its geoarea is selected and hilighted as {@link #btnRoadtripArea_chosen}.
	 */
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
	 * @see TStop#TEMPFLAG_CREATED_VIAROUTE
	 */
	private ViaRoute viaRouteObjCreatedHere = null;

	/**
	 * When at a stop, the previous stop's location ID; for trip's first stop, the trip start location;
	 * from {@link VehSettings#getPreviousLocation(RDBAdapter, Vehicle, boolean)}.
	 * Used for {@link #viaRouteObj}.
	 * If {@code null} because of a missing {@link VehSettings}, ViaRoutes can't be created or autocompleted,
	 * but causes no other problems: Make sure code checks != null before using prevLocObj.
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
	 * {@link #locObj} was created for this TStop.
	 * @see TStop#TEMPFLAG_CREATED_LOCATION
	 */
	private Location locObjCreatedHere = null;

	/**
	 * if true, then <tt>currTS != null</tt>, and <tt>stopGas.gas_brandgrade</tt>
	 * was created for this TStop.
	 *<P>
	 * Remember that <tt>stopGas</tt> isn't loaded from the db until
	 * {@link #onClick_BtnGas(View)} or {@link #onClick_BtnEnterTStop(View)}.
	 * @see TStop#TEMPFLAG_CREATED_GASBRANDGRADE
	 */
	private boolean gbgCreatedHere = false;

	/** TStop's date-time for time_stop, time_continue */
	private Calendar stopTime, contTime;

	/**
	 * Which date to update: {@link #stopTime} or {@link #contTime}?
	 * Set in {@link #onCreateDialog(int)}, checked in {@link #onDateSet(DatePicker, int, int, int)}.
	 */
	private Calendar currentDateToPick;

	/**
	 * date formatter for use by {@link DateFormat#format(CharSequence, Calendar)},
	 * initialized once in {@link #updateDateButtons(int)}.
	 */
	private StringBuffer fmt_dow_shortdate;
	private Button btnStopTimeDate, btnContTimeDate;

	/**
	 * When stopping during a roadtrip, buttons to pick the geoarea for
	 * the Location textfield {@link #loc} and {@link #areaLocs_areaID}.
	 * Null unless currT.isRoadtrip.
	 * @see #btnRoadtripArea_chosen
	 */
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
	 * @see #onClick_BtnAreaOther(View)
	 */
	private Button btnRoadtripArea_chosen;

	///////////////////////////////
	// Start of calculator fields
	///////////////////////////////

	private static final int CALC_OP_NONE = 0, CALC_OP_ADD = 1,
	  CALC_OP_SUB = 2, CALC_OP_MUL = 3, CALC_OP_DIV = 4;

	/**
	 * Non-null, for callbacks, when the odometer editor calculator is showing.
	 * @see #onClickEditOdo(OdometerNumberPicker, boolean)
	 */
	private View popupCalcItems = null;

	/** If displayed, is the calculator for {@link #odo_trip} and not {@link #odo_total}? */
	private boolean calcOdoIsTrip;

	/** Calculator's current value, for {@link #onClickEditOdo(OdometerNumberPicker, boolean)} callbacks */
	private EditText calcValue;

	/** Calculator's memory register (M+ M- MR MC buttons) */
	private float calcMemory = 0.0f;

	/** If true, the next button press clears {@link #calcValue} */
	private boolean calcNextPressClears;

	/** Calculator's previous value */
	private float calcPrevOperand;

	/**
	 * Calculator's operation: {@link #CALC_OP_ADD}, {@link #CALC_OP_SUB},
	 * {@link #CALC_OP_MUL}, {@link #CALC_OP_DIV} or {@link #CALC_OP_NONE}.
	 */
	private int calcOperation;

	/** Digit buttons 0-9 and '.' for {@link #onClickEditOdo(OdometerNumberPicker, boolean)} callbacks */
	private View calc0, calc1, calc2, calc3, calc4, calc5,
		calc6, calc7, calc8, calc9, calcDeci;

	///////////////////////////////
	// End of calculator fields
	///////////////////////////////

	/** Called when the activity is first created.
	 * See {@link #updateTextAndButtons()} for remainder of init work,
	 * which includes checking the current driver/vehicle/trip
	 * and hiding/showing buttons as appropriate.
	 * Also calls {@link #onRestoreInstanceState(Bundle)} if
	 * our state was saved.
	 * Sets {@link #areaLocs_areaID} and fills {@link #areaLocs} based
	 * on current TStop, prev location, or trip/roadtrip fields.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		db = new RDBOpenHelper(this);
		setContentView(R.layout.trip_tstop_entry);
		neverPaused = true;

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
		tp_time_stop.setOnTimeChangedListener(this);  // onTimeChanged - to set checkbox if user sets time
		tp_time_cont.setOnTimeChangedListener(this);  // onTimeChanged - to cancel auto-update of time if user sets time

		btnStopTimeDate = (Button) findViewById(R.id.trip_tstop_btn_stop_date);
		btnContTimeDate = (Button) findViewById(R.id.trip_tstop_btn_cont_date);
		btnGas = (Button) findViewById(R.id.trip_tstop_btn_gas);

		// get currA, currV, currT, maybe currTS and prevLocObj
		if (! checkCurrentDriverVehicleTripSettings())
		{
			// Internal error: Current area/driver/vehicle/trip not found in db
			Toast.makeText
				(getApplicationContext(),
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

		if (! isCurrentlyStopped)
		{
			View sb = findViewById(R.id.trip_tstop_btn_save);
			if (sb != null)
				sb.setVisibility(View.GONE);
		}

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

		// Hide rows unless stopEndsTrip;
		// set "continue" button text if already stopped
		if (stopEndsTrip)
		{
			spTripCat = (Spinner) findViewById(R.id.trip_tstop_end_category);
			SpinnerDataFactory.setupTripCategoriesSpinner(db, this, spTripCat, currT.getTripCategoryID());
			if (Settings.getBoolean(db, Settings.SHOW_TRIP_PAX, false))
			{
				final int pax = currT.getPassengerCount();
				if (pax != -1)
				{
					EditText et = (EditText) findViewById(R.id.trip_tstop_end_pax);
					if (et != null)
						et.setText(Integer.toString(pax));
				}
			} else {
				View vrow = findViewById(R.id.trip_tstop_row_end_pax);
				if (vrow != null)
					vrow.setVisibility(View.GONE);
			}
			if (Settings.getBoolean(db, Settings.HIDE_FREQTRIP, false))
			{
				View vrow = findViewById(R.id.trip_tstop_row_end_mk_freq);
				if (vrow != null)
					vrow.setVisibility(View.GONE);
			}
		} else {
			View vrow = findViewById(R.id.trip_tstop_row_end_pax);
			if (vrow != null)
				vrow.setVisibility(View.GONE);
			vrow = findViewById(R.id.trip_tstop_row_end_tcat);
			if (vrow != null)
				vrow.setVisibility(View.GONE);
			vrow = findViewById(R.id.trip_tstop_row_end_mk_freq);
			if (vrow != null)
				vrow.setVisibility(View.GONE);

			if (currTS != null)
			{
				Button eb = (Button) findViewById(R.id.trip_tstop_btn_enter);
				if (eb != null)
					eb.setText(R.string.continu);
			}
		}

		// Change title if needed; default title label is stop_during_a_trip
		if (stopEndsTrip)
		{
			setTitle(getResources().getString(R.string.end_trip));
		} else if (isCurrentlyStopped) {
			setTitle(getResources().getString(R.string.continu_from_stop));
		}

		// Set up autocompletes
		loc = (AutoCompleteTextView) findViewById(R.id.trip_tstop_loc);
		loc.addTextChangedListener(this);
		areaLocs_areaID = -1;
		areaLocs = null;
		// loc, areaLocs, areaLocs_areaID will be filled soon.

		via = (AutoCompleteTextView) findViewById(R.id.trip_tstop_via);
		if (Settings.getBoolean(db, Settings.HIDE_VIA, false))
		{
			via.setVisibility(View.GONE);
			View vlab = findViewById(R.id.trip_tstop_via_label);
			vlab.setVisibility(View.GONE);
		} else {
			viaListener = new ViaRouteListenerWatcher();
			via.addTextChangedListener(viaListener);
		}

		// adjust date/time fields, now that we know if we have currTS
		// and know if stopEndsTrip.
		final long timeNow = System.currentTimeMillis();
		boolean contTimeIsNow = false;

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
			contTimeIsNow = true;
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
					contTimeIsNow = false;
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
			long latestVehTime = 1000L * currV.readLatestTime(currT);
			if ((latestVehTime != 0L)
			    && (Math.abs(latestVehTime - timeNow) >= TIMEDIFF_HISTORICAL_MILLIS))
			{
				Toast.makeText
					(this,
					 R.string.using_old_date_due_to_previous,
					 Toast.LENGTH_SHORT).show();
				setTimeStopCheckbox = false;
			} else {
				latestVehTime = timeNow;
				setTimeStopCheckbox = true;
			}
			stopTime.setTimeInMillis(latestVehTime);
		}

		tp_time_stop_chk.setChecked(setTimeStopCheckbox);
		updateDateButtons(0);
		initTimePicker(stopTime, tp_time_stop);
		tp_time_stop_init = true;
		if (contTime != null)
		{
			initTimePicker(contTime, tp_time_cont);
			if (contTimeIsNow)
				initContTimeRunning(contTime);  // keep tp_time_cont current
		}
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
			FreqTrip ft = VehSettings.getCurrentFreqTrip(db, currV, false);
			if (stopEndsTrip)
			{
				// Ending frequent trip. Copy default field values from FreqTrip.
				copyValuesFromFreqTrip(ft);
			}
			else if (ft != null)
			{
				// Not ending trip yet. Should ask the user to choose a FreqTripTStop, if available.
				try {
					// Get list string directly, not via getter, to skip parsing it;
					// we only care whether it's empty
					VehSettings cTSL = new VehSettings
						(db, VehSettings.CURRENT_FREQTRIP_TSTOPLIST, currV);
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

			View v = findViewById(R.id.trip_tstop_area_local_row);
			if (v != null)
				v.setVisibility(View.GONE);

			btnRoadtripAreaStart = (Button) findViewById(R.id.trip_tstop_btn_area_start);
			btnRoadtripAreaNone = (Button) findViewById(R.id.trip_tstop_btn_area_none);
			btnRoadtripAreaEnd = (Button) findViewById(R.id.trip_tstop_btn_area_end);
			btnRoadtripAreaStart.setText(ga_s.getName());
			btnRoadtripAreaEnd.setText(ga_e.getName());
			hilightRoadtripAreaButton(areaLocs_areaID, null, false, 0);
		} else {
			View v = findViewById(R.id.trip_tstop_area_buttons);
			if (v != null)
				v.setVisibility(View.GONE);

			// Look up and show current geoarea name
			TextView tv = (TextView) findViewById(R.id.trip_tstop_area_local_value);
			if ((tv != null) && (areaLocs_areaID > 0))
			{
				try {
					GeoArea ga = new GeoArea(db, areaLocs_areaID);
					tv.setText(ga.getName());
				} catch (Exception e) {}  // ignore: display only, and inconsistency should not occur
			}
		}
	}

	/** set a timepicker's hour and minute, based on a calendar's current time */
	private final static void initTimePicker(Calendar c, TimePicker tp)
	{
		tp.setCurrentHour(c.get(Calendar.HOUR_OF_DAY));
		tp.setCurrentMinute(c.get(Calendar.MINUTE));
	}

	/**
	 * Set up to update {@link #tp_time_cont} as the time changes.
	 * Sets up {@link #contTimeRunningHourMinute}, {@link #contTimeRunningHandler}
	 * and related variables.
	 * @param cNow   A Calendar initialized with {@link System#currentTimeMillis()};
	 *   {@link #tp_time_cont} must already be set to its hour and minute.
	 *   <BR>
	 *   Or, if <tt>cNow</tt> is null, also initialize the timepicker.
	 */
	private final void initContTimeRunning(Calendar cNow)
	{
		final boolean setTimePicker;
		if (cNow == null)
		{
			cNow = Calendar.getInstance();
			cNow.setTimeInMillis(System.currentTimeMillis());
			setTimePicker = true;
		} else {
			setTimePicker = false;
		}

		// given the current time, and the time until next minute (from calendar),
		// set up the handler and the time until it.
		final int millisUntilNextMinute =
			(1000 * (60 - cNow.get(Calendar.SECOND)))
			+ (1000 - cNow.get(Calendar.MILLISECOND)) + 25;
		final int nowHr = cNow.get(Calendar.HOUR_OF_DAY);
		final int nowMn = cNow.get(Calendar.MINUTE);
		contTimeRunningHourMinute = (nowHr << 8) | nowMn;

		if (setTimePicker &&
			((nowMn != tp_time_cont.getCurrentMinute()) || (nowHr != tp_time_cont.getCurrentHour())))
		{
			tp_time_cont.setCurrentMinute(nowMn);
			tp_time_cont.setCurrentHour(nowHr);

			// toast if first time doing so
			if (! contTimeRunningAlreadyToasted)
			{
				contTimeRunningAlreadyToasted = true;
				Toast.makeText(this, R.string.trip_tstop_entry_time_cont_update, Toast.LENGTH_LONG).show();
			}
		}

		if (contTimeRunningHandler == null)
			contTimeRunningHandler = new Handler();

		if (contTimeRunningRunnable == null)
		{
			contTimeRunningRunnable = new Runnable()
			{
				private Calendar cal = Calendar.getInstance();

				public void run()
				{
					if (contTimeRunningHourMinute == -1)
						return;

					final long now = System.currentTimeMillis();
					cal.setTimeInMillis(now);
					final int hr = cal.get(Calendar.HOUR_OF_DAY);
					final int mn = cal.get(Calendar.MINUTE);
					contTimeRunningHourMinute = (hr << 8) | mn;
					tp_time_cont.setCurrentMinute(mn);
					tp_time_cont.setCurrentHour(hr);

					contTimeRunningHandler.removeCallbacks(this);
					contTimeRunningHandler.postDelayed(this, 60L * 1000L);  // again in 1 minute

					// toast if first time doing so
					if (! contTimeRunningAlreadyToasted)
					{
						contTimeRunningAlreadyToasted = true;
						Toast.makeText
							(TripTStopEntry.this,
							 R.string.trip_tstop_entry_time_cont_update,
							 Toast.LENGTH_LONG).show();
					}
				}
			};
		} else {
			contTimeRunningHandler.removeCallbacks(contTimeRunningRunnable);
		}

		// Fire when the next minute begins.
		contTimeRunningHandler.postDelayed(contTimeRunningRunnable, millisUntilNextMinute);
	}

	/**
	 * Hilight the matching GeoArea's button with a green dot, update {@link #areaLocs_areaID},
	 * and optionally update related data.  (Stops during a roadtrip have 3 GeoArea buttons
	 * to select the starting area, no area, or ending area.)
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
	 * If there's an inconsistency between Settings and GeoArea/Vehicle/Person tables, don't fix it
	 * in those tables, but don't load objects either.  The current GeoArea setting may be updated if missing.
	 *
	 * @return true if settings exist and are OK, false otherwise.
	 */
	private boolean checkCurrentDriverVehicleTripSettings()  // TODO refactor common
	{
		currV = Settings.getCurrentVehicle(db, false);
		if (currV == null)
			return false;
		currA = VehSettings.getCurrentArea(db, currV, false);
		if (currA == null)
		{
			final String homearea = getResources().getString(R.string.home_area);
			currA = new GeoArea(homearea);
			currA.insert(db);
			VehSettings.setCurrentArea(db, currV, currA);
		}
		currD = VehSettings.getCurrentDriver(db, currV, false);
		currT = VehSettings.getCurrentTrip(db, currV, true);
		currTS = VehSettings.getCurrentTStop(db, currV, false);
		prevLocObj = VehSettings.getPreviousLocation(db, currV, false);

		return ((currA != null) && (currD != null) && (currT != null));
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
			if ((stopGas.gas_brandgrade_id != 0)
			    && currTS.isSingleFlagSet(TStop.TEMPFLAG_CREATED_GASBRANDGRADE))
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
		neverPaused = false;
		if (contTimeRunningHandler != null)
			contTimeRunningHandler.removeCallbacks(contTimeRunningRunnable);
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

	/**
	 * Choose another geoarea for the current stop. This could transform a local trip into a roadtrip.
	 * @since 0.9.43
	 */
	public void onClick_BtnAreaOther(View v)
	{
		Toast.makeText(this, "Not implemented yet (TODO)", Toast.LENGTH_SHORT).show();
	}

	/** Show or hide the Via dropdown if available */
	public void onClick_BtnViaDropdown(View v)
	{
		if (loc.getText().length() == 0)
		{
			Toast.makeText(this, R.string.please_enter_the_location, Toast.LENGTH_SHORT).show();
			return;
		}

		if ((via.getAdapter() == null) && (locObj == null) && (via.getText().length() == 0))
		{
			// Probably stopping at a new location. Just in case location name is
			// already in db, though, check that and if found update the vias.

			locObj = Location.getByDescr(db, loc.getText().toString().trim());
			if (locObj != null)
				updateViaRouteAutocomplete(null, false);
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

	/** Bring up the trip-odometer editor (calculator) dialog */
	public void onClick_BtnEditOdoTrip(View v)
	{
		onClickEditOdo(odo_trip, true);
	}

	/** Bring up the total-odometer editor (calculator) dialog */
	public void onClick_BtnEditOdoTotal(View v)
	{
		onClickEditOdo(odo_total, false);
	}

	/**
	 * Save changes while {@link #isCurrentlyStopped}:
	 * Read fields, and record this TStop in the database.
	 * Don't continue from the stop at this time.
	 * Finish this Activity.
	 */
	public void onClick_BtnSaveChanges(View v)
	{
		if (! isCurrentlyStopped)
			return;

		enterTStop(true);
	}

	/**
	 * Read fields, and record this TStop in the database.
	 * If {@link #isCurrentlyStopped continuing} from the stop, update {@link VehSettings#PREV_LOCATION}.
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
		enterTStop(false);
	}

	/**
	 * Internal call for {@link #onClick_BtnSaveChanges(View)}
	 * and {@link #onClick_BtnEnterTStop(View)}.
	 *<P>
	 * Check for required fields, prompt if missing. Otherwise
	 * save changes, continue from stop if {@link #isCurrentlyStopped}
	 * unless <tt>saveOnly</tt>, and finish this Activity.
	 * @param saveOnly  If true, save changes but don't leave
	 *   the stop or continue the trip.
	 * @since 0.9.20
	 */
	protected void enterTStop(final boolean saveOnly)
	{
		String locat = null, via_route = null, comment = null;
		boolean createdLoc = false, createdVia = false;
		boolean allOK = true;  // set to false if exception thrown, etc; if false, won't finish() the activity

		/**
		 * Before any db changes, check entered data for consistency.
		 */

		int stopTimeSec = 0;  // optional stop-time
		if ((tp_time_stop_chk != null) && tp_time_stop_chk.isChecked())
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
			Toast.makeText
				(this,
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
		if ((odoTotal == 0) && (! saveOnly) &&
			(stopEndsTrip || (bundleGas != null)))
		{
			odo_total.requestFocus();
			Toast.makeText
				(this,
				 R.string.please_check_the_total_odometer,
				 Toast.LENGTH_SHORT).show();
			return;  // <--- Early return: missing required field ---
		}

		final boolean mkFreqTrip;
		if (saveOnly || ! stopEndsTrip)
		{
			mkFreqTrip = false;
		} else {
			CheckBox et = (CheckBox) findViewById(R.id.trip_tstop_end_mk_freq);
			mkFreqTrip = (et != null) && et.isChecked();
		}

		if (mkFreqTrip && (odoTrip == 0))
		{
			odo_trip.requestFocus();
			Toast.makeText
				(this,
				 R.string.please_check_the_trip_odometer,
				 Toast.LENGTH_SHORT).show();
			return;  // <--- Early return: missing required field ---
		}

		// If about to continue the trip, and neither odo_trip nor odo_total
		// are entered, ask to confirm.
		if ((odoTrip == 0) && (odoTotal == 0)
			&& isCurrentlyStopped
			&& (! saveOnly) && ! askedConfirmEmptyOdos)
		{
			AlertDialog.Builder alert = new AlertDialog.Builder(this);

			alert.setTitle(R.string.confirm);
			alert.setMessage(R.string.trip_tstop_entry_no_odos_are_you_sure);
			alert.setPositiveButton(android.R.string.no, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton)
				{ }
			});
			alert.setNegativeButton(R.string.continu, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton)
				{
					onClick_BtnEnterTStop(null);
				}
			});
			askedConfirmEmptyOdos = true;
			alert.show();

			return;  // <--- Early return: asked about odometers ---
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
					toastText = getResources().getString
						(R.string.trip_tstop_entry_totalodo_low, wholeOdo0);  // %1$d
				}
			}
			else if ((odoTrip > 0) && (odoTrip < odos[1]))
			{
				focusStopHere = odo_trip;
				toastText = getResources().getString
					(R.string.trip_tstop_entry_tripodo_low, odos[1] / 10.0);  // %1$.1f
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
		if (isCurrentlyStopped && (! stopEndsTrip) && (! saveOnly)
			&& tp_time_cont_chk.isChecked() && (contTime != null))
		{
			// Convert to unix time:
			contTime.set(Calendar.HOUR_OF_DAY, tp_time_cont.getCurrentHour());
			contTime.set(Calendar.MINUTE, tp_time_cont.getCurrentMinute());
			contTimeSec = (int) (contTime.getTimeInMillis() / 1000L);

			// Validate continue-time:
			int stopSec = stopTimeSec;
			if (stopSec == 0)
				stopSec = currT.getTime_start();
			if ((stopSec != 0) && (contTimeSec < stopSec))
			{
				tp_time_cont.requestFocus();
				Toast.makeText
					(this,
					 R.string.this_time_must_be_no_earlier_than,
					 Toast.LENGTH_LONG).show();
				return;  // <--- inconsistent time ---
			}
		}

		// area ID @ end of roadtrip
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

		// Check for required trip category:
		if (stopEndsTrip && ! saveOnly)
		{
			final int tripCat = ((TripCategory) (spTripCat.getSelectedItem())).getID();
			if ((Settings.getBoolean(db, Settings.REQUIRE_TRIPCAT, false))
			    && (tripCat <= 0))
			{
				spTripCat.requestFocus();
				Toast.makeText(this, R.string.trip_tstart_categ_req, Toast.LENGTH_SHORT).show();
				return;  // <--- Early return: missing required ---
			}
		}

		/**
		 * Done checking data entered, time to update the db.
		 * tsid is the TStop ID we'll create or update here.
		 */
		final int tsid;

		int locID = 0;

		// Get or create the Location db record,
		// if we don't already have it
		if ((locObj == null)
			|| (! locObj.getLocation().equalsIgnoreCase(locat))
			|| ((areaLocs_areaID != locObj.getAreaID())
			    && ((locObjCreatedHere == null) || (locObj.getID() != locObjCreatedHere.getID()))))
		{
			final int locatIdx = loc.getListSelection();
			ListAdapter la = loc.getAdapter();
			if ((locatIdx != ListView.INVALID_POSITION)
			    && (locatIdx != ListAdapter.NO_SELECTION) && (la != null))
			{
				locObj = (Location) la.getItem(locatIdx);
				if (locObj != null)
					locID = locObj.getID();
			}

			if (locObj == null)
			{
				// search the table, avoid creating 2 locations with same name
				locObj = Location.getByDescr(db, locat);
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
			// record created at this tstop wasn't used, so remove it from db
			locObjCreatedHere.delete();
			locObjCreatedHere = null;
		}

		// Get or create the ViaRoute db record,
		// if we don't already have it
		final int viaID;

		if ((viaRouteObj == null)
		    && ! ((locID == 0) || (via_route == null) || (prevLocObj == null)))
			// via description may have been typed instead of picked from dropdown.
			// search the table: avoid creating 2 vias with same locations and desc.
			viaRouteObj = ViaRoute.getByLocsAndDescr(db, prevLocObj.getID(), locID, via_route);
			    // if none found, will still be null

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
			if ((isCurrentlyStopped || stopEndsTrip) && ! saveOnly)
			{
				if (odo_trip_chk.isChecked())
				{
					if ((0 == viaRouteObj.getOdoDist())
						|| ((viaRouteObjCreatedHere != null)
							&& (viaID == viaRouteObjCreatedHere.getID())))
					{
						final int prev_tripOdo = TStop.tripReadPrevTStopOdo
							(currT, prevLocObj, currTS);
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
			// via-route text doesn't match; create new ViaRoute or change viaRouteObjCreatedHere
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
			// record created at this tstop wasn't used, so remove it from db
			viaRouteObjCreatedHere.delete();
			viaRouteObjCreatedHere = null;
		}

		// If we've chosen a frequent tstop's location,
		// remove it from the list of unused ones.
		if ((wantsFTS != null) && (locID == wantsFTS.getLocationID())
			 && isCurrentlyStopped && (! stopEndsTrip) && (! saveOnly))
		{
			VehSettings.reduceCurrentFreqTripTStops(db, currV, wantsFTS);
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
					gbgCreatedHere = true;
					if (stopGas != null)
					{
						stopGas.gas_brandgrade_id = bgid;
						stopGas.gas_brandgrade = bg;
					}
				} else {
					createdGasBrandGrade = false;  // null or 0-length name
					if (gbgCreatedHere)
					{
						gbgCreatedHere = false;
						// TODO delete the gbg created, since we aren't using it
					}
				}
			}
			else if (gbgCreatedHere && (0 != bgid))
			{
				// brand was created here when we stopped, now we're saving
				// or continuing; see if the created name was changed.
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
			TStop newStop = new TStop
			  (currT, odoTotal, odoTrip, stopTimeSec, 0, locID, areaID, null, null, flags, viaID, comment);
			tsid = newStop.insert(db);
			currT.addCommittedTStop(newStop);  // add it to the Trip's list
			if (! stopEndsTrip)
				VehSettings.setCurrentTStop(db, currV, newStop);
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
			// Currently stopped; saving, resuming from stop, or ending trip.
			tsid = currTS.getID();
			currTS.setOdos(odoTotal, odoTrip);
			currTS.setTime_stop(stopTimeSec);
			// text fields, info fields
			currTS.setLocationID(locID);
			currTS.setVia_id(viaID);
			currTS.setComment(comment);
			if (currT.isRoadtrip() && (areaLocs_areaID != -1))
				currTS.setAreaID(areaLocs_areaID);

			if (! saveOnly)
			{
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
			} else {
				// Currently stopped, not continuing yet, so this stop may be edited again:

				// If any related records were newly created, make sure their flags are set in currTS.

				// If any related records were previously created for this stop, but the current changes
				// chose other preexisting ones instead of the ones created, clear those flags in currTS
				// so that future edits won't think the preexisting ones were created for this stop, and
				// then mistakenly delete them if their activity field is cleared.

				if ((locObjCreatedHere == null)
				    && currTS.isSingleFlagSet(TStop.TEMPFLAG_CREATED_LOCATION))
					currTS.clearFlagSingle(TStop.TEMPFLAG_CREATED_LOCATION);
				else if (createdLoc)
					currTS.setFlagSingle(TStop.TEMPFLAG_CREATED_LOCATION);

				if (((currTS.getVia_id() == 0) || (viaRouteObjCreatedHere == null))
				    && currTS.isSingleFlagSet(TStop.TEMPFLAG_CREATED_VIAROUTE))
					currTS.clearFlagSingle(TStop.TEMPFLAG_CREATED_VIAROUTE);
				else if (createdVia)
					currTS.setFlagSingle(TStop.TEMPFLAG_CREATED_VIAROUTE);

				if ((! (gbgCreatedHere && currTS.isSingleFlagSet(TStop.FLAG_GAS)))
				    && currTS.isSingleFlagSet(TStop.TEMPFLAG_CREATED_GASBRANDGRADE))
					currTS.clearFlagSingle(TStop.TEMPFLAG_CREATED_GASBRANDGRADE);
				else if (createdGasBrandGrade)
					currTS.setFlagSingle(TStop.TEMPFLAG_CREATED_GASBRANDGRADE);
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

		if ((stopGas != null) && (bundleGas != null) && ! saveOnly)
		{
			// For gas, update Location's latest_gas_brandgrade_id
			if (stopGas.gas_brandgrade_id != 0)
			{
				locObj.setLatestGasBrandGradeID(stopGas.gas_brandgrade_id);
				locObj.commit();  // does nothing if unchanged from location's previous bgid
			}
		}

		if (stopEndsTrip && ! saveOnly)
		{
			int pax = -1;
			final String paxTxt = textIfEntered(R.id.trip_tstop_end_pax);
			if (paxTxt != null)
			{
				try
				{
					pax = Integer.parseInt(paxTxt);
				} catch (NumberFormatException e) {
					// shouldn't occur: layout declaration has inputType=number
				}
			}

			try
			{
				VehSettings.endCurrentTrip
					(db, currV, tsid, odo_total.getCurrent10d(), stopTimeSec,
					 (TripCategory) spTripCat.getSelectedItem(), pax);
			} catch (Exception e) {
				// All validation is done above, so no exception is expected
				allOK = false;
				Misc.showExceptionAlertDialog(this, e);
			}
		}

		if (! saveOnly)
		{
			if (currTS != null)  // if we were stopped already, now continuing trip...
			{
				VehSettings.setCurrentTStop(db, currV, null);
				VehSettings.setPreviousLocation(db, currV, locObj); // update PREV_LOCATION
			}

			if (stopEndsTrip && mkFreqTrip && allOK)
			{
				// make new intent, set "_id" to currT.id, call it.
				Intent i = new Intent(TripTStopEntry.this, TripCreateFreq.class);
				i.putExtra("_id", currT.getID());
				startActivity(i);
			}
		}

		if (allOK)
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
			final int odosAreSetFrom_total = odo_total.getCurrent10d() + odoDiff;
			odo_trip.setCurrent10d(odoStop, false);
			odo_trip_chk.setChecked(true);
			odo_total.setCurrent10d(odosAreSetFrom_total, false);
			odosAreSetFromFreq = true;
		} else {
			odosAreSetFromFreq = false;
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
			// == trip_tstop_btn_stop_date
			currentDateToPick = stopTime;
		}

		return new DatePickerDialog
			(this, this,
			 currentDateToPick.get(Calendar.YEAR),
			 currentDateToPick.get(Calendar.MONTH),
			 currentDateToPick.get(Calendar.DAY_OF_MONTH));
	}

	/**
	 * Callback from {@link DatePickerDialog} for TStop stop-date or continue-date.
	 * Updates {@link #stopTime} or {@link #contTime}, calls {@link #updateDateButtons(int)}.
	 */
	public void onDateSet(DatePicker dp, final int year, final int month, final int monthday)
	{
		if (currentDateToPick == null)
			return;  // shouldn't happen

		currentDateToPick.set(Calendar.YEAR, year);
		currentDateToPick.set(Calendar.MONTH, month);
		currentDateToPick.set(Calendar.DAY_OF_MONTH, monthday);

		CheckBox dateCB;
		if (currentDateToPick == stopTime)
		{
			updateDateButtons(1);
			dateCB = tp_time_stop_chk;
		} else {
			updateDateButtons(2);
			dateCB = tp_time_cont_chk;
		}
		if (! dateCB.isChecked())
			dateCB.setChecked(true);
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
	 * Callback for {@link OnItemClickListener} for location autocomplete; read {@link #loc}, set {@link #locObj},
	 * and update the via-route list.
	 */
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
							if (isFromOnCreate
							    && currTS.isSingleFlagSet(TStop.TEMPFLAG_CREATED_VIAROUTE))
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
	 * Time callbacks for both TimePickers.
	 * Callback when {@link #tp_time_stop} is updated by the user, to set {@link #tp_time_stop_chk}.
	 * Callback when {@link #tp_time_cont} is updated by the
	 * user or by {@link #contTimeRunningRunnable}.
	 * Called twice for each time change: First for {@link TimePicker#setCurrentMinute(Integer)},
	 * then for {@link TimePicker#setCurrentHour(Integer)}.
	 * {@link #contTimeRunningHourMinute} must be updated before calling those set methods.
	 */
	public void onTimeChanged(TimePicker view, final int hour, final int minute)
	{
		if ((view == tp_time_stop) && tp_time_stop_init && ! tp_time_stop_chk.isChecked())
			tp_time_stop_chk.setChecked(true);

		if (view != tp_time_cont)
		{
			return;  // The rest of this method is for tp_time_cont only
		}
		if (contTimeRunningHourMinute == -1)
		{
			return;  // Not active
		}

		final int contTimeMinute = contTimeRunningHourMinute & 0xFF,
		          contTimeHour = contTimeRunningHourMinute >> 8;
		if ((minute == contTimeMinute)
			&& (hour == contTimeHour))
		{
			return;  // Same time as contTimeRunningHourMinute
		}

		// If contTimeRunningHourMinute's hour changes,
		// the first of the two onTimeChanged calls will appear to
		// be off by 1 hour:
		//   6,59 -> 6,00 (1hr less than contTimeRunningHourMinute) -> 7,00
		//   or 23,59 -> 23,00 (when contTimeRunningHourMinute==0) -> 0,00
		// So, check this before assuming it's been manually
		// changed by the user.
		if ((minute == 0) && (contTimeMinute == 0)
			&& ( ((hour + 1) == contTimeHour))
			     || ((hour == 23) && (contTimeHour == 0)))
		{
			return;  // Hour wrapped, assuming not user
		}

		// Changed by the user, so cancel the recurring contTime
		contTimeRunningHourMinute = -1;
		if ((contTimeRunningHandler != null) && (contTimeRunningRunnable != null))
			contTimeRunningHandler.removeCallbacks(contTimeRunningRunnable);
	}

	///////////////////////////////
	// Start of calculator methods
	///////////////////////////////

	/** Bring up an odometer's editor (calculator) dialog */
	private void onClickEditOdo(final OdometerNumberPicker odo, final boolean isOdoTrip)
	{
		// TODO managed dialog lifecycle: onCreateDialog etc
		// fields: bool for isTotal, bool for if any key pressed, int for prev value, op for + - * /
		// TODO activity int field for memory,
		//   TODO and load/save them with rest of fields
		final View calcItems = getLayoutInflater().inflate(R.layout.trip_tstop_popup_odo_calc, null);
		popupCalcItems = calcItems;
		calcOdoIsTrip = isOdoTrip;
		calcOperation = CALC_OP_NONE;
		calcPrevOperand = 0;
		calcNextPressClears = false;

		calcValue = (EditText) calcItems.findViewById(R.id.trip_tstop_popup_odo_calc_value);
		calc0 = calcItems.findViewById(R.id.trip_tstop_popup_odo_calc_0);
		calc1 = calcItems.findViewById(R.id.trip_tstop_popup_odo_calc_1);
		calc2 = calcItems.findViewById(R.id.trip_tstop_popup_odo_calc_2);
		calc3 = calcItems.findViewById(R.id.trip_tstop_popup_odo_calc_3);
		calc4 = calcItems.findViewById(R.id.trip_tstop_popup_odo_calc_4);
		calc5 = calcItems.findViewById(R.id.trip_tstop_popup_odo_calc_5);
		calc6 = calcItems.findViewById(R.id.trip_tstop_popup_odo_calc_6);
		calc7 = calcItems.findViewById(R.id.trip_tstop_popup_odo_calc_7);
		calc8 = calcItems.findViewById(R.id.trip_tstop_popup_odo_calc_8);
		calc9 = calcItems.findViewById(R.id.trip_tstop_popup_odo_calc_9);
		calcDeci = calcItems.findViewById(R.id.trip_tstop_popup_odo_calc_deci);
		calcDeci.setEnabled(isOdoTrip);
		calcLoadValueFromOdo();

		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		if (isOdoTrip)
			alert.setTitle(R.string.trip_tstop_entry_calc_trip_odo);  // Calculator: Trip odometer
		else
			alert.setTitle(R.string.trip_tstop_entry_calc_total_odo);  // Calculator: Total odometer
		alert.setView(calcItems);
		alert.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton)
			{
				popupCalcItems = null;
				float ov;
				try
				{
					ov = Float.parseFloat(calcValue.getText().toString());
				}
				catch (NumberFormatException e)
				{
					return;
				}
				odo.setCurrent10dAndRelated(Math.round(ov * 10));
			}
		});
		alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton)
			{
				popupCalcItems = null;
			}
		});
		alert.show();
	}

	/** Load the calculator's {@link #calcValue} field from the
	 *  odometer ({@link #calcOdoIsTrip} flag),
	 *  or clear to blank if the odo is 0.
	 */
	private void calcLoadValueFromOdo()
	{
		final OdometerNumberPicker odo = (calcOdoIsTrip) ? odo_trip : odo_total;
		final int ov = odo.getCurrent10d();
		if (ov == 0)
		{
			calcValue.setText("");
		} else {
			if (calcOdoIsTrip)
				calcValue.setText( Integer.toString(ov / 10) + "." + Integer.toString(ov % 10) );
			else
				calcValue.setText( Integer.toString(ov / 10) );
			calcValue.setSelection(calcValue.getText().length());  // move cursor to end
		}
		calcNextPressClears = false;
	}

	public void onClick_CalcBtnDigit(View v)
	{
		if (calcNextPressClears)
		{
			calcValue.setText("");
			calcNextPressClears = false;
		}

		// TODO max length, and/or max deci places?

		if (v == calcDeci)
		{
			if (! calcOdoIsTrip)
				return;

			// Make sure there's not already a decimal point
			final Editable tx = calcValue.getText();
			for (int i = tx.length()-1; i>=0; --i)
				if (tx.charAt(i) == '.')
					return;
			calcValue.append(".");
			return;
		}
		else if (v == calc0)
			calcValue.append("0");
		else if (v == calc1)
			calcValue.append("1");
		else if (v == calc2)
			calcValue.append("2");
		else if (v == calc3)
			calcValue.append("3");
		else if (v == calc4)
			calcValue.append("4");
		else if (v == calc5)
			calcValue.append("5");
		else if (v == calc6)
			calcValue.append("6");
		else if (v == calc7)
			calcValue.append("7");
		else if (v == calc8)
			calcValue.append("8");
		else if (v == calc9)
			calcValue.append("9");
	}

	/**
	 * The calculator Reset button loads {@link #calcValue} from the odometer's
	 * current value.
	 * @since 0.9.42
	 * @see #onClick_CalcBtnClear(View)
	 */
	public void onClick_CalcBtnReset(View v)
	{
		calcLoadValueFromOdo();
	}

	/**
	 * The calculator Clear button.
	 * @see #onClick_CalcBtnReset(View)
	 */
	public void onClick_CalcBtnClear(View v)
	{
		calcValue.setText("");
	}

	/** The calculator Backspace button. */
	public void onClick_CalcBtnBackspace(View v)
	{
		final Editable tx = calcValue.getText();
		final int L = tx.length();
		if (L > 0)
		{
			calcValue.setText(tx.subSequence(0, L - 1));
			calcValue.setSelection(L-1);  // move cursor to end
		}
	}

	/**
	 * Handle pressing an odometer calculator operation button:
	 * Store {@link #calcPrevOperand} and {@link #calcOperation}.
	 * @param calcOp {@link #CALC_OP_ADD}, {@link #CALC_OP_SUB}, etc
	 */
	private void calcOpBtnClicked(final int calcOp)
	{
		float cv;  // current value
		try
		{
			cv = Float.parseFloat(calcValue.getText().toString());
		}
		catch (NumberFormatException e)
		{
			// TODO error toast
			return;
		}

		calcPrevOperand = cv;
		calcOperation = calcOp;
		calcNextPressClears = true;
		// TODO visually indicate the op somewhere
	}

	public void onClick_CalcBtnDiv(View v)
	{
		calcOpBtnClicked(CALC_OP_DIV);
	}

	public void onClick_CalcBtnMul(View v)
	{
		calcOpBtnClicked(CALC_OP_MUL);
	}

	public void onClick_CalcBtnSub(View v)
	{
		calcOpBtnClicked(CALC_OP_SUB);
	}

	public void onClick_CalcBtnAdd(View v)
	{
		calcOpBtnClicked(CALC_OP_ADD);
	}

	public void onClick_CalcBtnEquals(View v)
	{
		float cv;  // current value
		try
		{
			cv = Float.parseFloat(calcValue.getText().toString());
		}
		catch (NumberFormatException e)
		{
			// TODO error toast
			return;
		}

		float nv;  // new value

		if (calcNextPressClears)
		{
			// handle repetitive '=' presses,
			//  by swapping cv & calcPrevOperand
			nv = cv;
			cv = calcPrevOperand;
			calcPrevOperand = nv;
		}

		switch (calcOperation)
		{
		case CALC_OP_ADD:
			nv = calcPrevOperand + cv;
			break;
		case CALC_OP_SUB:
			nv = calcPrevOperand - cv;
			break;
		case CALC_OP_MUL:
			nv = calcPrevOperand * cv;
			break;
		case CALC_OP_DIV:
			nv = calcPrevOperand / cv;
			break;
		default:  // CALC_OP_NONE
			calcPrevOperand = cv;
			return;
		}
		calcPrevOperand = cv;
		calcValue.setText(Float.toString(nv));
		calcNextPressClears = true;
	}

	/** Handle the calculator M+, M- buttons */
	private void calcMemButtonClicked(final boolean addNotSub)
	{
		float cv;  // current value
		try
		{
			cv = Float.parseFloat(calcValue.getText().toString());
		}
		catch (NumberFormatException e)
		{
			// TODO error toast
			return;
		}
		// TODO is there a visual indicator?
		if (addNotSub)
			calcMemory += cv;
		else
			calcMemory -= cv;
	}

	public void onClick_CalcBtnMemPlus(View v)
	{
		calcMemButtonClicked(true);
	}

	public void onClick_CalcBtnMemMinus(View v)
	{
		calcMemButtonClicked(false);
	}

	public void onClick_CalcBtnMemClear(View v)
	{
		calcMemory = 0.0f;
		// TODO is there a visual indicator?
	}

	public void onClick_CalcBtnMemRecall(View v)
	{
		if (calcMemory != 0.0f)
			calcValue.setText(Float.toString(calcMemory));
	}

	/////////////////////////////
	// End of calculator methods
	/////////////////////////////

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
		outState.putInt("TCR", contTimeRunningHourMinute);
		outState.putBoolean("TCRT", contTimeRunningAlreadyToasted);
		outState.putInt("AID", areaLocs_areaID);
		outState.putInt("LOCID", (locObj != null) ? locObj.getID() : 0);
		outState.putInt("VIAID", (viaRouteObj != null) ? viaRouteObj.getID() : 0);
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
		tp_time_stop_init = true;
		hhmm = inState.getInt("TCV");
		tp_time_cont.setCurrentHour(hhmm >> 8);
		tp_time_cont.setCurrentMinute(hhmm & 0xFF);
		contTimeRunningHourMinute = inState.getInt("TCR");
		contTimeRunningAlreadyToasted = inState.getBoolean("TCRT");
		areaLocs_areaID = inState.getInt("AID", -1);

		int id = inState.getInt("LOCID");
		if (id > 0)
			try {
				locObj = new Location(db, id);
			} catch (IllegalStateException e) {
			} catch (RDBKeyNotFoundException e) {
			}

		id = inState.getInt("VIAID");
		if (id > 0)
			try {
				viaRouteObj = new ViaRoute(db, id);
			} catch (IllegalStateException e) {
			} catch (RDBKeyNotFoundException e) {
			}

		if (contTimeRunningHourMinute != -1)
			initContTimeRunning(null);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		if (neverPaused)
			return;  // Don't init twice, we just now finished onCreate.

		if (contTimeRunningHourMinute != -1)
			initContTimeRunning(null);
	}

	/**
	 * For ViaRoute autocomplete ({@link TripTStopEntry#via}), the callbacks for {@link OnItemClickListener}
	 * and {@link TextWatcher}; sets or clears {@link TripTStopEntry#viaRouteObj},
	 * possibly updates trip_odo, total_odo.
	 */
	private class ViaRouteListenerWatcher implements OnItemClickListener, TextWatcher
	{
		/**
		 * For ViaRoute autocomplete, the callback for {@link OnItemClickListener};
		 * sets {@link TripTStopEntry#viaRouteObj}.
		 */
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
					odo_trip.setCurrent10d(odoTripOrig + odo_dist, false);

				if (! odo_total_chk.isChecked())
					odo_total.setCurrent10d(odoTotalOrig + odo_dist, false);
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
