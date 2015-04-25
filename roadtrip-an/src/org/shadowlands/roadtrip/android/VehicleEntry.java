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
import java.util.Vector;

import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.android.util.Misc;
import org.shadowlands.roadtrip.db.GeoArea;
import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.VehSettings;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.VehicleMake;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.Dialog;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Enter a new vehicle, or edit a vehicle.
 * Read-only if current trip.
 *<P>
 * <b>When no intent extras are used:</b><BR>
 * will next go to Main.
 *<P>
 * If no vehicles in db, new vehicle entered here must be active:
 * The cbActive checkbox will be made checked and hidden.
 *<P>
 * <b>When {@link #EXTRAS_FLAG_ASKED_NEW} is set:</b><BR>
 * Wait for the new vehicle to be entered.
 * Finish this activity and return to what the user was previously doing.
 * The Result code will be set to {@link ChangeDriverOrVehicle#RESULT_ADDED_NEW}, and the Intent will get
 * an int extra called "_id" with the ID of the newly added vehicle.
 *<P>
 * <b>When {@link #EXTRAS_INT_EDIT_ID} is set:</b><BR>
 * Edit the specified vehicle.
 * Finish this activity and return to what the user was previously doing.
 * The Result code will be set to RESULT_OK, and the Intent will get
 * an int extra called "_id" with the ID of the edited vehicle.
 *<P>
 * The "OK" button to finish this Activity is handled in {@link #onClick_BtnOK(View)}.
 */
public class VehicleEntry
	extends Activity implements OnDateSetListener
{
	/** Flag to show we already have a driver entered,
	 *  but the user asked to enter a new driver;
	 *  for {@link Intent#putExtra(String, boolean)}.
	 */
	public static final String EXTRAS_FLAG_ASKED_NEW = "new";

	/**
	 * Int extra for a vehicle ID to edit here;
	 * for {@link Intent#putExtra(String, int)}.
	 */
	public static final String EXTRAS_INT_EDIT_ID = "edit";

	private static VehicleMake[] VEHICLEMAKES = null;

	/**
	 * If true, {@link #EXTRAS_FLAG_ASKED_NEW} was set.
	 */
	private boolean cameFromAskNew;

	/**
	 * Are we on a trip? Is {@link VehSettings#getCurrentTrip(RDBAdapter, Vehicle, boolean)} != null?
	 */
	private boolean hasCurrentTrip;

	/**
	 * If not null, {@link #EXTRAS_INT_EDIT_ID} was set to this vehicle's ID,
	 * it was in the database, and we're editing that vehicle.
	 * @see #isCurrentV
	 */
	private Vehicle cameFromEdit_veh;

	/**
	 * New vehicle's starting geoarea chosen in {@link #etGeoArea}.
	 * Initially set using {@link #findCurrentGeoArea()}.
	 * @since 0.9.41
	 */
	private GeoArea geoAreaObj;

	/** For new vehicle, initial value of {@link #geoAreaObj} at start of activity */
	private GeoArea geoAreaObj_orig;

	/**
	 * Is this vehicle {@link Settings#getCurrentVehicle(RDBAdapter, boolean)}?
	 * @since 0.9.20
	 */
	private boolean isCurrentV;

	private RDBAdapter db = null;

	private EditText nickname, vmodel, vin, plate, comment, year;
	private Spinner driver, vmake;
	private OdometerNumberPicker odo_orig, odo_curr;
	/** New vehicle's starting geoarea, or null. Clicking here sets {@link #geoAreaObj}. */
	private AutoCompleteTextView etGeoArea;
	private CheckBox cbActive;

	/** Button to set or change {@link #dateFrom} */
	private Button btnDateFrom;

	/** Vehicle's "in use since" date_from if any, or null; set with button {@link #btnDateFrom} */
	private Calendar dateFrom;

	/**
	 * Date formatter for use with
	 * {@link DateFormat#format(CharSequence, Calendar)} by {@link #btnDateFrom}.
	 * initialized once in {@link #updateDateButton()}.
	 */
	private StringBuffer fmt_dow_shortdate;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
        setContentView(R.layout.vehicle_entry);
 
        int cameFromEdit_id;
		Intent i = getIntent();
		if (i != null)
		{
			cameFromAskNew = i.getBooleanExtra(EXTRAS_FLAG_ASKED_NEW, false);
			cameFromEdit_id = i.getIntExtra(EXTRAS_INT_EDIT_ID, 0);
		} else {
	        cameFromAskNew = false;
	        cameFromEdit_id = 0;
		}

		nickname = (EditText) findViewById(R.id.vehicle_entry_name);
	    vmodel = (EditText) findViewById(R.id.vehicle_entry_model);
	    vin = (EditText) findViewById(R.id.vehicle_entry_vin);
	    plate = (EditText) findViewById(R.id.vehicle_entry_plate);
	    comment = (EditText) findViewById(R.id.vehicle_entry_comment);
	    year = (EditText) findViewById(R.id.vehicle_entry_year);
	    driver = (Spinner) findViewById(R.id.vehicle_entry_driver);
	    vmake = (Spinner) findViewById(R.id.vehicle_entry_vmake);
	    odo_orig = (OdometerNumberPicker) findViewById(R.id.vehicle_entry_odo_orig);
	    odo_curr = (OdometerNumberPicker) findViewById(R.id.vehicle_entry_odo_curr);
	    odo_orig.setTenthsVisibility(false);
	    odo_curr.setTenthsVisibility(false);
	    if (cameFromEdit_id == 0)
	    	odo_orig.setRelatedUncheckedOdoOnChanges(odo_curr, null);
	    btnDateFrom = (Button) findViewById(R.id.vehicle_entry_btn_date_from);
	    cbActive = (CheckBox) findViewById(R.id.vehicle_entry_active_cb);

	    db = new RDBOpenHelper(this);

		final Vehicle currV = Settings.getCurrentVehicle(db, false);
		if (currV != null)
		{
			hasCurrentTrip = (null != VehSettings.getCurrentTrip(db, currV, false));
			if (hasCurrentTrip)
			{
				setTitle(R.string.view_vehicles);
				// most fields will be made read-only in updateScreenFieldsFromVehicle().
			}
		} else {
			hasCurrentTrip = false;
		}

		if ((cameFromEdit_id == 0) && (Vehicle.getMostRecent(db) == null))
		{
			// initial setup
			cbActive.setChecked(true);
			cbActive.setVisibility(View.GONE);
			findViewById(R.id.vehicle_entry_active_txt).setVisibility(View.GONE);
		}

	    populateVehMakesList();
	    if (VEHICLEMAKES != null)
	    {
	    	ArrayAdapter<VehicleMake> vaa = new ArrayAdapter<VehicleMake>(this, android.R.layout.simple_spinner_item, VEHICLEMAKES);
	    	vaa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    	vmake.setAdapter(vaa);
	    }

        cameFromEdit_veh = null;
	    if (cameFromEdit_id != 0)
	    {
	    	try {
				cameFromEdit_veh = new Vehicle(db, cameFromEdit_id);
				updateScreenFieldsFromVehicle();
			} catch (Throwable e) {
				// should not happen
				Toast.makeText(this, R.string.not_found, Toast.LENGTH_SHORT).show();
				setResult(Activity.RESULT_CANCELED);
				finish();
				return;   // <--- Early return: Could not load from db to view fields ---
			}
	    } else {
	    	cbActive.setChecked(true);
	    }

	    int currentDriverID = -1;
	    if (cameFromAskNew)
	    {
		Vehicle v = Settings.getCurrentVehicle(db, false);
	    	Person dr = (v != null) ? VehSettings.getCurrentDriver(db, v, false) : null;
	    	if (dr != null)
	    		currentDriverID = dr.getID();

		View vv = findViewById(R.id.vehicle_entry_geoarea_text);  // not editing: hide GeoArea display field
		if (vv != null)
			vv.setVisibility(View.GONE);

		etGeoArea = (AutoCompleteTextView) findViewById(R.id.vehicle_entry_geoarea);
		GeoArea[] areas = GeoArea.getAll(db, -1);
		if (areas != null)
		{
			ArrayAdapter<GeoArea> adapter = new ArrayAdapter<GeoArea>(this, R.layout.list_item, areas);
			etGeoArea.setAdapter(adapter);
			geoAreaObj = findCurrentGeoArea();
			if (geoAreaObj != null)
			{
				geoAreaObj_orig = geoAreaObj;
				etGeoArea.setText(geoAreaObj.toString());
			}
			etGeoArea.setOnItemClickListener(new GeoAreaOnItemClickListener());
		} else {
			etGeoArea.setAdapter((ArrayAdapter<GeoArea>) null);
		}
	    } else {
		// editing an existing vehicle

		View v = findViewById(R.id.vehicle_entry_geoarea);  // not new: hide GeoArea text field
		if (v != null)
			v.setVisibility(View.GONE);
		v = findViewById(R.id.vehicle_entry_geoarea_arrow);
		if (v != null)
			v.setVisibility(View.GONE);

		if (cameFromEdit_veh != null)
		{
			currentDriverID = cameFromEdit_veh.getDriverID();

			TextView tvG = (TextView) findViewById(R.id.vehicle_entry_geoarea_text);
			if (tvG != null)
			{
				GeoArea geoa = VehSettings.getCurrentArea(db, cameFromEdit_veh, false);
				if (geoa != null)
					tvG.setText(geoa.getName());
			}
		}
	    }

	    SpinnerDataFactory.setupDriversSpinner
	    	(db, this, driver, currentDriverID);
	    if (hasCurrentTrip)
	    	driver.setEnabled(false);
	}

	/**
	 * During onCreate, show this vehicle's fields, except the Driver spinner.
	 * {@link #cameFromEdit_veh} must not be null. Also sets {@link #isCurrentV}.
	 * Spinners are set in {@link #onCreate(Bundle)}.
	 */
	private void updateScreenFieldsFromVehicle()
	{
		final Vehicle veh = cameFromEdit_veh;

		isCurrentV = (veh.getID() == Settings.getInt(db, Settings.CURRENT_VEHICLE, 0));

		nickname.setText(veh.getNickname());
		// driver is already set
		vmodel.setText(veh.getModel());
		year.setText(Integer.toString(veh.getYear()));
		vin.setText(veh.getVin());
		plate.setText(veh.getPlate());
		odo_orig.setCurrent10d(veh.getOdometerOriginal(), true);
		odo_orig.setEnabled(false);
		odo_curr.setCurrent10d(veh.getOdometerCurrent(), true);
		comment.setText(veh.getComment());

		if (veh.getDate_from() != 0)
		{
			if (dateFrom == null)
				dateFrom = Calendar.getInstance();
			dateFrom.setTimeInMillis(1000L * veh.getDate_from());
			updateDateButton();
		}

		// set vmake spinner:
		final int id = veh.getMakeID();
		for (int i = VEHICLEMAKES.length - 1; i >= 0; --i)
		{
			if (id == VEHICLEMAKES[i].getID())
			{
				vmake.setSelection(i, true);
				break;
			}
		}

		if (hasCurrentTrip)
		{
			nickname.setEnabled(false);
			vmodel.setEnabled(false);
			year.setEnabled(false);
			vin.setEnabled(false);
			plate.setEnabled(false);
			odo_curr.setEnabled(false);
			comment.setEnabled(false);
			btnDateFrom.setEnabled(false);
			vmake.setEnabled(false);
			cbActive.setEnabled(false);
		}

    	cbActive.setChecked(veh.isActive());
    	if (isCurrentV)
    	{
    		// Current Vehicle must stay active
    		cbActive.setText(R.string.current_vehicle);
    		cbActive.setEnabled(false);
    	}
	}

	/** Update the date shown on {@link #btnDateFrom} from {@link #dateFrom} */
	private void updateDateButton()
	{
		if (dateFrom == null)
			return;
		if (fmt_dow_shortdate == null)
			fmt_dow_shortdate = Misc.buildDateFormatDOWShort(this, true);

		// update btn date text:
		btnDateFrom.setText(DateFormat.format(fmt_dow_shortdate, dateFrom));
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

	/** Show or hide the {@link #etGeoArea} geographic-area dropdown */
	public void onClick_BtnGeoAreaDropdown(View v)
	{
		if (etGeoArea == null)
			return;

		if (etGeoArea.isPopupShowing())
		{
			etGeoArea.dismissDropDown();

			// if text field is empty, fill in from initial value 
			if ((geoAreaObj_orig != null) && (etGeoArea.getText().toString().trim().length() == 0))
			{
				geoAreaObj = geoAreaObj_orig;
				etGeoArea.setText(geoAreaObj_orig.toString());
			}

		} else {
			// we'll need to clear the text first, to show all values in the dropdown
			etGeoArea.setText("");
			etGeoArea.post(new Runnable() {
				public void run() {
					etGeoArea.showDropDown();
				}
			});
		}
	}

	/**
	 * Show the {@link DatePickerDialog} when the in-use-since-date button is clicked.
	 * @see #onCreateDialog(int)
	 */
	public void onClick_BtnDateFrom(View v)
	{
		showDialog(R.id.vehicle_entry_btn_date_from);
	}

	/**
	 * Callback for displaying {@link DatePickerDialog} after {@link #onClick_BtnDateFrom(View)}.
	 * @see #onDateSet(DatePicker, int, int, int)
	 */
	@Override
	protected Dialog onCreateDialog(final int id)
	{
		// Assumes id == R.id.trip_tstop_btn_cont_date

		if (dateFrom == null)
			dateFrom = Calendar.getInstance();  // will be today's date

        return new DatePickerDialog
        	(this, this,
        	 dateFrom.get(Calendar.YEAR),
        	 dateFrom.get(Calendar.MONTH),
        	 dateFrom.get(Calendar.DAY_OF_MONTH));
	}

	/**
	 * Callback from {@link DatePickerDialog} for vehicle's "in use since" date.
	 * Updates {@link #dateFrom}, calls {@link #updateDateButton()}.
	 */
	public void onDateSet(DatePicker dp, final int year, final int month, final int monthday)
	{
		if (dateFrom == null)
			return;  // shouldn't happen

		dateFrom.set(Calendar.YEAR, year);
		dateFrom.set(Calendar.MONTH, month);
		dateFrom.set(Calendar.DAY_OF_MONTH, monthday);

		updateDateButton();
	}

	/**
	 * Validate fields, if good then update the database and finish this activity.
	 * See {@link VehicleEntry} class javadoc for details.
	 */
	public void onClick_BtnOK(View v)
	{
		// TODO validate non-blank veh fields
		// TODO blank -> null, not 0-length
		// TODO look for null getSelectedItem in spinners

		final int yr;
		try
		{
			yr = Integer.parseInt(year.getText().toString());
		}
		catch (NumberFormatException e)
		{
			year.requestFocus();
			Toast.makeText(this, R.string.vehicle_entry_year, Toast.LENGTH_SHORT).show();
			return;
		}

		if (odo_curr.getCurrent10d() < odo_orig.getCurrent10d())
		{
			odo_curr.requestFocus();			
			Toast.makeText(this, R.string.vehicle_entry_odo_curr_low, Toast.LENGTH_SHORT).show();
			return;
		}

		int datefrom_int;
		if (dateFrom == null)
			datefrom_int = 0;
		else
			datefrom_int = (int) (dateFrom.getTimeInMillis() / 1000L);
		// TODO on edit: check against first trip's minimum date

		String plateText = plate.getText().toString().trim();
		if (plateText.length() == 0)
			plateText = null;

		if (cameFromAskNew && (etGeoArea != null))
		{
			String geoarea = etGeoArea.getText().toString().trim();
			if ((geoAreaObj == null) || ! geoAreaObj.toString().equalsIgnoreCase(geoarea))
			{
				if (geoarea.length() == 0)
				{
					etGeoArea.requestFocus();
					Toast.makeText(getApplicationContext(),
					    getResources().getString(R.string.vehicle_entry_geoarea_prompt),
					    Toast.LENGTH_SHORT).show();
					return;  // <--- Early return: getGeoArea empty ---
				}

				geoAreaObj = new GeoArea(geoarea);
				geoAreaObj.insert(db);
			}
		}

		final Person nvDriv = (Person) driver.getSelectedItem();
		Vehicle nv;
		if (cameFromEdit_veh == null)
		{
			nv = new Vehicle
			  (nickname.getText().toString(),
			   nvDriv, ((VehicleMake) vmake.getSelectedItem()).getID(),
			   vmodel.getText().toString(),
			   yr,
			   datefrom_int, 0, vin.getText().toString(), plateText,
			   odo_orig.getCurrent10d(), odo_curr.getCurrent10d(),
			   comment.getText().toString());
			nv.setActive(cbActive.isChecked());
			nv.insert(db);
		} else {
			nv = cameFromEdit_veh;
			nv.setNickname(nickname.getText().toString());
			nv.setDriverID(nvDriv);
			nv.setMakeID(((VehicleMake) vmake.getSelectedItem()).getID());
			nv.setModel(vmodel.getText().toString());
			nv.setYear(yr);
			nv.setDate_from(datefrom_int);
			nv.setVin(vin.getText().toString());
			nv.setPlate(plateText);
			nv.setOdometerCurrent(odo_curr.getCurrent10d());
			nv.setComment(comment.getText().toString());
			if (! isCurrentV)
				nv.setActive(cbActive.isChecked());
			nv.commit();
		}

		// Set new vehicle's CURRENT_AREA from geoarea dropdown or current vehicle's.
		if ((geoAreaObj != null) || ! VehSettings.exists(db, VehSettings.CURRENT_AREA, nv))
	    	{
			GeoArea currA = geoAreaObj;
			if (currA == null)
				currA = findCurrentGeoArea();

			if (currA != null)
				VehSettings.setCurrentArea(db, nv, currA);
	    	}

    	if (! Settings.exists(db, Settings.CURRENT_VEHICLE))
    	{
    		Settings.setCurrentVehicle(db, nv);
    		VehSettings.setPreviousLocation(db, nv, null);
    	}

    	if (! VehSettings.exists(db, VehSettings.CURRENT_DRIVER, nv))
    	{
    		VehSettings.setCurrentDriver(db, nv, nvDriv);
    	}

    	if ((cameFromEdit_veh == null) && ! cameFromAskNew)
		{
	    	startActivity(new Intent(VehicleEntry.this, Main.class));
		} else {
    		Intent i = getIntent();
	    	i.putExtra("_id", nv.getID());
	    	setResult(((cameFromAskNew) ? ChangeDriverOrVehicle.RESULT_ADDED_NEW : RESULT_OK), i);
		}

    	finish();
	}

	/**
	 * Find the {@code CURRENT_AREA} to use for a new vehicle.
	 * Check the current vehicle's GeoArea, or if none use the first geoarea in the db.
	 * @return A GeoArea if possible, or null
	 * @since 0.9.41
	 */
	private GeoArea findCurrentGeoArea()
	{
		GeoArea currA = null;

		Vehicle cv = Settings.getCurrentVehicle(db, false);
		if (cv != null)
			currA = VehSettings.getCurrentArea(db, cv, false);

		if (currA == null) {
			// No GeoArea setting, or no current vehicle: Probably initial setup
			GeoArea[] areas = GeoArea.getAll(db, -1);
			if (areas != null)
				currA = areas[0];
		}

		return currA;
	}

	private void populateVehMakesList()
	{
		if (VEHICLEMAKES != null)
			return;

    	try
    	{
    		Vector<VehicleMake> names = VehicleMake.getAll(db);
        	if (names != null)
        	{
        		final int L = names.size();
        		VEHICLEMAKES = new VehicleMake[L];
        		for (int i = 0; i < L; ++i)
        			VEHICLEMAKES[i] = names.elementAt(i);
        	}
    	}
    	catch (SQLiteException e)
    	{}
	}

	/**
	 * For GeoArea autocomplete, the callback for {@link OnItemClickListener}
	 * to set {@link VehicleEntry#geoAreaObj}.
	 * @since 0.9.41
	 */
	private class GeoAreaOnItemClickListener implements OnItemClickListener
	{
		/** For GeoArea autocomplete, the callback for {@link OnItemClickListener} */
		public void onItemClick(AdapterView<?> parent, View clickedOn, int position, long rowID)
		{
			ListAdapter la = etGeoArea.getAdapter();
			if (la == null)
				return;
			geoAreaObj = (GeoArea) la.getItem(position);
		}
	}

}
