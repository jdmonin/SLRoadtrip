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

import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Spinner;
import android.widget.Toast;

/**
 * Change the current driver or the current vehicle
 * ({@link Settings#CURRENT_DRIVER}, {@link Settings#CURRENT_VEHICLE}.
 * If the vehicle changes, also clear {@link Settings#PREV_LOCATION}.
 *<P>
 * Will call {@link #setResult(int) setResult}<tt>(RESULT_OK)</tt> if changed,
 *    <tt>setResult(RESULT_CANCELED)</tt> otherwise.
 *<P>
 * The vehicle can't be changed during a Trip.
 *
 * @author jdmonin
 */
public class ChangeDriverOrVehicle extends Activity
{
	private RDBAdapter db = null;
	private Spinner driver, veh;
	private int currDID = 0, currVID = 0;
	private boolean hasCurrentTrip;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.change_vehicle_or_driver);

	    hasCurrentTrip = false;

		db = new RDBOpenHelper(this);
		currDID = Settings.getCurrentDriver(db, false).getID();
		currVID = Settings.getCurrentVehicle(db, false).getID();
		hasCurrentTrip = (null != Settings.getCurrentTrip(db, false));

		driver = (Spinner) findViewById(R.id.change_cvd_driver);
	    veh = (Spinner) findViewById(R.id.change_cvd_vehicle);
	    SpinnerDataFactory.setupDriversSpinner(db, this, driver, currDID);

	    if (hasCurrentTrip)
	    {
	    	veh.setVisibility(View.GONE);
	    	findViewById(R.id.change_cvd_vehicle_label).setVisibility(View.GONE);
	    	findViewById(R.id.change_cvd_vehicle_new).setVisibility(View.GONE);
	    } else {
		    SpinnerDataFactory.setupVehiclesSpinner(db, this, veh, currVID);
	    }
	}

    public void onClick_BtnOK(View v)
    {
    	boolean anyChange = false;

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

    	Vehicle ve = (Vehicle) veh.getSelectedItem();
    	if ((ve != null) && ! hasCurrentTrip)
    	{
    		final int newID = ve.getID();
    		if (newID != currVID)
    		{
    			anyChange = true;
    			Settings.setCurrentVehicle(db, ve);
    			Settings.setPreviousLocation(db, null);
    		}
    	}

    	setResult(anyChange ? RESULT_OK : RESULT_CANCELED);
    	finish();
    }

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
    	Toast.makeText(this, "Stub: Edit drivers", Toast.LENGTH_SHORT).show();
    	// TODO.
    	// Intent i = new Intent(this, DriversEdit.class);
    	// startActivityForResult(i, R.id.change_cvd_drivers_edit);
    	// TODO update onActivityResult
    }

    public void onClick_BtnVehiclesEdit(View v)
    {
    	Toast.makeText(this, "Stub: Edit vehicles", Toast.LENGTH_SHORT).show();
    	// TODO.
    	// Intent i = new Intent(this, VehiclesEdit.class);
    	// startActivityForResult(i, R.id.change_cvd_vehicles_edit);
    	// TODO update onActivityResult
    }

    /**
	 * Callback from {@link DriverEntry} or {@link VehicleEntry}.
	 * @param idata  intent containing extra int "_id" with the
	 *     ID of the newly added driver or vehicle
	 */
	@Override
	public void onActivityResult(final int requestCode, final int resultCode, Intent idata)
	{
		if (resultCode == RESULT_CANCELED)
			return;

		switch (requestCode)
		{
		case R.id.change_cvd_driver_new:
			spinnerAddNewItem(true, driver, idata);  break;

		case R.id.change_cvd_vehicle_new:
			spinnerAddNewItem(false, veh, idata);    break;

		case R.id.change_cvd_drivers_edit:
			// TODO requery all-drivers spinner;     break;

		case R.id.change_cvd_vehicles_edit:
			// TODO requery all-vehicles spinner;    break;
		}
	}

	/** update the Driver or Vehicle spinner contents, via intent extra "_id" */
    private void spinnerAddNewItem(final boolean isDriver, Spinner sp, Intent idata)
    {
    	final int newID = idata.getIntExtra("_id", 0);
    	if (newID == 0)
    		return;
		try
		{
	    	if (isDriver)
	    	{
				currDID = Settings.getCurrentDriver(db, false).getID();
				SpinnerDataFactory.setupDriversSpinner(db, this, driver, currDID);
			} else {
				currVID = Settings.getCurrentVehicle(db, false).getID();
				SpinnerDataFactory.setupVehiclesSpinner(db, this, veh, currVID);
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
