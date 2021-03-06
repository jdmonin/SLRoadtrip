/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2016,2019 Jeremy D Monin <jdmonin@nand.net>
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
import java.util.Date;

import org.shadowlands.roadtrip.AndroidStartup;
import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.android.util.Misc;
import org.shadowlands.roadtrip.db.FreqTrip;
import org.shadowlands.roadtrip.db.GeoArea;
import org.shadowlands.roadtrip.db.Location;
import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.RDBKeyNotFoundException;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.TripCategory;
import org.shadowlands.roadtrip.db.TStop;
import org.shadowlands.roadtrip.db.Trip;
import org.shadowlands.roadtrip.db.VehSettings;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

/**
 * Confirm settings and location and begin a trip, from {@link Main} activity.
 * Assumes no current trip. Assumes {@link Settings#CURRENT_VEHICLE CURRENT_VEHICLE},
 * {@link VehSettings#CURRENT_DRIVER CURRENT_DRIVER} are set.
 *<P>
 * To simplify interaction, all trips begin as local trips except
 * when the user has chosen a frequent trip that's a roadtrip.
 *<P>
 * By default the trip will not be based on a frequent trip. To have the user select
 * a frequent trip to use, set {@link #EXTRAS_FLAG_FREQUENT} when creating the intent
 * starting this activity.  See that flag's javadoc for details.
 *<P>
 * If the "Change Driver/Vehicle" button is pressed,
 * the {@link ChangeDriverOrVehicle} activity is shown;
 * returning from that, {@link #onActivityResult(int, int, Intent)}
 * will probably call {@link #updateDriverVehTripTextAndButtons()}.
 *<P>
 * The method handling the Begin Trip button that finishes
 * this Activity is {@link #onClick_BtnBeginTrip(View)}.
 *<P>
 * User can choose a different GeoArea than the vehicle's previous recorded trip,
 * in case there've been unrecorded roadtrips in between.
 *
 *<H5>Historical Mode:</H5>
 * To make it easier to add trips which occurred in the past, if either of the
 * following conditions are true, when this activity starts it will ask if the
 * Trip Start Time widget should be set to <em>now</em>, or to the <em>most recent time
 * recorded</em> for the vehicle (typically the previous trip's ending time):
 *<UL>
 * <LI> The vehicle's previous trip ended at least 21 days ago
 * <LI> The previous trip was at least 1 day old when it was added,
 *      and it was added within the last 2 days
 *</UL>
 *
 * @author jdmonin
 */
public class TripBegin extends Activity
	implements OnDateSetListener, OnItemClickListener
{
	/**
	 * Flag for beginning a trip based on a previously defined frequent trip, for
	 * {@link Intent#putExtra(String, boolean)}.
	 *<P>
	 * When the activity is started with this flag, the current vehicle's current
	 * location is found, and the {@link TripBeginChooseFreq} activity is called
	 * to choose a frequent trip starting from that location or the current area.
	 * When one is chosen, {@code TripBegin}.{@link #onActivityResult(int, int, Intent)}
	 * will set {@link #isFrequent}, {@link #wantsFT}, {@link #isRoadtrip}, and
	 * other activity fields for a trip based on that frequent trip.
	 */
	public static final String EXTRAS_FLAG_FREQUENT = "frequent";

	/**
	 * For asking about historical mode, the current time when the historical previous trip was created.
	 * When this key is present in shared prefs, the associated vehicle's most recent trip was historical when created.
	 * Creating a trip which starts at the current time, not 'historical', will remove this key from shared prefs.
	 *<P>
	 * A trip is considered 'historical' if, when created, its starting time was more than
	 * {@link TripTStopEntry#TIMEDIFF_HISTORICAL_MILLIS} ago from the current time.
	 * See class javadoc for details.
	 * @see #PREF_PREV_HISTORICAL_V_ID
	 * @see #TIMEDIFF_HISTORICAL_RECENT_MILLIS
	 * @since 0.9.50
	 */
	private static final String PREF_PREV_HISTORICAL_TIME_CREATED = "slroadtrip.tripbegin.hist.time_created";

	/**
	 * For asking about historical mode, the vehicle ID for which the previous historical trip was created.
	 * @see #PREF_PREV_HISTORICAL_TIME_CREATED
	 * @since 0.9.50
	 */
	private static final String PREF_PREV_HISTORICAL_V_ID = "slroadtrip.tripbegin.hist.v_id";

	/**
	 * Historical Mode threshold is 21 days, in milliseconds.
	 *<P>
	 * Must be larger than the other Historical Mode threshold {@link #TIMEDIFF_HISTORICAL_RECENT_MILLIS}.
	 */
	private static final long TIMEDIFF_HISTORICAL_MILLIS = 21 * 24 * 60 * 60 * 1000L;
		// if this is changed, update class javadoc & keep it larger than TIMEDIFF_HISTORICAL_RECENT_MILLIS

	/**
	 * 'Recent' Historical Mode threshold is 2 days, in milliseconds.
	 * This is used to determine in {@code onCreate} whether the vehicle's previous historical trip
	 * was created recently (that is, the user is entering a series of historical trips).
	 *<P>
	 * When a new trip is saved to the DB at the end of this activity:
	 * To determine whether the just-created trip is 'historical' or not,
	 * for consistency the activity uses {@link TripTStopEntry#TIMEDIFF_HISTORICAL_MILLIS}.
	 *<P>
	 * See class javadoc for details.
	 *<P>
	 * Must be significantly smaller than the other Historical Mode threshold
	 * {@link #TIMEDIFF_HISTORICAL_MILLIS}.
	 * @see #PREF_PREV_HISTORICAL_TIME_CREATED
	 * @since 0.9.50
	 */
	private static final long TIMEDIFF_HISTORICAL_RECENT_MILLIS = 2 * 24 * 60 * 60 * 1000L;
		// if this is changed, update class javadoc & keep it much smaller than TIMEDIFF_HISTORICAL_MILLIS

	/** tag for Log debugs */
	@SuppressWarnings("unused")
	private static final String TAG = "Roadtrip.TripBegin";

	private RDBAdapter db = null;

	/**
	 * Trip is beginning as a roadtrip.  In v0.9.50 and newer, only frequent trips can begin as roadtrips,
	 * so this will be set in {@link #onActivityResult(int, int, Intent)}; see {@link #isFrequent}.
	 */
	private boolean isRoadtrip;

	/**
	 * Trip will be based on frequent trip {@link #wantsFT}, probably starting from {@link #locObj}.
	 * Might also set {@link #isRoadtrip}. For more details see {@link #EXTRAS_FLAG_FREQUENT}.
	 */
	private boolean isFrequent;

	/**
	 * True if {@link #checkCurrentDriverVehicleSettings()} has been called at least once.
	 * @since 0.9.70
	 */
	private boolean hasCalledCheckCurrent;

	/** Shows name of current driver and vehicle. Updated in {@link #updateDriverVehTripTextAndButtons()}. */
	private TextView tvCurrentSet;

	/**
	 * Destination {@link GeoArea} textfield, or null if ! {@link #isRoadtrip}.
	 * Selected item (if any) is {@link #destAreaObj}.  Listener is {@link #etGeoAreaListener}.
	 */
	private AutoCompleteTextView etGeoArea;

	/** Listener for {@link #etGeoArea} to update {@link #destAreaObj}. */
	private GeoAreaOnItemClickListener etGeoAreaListener;

	/**
	 * Starting-location textfield. Tapping its autocomplete suggestions sets {@link #locObj}.
	 */
	private AutoCompleteTextView etLoc;

	/** starting date-time */
	private Calendar startTime;

	/** value of {@link #startTime} as set by Activity, not by user, in milliseconds;
	 *  used to determine whether to leave {@link #startTime} unchanged */
	private long startTimeAtCreate;

	/**
	 * date formatter for use by {@link DateFormat#format(CharSequence, Calendar)},
	 * initialized once in {@link #updateStartDateButton()}.
	 */
	private StringBuffer fmt_dow_shortdate;

	private Button btnStartDate;
	private TimePicker tpStartTime;  // TODO on hour wraparound: Chg date

	/** optional passenger count */
	private EditText etPax;

	/** optional {@link TripCategory} */
	private Spinner spTripCat;

	/**
	 * View or change vehicle's current {@link GeoArea} ({@link #currA}).
	 * Selection Listener is added during first call to {@link #checkCurrentDriverVehicleSettings()}.
	 * @since 0.9.70
	 */
	private Spinner spGeoArea;

	/**
	 * Vehicle's GeoArea; user can change with {@link #spGeoArea}.
	 * Updated by calling {@link #setCurrentArea(GeoArea, boolean)}
	 * in {@link #checkCurrentDriverVehicleSettings()}
	 * and {@link #spGeoArea}'s {@link AdapterView.OnItemSelectedListener}.
	 * @see #prevA
	 */
	private GeoArea currA;

	/**
	 * Value of {@link #currA} previously set up in {@link #checkCurrentDriverVehicleSettings()}
	 * or {@link #setCurrentArea(GeoArea, boolean)}. Prevents unneeded work when changing to a
	 * vehicle with same GeoArea as previous one.
	 */
	private GeoArea prevA;

	private Vehicle currV;
	private Person currD;
	private int prevVId, prevDId;

	/**
	 * Location to start from, null or as determined from previous trip, for {@link #locObj}.
	 * Set by {@link #updateDriverVehTripTextAndButtons()} based on {@link #startingPrevTStop}.
	 */
	private Location locObjOrig;

	/** Location to start from, possibly null or selected from {@link #etLoc} dropdown; see {@link #locObjOrig} */
	private Location locObj;

	/**
	 * Roadtrip destination geoarea.
	 * Set in {@link #updateETGeoArea(int, int)} and {@link GeoAreaOnItemClickListener}.
	 */
	private GeoArea destAreaObj;

	/** If freqtrip, the chosen freqtrip, or null */
	private FreqTrip wantsFT;

	/**
	 * If not null, the vehicle's previous trip's final TStop that we can continue from.
	 * Set by {@link #updateDriverVehTripTextAndButtons()} along with {@link #locObjOrig}.
	 *<P>
	 * To use this TStop, its {@link TStop#getLocationID() getLocationID()} must equal
	 * {@link #locObj}.{@link Location#getID() getID()}, and the starting odometer must be
	 * the same as this TStop's; otherwise there's a gap in this vehicle's history, and 
	 * {@code startingPrevTStop} should be null to keep the data consistent.
	 * If this TStop can't be used, a new TStop will be created to begin the new Trip.
	 */
	private TStop startingPrevTStop;

	private OdometerNumberPicker odo;

	/** Called when the activity is first created.
	 * See {@link #onResume()} for remainder of init work,
	 * which includes checking the current driver/vehicle/trip
	 * and hiding/showing buttons as appropriate.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.trip_begin);

		Intent i = getIntent();
		if (i != null)
		{
			isFrequent = i.getBooleanExtra(EXTRAS_FLAG_FREQUENT, false);
		} // else, is false

		db = new RDBOpenHelper(this);
		startTime = Calendar.getInstance();
		startTimeAtCreate = System.currentTimeMillis();
		startTime.setTimeInMillis(startTimeAtCreate);

		tvCurrentSet = (TextView) findViewById(R.id.trip_begin_text_current);
		odo = (OdometerNumberPicker) findViewById(R.id.trip_begin_odometer);
		odo.setTenthsVisibility(false);
		etLoc = (AutoCompleteTextView) findViewById(R.id.trip_begin_location);

		// Because this is the first edittext in the layout, it will have focus when the activity starts.
		// updateDriverVehTripTextAndButtons will have filled it in from locObj, with more characters than
		// the threshold. Some Android versions will then "helpfully" show the autocomplete dropdown,
		// which only obscures other parts of the activity. (Seen on android 4.1, 5.0)
		// So, hide the dropdown when gaining focus if text contents are locObj:
		// Doesn't always work, but should reduce frequency.
		etLoc.setOnFocusChangeListener(new View.OnFocusChangeListener()
		{
			public void onFocusChange(View v, boolean hasFocus)
			{
				if (! hasFocus)
					return;

				if ((locObj != null) && etLoc.getText().toString().equals(locObj.toString()))
				{
					etLoc.postDelayed(new Runnable()
					{
						public void run() { etLoc.dismissDropDown(); }
					}, 250);
				}
			}
		});

		if (isRoadtrip)
			etGeoArea = (AutoCompleteTextView) findViewById(R.id.trip_begin_roadtrip_desti);
		btnStartDate = (Button) findViewById(R.id.trip_begin_btn_start_date);

		tpStartTime = (TimePicker) findViewById(R.id.trip_begin_start_time);
		tpStartTime.setIs24HourView(DateFormat.is24HourFormat(this));
		// make sure hour is correct after noon ("pm" hour < 12 seen in 4.2.2 in 24-hour mode)
		tpStartTime.setCurrentHour(startTime.get(Calendar.HOUR_OF_DAY));

		if (Settings.getBoolean(db, Settings.SHOW_TRIP_PAX, false))
		{
			etPax = (EditText) findViewById(R.id.trip_begin_pax);
		} else {
			View vrow = findViewById(R.id.trip_begin_row_pax);
			if (vrow != null)
				vrow.setVisibility(View.GONE);
		}

		spGeoArea = (Spinner) findViewById(R.id.trip_begin_geoarea);
		SpinnerDataFactory.setupGeoAreasSpinner(db, this, spGeoArea, -1, false, -1);
			// currA won't be known until onResume() calls checkCurrentDriverVehicleSettings(),
			// which will also update the current GeoArea in the spinner.

		spTripCat = (Spinner) findViewById(R.id.trip_begin_category);
		SpinnerDataFactory.setupTripCategoriesSpinner(db, this, spTripCat, -1);

		// update title text if frequent/roadtrip
		if (isRoadtrip || isFrequent)
		{
			Resources res = getResources();
			StringBuffer sb = new StringBuffer(res.getString(R.string.begin__initcap));
			sb.append(' ');
			int id;
			if (isFrequent)
			{
				if (isRoadtrip)
					id = R.string.frequent_roadtrip;
				else
					id = R.string.frequent_local;
			} else {
				id = R.string.road_trip;
			}
			sb.append(res.getString(id));
			setTitle(sb);
		}

		if (! isRoadtrip)
		{
			View rtr_row = findViewById(R.id.trip_begin_roadtrip_desti_row);
			rtr_row.setVisibility(View.GONE);
		}

		// all this will be set, checked in updateDriverVehTripTextAndButtons():
		prevVId = 0;
		prevDId = 0;
		locObjOrig = null;
		locObj = null;
		currA = null;
		prevA = null;

		// see onResume for rest of initialization, including startDate display.
	}

	/**
	 * Check settings tables for {@link Settings#CURRENT_VEHICLE CURRENT_VEHICLE},
	 * {@link VehSettings#CURRENT_DRIVER CURRENT_DRIVER}.
	 * Set {@link #currV} and {@link #currD} activity fields.
	 * Sets {@link #hasCalledCheckCurrent} flag at end of method.
	 *<P>
	 * If there's an inconsistency between settings and Vehicle/Person tables, delete the settings entry.
	 * {@code currV} and {@code currD} will be null unless they're set consistently in db settings.
	 *
	 * @return true if settings exist and are OK, false otherwise.
	 */
	private boolean checkCurrentDriverVehicleSettings()  // TODO refactor common
	{
		currV = Settings.getCurrentVehicle(db, true);
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
		setCurrentArea(currA, true);

		if (! hasCalledCheckCurrent)
		{
			spGeoArea.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
			{
				/** If area changes, call {@link TripBegin#setCurrentArea(GeoArea, boolean)} */
				public void onItemSelected
					(final AdapterView<?> spinner, final View itemv, final int pos, final long unusedViewID)
				{
					Object obj = spinner.getItemAtPosition(pos);
					if ((obj == null) || ! (obj instanceof GeoArea))
						return;  // just in case
					final GeoArea selA = (GeoArea) obj;
					if (selA != currA)
						setCurrentArea(selA, false);
				}

				/** Ignore, keep current GeoArea */
				public void onNothingSelected(AdapterView<?> spinner) {}
			});
		}

		currD = VehSettings.getCurrentDriver(db, currV, true);

		hasCalledCheckCurrent = true;

		return (currD != null);
	}

	/**
	 * Set value of {@link #currA}, and update related widgets if different from {@link #prevA}:
	 * {@link #etLoc}, {@link #etGeoArea}, maybe {@link #spGeoArea}. Also updates {@link #prevA}.
	 *<P>
	 * Before v0.9.70 this code was part of {@link #checkCurrentDriverVehicleSettings()}.
	 *
	 * @param geo  New GeoArea to use, or {@code null}
	 * @param setSPGeoArea  If true and updating widgets, select {@code geo} in {@link #spGeoArea} if not {@code null}
	 * @since 0.9.70
	 */
	private void setCurrentArea(final GeoArea geo, final boolean setSPGeoArea)
	{
		this.currA = geo;
		if (currA == prevA)
			return;

		final int aID = (currA != null) ? currA.getID() : -1;
		Location[] areaLocs = Location.getAll(db, aID);
		if (areaLocs != null)
		{
			etLoc.setAdapter(new ArrayAdapter<Location>(this, R.layout.list_item, areaLocs));
			etLoc.setOnItemClickListener(this);
		} else {
			etLoc.setAdapter((ArrayAdapter<Location>) null);
		}
		prevA = currA;

		if (setSPGeoArea && (aID > 0))
			SpinnerDataFactory.selectRecord(spGeoArea, aID);

		if (isRoadtrip)
			updateETGeoArea(-1, aID);

		if ((locObj != null) && (aID != locObj.getAreaID()))
		{
			// clear locObj text from etLoc, unless text's been changed since selection
			if (etLoc.getText().toString().trim().equalsIgnoreCase(locObj.getLocation()))
				etLoc.setText("");

			locObj = null;
		}
	}

	/**
	 * Update adapter for {@link #etGeoArea}, and optionally set {@link #destAreaObj}.
	 * @param setAreaID  Set {@link #destAreaObj} to this AreaID, show its text in {@code etGeoArea},
	 *     or 0 or -1 to leave {@code destAreaObj} unchanged.
	 * @param exceptAreaID  Exclude this area; -1 to include all areas
	 * @throws NullPointerException if {@link #etGeoArea} == null
	 * @since 0.9.50
	 */
	private void updateETGeoArea(final int selectAreaID, final int exceptAreaID)
		throws NullPointerException
	{
		final GeoArea[] geos = GeoArea.getAll(db, exceptAreaID);
		if (geos != null)
		{
			etGeoArea.setAdapter
				(new ArrayAdapter<GeoArea>(this, R.layout.list_item, geos));
			if (etGeoAreaListener == null)
				etGeoAreaListener = new GeoAreaOnItemClickListener();
			etGeoArea.setOnItemClickListener(etGeoAreaListener);
		} else {
			etGeoArea.setAdapter((ArrayAdapter<GeoArea>) null);
		}

		if (selectAreaID > 0)
		{
			destAreaObj = null;
			try
			{
				destAreaObj = new GeoArea(db, selectAreaID);
				etGeoArea.setText(destAreaObj.getName());
			}
			catch (Exception e) {}
		}
	}

	/**
	 * Update the text about current driver, vehicle and trip;
	 * update odometer and start-date {@link #updateStartDateButton()} too.
	 * {@link #startingPrevTStop}, {@link #locObj}, and
	 * {@link #locObjOrig} are also set here.
	 *<P>
	 * Called when activity appears ({@link #onResume()})
	 * and when a different driver or vehicle is chosen.
	 */
	private void updateDriverVehTripTextAndButtons()
	{
		final int dID = currD.getID();
		final int vID = currV.getID();
		if ((prevVId == vID) && (prevDId == dID))
			return;

		final Resources res = getResources();

		StringBuffer txt = new StringBuffer(res.getString(R.string.driver));
		txt.append(": ");
		txt.append(currD.toString());
		txt.append('\n');
		txt.append(res.getString(R.string.vehicle));
		txt.append(": ");
		txt.append(currV.toString());
		tvCurrentSet.setText(txt);

		if (prevVId != vID)
		{
			odo.setCurrent10d(currV.getOdometerCurrent(), false);
			prevVId = vID;

			// read veh's prev-trip stuff, fill in begin-from
			startingPrevTStop = null;
			locObj = null;
			locObjOrig = null;
			final int last_tid = currV.getLastTripID();
			if (last_tid != 0)
			{
				startingPrevTStop = TStop.latestStopForTrip(db, last_tid, false);
				if (startingPrevTStop != null)
				{
					try
					{
						int id = startingPrevTStop.getLocationID();
						if (id != 0)
						{
							locObj = new Location(db, id);
							locObjOrig = locObj;
						}
					} catch (RDBKeyNotFoundException e) {}
				}
			}

			if (startingPrevTStop != null)
			{
				etLoc.setText(startingPrevTStop.readLocationText());
				etLoc.postDelayed(new Runnable()
				{
					public void run() { etLoc.dismissDropDown(); }
				}, 250);
			}

			// How recent was that vehicle's most recent trip? (Historical Mode)
			{
				boolean willAskHistorical;

				long currStartTime = startTime.getTimeInMillis();
				if (currStartTime != startTimeAtCreate)
					return;  // it's been changed by the user already

				long latestVehTime = 1000L * currV.readLatestTime(null);
				willAskHistorical = (latestVehTime != 0L)
					&& (Math.abs(latestVehTime - currStartTime) >= TIMEDIFF_HISTORICAL_MILLIS);

				if (! willAskHistorical)
				{
					// check for "recent historical" entry mode

					final SharedPreferences sp
						= PreferenceManager.getDefaultSharedPreferences
						    (getApplicationContext());
					final int v_id = currV.getID();

					if (sp.contains(PREF_PREV_HISTORICAL_TIME_CREATED)
					    && (v_id == sp.getInt(PREF_PREV_HISTORICAL_V_ID, -1)))
					{
						final long prevCreatedTime =
							sp.getLong(PREF_PREV_HISTORICAL_TIME_CREATED, 0L);
						willAskHistorical =
							(Math.abs(System.currentTimeMillis() - prevCreatedTime)
							 <= TIMEDIFF_HISTORICAL_RECENT_MILLIS);
					}
				}

				if (willAskHistorical)
				{
					askStartNowOrHistorical(latestVehTime);
				}
			}
		}
		prevDId = dID;

		updateStartDateButton();
	}

	/**
	 * Ask whether to start now, or at the stop time of this vehicle's previous trip
	 * ({@link #TIMEDIFF_HISTORICAL_MILLIS} ago or more: Historical Mode).
	 * Default to start now.
	 * @param latestVehTime Historical starting time, to set {@link #startTime} and {@link #tpStartTime} if chosen
	 * @since 0.9.20
	 */
	public void askStartNowOrHistorical(final long latestVehTime)
	{
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle(R.string.confirm);
		alert.setMessage(getResources().getString
			(R.string.set_time_now_or_historical,
			 android.text.format.DateFormat.getDateFormat
				(getApplicationContext()).format(new Date(latestVehTime))));
		alert.setNegativeButton(R.string.now, new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int whichButton)
			{
				final long now = System.currentTimeMillis();
				startTime.setTimeInMillis(now);
				startTimeAtCreate = now;
				tpStartTime.setCurrentHour(startTime.get(Calendar.HOUR_OF_DAY));
				tpStartTime.setCurrentMinute(startTime.get(Calendar.MINUTE));

				updateStartDateButton();
			}
		});
		alert.setPositiveButton(R.string.historical, new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int whichButton)
			{
				startTime.setTimeInMillis(latestVehTime);
				startTimeAtCreate = latestVehTime;
					// set equal, to allow further updates if veh changes again
				tpStartTime.setCurrentHour(startTime.get(Calendar.HOUR_OF_DAY));
				tpStartTime.setCurrentMinute(startTime.get(Calendar.MINUTE));

				updateStartDateButton();
			}
		});
		alert.setCancelable(true);

		alert.show();
	}

	/** Set the start-date button text based on {@link #startTime}'s value */
	private void updateStartDateButton()
	{
		if (fmt_dow_shortdate == null)
			fmt_dow_shortdate = Misc.buildDateFormatDOWShort(this, true);

		// update btn text to current startTime:
		btnStartDate.setText(DateFormat.format(fmt_dow_shortdate, startTime));
	}

	@Override
	public void onPause()
	{
		super.onPause();
		if (db != null)
			db.close();
	}

	// TODO javadoc
	@Override
	public void onResume()
	{
		super.onResume();

		if (! checkCurrentDriverVehicleSettings())
		{
			Toast.makeText(getApplicationContext(),
				getResources().getString(R.string.current_driver_veh_not_found),
					// "Current driver/vehicle not found in db"
				Toast.LENGTH_SHORT).show();

			startActivity(new Intent(TripBegin.this, AndroidStartup.class));
			finish();

			return;
		}

		// Give status: driver, vehicle, start-date;
		// Also determines locObj from startingPrevTStop.
		updateDriverVehTripTextAndButtons();

		if (isFrequent && (wantsFT == null))
		{
			// ask user to choose their frequent trip
			Intent i = new Intent(TripBegin.this, TripBeginChooseFreq.class);
			if (locObj != null)
			{
				locObjOrig = locObj;  // since using with freqtrip
				i.putExtra(VehSettings.PREV_LOCATION, locObj.getID());
			}
			startActivityForResult
			    (i, R.id.main_btn_begin_freqtrip);
			// when it returns, activity execution continues in onActivityResult().
		}
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		if (db != null)
			db.close();
	}

	/** Show or hide the roadtrip destination-area dropdown */
	public void onClick_BtnRoadtripDestiDropdown(View v)
	{
		if (etGeoArea == null)
			return;

		if (etGeoArea.isPopupShowing())
			etGeoArea.dismissDropDown();
		else
			etGeoArea.showDropDown();
	}

	/**
	 * Read fields, and record start of the trip in the database.
	 * Finish this Activity.
	 * If starting location field is blank, prompt for that and don't finish yet.
	 */
	public void onClick_BtnBeginTrip(View v)
	{
		final long now = System.currentTimeMillis();

		// Check the time first:
		startTime.set(Calendar.HOUR_OF_DAY, tpStartTime.getCurrentHour());
		startTime.set(Calendar.MINUTE, tpStartTime.getCurrentMinute());
		final int startTimeSec;

		// If start time hasn't been changed since onCreate,
		// then update it to the current time when button was clicked.
		{
			long startTimeMillis = startTime.getTimeInMillis();
			if (Math.abs(startTimeMillis - startTimeAtCreate) < 2000)
			{
				startTimeAtCreate = now;
				startTime.setTimeInMillis(startTimeAtCreate);
				startTimeSec = (int) (startTimeAtCreate / 1000L);
			} else {
				startTimeSec = (int) (startTime.getTimeInMillis() / 1000L);
			}
		}

		// Check for required starting-location:

		if (currA == null)
		{
			// unlikely to happen, but easy to check
			spGeoArea.requestFocus();
			Toast.makeText(getApplicationContext(),
				getResources().getString(R.string.vehicle_entry_geoarea_prompt),
				Toast.LENGTH_SHORT).show();
				// "Please enter the vehicle's starting geographic area."
			return;  // <--- Early return: geoarea somehow not chosen ---
		}

		String startloc = etLoc.getText().toString().trim();
		if (startloc.length() == 0)
		{
			etLoc.requestFocus();
			Toast.makeText(getApplicationContext(),
				getResources().getString(R.string.trip_tstart_loc_prompt),
				Toast.LENGTH_SHORT).show();
			return;  // <--- Early return: etLoc contents ---
		}

		if ((locObj != null) && ! locObj.getLocation().equalsIgnoreCase(startloc))
			locObj = null;  // locObj outdated: text doesn't match

		// check location vs frequent trip:
		if ((wantsFT != null) &&
			((locObj == null) || (locObj.getID() != wantsFT.getStart_locID())))
		{
			// Location mismatch vs freqtrip location
			if (locObjOrig == null)
			{
				wantsFT = null;  // shouldn't happen, it's here just in case
			} else {
				// Prompt user if wants to revert back to locObjOrig.
				AlertDialog.Builder alert = new AlertDialog.Builder(this);

				alert.setTitle(R.string.confirm);
				alert.setMessage(R.string.trip_tstart_loc_not_freq);
				alert.setPositiveButton(R.string.keep_this, new DialogInterface.OnClickListener()
				{
				  public void onClick(DialogInterface dialog, int whichButton)
				  {
					wantsFT = null;
				  }
				});
				alert.setNegativeButton(R.string.revert, new DialogInterface.OnClickListener()
				{
				  public void onClick(DialogInterface dialog, int whichButton)
				  {
					locObj = locObjOrig;
					etLoc.setText(locObj.getLocation());
				  }
				});

				alert.show();
				return;  // <-- after alert, user will hit Begin button again ---
			}
		}

		// Check starting odometer:

		int startOdo = odo.getCurrent10d();
		final int prevOdo = currV.getOdometerCurrent();
		if (startOdo != prevOdo)
		{
			// check if it's only the hidden tenths digit
			final int wholeOdo0 = startOdo / 10;
			if (wholeOdo0 == (prevOdo/10))
			{
				// visually the same; update hidden tenths to keep the db consistent
				startOdo = prevOdo;
				odo.setCurrent10d(startOdo, false);
			} else {
				// visually different

				if (startOdo < prevOdo)
				{
					odo.requestFocus();
					final String toastText = getResources().getString
						(R.string.trip_tstop_entry_totalodo_low, prevOdo / 10);  // %1$d
					Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show();
					return;  // <--- Early return: Odometer is too low ---
				}
				else if (startingPrevTStop != null)
				{
					// if odo increases, there's a gap in this vehicle's
					// history, so we need to not "continue from previous stop"
					// to keep the data consistent.
					startingPrevTStop = null;
				}
			}
		}

		// Check for trip category if required:
		final int tripCat = ((TripCategory) (spTripCat.getSelectedItem())).getID();
		if ((Settings.getBoolean(db, Settings.REQUIRE_TRIPCAT, false))
		    && (tripCat <= 0))
		{
			spTripCat.requestFocus();
			Toast.makeText(this, R.string.trip_tstart_categ_req, Toast.LENGTH_SHORT).show();
			return;  // <--- Early return: missing required ---
		}

		// Check other fields; if roadtrip, create destAreaObj in db if needed

		if (isRoadtrip)
		{
			String destarea = etGeoArea.getText().toString().trim();
			if (destarea.equalsIgnoreCase(currA.getName()))
			{
				etGeoArea.requestFocus();
				Toast.makeText(getApplicationContext(),
					getResources().getString(R.string.trip_tstart_geoarea_different),
					Toast.LENGTH_SHORT).show();
				return;  // <--- Early return: same src,dest geoarea ---
			}

			if ((destAreaObj == null) || ! destAreaObj.toString().equalsIgnoreCase(destarea))
			{
				if (destarea.length() == 0)
				{
					etGeoArea.requestFocus();
					Toast.makeText(getApplicationContext(),
						getResources().getString(R.string.trip_tstart_geoarea_prompt),
						Toast.LENGTH_SHORT).show();
					return;  // <--- Early return: etArea empty ---
				}

				destAreaObj = GeoArea.getByName(db, destarea);  // try db lookup before creating new one
				if (destAreaObj == null)
				{
					destAreaObj = new GeoArea(destarea);
					destAreaObj.insert(db);
				}
			}
		}

		if (locObj == null)
			// search for existing; avoid creating 2 locations with same name
			locObj = Location.getByDescr(db, currA.getID(), startloc);

		if (locObj == null)
		{
			locObj = new Location(currA.getID(), null, null, startloc);
			locObj.insert(db);
		}

		// Are we in a different GeoArea from vehicle's previous recorded trip?
		// If so, there are probably unrecorded trips in between.
		final int currAID = currA.getID();
		{
			final GeoArea vCurrA = VehSettings.getCurrentArea(db, currV, false);
			if ((vCurrA == null) || (vCurrA.getID() != currAID))
			{
				VehSettings.setCurrentArea(db, currV, currA);
				startingPrevTStop = null;
			}
		}

		// can we use startingPrevTStop, or do we have a new starting location?
		if (startingPrevTStop != null)
		{
			final int locid = startingPrevTStop.getLocationID();
			if (locid != 0)
			{
				if (locid != locObj.getID())
					startingPrevTStop = null;
			}
			else if (! startloc.equalsIgnoreCase(startingPrevTStop.readLocationText()))
			{
				startingPrevTStop = null;
			}
		}

		Trip t = new Trip(currV, currD, startOdo, 0, currAID,
			startingPrevTStop, startTimeSec, 0,
			(String) null, (String) null, (String) null, (String) null,
			wantsFT, null,
			(isRoadtrip ? destAreaObj.getID() : 0),
			false);
		if (tripCat > 0)
			t.setTripCategoryID(tripCat);
		if (Settings.getBoolean(db, Settings.SHOW_TRIP_PAX, false))
		{
			String paxTxt = etPax.getText().toString().trim();
			if (paxTxt.length() > 0)
			{
				try
				{
					final int pax = Integer.parseInt(paxTxt);
					t.setPassengerCount(pax);
				} catch (NumberFormatException e) {
					// shouldn't occur: layout declaration has inputType=number
				}
			}
		}
		t.insert(db);

		// if wanted, set CURRENT_FREQTRIP and CURRENT_FREQTRIP_TSTOPLIST
		if (wantsFT != null)
			VehSettings.setCurrentFreqTrip(db, currV, wantsFT);

		// set CURRENT_TRIP, clear CURRENT_TSTOP, set PREV_LOCATION
		VehSettings.setCurrentTrip(db, currV, t);
		VehSettings.setCurrentTStop(db, currV, null);

		if (startingPrevTStop == null)
		{
			TStop ts = new TStop(t, startOdo, startTimeSec, locObj, null, null);
			ts.insert(db);
			t.addCommittedTStop(ts);
		}

		VehSettings.setPreviousLocation(db, currV, locObj);  // PREV_LOCATION

		// If this is a historical trip, note its time of creation (current time, not its start time)
		// so next TripBegin.onCreate can ask if should use Historical Mode; see class javadoc.
		{
			final SharedPreferences sp
				= PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			SharedPreferences.Editor spe = null;
			final long startTimeLong = startTimeSec * 1000L;
			final int v_id = currV.getID();

			if ((startTimeLong < now) && (now - startTimeLong > TripTStopEntry.TIMEDIFF_HISTORICAL_MILLIS))
			{
				// this trip is historical: set the prefs
				spe = sp.edit();
				spe.putInt(PREF_PREV_HISTORICAL_V_ID, v_id);
				spe.putLong(PREF_PREV_HISTORICAL_TIME_CREATED, now);
			}
			else if (sp.contains(PREF_PREV_HISTORICAL_TIME_CREATED))
			{
				// not historical: clear pref if same vehicle
				if (v_id == sp.getInt(PREF_PREV_HISTORICAL_V_ID, -1))
				{
					spe = sp.edit();
					spe.remove(PREF_PREV_HISTORICAL_TIME_CREATED);
				}
			}

			if (spe != null)
				spe.commit();
		}

		finish();
	}

	/**
	 * Show the {@link DatePickerDialog} when the date button is clicked.
	 * @see #onCreateDialog(int)
	 */
	public void onClick_BtnStartDate(View v)
	{
		showDialog(R.id.trip_begin_btn_start_date);
	}

	/**
	 * Callback for displaying {@link DatePickerDialog} after {@link #onClick_BtnStartDate(View)}.
	 * @see #onDateSet(DatePicker, int, int, int)
	 */
	@Override
	protected Dialog onCreateDialog(final int id)
	{
		return new DatePickerDialog
			(this, this,
			 startTime.get(Calendar.YEAR),
			 startTime.get(Calendar.MONTH),
			 startTime.get(Calendar.DAY_OF_MONTH));
	}

	/** Callback from {@link DatePickerDialog} for trip start-date. */
	public void onDateSet(DatePicker dp, final int year, final int month, final int monthday)
	{
		startTime.set(Calendar.YEAR, year);
		startTime.set(Calendar.MONTH, month);
		startTime.set(Calendar.DAY_OF_MONTH, monthday);
		updateStartDateButton();
	}

	public void onClick_BtnChangeDriverVehicle(View v)
	{
		startActivityForResult
		   (new Intent(TripBegin.this, ChangeDriverOrVehicle.class),
			R.id.main_btn_change_driver_vehicle);
	}

	/**
	 * Callback from {@link ChangeDriverOrVehicle} or {@link TripBeginChooseFreq}.
	 * @param requestCode  The activity request used with {@code startActivityForResult}
	 *   when creating the activity which led to the callback:
	 *   <UL>
	 *     <LI> {@link R.id#main_btn_begin_freqtrip}: A {@link FreqTrip} has been selected,
	 *       set {@link #isFrequent} and other activity fields for a new trip based on that FreqTrip.
	 *       {@code idata} contains int {@code _id} field with the FreqTrip ID.
	 *     <LI> {@link R.id#main_btn_change_driver_vehicle}: The current driver or vehicle
	 *       has been changed; update activity fields to that driver, vehicle, and location.
	 *   </UL>
	 * @param resultCode  Result from activity; will do nothing if {@link Activity#RESULT_CANCELED}.
	 * @param idata  Intent from activity, possibly with extra fields set
	 */
	@Override
	public void onActivityResult(final int requestCode, final int resultCode, Intent idata)
	{
		if (requestCode == R.id.main_btn_begin_freqtrip)
		{
			if ((resultCode == RESULT_CANCELED) || (idata == null))
			{
				finish();  // No data, can't begin a frequent trip
				return;
			}
			try {
				wantsFT = new FreqTrip(db, idata.getIntExtra("_id", 0));
			} catch (Throwable e) {
				// RDBKeyNotFoundException should occur only if it's 0
				finish();
				return;
			}

			isRoadtrip = (wantsFT.getEnd_aID_roadtrip() != 0);

			final int ftCat = wantsFT.getTripCategoryID();
			if (ftCat != 0)
			{
				for (int i = spTripCat.getCount() - 1; i >= 0; --i)
				{
					if (ftCat == ((TripCategory) (spTripCat.getItemAtPosition(i))).getID())
					{
						spTripCat.setSelection(i, true);
						break;
					}
				}

			}

			if (isRoadtrip)
			{
				// un-hide roadtrip items (activity starts as local trip)
				View rtr_row = findViewById(R.id.trip_begin_roadtrip_desti_row);
				rtr_row.setVisibility(View.VISIBLE);

				// set destAreaObj and text field
				if (etGeoArea == null)
					etGeoArea = (AutoCompleteTextView) findViewById(R.id.trip_begin_roadtrip_desti);
				if (etGeoArea != null)
					updateETGeoArea(wantsFT.getEnd_aID_roadtrip(), -1);
			}

			return;
		}

		if (resultCode == RESULT_CANCELED)
			return;

		if (requestCode == R.id.main_btn_change_driver_vehicle)
			updateDriverVehTripTextAndButtons();

	}

	/** For Location autocomplete, the callback for {@link OnItemClickListener} */
	public void onItemClick(AdapterView<?> parent, View clickedOn, int position, long rowID)
	{
		ListAdapter la = etLoc.getAdapter();
		if (la == null)
			return;
		locObj = (Location) la.getItem(position);
	}

	/** For GeoArea autocomplete, the callback for {@link OnItemClickListener} */
	private class GeoAreaOnItemClickListener implements OnItemClickListener
	{
		/** For GeoArea autocomplete, the callback for {@link OnItemClickListener} */
		public void onItemClick(AdapterView<?> parent, View clickedOn, int position, long rowID)
		{
			ListAdapter la = etGeoArea.getAdapter();
			if (la == null)
				return;
			destAreaObj = (GeoArea) la.getItem(position);
		}
	}

}
