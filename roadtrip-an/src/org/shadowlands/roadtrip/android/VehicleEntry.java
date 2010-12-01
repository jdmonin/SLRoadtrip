/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  Copyright (C) 2010 Jeremy D Monin <jdmonin@nand.net>
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

import java.util.Vector;

import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.VehicleMake;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

/**
 * Enter the new vehicle.
 * TODO allow edit too
 *<P>
 * <b>When {@link #EXTRAS_FLAG_ASKED_NEW} is not used:</b><BR>
 * will next go to Main.
 *<P>
 * <b>When {@link #EXTRAS_FLAG_ASKED_NEW} is set:</b><BR>
 * Wait for the new vehicle to be entered.
 * Finish this activity and return to what the user was previously doing.
 * The Result code will be set to RESULT_OK, and the Intent will get
 * an int extra called "_id" with the ID of the newly added vehicle.
 */
public class VehicleEntry extends Activity
{
	/** Flag to show we already have a driver entered,
	 *  but the user asked to enter a new driver;
	 *  for {@link Intent#putExtra(String, boolean)}.
	 */
	public static final String EXTRAS_FLAG_ASKED_NEW = "new";

	private static VehicleMake[] VEHICLEMAKES = null;

	/**
	 * If true, {@link #EXTRAS_FLAG_ASKED_NEW} was set.
	 */
	private boolean cameFromAskNew;

	private RDBAdapter db = null;

	private EditText nickname, vmodel, vin, comment, year;
	private Spinner driver, vmake;
	private OdometerNumberPicker odo_orig, odo_curr;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
        setContentView(R.layout.vehicle_entry);
 
		Intent i = getIntent();
		if (i != null)
		{
			cameFromAskNew = i.getBooleanExtra(EXTRAS_FLAG_ASKED_NEW, false);
		} else {
	        cameFromAskNew = false;
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

	    db = new RDBOpenHelper(this);
	    populateVehMakesList();
	    if (VEHICLEMAKES != null)
	    {
	    	ArrayAdapter<VehicleMake> vaa = new ArrayAdapter<VehicleMake>(this, android.R.layout.simple_spinner_item, VEHICLEMAKES);
	    	vaa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    	vmake.setAdapter(vaa);
	    }

	    SpinnerDataFactory.setupDriversSpinner(db, this, driver, -1);
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

	public void onClick_BtnOK(View v)
	{
		// TODO validate non-blank veh fields
		// TODO blank -> null, not 0-length
		// TODO look for null getSelectedItem in spinners

		Vehicle nv = new Vehicle
		  (nickname.getText().toString(),
		   (Person) driver.getSelectedItem(), ((VehicleMake) vmake.getSelectedItem()).getID(),
		   vmodel.getText().toString(),
		   Integer.parseInt(year.getText().toString()),
		   0, 0, vin.getText().toString(),
		   odo_orig.getCurrent10d(), odo_curr.getCurrent10d(),
		   comment.getText().toString());
		nv.insert(db);

    	if (! Settings.exists(db, Settings.CURRENT_VEHICLE))  // TODO also popup to ask user, if no curr_trip
    	{
    		Settings.setCurrentVehicle(db, nv);
    		Settings.setPreviousLocation(db, null);
    	}

    	if (! cameFromAskNew)
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
