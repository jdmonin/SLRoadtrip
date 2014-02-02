/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2014 Jeremy D Monin <jdmonin@nand.net>
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

import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.Trip;
import org.shadowlands.roadtrip.db.VehSettings;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;

/**
 * Change the current driver or the current vehicle
 * ({@link Settings#CURRENT_DRIVER}, {@link Settings#CURRENT_VEHICLE}.
 * If the vehicle changes, also clear {@link Settings#PREV_LOCATION}.
 *<P>
 * Will call {@link #setResult(int) setResult}<tt>(RESULT_OK)</tt> if changed,
 *    <tt>setResult(RESULT_CANCELED)</tt> otherwise.
 *<P>
 * The vehicle can't be changed during a Trip.
 * Changing the current vehicle will switch to a different current trip.
 * The driver can't be changed during a Trip yet.
 *<P>
 * If the current vehicle is changed in the dropdown, and then "Edit Vehicles"
 * is pressed, the dropdown's new current vehicle setting is saved immediately.
 * This lets the user change the current vehicle so that the previous one can be
 * marked inactive.
 *
 * @author jdmonin
 */
public class ChangeDriverOrVehicle extends Activity
{
	/**
	 * Activity result to indicate changes were made; used in callback from {@link VehiclesEdit} to here.
	 */
	public static final int RESULT_CHANGES_MADE = Activity.RESULT_FIRST_USER;

	/** Was the Current Vehicle or Current Driver setting changed? */
	boolean anyChange = false;

	private RDBAdapter db = null;
	private Spinner driver, veh;
	private int currDID = 0, currVID = 0;
	private Vehicle currV;
	private boolean hasCurrentTrip;
	private Trip currT;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.change_vehicle_or_driver);

	    hasCurrentTrip = false;

		db = new RDBOpenHelper(this);
		currDID = Settings.getCurrentDriver(db, false).getID();
		currV = Settings.getCurrentVehicle(db, false);
		currVID = currV.getID();
		currT = Settings.getCurrentTrip(db, false);
		hasCurrentTrip = (null != currT);

		driver = (Spinner) findViewById(R.id.change_cvd_driver);
		veh = (Spinner) findViewById(R.id.change_cvd_vehicle);
		SpinnerDataFactory.setupDriversSpinner(db, this, driver, currDID);

		if (hasCurrentTrip)
		{
			setTitle(R.string.view_drivers_vehicles);
			driver.setEnabled(false);
			findViewById(R.id.change_cvd_driver_new).setEnabled(false);
			((Button) findViewById(R.id.change_cvd_drivers_edit)).setText(R.string.view_drivers);
			((Button) findViewById(R.id.change_cvd_vehicles_edit)).setText(R.string.view_vehicles);
		}

		SpinnerDataFactory.setupVehiclesSpinner(db, true, this, veh, currVID);
	}

	/**
	 * Change current driver/vehicle, {@link #finish()} the activity.
	 * Set our result to {@link #RESULT_OK} if any current setting was changed,
	 * {@link #RESULT_CANCEL} otherwise (even if drivers/vehicles were edited).
	 * Also updates other settings from the new current vehicle if changed:
	 * Current GeoArea, Trip, TStop, etc.
	 */
	public void onClick_BtnChange(View v)
	{
    	if (! hasCurrentTrip)
    	{
	    	Person d = (Person) driver.getSelectedItem();
	    	if (d != null)
	    	{
	    		final int newID = d.getID();
	    		if (newID != currDID)
	    		{
	    			anyChange = true;
	    			Settings.setCurrentDriver(db, d);
	    		}
	    	}

	    	// eventually will allow change driver within trip, but not yet.
    	}

    	Vehicle ve = (Vehicle) veh.getSelectedItem();
    	if ((ve != null) && (ve.getID() != currVID))
    	{
    		anyChange = true;
    		VehSettings.changeCurrentVehicle(db, currV, ve);
    	}

    	setResult(anyChange ? RESULT_OK : RESULT_CANCELED);
    	finish();
    }

    /**
     * Don't change the current driver/vehicle, and {@link #finish()} the activity.
     * Result will be {@link #RESULT_CANCEL},even if drivers/vehicles were edited.
     */
    public void onClick_BtnCancel(View v)
    {
    	setResult(RESULT_CANCELED);
    	finish();
    }

    public void onClick_BtnDriverNew(View v)
    {
    	Intent i = new Intent(this, DriverEntry.class);
		i.putExtra(DriverEntry.EXTRAS_FLAG_ASKED_NEW, true);
		startActivityForResult(i, R.id.change_cvd_driver_new);
    }

    public void onClick_BtnVehicleNew(View v)
    {
    	Intent i = new Intent(this, VehicleEntry.class);
		i.putExtra(VehicleEntry.EXTRAS_FLAG_ASKED_NEW, true);
		startActivityForResult(i, R.id.change_cvd_vehicle_new);
    }

    public void onClick_BtnDriversEdit(View v)
    {
    	Intent i = new Intent(this, DriversEdit.class);
    	startActivityForResult(i, R.id.change_cvd_drivers_edit);
    }

    /**
     * The "Edit Vehicles" button was clicked. Before editing the list of vehicles,
     * update the current vehicle setting if the dropdown was changed.
     * Also updates other settings from the new current vehicle if changed:
     * Current GeoArea, Trip, TStop, etc.
     * @param v  ignored
     */
    public void onClick_BtnVehiclesEdit(View v)
    {
    	final Vehicle ve = (Vehicle) veh.getSelectedItem();
    	if ((ve != null) && (ve.getID() != currVID))
    	{
    		anyChange = true;
    		hasCurrentTrip = VehSettings.changeCurrentVehicle(db, currV, ve);
    		currV = ve;
    		currVID = ve.getID();
		currT = Settings.getCurrentTrip(db, false);
    	}
 
    	Intent i = new Intent(this, VehiclesEdit.class);
    	startActivityForResult(i, R.id.change_cvd_vehicles_edit);
    }

    /**
	 * Callback from {@link DriverEntry}, {@link VehicleEntry}, {@link DriversEdit} or {@link VehiclesEdit}.
	 *<P>
	 * If changes were made, also changes our "Cancel" button to "Done" to reduce confusion.
	 *
	 * @param idata  intent containing extra int "_id" with the
	 *     ID of the newly added driver or vehicle (for New only),
	 *     or with result code {@link #RESULT_CHANGES_MADE}
	 *     if any vehicle or driver was edited.
	 */
	@Override
	public void onActivityResult(final int requestCode, final int resultCode, Intent idata)
	{
		if (resultCode == RESULT_CANCELED)
			return;

		boolean changed = true;

		switch (requestCode)
		{
		case R.id.change_cvd_driver_new:
			spinnerAddNewItem_Ask(true, driver, idata);  break;

		case R.id.change_cvd_vehicle_new:
			spinnerAddNewItem_Ask(false, veh, idata);    break;

		case R.id.change_cvd_drivers_edit:
			if (resultCode == RESULT_CHANGES_MADE)
			{
				if (db == null)
					db = new RDBOpenHelper(this);
				SpinnerDataFactory.setupDriversSpinner(db, this, driver, currDID);
			} else {
				changed = false;
			}
			break;

		case R.id.change_cvd_vehicles_edit:
			if (resultCode == RESULT_CHANGES_MADE)
			{
				if (db == null)
					db = new RDBOpenHelper(this);
				SpinnerDataFactory.setupVehiclesSpinner(db, true, this, veh, currVID);
			} else {
				changed = false;
			}
			break;
		}

		if (changed)
		{
			Button b = (Button) findViewById(R.id.change_cvd_btn_cancel);
			b.setText(R.string.done);
		}
	}

	/**
	 * Ask whether to change the current driver/vehicle after adding a new one.
	 * Ask only if no current trip.
	 * When dialog is answered, will call {@link #spinnerAddNewItem(boolean, Spinner, boolean, int)} 
	 * to update the Driver or Vehicle spinner contents, including the current value.
	 * @param isDriver  True for driver, false for vehicle
	 * @param  sp  Spinner to update from <tt>idata</tt>'s data
	 */
    private void spinnerAddNewItem_Ask(final boolean isDriver, final Spinner sp, Intent idata)
    {
    	final int newID = idata.getIntExtra("_id", 0);
    	if (newID == 0)
    		return;
    	if (null != Settings.getCurrentTrip(db, false))
    		return;

    	final int toastMsg =
    		isDriver ? R.string.change_vehicle_driver_ask_chg_new_d
    				 : R.string.change_vehicle_driver_ask_chg_new_v;

    	AlertDialog.Builder alert = new AlertDialog.Builder(this);

    	alert.setTitle(R.string.change_vehicle_driver_ask_chg_title);
    	alert.setMessage(toastMsg);
    	alert.setPositiveButton(R.string.change, new DialogInterface.OnClickListener() {
	    	  public void onClick(DialogInterface dialog, int whichButton) {
		    	  spinnerAddNewItem(isDriver, sp, true, newID);
	    	  }
	    	});
    	alert.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
	    	  public void onClick(DialogInterface dialog, int whichButton) {
    			spinnerAddNewItem(isDriver, sp, false, newID);
	    	  }
	    	});
    	alert.show();
	}

	/**
	 * Update the Driver or Vehicle spinner contents after adding a new one.
	 * @param isDriver  True for driver, false for vehicle
	 * @param  sp  Spinner to update
	 * @param  doChange  True to change current, false to keep it
	 * @param newID  newly added item's ID
	 */
    private void spinnerAddNewItem(final boolean isDriver, final Spinner sp, final boolean doChange, final int newID)
    {
    	if (doChange)
    	{
    		try
    		{
	    		if (isDriver)
	    		{
	    			Settings.setCurrentDriver(db, new Person(db, newID));
	    		} else {
	    			Settings.setCurrentVehicle(db, new Vehicle(db, newID));	    			
	    		}
    		} catch (Throwable th) {}
    	}

    	try
		{
	    	if (isDriver)
	    	{
				currDID = Settings.getCurrentDriver(db, false).getID();
				SpinnerDataFactory.setupDriversSpinner(db, this, driver, currDID);
			} else {
				currVID = Settings.getCurrentVehicle(db, false).getID();
				SpinnerDataFactory.setupVehiclesSpinner(db, true, this, veh, currVID);
			}
		} catch (Throwable th) {
			return;
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
}
