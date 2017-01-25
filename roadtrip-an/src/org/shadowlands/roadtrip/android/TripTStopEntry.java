/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2017 Jeremy D Monin <jdmonin@nand.net>
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
import org.shadowlands.roadtrip.util.android.RTRAndroidDateTimeFormatter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
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
import android.widget.RadioButton;
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
 * Odometer fields include a calculator dialog for convenience (Edit button);
 * see {@link #onClickEditOdo(OdometerNumberPicker, boolean)}.
 *<P>
 * This activity is finished by pressing one of the two large buttons
 * {@link #onClick_BtnEnterTStop(View)} or {@link #onClick_BtnSaveChanges(View)},
 * which validate the displayed fields and update the database.
 * If the trip is local, but a {@link Location} in a different {@link GeoArea} was
 * chosen or created, the local trip is converted here to a roadtrip.
 *<P>
 * If stopping here creates a new {@link Location} or {@link ViaRoute}, and the
 * text is changed when resuming the trip, make sure the new item's text is updated
 * in the database.
 *<P>
 * <B>View Previous TStop mode</B> in v0.9.60 and higher:<BR>
 * Can instead be called in View Previous TStop mode to show any stop of any previous trip,
 * using {@link #EXTRAS_FIELD_VIEW_TSTOP_ID}. This view is mostly read-only except for
 * the Comment field; see that extra field's javadoc. If the comment is changed,
 * calls {@link #setResult(int) setResult}({@link Activity#RESULT_FIRST_USER RESULT_FIRST_USER},
 * {@link Activity#getIntent() getIntent()}) to return that news and the TStop ID
 * to the caller.
 *
 * @author jdmonin
 */
public class TripTStopEntry extends Activity
	implements OnDateSetListener, OnItemClickListener, TextWatcher, OnTimeChangedListener
{
	/**
	 * Flag for ending the entire trip (not just stopping), for {@link Intent#putExtra(String, boolean)}.
	 * Ignored if {@link #EXTRAS_FIELD_VIEW_TSTOP_ID} is also used.
	 */
	public static final String EXTRAS_FLAG_ENDTRIP = "endtrip";

	/**
	 * Extras field to view any previous {@link TStop} instead of the current trip and TStop
	 * (View Previous TStop Mode). Use the TStop's ID and {@link Intent#putExtra(String, int)}.
	 * Read-only except for Comment field. See class javadoc for details.
	 *<P>
	 * If this TStop ID is the current TStop of the current trip in Settings/VehSettings,
	 * the view will be completely read-only.
	 * @since 0.9.60
	 */
	public static final String EXTRAS_FIELD_VIEW_TSTOP_ID = "view_tstop_id";

	/**
	 * Historical Mode threshold is 24 hours, in milliseconds.
	 * Used by {@link TripTStopEntry} and {@link TripBegin}.
	 */
	static final long TIMEDIFF_HISTORICAL_MILLIS = 24 * 60 * 60 * 1000L;

	/**
	 * Placeholder (-2) for GeoArea ID when the geoarea name is newly entered text
	 * in {@link #etRoadtripAreaOther} and its record not yet created.
	 * Also used when calling {@link #selectRoadtripAreaButton(int, String, boolean, int)}
	 * when the "Other" radio button should be checked, regardless of the Area ID selected in "Other".
	 *<P>
	 * This value is not -1 because {@link GeoArea#getAll(RDBAdapter, int)} and other places use -1.
	 * @see #areaOther
	 * @see #areaLocs_areaID
	 * @since 0.9.50
	 */
	private static final int GEOAREAID_OTHER_NEW = -2;

	/** Determines if {@link #onCreate(Bundle)} should call {@link #onRestoreInstanceState(Bundle)} */
	private static final String TSTOP_BUNDLE_SAVED_MARKER = "tripTStopEntrySavedState";

	/** tag for Log debugs */
	@SuppressWarnings("unused")
	private static final String TAG = "Roadtrip.TripTStopEntry";

	private RDBAdapter db = null;

	/**
	 * Current or most recent GeoArea, from {@link #checkCurrentDriverVehicleTripSettings()}.
	 * For more details see {@link VehSettings#CURRENT_AREA}.
	 * Always a valid GeoArea in the db, never {@link GeoArea#GEOAREA_NONE}.
	 */
	private GeoArea currA;

	/** This trip's Vehicle, from {@link #checkCurrentDriverVehicleTripSettings()}. */
	private Vehicle currV;

	/** This trip's driver, from {@link #checkCurrentDriverVehicleTripSettings()}. */
	private Person currD;

	/**
	 * Current trip, from {@link #checkCurrentDriverVehicleTripSettings()},
	 * or {@link #viewTS}'s trip if not null.
	 */
	private Trip currT;

	/**
	 * Current TStop (from {@link #checkCurrentDriverVehicleTripSettings()}),
	 * or {@link #viewTS} if not null.
	 * @see #isViewTScurrTS
	 */
	private TStop currTS;

	/**
	 * For View Previous TStop mode; this field is null unless that
	 * mode was activated using {@link #EXTRAS_FIELD_VIEW_TSTOP_ID}.
	 * @see #isViewTScurrTS
	 * @since 0.9.60
	 */
	private TStop viewTS;

	/**
	 * In View Previous TStop mode, is the specified {@link #viewTS}
	 * the current stop of some vehicle's current trip? If so, must
	 * behave strictly read-only to prevent any possible inconsistencies
	 * based on assumptions that {@code viewTS} must be a previous stop.
	 * Set in {@link #checkCurrentDriverVehicleTripSettings()}.
	 * @since 0.9.60
	 */
	private boolean isViewTScurrTS;

	/**
	 * All locations within the {@link GeoArea} having ID {@link #areaLocs_areaID}, or null;
	 * used in {@link #loc} adapter to set {@link #locObj};
	 * filled in {@link #onCreate(Bundle)} when {@link #areaLocs_areaID} is calculated,
	 * updated when the selected area changes in {@link #selectRoadtripAreaButton(int, String, boolean, int)}.
	 */
	private Location[] areaLocs;

	/**
	 * The {@link GeoArea} ID of locations in {@link #areaLocs}, or -1 if that could not be determined.
	 * For roadtrips, also the currently selected area ID of {@link #rbRoadtripArea_chosen}.
	 * Geoarea id 0 is OK for tstops/locations within a roadtrip, but not
	 * for the start or end tstop/location.
	 *<P>
	 * In local trips, each stop's {@link TStop#getAreaID()} will be 0.
	 * {@code areaLocs_areaID} is not 0, is {@link Trip#getAreaID()} during local trips.
	 * During a roadtrip, is taken from {@link #currTS}.{@link TStop#getAreaID() getAreaID()}
	 * if currently stopped, otherwise from {@link #prevLocObj}.{@link Location#getAreaID() getAreaID()}
	 * if available, or {@link #currA}. See {@link #onCreate(Bundle)} code for details.
	 *<P>
	 * During a roadtrip, may become {@link #GEOAREAID_OTHER_NEW} in
	 * {@link #selectRoadtripAreaButton(int, String, boolean, int)}.
	 * Code using this field must handle -1 or {@link #GEOAREAID_OTHER_NEW} gracefully.
	 *<P>
	 * The exception to the rule "current selected area ID of {@link #rbRoadtripArea_chosen}" is when
	 * text is currently being typed into {@link #etRoadtripAreaOther}: Its radio button will be chosen
	 * for responsiveness, but {@link #areaLocs_areaID}, {@link #loc}, and related fields aren't updated
	 * from the previous area until {@code etRoadtripAreaOther} loses focus or {@link #enterTStop(boolean)}
	 * is called from a button press.
	 */
	private int areaLocs_areaID;

	/**
	 * true if {@link #EXTRAS_FLAG_ENDTRIP} was set when creating the activity.
	 * @see #isCurrentlyStopped
	 */
	private boolean stopEndsTrip;

	/**
	 * When this activity was created, were we already at a TStop?
	 * True if <tt>{@link #currTS} != null</tt> or <tt>{@link #viewTS} != null</tt>.
	 * @see #stopEndsTrip
	 */
	private boolean isCurrentlyStopped;

	/** If true, not paused yet; used by onResume to prevent initializing things twice right after onCreate. */
	private boolean neverPaused;

	/**
	 * If the {@link #tp_time_cont Continue Time} is the current time, and
	 * we're updating it as the time changes, the hour and minute of
	 * the last update.
	 *<BR>
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
	 *  During a roadtrip, its geoarea is selected and hilighted as {@link #rbRoadtripArea_chosen}.
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
	 * but causes no other problems: Make sure code checks != {@code null} before using {@code prevLocObj}.
	 *<P>
	 * Is {@code null} when <tt>{@link #viewTS viewTS} != null</tt>.
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

	/**
	 * For hh:mm output in View Previous TStop mode, an android-specific DateTimeFormatter
	 * for locale and user prefs, or {@code null}.
	 * @since 0.9.60
	 */
	private RTRAndroidDateTimeFormatter dtf;

	private Button btnStopTimeDate, btnContTimeDate;

	/**
	 * When stopping during a roadtrip, buttons to pick the geoarea for
	 * the Location textfield {@link #loc} and {@link #areaLocs_areaID}.
	 * Null and hidden unless {@link #currT}.{@link Trip#isRoadtrip() isRoadtrip()} && ! {@link #stopEndsTrip};
	 * if these are null, so is {@link #etRoadtripAreaOther}.
	 * @see #rbRoadtripArea_chosen
	 */
	private RadioButton rbRoadtripAreaStart, rbRoadtripAreaNone,
		rbRoadtripAreaEnd, rbRoadtripAreaOther;

	/**
	 * For roadtrip, the currently selected geoarea radiobutton.
	 * Null, or one of {@link #rbRoadtripAreaStart},
	 * {@link #rbRoadtripAreaNone} or {@link #rbRoadtripAreaEnd}.
	 * Starts as null, updated by {@link #selectRoadtripAreaButton(int, String, boolean, int)}.
	 * @see #areaLocs_areaID
	 * @see #onClick_BtnAreaStart(View)
	 * @see #onClick_BtnAreaNone(View)
	 * @see #onClick_BtnAreaEnd(View)
	 * @see #onClick_BtnAreaOther(View)
	 */
	private RadioButton rbRoadtripArea_chosen;

	/**
	 * For roadtrips, the Other Geoarea textfield.
	 * Its radiobutton is {@link #rbRoadtripAreaOther}.
	 * The selected GeoArea from the adapter, if any, is {@link #areaOther}.
	 * Autocomplete selections call {@link #etRoadtripAreaOtherListener}.
	 *<P>
	 * Like {@link #rbRoadtripAreaOther}, this field is null if {@link #stopEndsTrip} or not roadtrip.
	 * @since 0.9.50
	 */
	private AutoCompleteTextView etRoadtripAreaOther;

	/** For roadtrips, the listener for autocomplete selections in {@link #etRoadtripAreaOther}. */
	private GeoAreaListenerWatcher etRoadtripAreaOtherListener;

	/**
	 * Roadtrip geoarea selected in {@link #etRoadtripAreaOther},
	 * {@link GeoArea#GEOAREA_NONE} if none, or null for a newly entered name.
	 * Updated in {@link TripTStopEntry.GeoAreaListenerWatcher}.
	 * @since 0.9.50
	 * @see #GEOAREAID_OTHER_NEW
	 * @see #areaOther_prev
	 * @see #areaOtherCreatedHere
	 */
	private GeoArea areaOther;

	/**
	 * Previous {@link #areaOther} when a new one is selected.
	 * used by {@link #showRoadtripAreaButtonConfirmDialog(int, String, String, String)}
	 * in case Cancel button is pressed.
	 * @since 0.9.50
	 */
	private GeoArea areaOther_prev;

	/**
	 * if non-null, then {@link #currTS} {@code != null}, and
	 * {@link #areaOther} was created for this TStop.
	 * @see TStop#TEMPFLAG_CREATED_GEOAREA
	 * @since 0.9.50
	 */
	private GeoArea areaOtherCreatedHere;

	///////////////////////////////
	// Start of calculator fields
	///////////////////////////////

	// Operations for calcOperation.
	// If any are added, also update calcUpdateStatusView().
	private static final int CALC_OP_NONE = 0, CALC_OP_ADD = 1,
	  CALC_OP_SUB = 2, CALC_OP_MUL = 3, CALC_OP_DIV = 4;

	/**
	 * Non-null, for callbacks, when the odometer editor calculator is showing.
	 * @see #onClickEditOdo(OdometerNumberPicker, boolean)
	 */
	private AlertDialog popupCalcDia = null;

	/** If displayed, is the calculator for {@link #odo_trip} and not {@link #odo_total}? */
	private boolean calcOdoIsTrip;

	/** Calculator's current value, for {@link #onClickEditOdo(OdometerNumberPicker, boolean)} callbacks */
	private EditText calcValue;

	/**
	 * Calculator's status display: {@link #calcOperation} and {@link #calcMemory} indicator ("M" or nothing).
	 * Updated in {@link #calcUpdateStatusView()}.
	 * @since 0.9.50
	 */
	private TextView calcStatusView;

	/**
	 * Calculator's memory register (M+ M- {@link #calcMR MR} {@link #calcMC MC} buttons).
	 * @see #calcStatusView
	 */
	private float calcMemory = 0.0f;

	/** If true, the next button press clears {@link #calcValue} */
	private boolean calcNextPressClears;

	/** Calculator's previous value */
	private float calcPrevOperand;

	/**
	 * Calculator's operation: {@link #CALC_OP_ADD}, {@link #CALC_OP_SUB},
	 * {@link #CALC_OP_MUL}, {@link #CALC_OP_DIV} or {@link #CALC_OP_NONE}.
	 * @see #calcStatusView
	 */
	private int calcOperation;

	/** Digit buttons 0-9 and '.' for {@link #onClickEditOdo(OdometerNumberPicker, boolean)} callbacks */
	private View calc0, calc1, calc2, calc3, calc4, calc5,
		calc6, calc7, calc8, calc9, calcDeci;

	/**
	 * Calculator Memory Recall and Memory Clear buttons.  These are
	 * enabled or not based on whether {@link #calcMemory} is occupied,
	 * which also acts as a visual indicator for {@code calcMemory}.
	 * @since 0.9.50
	 */
	private View calcMR, calcMC;

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
		// will add tp_time_stop,tp_time_cont TimeChangedListener after setting them here.

		btnStopTimeDate = (Button) findViewById(R.id.trip_tstop_btn_stop_date);
		btnContTimeDate = (Button) findViewById(R.id.trip_tstop_btn_cont_date);
		btnGas = (Button) findViewById(R.id.trip_tstop_btn_gas);

		Intent i = getIntent();

		// check viewTS first: determines where to get other settings
		if ((i != null) && i.hasExtra(EXTRAS_FIELD_VIEW_TSTOP_ID))
		{
			final int tsid = i.getIntExtra(EXTRAS_FIELD_VIEW_TSTOP_ID, 0);
			if (tsid > 0)
				try
				{
					viewTS = new TStop(db, tsid);
				}
				catch(Exception e) {}
		}

		// get currA, currV, currT, maybe currTS and prevLocObj;
		// If viewTS != null, those will be taken from viewTS's fields
		// instead of current settings.
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

		isCurrentlyStopped = (currTS != null);  // true when viewTS != null

		// now check other intent extras
		if (i != null)
		{
			stopEndsTrip = i.getBooleanExtra(EXTRAS_FLAG_ENDTRIP, false) && (viewTS == null);
			if (stopEndsTrip)
			{
				Button eb = (Button) findViewById(R.id.trip_tstop_btn_enter);
				if (eb != null)
					eb.setText(R.string.end_trip);
				setTitle(R.string.end_trip);

				TextView tv = (TextView) findViewById(R.id.trip_tstop_loc_label);
				if (tv != null)
				{
					if (currT.isRoadtrip())
						tv.setText(R.string.destination_within_the_area);
					else
						tv.setText(R.string.destination);
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

		// if currTS != null, we'll read stopGas in updateTextAndButtons,
		// and set btnGas's green light.
		stopGas = null;

		// Hide or disable Save button in various modes.
		{
			Button b = (Button) findViewById(R.id.trip_tstop_btn_save);
			if (b != null)
			{
				if (! (isCurrentlyStopped || stopEndsTrip))
					b.setVisibility(View.GONE);
				else if (isViewTScurrTS)
					b.setEnabled(false);
			}
		}
		// In View Previous TStop mode, Enter will be renamed below to Close
		// and most other fields updated or disabled.

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
				{
					if (viewTS == null)
						eb.setText(R.string.continu);
					else
						eb.setText(R.string.close);
				}
			}
		}

		// Add TimeChangedListeners soon, after UI thread is done refreshing hour/minute values
		if (viewTS == null)
			tp_time_stop.postDelayed(new Runnable()
			{
				@Override
				public void run()
				{
					tp_time_stop.setOnTimeChangedListener(TripTStopEntry.this);
						// onTimeChanged - to set checkbox if user sets time
					tp_time_cont.setOnTimeChangedListener(TripTStopEntry.this);
						// onTimeChanged - to cancel auto-update of time if user sets time
				}
			}, 750);

		// Change title if needed; default title label is stop_during_a_trip
		final Resources res = getResources();
		if (stopEndsTrip)
		{
			setTitle(res.getString(R.string.end_trip));
		} else if (viewTS != null) {
			setTitle(res.getString(R.string.trip_tstop_entry__title_view_prev));
			findViewById(R.id.trip_tstop_prompt).setVisibility(View.GONE);
		} else if (isCurrentlyStopped) {
			setTitle(res.getString(R.string.continu_from_stop));
		}

		// Set up autocompletes

		loc = (AutoCompleteTextView) findViewById(R.id.trip_tstop_loc);
		if (viewTS == null)
			loc.addTextChangedListener(this);
		areaLocs_areaID = -1;
		areaLocs = null;
		// A bit further along, we'll determine areaLocs_areaID and
		// then fill areaLocs and loc.

		via = (AutoCompleteTextView) findViewById(R.id.trip_tstop_via);
		if (Settings.getBoolean(db, Settings.HIDE_VIA, false))
		{
			via.setVisibility(View.GONE);
			View vlab = findViewById(R.id.trip_tstop_via_label);
			vlab.setVisibility(View.GONE);
		} else {
			if (viewTS == null) {
				viaListener = new ViaRouteListenerWatcher();
				via.addTextChangedListener(viaListener);
			}
		}

		if (viewTS != null)
		{
			View v = findViewById(R.id.trip_tstop_via_dropdown);
			if (v != null)
				v.setEnabled(false);

			// Won't be able to set via_text in updateViaRouteAutocomplete() because
			// prevLocObj == null. Workaround is handled later in this method; search for
			// setEditText(currTS_via_text, R.id.trip_tstop_via).
		}

		// etRoadtripAreaOther is initialized below only if currT.isRoadtrip(), otherwise remains null.

		// adjust date/time fields, now that we know if we have currTS
		// and know if stopEndsTrip.
		final long timeNow = System.currentTimeMillis();
		boolean contTimeIsNow = false;

		if (viewTS == null)
		{
			findViewById(R.id.trip_tstop_time_stop_value_row).setVisibility(View.GONE);
			findViewById(R.id.trip_tstop_time_cont_value_row).setVisibility(View.GONE);
		} else {
			// if needed expand height of 2-line Stopped At Time and Continue At Time labels,
			// based on another 2-line with known-good height
			final TextView vStopLbl = (TextView) findViewById(R.id.trip_tstop_time_stop_value_lbl),
			               vContLbl = (TextView) findViewById(R.id.trip_tstop_time_cont_value_lbl);
			vStopLbl.post(new Runnable()
			{
				public void run() {
					final int chkHeight = odo_trip_chk.getHeight(),
					          stopHeight = vStopLbl.getHeight(),
					          contHeight = vContLbl.getHeight();
					if (stopHeight < chkHeight)
						vStopLbl.setHeight(chkHeight);
					if (contHeight < chkHeight)
						vContLbl.setHeight(chkHeight);
				}
			});
		}

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
			if (viewTS == null)
			{
				contTime.setTimeInMillis(timeNow);
				tp_time_cont_chk.setChecked(true);
				contTimeIsNow = true;
			} else {
				contTimeIsNow = false;
				final int conttime_sec = viewTS.getTime_continue();

				tp_time_cont.setEnabled(false);
				tp_time_cont_chk.setEnabled(false);
				btnContTimeDate.setEnabled(false);

				StringBuilder sb = new StringBuilder();
				if (conttime_sec != 0)
				{
					final long conttime_ms = conttime_sec * 1000L;
					contTime.setTimeInMillis(conttime_ms);

					if (fmt_dow_shortdate == null)
						fmt_dow_shortdate = Misc.buildDateFormatDOWShort(this, true);
					if (dtf == null)
						dtf = new RTRAndroidDateTimeFormatter(getApplicationContext());

					sb.append(DateFormat.format(fmt_dow_shortdate, contTime));
					sb.append(' ');
					sb.append(dtf.formatTime(conttime_ms));
				}
				else if (currT.isEnded())
				{
					// no continue time, and trip has ended: did it end at this TStop?
					final TStop endingTS = currT.readLatestTStop();
					if ((endingTS != null) && (endingTS.getID() == viewTS.getID()))
						sb.append(res.getString(R.string.trip_tstop_entry_trip_ends_here));
				}
				replaceViewWithText
					(findViewById(R.id.trip_tstop_time_cont_row),
					 R.id.trip_tstop_time_cont_value_txt, sb, false);
			}

			// Note that stop-time code may adjust contTime if Historical Mode.
		}

		// Stop Time:
		stopTime = Calendar.getInstance();
		boolean setTimeStopCheckbox = false;
		if (isCurrentlyStopped)
		{
			int stoptime_sec = currTS.getTime_stop();
			if (viewTS == null)
			{
				if (stoptime_sec != 0)
				{
					setTimeStopCheckbox = true;
					stopTime.setTimeInMillis(1000L * stoptime_sec);

					if ((contTime != null) && (viewTS == null)
						&& (Math.abs(timeNow - (1000L * stoptime_sec)) >= TIMEDIFF_HISTORICAL_MILLIS))
					{
						// Historical Mode: continue from that date & time, not from today
						contTime.setTimeInMillis(1000L * (stoptime_sec + 60));  // 1 minute later
						contTimeIsNow = false;
					}

				} else {
					// Stop time is unknown:
					// If the trip's most recent time is older than the historical threshold,
					// fill in that time as a "guessed" stop & continue time.

					long latestVehTime = 1000L * currV.readLatestTime(currT);
					if ((latestVehTime != 0L)
					    && (Math.abs(latestVehTime - timeNow) >= TIMEDIFF_HISTORICAL_MILLIS))
					{
						Toast.makeText
							(this,
							 R.string.using_old_date_due_to_previous,
							 Toast.LENGTH_SHORT).show();

						if (contTime != null)
						{
							// Historical Mode: continue from that date & time, not from today
							contTime.setTimeInMillis(latestVehTime + 60000L);  // 1 minute later
							contTimeIsNow = false;
						}
					} else {
						latestVehTime = timeNow;
					}

					setTimeStopCheckbox = false;
					stopTime.setTimeInMillis(latestVehTime);  // might be timeNow
				}

				if (! contTimeIsNow)
					tp_time_cont_chk.setChecked(false);

				// Focus on continue-time, to scroll the screen down
				tp_time_cont.requestFocus();
			} else {
				// viewTS != null:
				// disable/hide other elements, show stopTime if any

				tp_time_stop.setEnabled(false);
				tp_time_stop_chk.setEnabled(false);
				btnStopTimeDate.setEnabled(false);

				StringBuilder sb = new StringBuilder();
				if (stoptime_sec != 0)
				{
					final long stoptime_ms = stoptime_sec * 1000L;
					stopTime.setTimeInMillis(stoptime_ms);

					if (fmt_dow_shortdate == null)
						fmt_dow_shortdate = Misc.buildDateFormatDOWShort(this, true);
					if (dtf == null)
						dtf = new RTRAndroidDateTimeFormatter(getApplicationContext());

					sb.append(DateFormat.format(fmt_dow_shortdate, stopTime));
					sb.append(' ');
					sb.append(dtf.formatTime(stoptime_ms));
				}
				replaceViewWithText
					(findViewById(R.id.trip_tstop_time_stop_row),
					 R.id.trip_tstop_time_stop_value_txt, sb, false);
			}
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

		if (viewTS == null)
		{
			tp_time_stop_chk.setChecked(setTimeStopCheckbox);
			updateDateButtons(0);
			initTimePicker(stopTime, tp_time_stop);
			tp_time_stop_init = true;

			if (contTime != null)
			{
				initTimePicker(contTime, tp_time_cont);
				if (contTimeIsNow && (viewTS == null))
					initContTimeRunning(contTime);  // keep tp_time_cont current
			}
		}
		rbRoadtripArea_chosen = null;  // Will soon be set by calling selectRoadtripAreaButton

		// Give status, read odometer, etc;
		// if currTS != null, fill fields from it.
		updateTextAndButtons();
		if ((savedInstanceState != null) && savedInstanceState.containsKey(TSTOP_BUNDLE_SAVED_MARKER))
			onRestoreInstanceState(savedInstanceState);

		// If needed, determine current area.
		// (If this logic changes, update areaLocs_areaID javadoc)
		if (areaLocs_areaID == -1)
		{
			if (currTS != null)
			{
				if (currT.isRoadtrip())
				{
					areaLocs_areaID = currTS.getAreaID();
				} else {
					// get geoarea from trip: local currTS will have 0 (unused field)
					areaLocs_areaID = currT.getAreaID();
				}
			}
			else if ((prevLocObj != null) && currT.isRoadtrip())
			{
				final int aID = prevLocObj.getAreaID();
				if ((aID > 0) || ! stopEndsTrip)
					areaLocs_areaID = aID;
				// else, see fallback just below
			}
		}

		// fallback for current area if the usual sources aren't populated
		// (a new vehicle with no previous trips, or we're not stopped and stopEndsTrip, etc)
		if (areaLocs_areaID == -1)
		{
			areaLocs_areaID = currA.getID();  // likely == trip's starting area
			if (stopEndsTrip && (areaLocs_areaID == 0))  // can't end trip in geoarea 0
			{
				areaLocs_areaID = currT.getRoadtripEndAreaID();  // will be 0 for local trips
				if (areaLocs_areaID == 0)
					areaLocs_areaID = currT.getAreaID();
			}
		}

		// Based on current area, set up Location auto-complete
		if (viewTS == null)
			areaLocs = Location.getAll(db, areaLocs_areaID);
		if (areaLocs != null)
		{
			ArrayAdapter<Location> adapter = new ArrayAdapter<Location>(this, R.layout.list_item, areaLocs);
			loc.setAdapter(adapter);
			loc.setOnItemClickListener(this);
		}

		// See if we're stopping on a frequent trip:
		// Also assumes viewTS == null because ! isCurrentlyStopped.
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
							 R.id.main_btn_begin_freqtrip);

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

			rbRoadtripAreaStart = (RadioButton) findViewById(R.id.trip_tstop_btn_area_start);
			rbRoadtripAreaNone = (RadioButton) findViewById(R.id.trip_tstop_btn_area_none);
			rbRoadtripAreaEnd = (RadioButton) findViewById(R.id.trip_tstop_btn_area_end);
			rbRoadtripAreaOther = (RadioButton) findViewById(R.id.trip_tstop_btn_area_other);
			rbRoadtripAreaStart.setText(ga_s.getName());
			rbRoadtripAreaEnd.setText(ga_e.getName());
			selectRoadtripAreaButton(areaLocs_areaID, null, false, 0);
			if (viewTS != null)
			{
				rbRoadtripAreaStart.setEnabled(false);
				rbRoadtripAreaNone.setEnabled(false);
				rbRoadtripAreaEnd.setEnabled(false);
				rbRoadtripAreaOther.setEnabled(false);
				v = findViewById(R.id.trip_tstop_areas_et_other_dropdown);
				if (v != null)
					v.setVisibility(View.GONE);
			}

			etRoadtripAreaOther = (AutoCompleteTextView) findViewById(R.id.trip_tstop_areas_et_other);
			areaOther = null;
			areaOther_prev = null;

			// If areaLocs_areaID isn't start, end, or none, set areaOther and show its name
			final boolean tsAreaIsOther = (areaLocs_areaID != 0)
				&& (areaLocs_areaID != gaID_s) && (areaLocs_areaID != gaID_e);

			if (tsAreaIsOther || (viewTS == null))
			{
				if (etRoadtripAreaOtherListener == null)
					etRoadtripAreaOtherListener = new GeoAreaListenerWatcher();

				GeoArea[] othera = GeoArea.getAll(db, -1);
				if (othera != null)
				{
					if (viewTS == null)
					{
						ArrayAdapter<GeoArea> adapter = new ArrayAdapter<GeoArea>
							(this, R.layout.list_item, othera);

						etRoadtripAreaOther.setAdapter(adapter);
					}

					if (tsAreaIsOther)
					{
						// find and set other area name text from areaID
						for (int j = 0; j < othera.length; ++j)
						{
							if (othera[j].getID() == areaLocs_areaID)
							{
								areaOther = othera[j];
								etRoadtripAreaOther.setText(othera[j].getName());
								break;
							}
						}

						if ((currTS != null) && (viewTS == null)
						    && currTS.isSingleFlagSet(TStop.TEMPFLAG_CREATED_GEOAREA))
							areaOtherCreatedHere = areaOther;
					}

					if (viewTS == null)
						etRoadtripAreaOther.setOnItemClickListener(etRoadtripAreaOtherListener);
				} else {
					etRoadtripAreaOther.setAdapter((ArrayAdapter<GeoArea>) null);
				}
			}

			if (viewTS == null)
			{
				etRoadtripAreaOther.addTextChangedListener(etRoadtripAreaOtherListener);
				etRoadtripAreaOther.setOnFocusChangeListener(etRoadtripAreaOtherListener);
			} else {
				etRoadtripAreaOther.setFocusable(false);
			}

		} else {
			// local trip, not roadtrip

			View v = findViewById(R.id.trip_tstop_areas_row1);
			if (v != null)
				v.setVisibility(View.GONE);
			v = findViewById(R.id.trip_tstop_areas_row2);
			if (v != null)
				v.setVisibility(View.GONE);

			// Look up and show current geoarea name
			TextView tv = (TextView) findViewById(R.id.trip_tstop_area_local_value);
			if ((tv != null) && (areaLocs_areaID >= 0))
			{
				try {
					String newAreaText;
					if (areaLocs_areaID != 0)
						newAreaText = new GeoArea(db, areaLocs_areaID).getName();
					else
						newAreaText = getResources().getString(R.string.none__parens);
					tv.setText(newAreaText);
				} catch (Exception e) {}  // very unlikely, ignore: used for display only
			}

			if (viewTS != null)
			{
				v = findViewById(R.id.trip_tstop_area_local_change);
				if (v != null)
					v.setEnabled(false);
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
		if (viewTS != null)
			return;

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
	 * Select the matching GeoArea's radio button, update {@link #areaLocs_areaID}
	 * and {@link #loc}'s autocomplete adapter, and optionally update the Activity's
	 * related data fields. (Does not change anything in the db)
	 *<P>
	 * Stops during a roadtrip have several GeoArea radios to select the starting area, no area, or ending area.
	 * For a local trip, updates the current GeoArea textview; does not show the roadtrip GeoArea radio buttons.
	 *<P>
	 * When calling for the "Other Geoarea"'s radio button, textfield content changes, or autocomplete items,
	 * ({@code areaID} == {@link #GEOAREAID_OTHER_NEW}), either update {@link #areaOther} before calling this method,
	 * or set it to {@code null} to have this method determine it using
	 * {@link GeoArea#getByName(RDBAdapter, String) GeoArea.getByName}<tt>(db,
	 * {@link #etRoadtripAreaOther}.getText().trim())</tt>.
	 *<P>
	 * Before changing the currently selected button, this method confirms with the user
	 * if they've already picked a location there. This method is also used because the radios
	 * are laid out 2 columns and 2 rows, so a RadioGroup won't simply work.
	 *<P>
	 * Before v0.9.50, this method was {@code hilightRoadtripAreaButton}.
	 *
	 * @param areaID  GeoArea ID to select, 0 for none, or {@link #GEOAREAID_OTHER_NEW}
	 *   to determine it from {@link #areaOther} or {@link #etRoadtripAreaOther} contents
	 * @param newAreaText  New GeoArea's name, or null for "none" (no area) or "other" (separate text field).
	 *   Not needed unless <tt>alsoUpdateData</tt>.
	 *   Used for display and/or geoarea textfield contents, will not go directly into the database.
	 *   Used only for the confirmation dialog if the area changes after a location is chosen.
	 * @param alsoUpdateData If true, also update currTS,
	 *   and re-query location fields.  If false, only change the
	 *   checkmark, {@link #areaLocs_areaID}, {@link #loc} autocomplete adapter,
	 *   and {@link #rbRoadtripArea_chosen}.
	 * @param confirmChange  User-confirm action, if <tt>alsoUpdateData</tt>,
	 *   if they've already chosen a Location in another geoarea:
	 *   <UL>
	 *   <LI> 0: Ask the user in a popup AlertDialog: Calls
	 *     {@link #showRoadtripAreaButtonConfirmDialog(int, String, String, String)}
	 *   <LI> 1: Change to this location in the new area
	 *   <LI> 2: Clear the Location and ViaRoute fields
	 *   </UL>
	 *   The buttons of the popup in choice 0 will either call this method again,
	 *   with <tt>confirmChange</tt> 1 or 2, or cancel changing the GeoArea.
	 */
	private void selectRoadtripAreaButton
		(int areaID, String newAreaText, final boolean alsoUpdateData, final int confirmChange)
	{
		final boolean btnWasOther = (areaID == GEOAREAID_OTHER_NEW);

		if (btnWasOther)
		{
			String areaOtherName;
			Editable etOtherText = etRoadtripAreaOther.getText();
			if ((etOtherText != null) && (etOtherText.length() > 0))
				areaOtherName = etOtherText.toString().trim();
			else
				areaOtherName = "";

			if (areaOther != null)
				// validate area name too, in case text was changed since selection
				if ((areaOtherName.length() == 0) || areaOtherName.equalsIgnoreCase(areaOther.getName()))
					areaID = areaOther.getID();

			if (areaID == GEOAREAID_OTHER_NEW)
			{
				// Wasn't picked from dropdown: search the table from text

				// If it's new, check if new geoarea obj created at this tstop

				GeoArea geo = GeoArea.getByName(db, areaOtherName);
				if (geo != null)
				{
					areaOther = geo;
					areaID = areaOther.getID();
				}
			}
		}

		if ((areaLocs_areaID == areaID) && alsoUpdateData)
			return;

		final boolean locObjIsDifferentArea = alsoUpdateData
			&& (locObj != null) && (areaID != locObj.getAreaID())
			&& ((locObjCreatedHere == null) || (locObj.getID() != locObjCreatedHere.getID()));
		if (locObjIsDifferentArea && (confirmChange == 0))
		{
			// popup to confirm changing it; see confirmChange javadoc
			if (newAreaText == null)
				newAreaText = getResources().getString(R.string.none__parens);
			showRoadtripAreaButtonConfirmDialog
				(areaID, locObj.toString(), newAreaText, via.getText().toString());

			return;  // <--- Early return: Popup to confirm ---
		}

		// Start by updating the display.
		// Afterwards we'll also update the activity's data fields if requested.

		if (rbRoadtripArea_chosen != null)
			// un-select previous
			rbRoadtripArea_chosen.setChecked(false);

		RadioButton toChg;
		if (stopEndsTrip || ! currT.isRoadtrip())
		{
			toChg = null;

			// Update displayed current-geoarea name
			TextView tv = (TextView) findViewById(R.id.trip_tstop_area_local_value);
			if (tv != null)
			{
				if ((newAreaText == null) || (areaID == 0))
					newAreaText = getResources().getString(R.string.none__parens);
				tv.setText(newAreaText);
			}
		}
		else if (areaID == currT.getAreaID())
			toChg = rbRoadtripAreaStart;
		else if (areaID == currT.getRoadtripEndAreaID())
			toChg = rbRoadtripAreaEnd;
		else if (areaID == 0)
			toChg = rbRoadtripAreaNone;
		else
			toChg = rbRoadtripAreaOther;

		// Update GUI-related activity fields

		areaLocs_areaID = areaID;
		rbRoadtripArea_chosen = toChg;

		if (toChg != null)
			toChg.setChecked(true);

		if (! alsoUpdateData)
		{
			return;   // <--- Early return: No data changes ---
		}

		// Re-query and update other activity data fields

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
	 * Dialog to ask, when a new GeoArea button is pressed, whether to keep the
	 * currently entered location, clear the location text and object fields,
	 * or cancel changing the geoarea.
	 * Called from {@link #selectRoadtripAreaButton(int, String, boolean, int)},
	 * which this dialog's buttons will call again to do the action chosen.
	 * @param areaID  GeoArea ID to confirm changing to
	 * @param locText  Location text currently entered, or null
	 * @param newAreaText  New GeoArea's name; used for display and/or geoarea textfield contents
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
				// "You've already chosen a location in the previous area."
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
			selectRoadtripAreaButton(areaID, newAreaText, true, 1);  // keep location, change area
		  }
		});
		alert.setNegativeButton(R.string.trip_tstop_entry_clear_location, new DialogInterface.OnClickListener() {
		  public void onClick(DialogInterface dialog, int whichButton)
		  {
			selectRoadtripAreaButton(areaID, newAreaText, true, 2);  // clear location, change area
		  }
		});
		alert.setNeutralButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
		  public void onClick(DialogInterface dialog, int whichButton)
		  {
			// canceled, staying in the previous geoarea:
			// don't change the area, just make sure the original geoarea is the only radio button selected.

			if (rbRoadtripAreaStart == null)
				return;  // <--- Early return: None of these fields are visible ---

			// set this text now before changing radios,
			// because its textwatcher may want to pick its own radio.
			if (areaOther == null)
				etRoadtripAreaOther.setText("");
			else
				etRoadtripAreaOther.setText(areaOther.getName());

			// rbRoadtripArea_chosen won't necessarily be the one checked right now. Un-check all.
			rbRoadtripAreaStart.setChecked(false);
			rbRoadtripAreaEnd.setChecked(false);
			rbRoadtripAreaNone.setChecked(false);
			rbRoadtripAreaOther.setChecked(false);

			RadioButton toChg;
			final int areaID = areaLocs_areaID;
			if (areaID == currT.getAreaID())
				toChg = rbRoadtripAreaStart;
			else if (areaID == currT.getRoadtripEndAreaID())
				toChg = rbRoadtripAreaEnd;
			else if (areaID == 0)
				toChg = rbRoadtripAreaNone;
			else
				toChg = rbRoadtripAreaOther;

			if (toChg == rbRoadtripAreaOther)
				areaOther = areaOther_prev;

			rbRoadtripArea_chosen = toChg;
			toChg.setChecked(true);
		  }
		});

		alert.show();
	}

	/**
	 * Check Settings and VehSettings tables for <tt>CURRENT_DRIVER</tt>, <tt>CURRENT_VEHICLE</tt>,
	 * <tt>CURRENT_TRIP</tt>.
	 * Set {@link #currA}, {@link #currD}, {@link #currV} and {@link #currT}.
	 * Set {@link #currTS} if <tt>CURRENT_TSTOP</tt> is set.
	 * Set {#link {@link #prevLocObj}} if <tt>PREV_LOCATION</tt> is set.
	 * Check for and set {@link #isViewTScurrTS} if {@link #viewTS} is set.
	 *<P>
	 * If there's an inconsistency between Settings and GeoArea/Vehicle/Person tables, don't fix it
	 * in those tables, but don't load objects either.  The current GeoArea setting may be updated if missing.
	 *<P>
	 * If {@link #viewTS != null} when called, those fields will all be set from viewTS
	 * instead of current settings. If a record is missing, will return false.
	 *
	 * @return true if settings exist and are OK, false otherwise.
	 */
	private boolean checkCurrentDriverVehicleTripSettings()
	{
		if (viewTS != null)
		{
			currTS = viewTS;
			try
			{
				currT = new Trip(db, viewTS.getTripID());
				currV = new Vehicle(db, currT.getVehicleID());
				currD = new Person(db, currT.getDriverID());

				int a_id = viewTS.getAreaID();
				if (a_id == 0)
					a_id = currT.getAreaID();
				currA = new GeoArea(db, a_id);

				if (! currT.isEnded())
				{
					TStop ts = currT.readLatestTStop();
					if (ts != null)
						isViewTScurrTS = (viewTS.getID() == ts.getID());
				}
			}
			catch (Exception e) {
				return false;  // missing required data
			}

			return true;
		}

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

		// Disable odos in viewTS mode
		if (viewTS != null)
		{
			odo_trip_chk.setEnabled(false);
			odo_total_chk.setEnabled(false);
			odo_trip.setEnabled(false);
			odo_total.setEnabled(false);
			View v = findViewById(R.id.trip_tstop_odo_trip_calc_edit);
			if (v != null)
				v.setVisibility(View.GONE);
			v = findViewById(R.id.trip_tstop_odo_total_calc_edit);
			if (v != null)
				v.setVisibility(View.GONE);
			// Current total odometer -> Total odometer
			TextView tv = (TextView) findViewById(R.id.trip_tstop_odo_total_calc_lbl);
			if (tv != null)
				tv.setText(R.string.total_odometer);

			// find and replace odometer widgets with text values
			odo = currTS.getOdo_total();
			replaceViewWithText
				(odo_total, R.id.trip_tstop_odo_total_value_txt,
				 ((odo != 0) ? Integer.toString(odo / 10) : " "),
				 true);
			odo = currTS.getOdo_trip();
			replaceViewWithText
				(odo_trip, R.id.trip_tstop_odo_trip_value_txt,
				 ((odo != 0) ? getResources().getString(R.string.value__odo__float, odo / 10f) : " "),
				 true);
		}

		// Prep to fill via_route text field in read-only View Previous TStop mode:
		// Won't be able to set via_text in updateViaRouteAutocomplete() because
		// prevLocObj == null. Query it here instead:
		String currTS_via_text = currTS.getVia_route();
		if ((viewTS != null) && ((currTS_via_text == null) || (currTS_via_text.length() == 0)))
		{
			int via_id = viewTS.getVia_id();
			if (via_id != 0)
			{
				try
				{
					ViaRoute via = new ViaRoute(db, via_id);
					currTS_via_text = via.getDescr();
				}
				catch (Exception e) {}
			}
		}

		// fill text fields, unless null or 0-length; if viewTS != null, sets read-only
		setEditText(currTS.readLocationText(), R.id.trip_tstop_loc);
		setEditText(currTS_via_text, R.id.trip_tstop_via);  // if via_id, sets in updateViaRouteAutocomplete()
		setEditText(currTS.getComment(), R.id.trip_tstop_comment);  // not read-only unless isViewTScurrTS

		// Set or hide Comment Status field
		TextView tv = (TextView) findViewById(R.id.trip_tstop_comment_status_txt);
		boolean setTV = false;
		if ((isViewTScurrTS) && (tv != null))
		{
			tv.setText(R.string.trip_tstop_entry__view_prev_cannot_edit_current);
			setTV = true;
		}
		else if ((viewTS != null) && (tv != null))
		{
			// check comment status flags and form a localized string:
			// "Comment was added and edited later.", etc
			final Resources res = getResources();
			final String[] cverbs = new String[3];  // contains "added", "edited", etc
			int ci = 0;  // next index to use

			final boolean wasRemoved = currTS.isSingleFlagSet(TStop.FLAG_COMMENT_REMOVED);
			boolean removedThenAdded = false;
			if (wasRemoved)
			{
				final String comment = viewTS.getComment();
				if ((comment != null) && (comment.length() > 0))
				{
					removedThenAdded = true;
					cverbs[0] = res.getString(R.string.trip_tstop_entry_comment_later__removed);
					++ci;
				}
			}

			if (currTS.isSingleFlagSet(TStop.FLAG_COMMENT_ADDED))
			{
				cverbs[ci] = res.getString(R.string.trip_tstop_entry_comment_later__added);
				++ci;
			}
			if (currTS.isSingleFlagSet(TStop.FLAG_COMMENT_EDITED))
			{
				cverbs[ci] = res.getString(R.string.trip_tstop_entry_comment_later__edited);
				++ci;
			}
			if (wasRemoved && ! removedThenAdded)
			{
				cverbs[ci] = res.getString(R.string.trip_tstop_entry_comment_later__removed);
				++ci;
			}

			if (ci > 0)
			{
				final int id;
				switch (ci)
				{
				case 1:  // "Comment was (1) later."
					id = R.string.trip_tstop_entry_comment_later__1;
					break;
				case 2:  // "Comment was (1) and (2) later."
					id = R.string.trip_tstop_entry_comment_later__2;
					break;
				default:  // "Comment was (1), (2), and (3) later."
					id = R.string.trip_tstop_entry_comment_later__3;
				}

				tv.setText(res.getString(id, (Object[]) cverbs));
				setTV = true;
			}
		}

		if (! setTV)
			findViewById(R.id.trip_tstop_comment_status_txt).setVisibility(View.GONE);

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
			if (viewTS != null)
				btnGas.setEnabled(false);
		}
	}

	/**
	 * Replace a View within the layout with a {@link TextView} containing given text.
	 * @param vOld  The view to hide (visibility becomes {@link View#GONE}), or {@code null}
	 * @param vTxtID  The text view's ID to use
	 * @param ext  Text to use; if null or "", will use {@code "(none)"} from {@code R.string.none__parens}.
	 * @param setPadLeft  True to set 6dp padding on left
	 * @since 0.9.60
	 */
	private void replaceViewWithText
		(final View vOld, final int vTxtID, final CharSequence txt, final boolean setPadLeft)
	{
		final TextView tv = (TextView) findViewById(vTxtID);
		if (tv == null)
			return;

		if (vOld != null)
			vOld.setVisibility(View.GONE);

		// pad left 6dp and set text:
		if (setPadLeft)
			tv.setPadding((int) (6.0f * getResources().getDisplayMetrics().density), 0, 0, 0);
		if ((txt != null) && (txt.length() > 0))
			tv.setText(txt);
		else
			tv.setText(R.string.none__parens);
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
		areaOther_prev = areaOther;  // in case change is canceled
		selectRoadtripAreaButton
			(currT.getAreaID(), rbRoadtripAreaStart.getText().toString(), true, 0);
	}

	/** For roadtrips, update GUI and data from a click on the 'no geoarea' button. */
	public void onClick_BtnAreaNone(View v)
	{
		areaOther_prev = areaOther;  // in case change is canceled
		selectRoadtripAreaButton(0, null, true, 0);
	}

	/** For roadtrips, update GUI and data from a click on the 'ending geoarea' button. */
	public void onClick_BtnAreaEnd(View v)
	{
		areaOther_prev = areaOther;  // in case change is canceled
		selectRoadtripAreaButton
			(currT.getRoadtripEndAreaID(), rbRoadtripAreaEnd.getText().toString(), true, 0);
	}

	/**
	 * For roadtrips, update GUI and data from a click on the 'other geoarea' button.
	 * Should be called only when the radio button is clicked, not when text is typed
	 * or an area is selected from {@link #etRoadtripAreaOther}.
	 * @see #onClick_BtnAreaLocalChange(View)
	 * @since 0.9.50
	 */
	public void onClick_BtnAreaOther(View v)
	{
		String areaName = null;
		if (etRoadtripAreaOther != null)
		{
			Editable aNEd = etRoadtripAreaOther.getText();
			if ((aNEd != null) && (aNEd.length() > 0))
				areaName = aNEd.toString();
			else
				areaName = getResources().getString(R.string.other__dots);  // fallback: "Other..."
		}

		areaOther_prev = areaOther;
		selectRoadtripAreaButton(GEOAREAID_OTHER_NEW, areaName, true, 0);
	}

	/**
	 * Button during local trips to choose another geoarea for the current stop.
	 * This could transform a local trip into a roadtrip.
	 * This button is also visible when ending a roadtrip; the dialog for that omits GeoArea "none"
	 * by calling {@link #onCreateDialog(int) onCreateDialog(trip_tstop_area_local_row)}.
	 * Choosing an area in the dialog will update {@link #areaLocs_areaID} and related activity fields,
	 * even if ! {@link Trip#isRoadtrip() currT.isRoadtrip()}.
	 * @see #onClick_BtnAreaOther(View)
	 * @since 0.9.50
	 */
	public void onClick_BtnAreaLocalChange(View v)
	{
		showDialog(R.id.trip_tstop_area_local_row);
			// omit "none" in case stopEndsTrip; will add it soon.
	}

	/** Show or hide the roadtrip other-geoarea dropdown if available */
	public void onClick_BtnAreasETOtherDropdown(View v)
	{
		if (etRoadtripAreaOther == null)
			return;

		if (etRoadtripAreaOther.isPopupShowing())
			etRoadtripAreaOther.dismissDropDown();
		else
			etRoadtripAreaOther.showDropDown();
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

			locObj = Location.getByDescr(db, areaLocs_areaID, loc.getText().toString().trim());
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
	 *<P>
	 * Save button is also visible if {@link #stopEndsTrip}:
	 * If not {@link #isCurrentlyStopped}, will create TStop
	 * but not end trip.
	 */
	public void onClick_BtnSaveChanges(View v)
	{
		if (! (isCurrentlyStopped || stopEndsTrip))
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
	 *<P>
	 * In View Previous TStop mode, the "Close" button calls this method.
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
	 * save changes to db, continue from stop if {@link #isCurrentlyStopped}
	 * unless <tt>saveOnly</tt>, and finish this Activity.
	 *<P>
	 * If {@link #viewTS} != null: There's nothing to save except possibly comments:
	 * finish the activity immediately after checking those. If also {@link #isViewTScurrTS},
	 * everything is read-only including comments. If {@code ! saveOnly}, treat as Close button
	 * and save nothing.
	 *
	 * @param saveOnly  If true, save changes but don't leave
	 *   the stop or continue the trip.  Can assume {@link #isCurrentlyStopped} when true,
	 *   unless {@link #stopEndsTrip}.
	 * @since 0.9.20
	 */
	protected void enterTStop(final boolean saveOnly)
	{
		String locat = null, via_route = null, comment = null;
		boolean createdGeoArea = false, createdLoc = false, createdVia = false;
		boolean allOK = true;  // set to false if exception thrown, etc; if false, won't finish() the activity

		/**
		 * View Previous TStop mode: Almost no changes to save.
		 * If isViewTScurrTS or not saveOnly, close & finish without saving changes.
		 */
		if (viewTS != null)
		{
			boolean anyChanges = false;

			if (saveOnly && ! isViewTScurrTS)
			{
				// Save any comment field changes
				comment = textIfEntered(R.id.trip_tstop_comment);
				anyChanges = viewTS.setComment(comment, true, true);
			}

			setResult((anyChanges ? RESULT_FIRST_USER : RESULT_CANCELED), getIntent());
			finish();  // <--- Finish this Activity ---
			return;
		}

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

		// validate area ID @ end of roadtrip: can't end in 0 (none);
		// user can pick any geo area to set the trip's ending area.
		// If not stopEndsTrip, area ID 0 is OK: can still convert local to roadtrip.
		if (stopEndsTrip && (areaLocs_areaID <= 0) && ! saveOnly)
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
			return;  // <--- Early return: Must choose stop's geo area ---
		}

		// Roadtrip stop within Other area: validate a geoarea name was chosen or entered;
		// if needed, search db for geoarea name and update areaLocs_areaID if found.
		boolean usedAreaOther = false;
		String areaOtherName = null;
		if (currT.isRoadtrip() && (rbRoadtripAreaOther != null) && rbRoadtripAreaOther.isChecked())
		{
			areaOtherName = etRoadtripAreaOther.getText().toString().trim();
			if (areaOtherName.length() == 0)
			{
				etRoadtripAreaOther.requestFocus();
				Toast.makeText
					(this, R.string.trip_tstop_entry_choose_geoarea, Toast.LENGTH_SHORT).show();
				return;  // <--- Early return: No geoarea text entered ---
			}
			else if ((areaOther == null) || ! areaOther.getName().equalsIgnoreCase(areaOtherName))
			{
				// Wasn't picked from dropdown:
				// search the table, avoid creating 2 geoareas with same name.

				// If an area is found here, and areaOtherCreatedHere != null
				// and isn't that area, code later in the method will delete
				// areaOtherCreatedHere to clean up before committing currTS.

				areaOther = GeoArea.getByName(db, areaOtherName);
				if (areaOther != null)
					areaLocs_areaID = areaOther.getID();  // may become start or ending area id
				// else
				//      areaLocs_areaID will be set soon, when new GeoArea is created
				//      or areaOtherCreatedHere is reused.
			}

			usedAreaOther = (areaOther == null)
			     || ((areaLocs_areaID != currT.getAreaID())
				 && (areaLocs_areaID != currT.getRoadtripEndAreaID()));

			// TODO ask about locObj areaid vs chosen areaid, like selectRoadtripAreaButton does?
			// See comment and code at "Get or create the Location db record" below,
			// which ignores existing locObj if areaid differs.
		}

		final boolean wantsConvertLocalToRoadtrip
			= (! currT.isRoadtrip())
			  && (areaLocs_areaID >= ((stopEndsTrip && ! saveOnly) ? 1 : 0))
			  && (areaLocs_areaID != currT.getAreaID());

		// areaLocs_areaID is set in onCreate, updated by radios or dialogs
		// or dropdowns, but check again now just in case.
		// This is a fallback in case other checks somehow missed it,
		// to avoid data inconsistency caused by ending a trip at null geoarea.

		// Note: If not stopEndsTrip, we allow conversion of local to roadtrip but
		//    still preserve areaLocs_areaID == 0 if stopping in null geoarea.
		//    (To avoid roadtrip_end_aid 0, which means local trip in db,
		//     Trip.convertLocalToRoadtrip uses trip's starting area as a nonzero placeholder)

		if ((areaLocs_areaID <= 0)
		    && ((stopEndsTrip && ! saveOnly) || ! (currT.isRoadtrip() || wantsConvertLocalToRoadtrip)))
		{
			if (! stopEndsTrip)
			{
				areaLocs_areaID = currA.getID();
			} else {
				areaLocs_areaID = currT.getRoadtripEndAreaID();  // will be 0 for local trips
				if (areaLocs_areaID == 0)  // can't end trip in geoarea 0
					areaLocs_areaID = currA.getID();
			}

			// if usedAreaOther, will set areaLocs_areaID soon below from areaOther and areaOtherName.
		}

		// Check for required trip category:
		if (stopEndsTrip && (! saveOnly)
		    && Settings.getBoolean(db, Settings.REQUIRE_TRIPCAT, false))
		{
			final int tripCat = ((TripCategory) (spTripCat.getSelectedItem())).getID();
			if (tripCat <= 0)
			{
				spTripCat.requestFocus();
				Toast.makeText(this, R.string.trip_tstart_categ_req, Toast.LENGTH_SHORT).show();
				return;  // <--- Early return: missing required ---
			}
		}

		/**
		 * Done checking data entered, time to update the db.
		 * tsid is the TStop ID we'll create or update here.
		 * May convert a local trip into a roadtrip; see wantsConvertLocalToRoadtrip below.
		 */
		final int tsid;

		// Roadtrip stop within other geoarea:
		// Get or create the GeoArea db record, if we don't already have it
		if (usedAreaOther && (areaOther == null))
		{
			if (areaOtherCreatedHere == null)
			{
				areaOther = new GeoArea(areaOtherName);
				areaLocs_areaID = areaOther.insert(db);
				createdGeoArea = true;
			} else {
				// re-use it
				areaOther = areaOtherCreatedHere;
				areaOther.setName(areaOtherName);
				areaOther.commit();
				areaLocs_areaID = areaOther.getID();
			}
		}

		if ((areaOtherCreatedHere != null) && (areaLocs_areaID != areaOtherCreatedHere.getID()))
		{
			// record created at this tstop wasn't used, so remove it from db
			areaOtherCreatedHere.delete();
			areaOtherCreatedHere = null;
			// code below will clearFlagSingle(TEMPFLAG_CREATED_GEOAREA)
		}

		int locID = 0;

		// Get or create the Location db record,
		// if we don't already have it or its area ID != areaLocs_areaID
		if ((locObj == null)
			|| (! locObj.getLocation().equalsIgnoreCase(locat))
			|| ((areaLocs_areaID != locObj.getAreaID())
			    && ((locObjCreatedHere == null) || (locObj.getID() != locObjCreatedHere.getID()))))
		{
			locObj = null;

			final int locatIdx = loc.getListSelection();
			ListAdapter la = loc.getAdapter();
			if ((locatIdx != ListView.INVALID_POSITION)
			    && (locatIdx != ListAdapter.NO_SELECTION) && (la != null))
			{
				locObj = (Location) la.getItem(locatIdx);
				// use same criteria as above
				if ((locObj != null) && locObj.getLocation().equalsIgnoreCase(locat)
				    && (areaLocs_areaID == locObj.getAreaID()))
					locID = locObj.getID();
			}

			if (locObj == null)
			{
				// search the table, avoid creating 2 locations with same name
				locObj = Location.getByDescr(db, areaLocs_areaID, locat);
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

				// no need to also update locObj.areaid field, because
				// we're resuming travel from this stop and won't be at
				// locObj next time this activity is called; locObj and
				// locObjCreatedHere are the same row in the db, which
				// we've updated from locObjCreatedHere.
			}

		}
		if ((locObjCreatedHere != null) && (locID != locObjCreatedHere.getID()))
		{
			// record created at this tstop wasn't used, so remove it from db
			locObjCreatedHere.delete();
			locObjCreatedHere = null;
			// code below will clearFlagSingle(TEMPFLAG_CREATED_LOCATION)
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

			// update description if via created here and its capitalization has changed;
			// must check because of above getDescr().equalsIgnoreCase
			if ((viaRouteObjCreatedHere != null) && (viaID == viaRouteObjCreatedHere.getID())
			    && ! viaRouteObjCreatedHere.getDescr().equals(via_route))
			{
				viaRouteObjCreatedHere.setDescr(via_route);
				viaRouteObjCreatedHere.commit();
			}

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
			// code below will clearFlagSingle(TEMPFLAG_CREATED_VIAROUTE)
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

		// Now either create a new TStop in the database, or update currTS there.

		// Note: A roadtrip can't end in geoarea 0, the db roadtrip field uses 0 when it's a local trip.
		//       Can't allow tstop in area 0 if stopEndsTrip, so code earlier in this method checks
		//       for (stopEndsTrip && (areaLocs_areaID <= 0) && ! saveOnly) and if found, shows a
		//       Toast and returns. So at this point areaLocs_areaID > 0 if stopEndsTrip && ! saveOnly.

		/**
		 * Done creating/updating related and 'master record' data.
		 * Now, create or update the actual TStop.
		 */
		if (! isCurrentlyStopped)
		{
			// Create a new TStop; set tsid (not currTS).

			int areaID;  // geoarea of new tstop
			if (stopEndsTrip && ! saveOnly)
			{
				// For local trips, this ending TStop's areaID will be 0.
				// For roadtrips, can't end in area ID 0 (no geoarea),
				// so as a fallback change that here to the ending area.
				// (The GUI has already given the user a chance to correct it.)
				// If all the roadtrip's stops are in the starting geoarea, it will be
				// converted into a local trip by VehSettings.endCurrentTrip.
				// Similar code is below, used when updating an existing TStop;
				// search for "For local trips, this ending TStop's areaID".

				if (wantsConvertLocalToRoadtrip)
					areaID = areaLocs_areaID;  // in a non-local area. Assert: areaID > 0
				else if (currT.isRoadtrip() && (areaLocs_areaID > 0))
					areaID = areaLocs_areaID;  // in any geoarea > 0
				else
					areaID = currT.getRoadtripEndAreaID();  // in ending area, or 0 if local trip
			}
			else if ((areaLocs_areaID >= 0)
				 && (currT.isRoadtrip() || wantsConvertLocalToRoadtrip))
			{
				areaID = areaLocs_areaID;
				// New tstop during roadtrip; areaID 0 is OK since not ending trip.
				// historical db note: before March 2011 (r48) unless stopEndsTrip, tstop.a_id always 0
			} else {
				areaID = 0;  // unused in local trip tstops
			}

			int flags = 0;
			if (saveOnly || ! stopEndsTrip)
			{
				if (createdGeoArea)
					flags |= TStop.TEMPFLAG_CREATED_GEOAREA;
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
			currT.addCommittedTStop(newStop);  // update the Trip's cached TStop list, if any
			if (saveOnly || ! stopEndsTrip)
				VehSettings.setCurrentTStop(db, currV, newStop);

			// Convert local trip to roadtrip now if requested.
			// areaID 0 (none) is allowed for TStops during a trip,
			// but not for the final tstop ending the trip.
			if (wantsConvertLocalToRoadtrip
			    && (areaID >= ((stopEndsTrip && ! saveOnly) ? 1 : 0)))
				currT.convertLocalToRoadtrip(newStop);

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
			// Currently stopped; saving, resuming travel from stop, or ending trip.
			tsid = currTS.getID();
			currTS.setOdos(odoTotal, odoTrip);
			currTS.setTime_stop(stopTimeSec);
			// text fields, info fields
			currTS.setLocationID(locID);
			currTS.setVia_id(viaID);
			currTS.setComment(comment, false, false);
			if ((currT.isRoadtrip() || wantsConvertLocalToRoadtrip)
			    && (areaLocs_areaID >= ((stopEndsTrip && ! saveOnly) ? 1 : 0)))
				currTS.setAreaID(areaLocs_areaID);

			if (! saveOnly)
			{
				currTS.clearTempFlags();

				// continue-time
				if ((! stopEndsTrip) && (contTimeSec != 0))
					currTS.setTime_continue(contTimeSec, false);

				// when ending trip, check tstop's geoarea vs trip's areas
				if (stopEndsTrip)
				{
					// For local trips, this ending TStop's areaID is already 0.
					// For roadtrips, can't end in area ID 0 (no geoarea),
					// so as a fallback change that here to the ending area.
					// (The GUI has likely already given the user a chance to correct it.)
					// If all the roadtrip's stops are in the starting geoarea, it will be
					// converted into a local trip by VehSettings.endCurrentTrip.
					// Similar code is above, used when creating a new TStop;
					// search for "For local trips, this ending TStop's areaID".

					if (currT.isRoadtrip() && (currTS.getAreaID() <= 0))
						currTS.setAreaID(currT.getRoadtripEndAreaID());
				}
			} else {
				// Currently stopped, not continuing yet, so this stop may be edited again:

				// If any related records were newly created, make sure their flags are set in currTS.

				// If any related records were previously created for this stop, but the current changes
				// chose other preexisting ones instead of the ones created, clear those flags in currTS
				// so that future edits won't think the preexisting ones were created for this stop, and
				// then mistakenly delete them if their activity field is cleared.

				if ((areaOtherCreatedHere == null)
				    && currTS.isSingleFlagSet(TStop.TEMPFLAG_CREATED_GEOAREA))
					currTS.clearFlagSingle(TStop.TEMPFLAG_CREATED_GEOAREA);
				else if (createdGeoArea)
					currTS.setFlagSingle(TStop.TEMPFLAG_CREATED_GEOAREA);

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
			currT.updateCachedCurrentTStop(currTS);

			if (wantsConvertLocalToRoadtrip)
			{
				final int newAreaID = currTS.getAreaID();
				if ((newAreaID != currT.getAreaID())
				    && (newAreaID >= ((stopEndsTrip && ! saveOnly) ? 1 : 0)))
					currT.convertLocalToRoadtrip(currTS);
			}

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
		} else {
			// Continuing current trip.

			// Update TripCategory if its spinner was showing
			if (stopEndsTrip)
			{
				int tripCat = ((TripCategory) (spTripCat.getSelectedItem())).getID();
				if (tripCat < 0)
					tripCat = 0;
				currT.setTripCategoryID(tripCat);
				if (currT.isDirty())
					try
					{
						currT.commit();
					} catch (Exception e) {
						// All validation is done above, so no exception is expected
						allOK = false;
						Misc.showExceptionAlertDialog(this, e);
					}
			}

			// If roadtrip, update CURRENT_AREA if new stop is in a new GeoArea
			// (optional, helps future guesses for currA after stops in no area).
			if (currT.isRoadtrip())
			{
				final int locAID = locObj.getAreaID();
				if (locAID > 0)
				{
					GeoArea dbCurrA = VehSettings.getCurrentArea(db, currV, false);
					if ((dbCurrA == null) || (locAID != dbCurrA.getID()))
						try
						{
							GeoArea geo = new GeoArea(db, locAID);
							VehSettings.setCurrentArea(db, currV, geo);
							currA = geo;
						}
						catch (Exception e) {}
				}
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

		if (viewTS != null)
			i.putExtra(EXTRAS_FIELD_VIEW_TSTOP_ID, viewTS.getID());

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

		case R.id.main_btn_begin_freqtrip:  // TripTStopChooseFreq
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
	 * Callback for displaying {@link DatePickerDialog} after {@link #onClick_BtnStartDate(View)},
	 * or "Choose a new GeoArea" after {@link #onClick_BtnAreaLocalChange(View)}.
	 * @param id  Unique dialog key, borrowed from various controls' {@code R.id}s:
	 *    <UL>
	 *    <LI> {@code trip_tstop_area_local_row}: Show "Choose a new GeoArea for this stop"
	 *         (including "none" choice {@link GeoArea#GEOAREA_NONE} unless {@link #stopEndsTrip}).
	 *         Used when the trip is currently local, to convert it to a roadtrip.
	 *         When "OK" is pressed, sets {@link #areaLocs_areaID} by calling
	 *         {@link #selectRoadtripAreaButton(int, String, boolean, int)}.
	 *         The spinner includes the current (starting) geoarea as the default selection,
	 *         for visual consistency and to prevent accidentally choosing the first area in the list.
	 *         If the starting geoarea is still the selected one when the activity is finished,
	 *         the trip will remain local.
	 *    <LI> {@code trip_tstop_btn_cont_date}: Choose a date for Continue time
	 *    <LI> Otherwise: Choose a date for Stopped At time
	 *    </UL>
	 * @see #onDateSet(DatePicker, int, int, int)
	 */
	@Override
	protected Dialog onCreateDialog(final int id)
	{
		if (id == R.id.trip_tstop_area_local_row)
		{
			// converting local to roadtrip: choose tstop's geoarea

			final View popupLayout = getLayoutInflater().inflate
				(R.layout.trip_tstop_popup_choose_area, null);
			final Spinner areas = (Spinner) popupLayout.findViewById(R.id.logbook_show_popup_locs_areas);
			SpinnerDataFactory.setupGeoAreasSpinner(db, this, areas, areaLocs_areaID, ! stopEndsTrip, -1);
				// unless stopEndsTrip, includes "none" placeholder object GeoArea.GEOAREA_NONE

			AlertDialog.Builder alert = new AlertDialog.Builder(this);
			alert.setView(popupLayout);
			alert.setPositiveButton(android.R.string.ok,
					new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int whichButton)
				{
					final GeoArea newArea = (GeoArea) areas.getSelectedItem();
					if (newArea == null)
						return;  // unlikely

					selectRoadtripAreaButton(newArea.getID(), newArea.getName(), true, 0);
				}
			});
			alert.setNegativeButton(android.R.string.cancel, null);

			// When a new area is selected from the spinner, dismiss the dialog
			// instead of needing to also hit OK.
			SpinnerDataFactory.SpinnerItemSelectedListener spinListener
				= new SpinnerDataFactory.SpinnerItemSelectedListener(areaLocs_areaID)
			{
				@Override
				public void onItemSelected(AdapterView<?> parent, View v, int pos, long pos_long)
				{
					if (pos == ListAdapter.NO_SELECTION)
						return;

					Object a = parent.getAdapter();
					if ((a == null) || ! (a instanceof ListAdapter))
						return;

					try
					{
						Object obj = ((ListAdapter) a).getItem(pos);
						if ((obj != null) && (obj instanceof GeoArea))
						{
							GeoArea area = (GeoArea) obj;
							if (area.getID() == areaLocs_areaID)
								return;  // no change; may be calling from spinner init

							selectRoadtripAreaButton(area.getID(), area.getName(), true, 0);
							if (dia != null)
								dia.dismiss();
						}
					}
					catch (Exception e) {}
				}
			};

			AlertDialog aDia = alert.create();
			spinListener.dia = aDia;
			areas.setOnItemSelectedListener(spinListener);
			return aDia;
		}

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

	/**
	 * Unless <tt>txt</tt> is <tt>null</tt>, set <tt>editTextID</tt>'s contents.
	 *<P>
	 * If <tt>{@link #viewTS} != null</tt>, makes the field read-only but not visibly dark
	 * by calling {@link View#setFocusable(boolean)}. Exception: The {@code trip_tstop_comment}
	 * field isn't made read-only unless {@link #isViewTScurrTS}.
	 */
	private void setEditText(String txt, final int editTextID)
	{
		if (((txt == null) || (txt.length() == 0)) && (viewTS == null))
			return;
		else if (txt == null)
			txt = "";

		EditText et = (EditText) findViewById (editTextID);
		et.setText(txt);
		if ((viewTS != null) && ((editTextID != R.id.trip_tstop_comment) || isViewTScurrTS))
			et.setFocusable(false);
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
			if (! tp_time_cont_chk.isChecked())
				tp_time_cont_chk.setChecked(true);

			return;  // Running Count auto-update not active
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
		if (! tp_time_cont_chk.isChecked())
			tp_time_cont_chk.setChecked(true);
	}

	///////////////////////////////
	// Start of calculator methods
	///////////////////////////////

	/**
	 * Bring up an odometer's editor (calculator) dialog.
	 * Includes + - * / and reset, with memory register (M+ M- MC MR)
	 * saved to activity's {@link #calcMemory} field.
	 *<P>
	 * To prevent changing the odometer to the second operand instead of the
	 * calculation result, disables its Save button after any op button until
	 * Equals, Reset, or Clear is pressed.
	 * @param odo odometer to start from and save to
	 * @param isOdoTrip  true if decimal button should be enabled;
	 *    even if false, does not ignore '.' presses from soft keyboard
	 */
	private void onClickEditOdo(final OdometerNumberPicker odo, final boolean isOdoTrip)
	{
		// TODO managed dialog lifecycle: onCreateDialog etc
		// fields: bool for isTotal, bool for if any key pressed, int for prev value, op for + - * /
		// TODO activity int field for memory,
		//   TODO and load/save them with rest of fields
		final View calcItems = getLayoutInflater().inflate(R.layout.trip_tstop_popup_odo_calc, null);
		calcOdoIsTrip = isOdoTrip;
		calcOperation = CALC_OP_NONE;
		calcPrevOperand = 0;

		calcValue = (EditText) calcItems.findViewById(R.id.trip_tstop_popup_odo_calc_value);
		calcStatusView = (TextView) calcItems.findViewById(R.id.trip_tstop_popup_odo_calc_status);
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
		calcMC = calcItems.findViewById(R.id.trip_tstop_popup_odo_calc_mc);
		calcMR = calcItems.findViewById(R.id.trip_tstop_popup_odo_calc_mr);
		calcMC.setEnabled(calcMemory != 0.0f);
		calcMR.setEnabled(calcMemory != 0.0f);

		calcLoadValueFromOdo();
		calcUpdateStatusView();
		calcNextPressClears = true;

		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		if (isOdoTrip)
			alert.setTitle(R.string.trip_tstop_entry_calc_trip_odo);  // Calculator: Trip odometer
		else
			alert.setTitle(R.string.trip_tstop_entry_calc_total_odo);  // Calculator: Total odometer
		alert.setView(calcItems);
		alert.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton)
			{
				popupCalcDia = null;
				float ov;
				try
				{
					ov = Float.parseFloat(calcValue.getText().toString());
					if (ov < 0)
						ov = -ov;  // ignore negative sign
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
				popupCalcDia = null;
			}
		});

		popupCalcDia = alert.create();
		popupCalcDia.show();
	}

	/**
	 * Update {@link #calcStatusView} with the current {@link #calcOperation}
	 * and whether {@link #calcMemory} is occupied.
	 * @since 0.9.50
	 */
	private void calcUpdateStatusView()
	{
		StringBuilder sb = new StringBuilder();

		if (calcMemory != 0.0f)
			sb.append("M ");

		switch (calcOperation)
		{
		case CALC_OP_ADD:  sb.append("+");  break;
		case CALC_OP_SUB:  sb.append("-");  break;
		case CALC_OP_MUL:  sb.append("×");  break;
		case CALC_OP_DIV:  sb.append("÷");  break;
		}

		calcStatusView.setText(sb);
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
	 * current value. Also re-enables "Save" button.
	 * @since 0.9.42
	 * @see #onClick_CalcBtnClear(View)
	 */
	public void onClick_CalcBtnReset(View v)
	{
		calcLoadValueFromOdo();

		if (popupCalcDia != null)
			popupCalcDia.getButton(Dialog.BUTTON_POSITIVE).setEnabled(true);
	}

	/**
	 * The calculator Clear button.
	 * Also re-enables "Save" button.
	 * @see #onClick_CalcBtnReset(View)
	 */
	public void onClick_CalcBtnClear(View v)
	{
		calcValue.setText("");

		if (popupCalcDia != null)
			popupCalcDia.getButton(Dialog.BUTTON_POSITIVE).setEnabled(true);
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
	 * Disable "Save" button so Equals button will be pressed first
	 * instead of saving second operand to the odometer.
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
		calcUpdateStatusView();

		if (popupCalcDia != null)
		{
			Button btnSave = popupCalcDia.getButton(Dialog.BUTTON_POSITIVE);
			if (btnSave.isEnabled())
				btnSave.setEnabled(false);
		}
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

	/**
	 * Calculate {@link #calcOperation}, show the result, re-enable the "Save" button.
	 * @param v  ignored
	 */
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

		if (popupCalcDia != null)
			popupCalcDia.getButton(Dialog.BUTTON_POSITIVE).setEnabled(true);

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
		calcUpdateStatusView();
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

		if (addNotSub)
			calcMemory += cv;
		else
			calcMemory -= cv;

		// enable buttons, also acts as a visual indicator
		if (! calcMC.isEnabled())
		{
			calcMC.setEnabled(true);
			calcMR.setEnabled(true);
		}
		calcUpdateStatusView();
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
		calcMC.setEnabled(false);
		calcMR.setEnabled(false);
		calcUpdateStatusView();
	}

	public void onClick_CalcBtnMemRecall(View v)
	{
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
		if (etRoadtripAreaOther != null)
			outState.putCharSequence("AIDO_TXT", etRoadtripAreaOther.getText());
		outState.putInt("AIDO_ID", (areaOther != null) ? areaOther.getID() : -1);  // getID might be 0
		outState.putInt("AIDO_PR", (areaOther_prev != null) ? areaOther_prev.getID() : -1);
		outState.putInt("LOCID", (locObj != null) ? locObj.getID() : 0);
		outState.putInt("VIAID", (viaRouteObj != null) ? viaRouteObj.getID() : 0);
		outState.putFloat("CMEM", calcMemory);
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

		if (etRoadtripAreaOther != null)
			etRoadtripAreaOther.setText(inState.getCharSequence("AIDO_TXT"));
		int id = inState.getInt("AIDO_ID", -1);
		if (id == 0) {
			areaOther = GeoArea.GEOAREA_NONE;
		} else if (id > 0) {
			try {
				areaOther = new GeoArea(db, id);
			}
			catch (IllegalStateException e) {}
			catch (RDBKeyNotFoundException e) {}
		} else {
			areaOther = null;
		}
		id = inState.getInt("AIDO_PR", -1);
		if (id == 0) {
			areaOther_prev = GeoArea.GEOAREA_NONE;  // unlikely, covered just in case
		} else if (id > 0) {
			try {
				areaOther_prev = new GeoArea(db, id);
			}
			catch (IllegalStateException e) {}
			catch (RDBKeyNotFoundException e) {}
		} else {
			areaOther_prev = null;
		}

		id = inState.getInt("LOCID");
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

		calcMemory = inState.getFloat("CMEM");

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

	/**
	 * For Other GeoArea autocomplete, the callbacks for {@link OnItemClickListener}
	 * and {@link TextWatcher} when an area is selected or typed, to set or clear
	 * {@link #areaOther} and update other fields by calling
	 * {@link TripTStopEntry#selectRoadtripAreaButton(int, String, boolean, int) selectRoadtripAreaButton}
	 * ({@link TripTStopEntry#GEOAREAID_OTHER_NEW GEOAREAID_OTHER_NEW}, {@code areaName, true, 0)}.
	 */
	private class GeoAreaListenerWatcher implements OnItemClickListener, TextWatcher, View.OnFocusChangeListener
	{
		/** For Other GeoArea autocomplete, the callback for {@link OnItemClickListener} */
		public void onItemClick(AdapterView<?> parent, View clickedOn, int position, long rowID)
		{
			ListAdapter la = etRoadtripAreaOther.getAdapter();
			if (la == null)
				return;

			GeoArea area = (GeoArea) la.getItem(position);

			if (rbRoadtripAreaOther.isChecked() && (area != null) && (areaOther != null)
			    && area.getID() == areaOther.getID())
				return;

			areaOther_prev = areaOther;
			areaOther = area;

			// update radios, ask for confirmation if loc entered, etc
			String areaName;
			Editable aNEd = etRoadtripAreaOther.getText();
			if ((aNEd != null) && (aNEd.length() > 0))
				areaName = aNEd.toString().trim();
			else
				areaName = getResources().getString(R.string.other__dots);  // "Other..."

			selectRoadtripAreaButton(GEOAREAID_OTHER_NEW, areaName, true, 0);
		}

		/**
		 * When focus is lost, check {@link TripTStopEntry#areaOther areaOther} name against Other Geoarea
		 * textfield contents ({@link TripTStopEntry#etRoadtripAreaOther etRoadtripAreaOther}).
		 * If new text typed into {@code etRoadtripAreaOther} no longer matches {@code areaOther},
		 * update it and related activity data fields.
		 *<P>
		 * {@link TripTStopEntry#enterTStop(boolean)} must perform the same checks, because button clicks
		 * don't result in focus loss which will call this method.
		 *
		 * @see #afterTextChanged(Editable)
		 */
		public void onFocusChange(View unused, final boolean gainedFocus)
		{
			if (gainedFocus)
				return;

			final String areaName = etRoadtripAreaOther.getText().toString().trim();
			if (areaName.length() == 0)
			{
				areaOther_prev = areaOther;
				areaOther = null;

				return;
			}

			if ((areaOther == null) || (! areaOther.getName().equalsIgnoreCase(areaName))
			    || (areaLocs_areaID != areaOther.getID()))
			{
				// Mismatch: object no longer matches typed GeoArea description

				areaOther_prev = areaOther;
				areaOther = null;  // selectRoadtripAreaButton will set it using GeoArea.getByName

				// update radios, ask for confirmation if loc already entered, etc
				selectRoadtripAreaButton(GEOAREAID_OTHER_NEW, areaName, true, 0);
				    // TODO even if already checked, maybe remove location adapter if changed areaOther
			}
		}

		/**
		 * If new geoarea text is typed into {@link TripTStopEntry#etRoadtripAreaOther etRoadtripAreaOther},
		 * for responsiveness be sure the radio button is checked.
		 *<P>
		 * Because this can be called after eack character entered, further data checks
		 * ({@link TripTStopEntry#areaOther areaOther} against that text field, etc) will
		 * wait until {@link #onFocusChange(View, boolean)}.
		 *<P>
		 * Also doesn't update {@link TripTStopEntry#areaLocs_areaID areaLocs_areaID}
		 * or the {@link TripTStopEntry#loc loc} autocomplete adapter; they aren't needed yet
		 * because {@code loc} doesn't have focus. When {@code etRoadtripAreaOther} loses focus,
		 * they will be updated at that time by {@link #onFocusChange(View, boolean)}.
		 *<P>
		 * (callback method for addTextChangedListener / {@link TextWatcher})
		 * @param et  {@link TripTStopEntry#etRoadtripAreaOther etRoadtripAreaOther}
		 */
		public void afterTextChanged(Editable et)
		{
			if (rbRoadtripAreaOther.isChecked())
				return;

			if (et.toString().trim().length() > 0)
			{
				if (rbRoadtripArea_chosen != null)
					rbRoadtripArea_chosen.setChecked(false);

				rbRoadtripAreaOther.setChecked(true);
				rbRoadtripArea_chosen = rbRoadtripAreaOther;
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
