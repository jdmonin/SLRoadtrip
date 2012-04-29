/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  Copyright (C) 2010-2012 Jeremy D Monin <jdmonin@nand.net>
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
import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.Settings;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

/**
 * Enter a new vehicle, or edit a vehicle.
 * Read-only if current trip.
 *<P>
 * <b>When no intent extras are used:</b><BR>
 * will next go to Main.
 *<P>
 * <b>When {@link #EXTRAS_FLAG_ASKED_NEW} is set:</b><BR>
 * Wait for the new vehicle to be entered.
 * Finish this activity and return to what the user was previously doing.
 * The Result code will be set to RESULT_OK, and the Intent will get
 * an int extra called "_id" with the ID of the newly added vehicle.
 *<P>
 * <b>When {@link #EXTRAS_INT_EDIT_ID} is set:</b><BR>
 * Edit the specified vehicle.
 * Finish this activity and return to what the user was previously doing.
 * The Result code will be set to RESULT_OK, and the Intent will get
 * an int extra called "_id" with the ID of the edited vehicle.
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
	 * Are we on a trip? Is the {@link Settings#getCurrentTrip(RDBAdapter, boolean)} != null?
	 */
	private boolean hasCurrentTrip;

	/**
	 * If not null, {@link #EXTRAS_INT_EDIT_ID} was set to this vehicle's ID,
	 * it was in the database, and we're editing that vehicle.
	 */
	private Vehicle cameFromEdit_veh;

	private RDBAdapter db = null;

	private EditText nickname, vmodel, vin, comment, year;
	private Spinner driver, vmake;
	private OdometerNumberPicker odo_orig, odo_curr;

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
	    comment = (EditText) findViewById(R.id.vehicle_entry_comment);
	    year = (EditText) findViewById(R.id.vehicle_entry_year);
	    driver = (Spinner) findViewById(R.id.vehicle_entry_driver);
	    vmake = (Spinner) findViewById(R.id.vehicle_entry_vmake);
	    odo_orig = (OdometerNumberPicker) findViewById(R.id.vehicle_entry_odo_orig);
	    odo_curr = (OdometerNumberPicker) findViewById(R.id.vehicle_entry_odo_curr);
	    odo_orig.setTenthsVisibility(false);
	    odo_curr.setTenthsVisibility(false);
	    btnDateFrom = (Button) findViewById(R.id.vehicle_entry_btn_date_from);

	    db = new RDBOpenHelper(this);

		hasCurrentTrip = (null != Settings.getCurrentTrip(db, false));
		if (hasCurrentTrip)
		{
			setTitle(R.string.view_vehicles);
			// most fields are made read-only in updateScreenFieldsFromVehicle().
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
	    }

	    int currentDriverID = -1;
	    if (cameFromAskNew)
	    {
	    	Person dr = Settings.getCurrentDriver(db, false);
	    	if (dr != null)
	    		currentDriverID = dr.getID();
	    }
	    else if (cameFromEdit_veh != null)
	    {
	    	currentDriverID = cameFromEdit_veh.getDriverID();
	    }
	    SpinnerDataFactory.setupDriversSpinner
	    	(db, this, driver, currentDriverID);
	    if (hasCurrentTrip)
	    	driver.setEnabled(false);
	}

	/**
	 * During onCreate, show this vehicle's fields, except the Driver spinner.
	 * Spinners are set in {@link #onCreate(Bundle)}.
	 * @param veh  Always {@link #cameFromEdit_veh}
	 */
	private void updateScreenFieldsFromVehicle()
	{
		final Vehicle veh = cameFromEdit_veh;
		
		nickname.setText(veh.getNickname());
		// driver is already set
		vmodel.setText(veh.getModel());
		year.setText(Integer.toString(veh.getYear()));
		vin.setText(veh.getVin());
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
			odo_curr.setEnabled(false);
			comment.setEnabled(false);
			btnDateFrom.setEnabled(false);
			vmake.setEnabled(false);
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
		// TODO on edit: check against trips' minimum date

		Vehicle nv;
		if (cameFromEdit_veh == null)
		{
			nv = new Vehicle
			  (nickname.getText().toString(),
			   (Person) driver.getSelectedItem(), ((VehicleMake) vmake.getSelectedItem()).getID(),
			   vmodel.getText().toString(),
			   yr,
			   datefrom_int, 0, vin.getText().toString(),
			   odo_orig.getCurrent10d(), odo_curr.getCurrent10d(),
			   comment.getText().toString());
			nv.insert(db);
		} else {
			nv = cameFromEdit_veh;
			nv.setNickname(nickname.getText().toString());
			nv.setDriverID((Person) driver.getSelectedItem());
			nv.setMakeID(((VehicleMake) vmake.getSelectedItem()).getID());
			nv.setModel(vmodel.getText().toString());
			nv.setYear(yr);
			nv.setDate_from(datefrom_int);
			nv.setVin(vin.getText().toString());
			nv.setOdometerCurrent(odo_curr.getCurrent10d());
			nv.setComment(comment.getText().toString());
			nv.commit();
		}

    	if (! Settings.exists(db, Settings.CURRENT_VEHICLE))
    	{
    		Settings.setCurrentVehicle(db, nv);
    		Settings.setPreviousLocation(db, null);
    	}
		// TODO also popup to ask user whether to change setting to the new one, if no curr_trip

    	if ((cameFromEdit_veh == null) && ! cameFromAskNew)
		{
	    	startActivity(new Intent(VehicleEntry.this, Main.class));
		} else {
    		Intent i = getIntent();
	    	i.putExtra("_id", nv.getID());
	    	setResult(RESULT_OK, i);
		}
    	finish();
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

}
