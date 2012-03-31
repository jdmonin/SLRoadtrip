/*
 *  Vehicles Editor list.
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2012 Jeremy D Monin <jdmonin@nand.net>
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
import org.shadowlands.roadtrip.db.FreqTrip;
import org.shadowlands.roadtrip.db.GeoArea;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

/**
 * List of {@link Vehicle}s to edit.
 *<P>
 * Called from {@link ChangeDriverOrVehicle}
 * with {@link Activity#startActivityForResult(android.content.Intent, int)}.
 *<P>
 * If any changes were made to vehicles, will call <tt>{@link #setResult(int) setResult(}
 * {@link ChangeDriverOrVehicle#RESULT_CHANGES_MADE})</tt> before returning.
 * Otherwise, will call setResult({@link Activity#RESULT_OK}).
 *<P>
 * If somehow no vehicles are found in the database, will call setResult({@link Activity#RESULT_CANCELED}),
 * display a "not found" toast, and return immediately.
 *
 * @author jdmonin
 */
public class VehiclesEdit extends Activity
	implements OnItemClickListener
{
	/** tag for debug logging */
	private static final String TAG = "RTR.VehiclesEdit";

	private RDBAdapter db = null;

	/** available vehicles; {@link #veh} contents  */
	private ListView lvVeh;
	/** available vehicles; contents of {@link #lvVeh} */
	private Vehicle[] veh;

	/** Called when the activity is first created.
	 * Populate {@link #veh} and {@link #lvVeh}.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.vehicles_edit);

	    db = new RDBOpenHelper(this);

	    lvVeh = (ListView) findViewById(R.id.list);
	    lvVeh.setOnItemClickListener(this);

		if (! populateVehiclesList(db))
		{
			Toast.makeText(this, R.string.not_found, Toast.LENGTH_SHORT).show();
	    	setResult(RESULT_CANCELED);
			finish();
		} else {
			setResult(RESULT_OK);  // No vehicle changes made yet
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

	/**
	 * List the vehicles currently available.
	 * Fill {@link #veh}.  Call {@link #lvVeh}.{@link ListView#setAdapter(android.widget.ListAdapter)}.
	 * @return true if {@link Vehicle}s found, false otherwise
	 */
	private boolean populateVehiclesList(RDBAdapter db)
	{
		Vehicle[] allV = Vehicle.getAll(db);
		veh = allV;
		if (allV == null)
			return false;
		lvVeh.setAdapter(new ArrayAdapter<Vehicle>(this, R.layout.list_item, allV));
		return true;
	}

	/** 'New' button was clicked: activity for that */
	public void onClick_BtnNewVehicle(View v)
	{
    	Intent i = new Intent(this, VehicleEntry.class);
		i.putExtra(VehicleEntry.EXTRAS_FLAG_ASKED_NEW, true);
		startActivityForResult(i, R.id.vehicles_edit_new);		
	}

	/** When an item is selected in the list, edit its ID. */
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		if ((veh == null) || (position >= veh.length))
			return;  // unlikely, but just in case

		Vehicle v = veh[position];
    	Intent i = new Intent(this, VehicleEntry.class);
		i.putExtra(VehicleEntry.EXTRAS_INT_EDIT_ID, v.getID());
		startActivityForResult(i, R.id.list);
	}

	/** 'Done' button was clicked: finish this activity. */
	public void onClick_BtnDone(View v)
	{
		// setResult was already called.
    	finish();
	}

    /**
	 * Callback from {@link VehicleEntry}.
	 * If a vehicle was added or edited:
	 * Set our result to {@link ChangeDriverOrVehicle#RESULT_CHANGES_MADE}.
	 * Repopulate vehicles list.
	 * 
	 * @param idata  intent containing extra int "_id" with the
	 *     ID of the added or edited vehicle
	 */
	@Override
	public void onActivityResult(final int requestCode, final int resultCode, Intent idata)
	{
		if (resultCode == RESULT_CANCELED)
			return;
		if ((requestCode != R.id.vehicles_edit_new) && (requestCode != R.id.list))
			return;
		if ((idata == null) || (0 == idata.getIntExtra("_id", 0)))
			return;

		setResult(ChangeDriverOrVehicle.RESULT_CHANGES_MADE);
		if (db == null)
			db = new RDBOpenHelper(this);
		populateVehiclesList(db);		
	}

}
