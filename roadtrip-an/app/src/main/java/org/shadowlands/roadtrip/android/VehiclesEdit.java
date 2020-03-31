/*
 *  Vehicles Editor list.
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2012-2015,2020 Jeremy D Monin <jdmonin@nand.net>
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
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.VehSettings;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * List of {@link Vehicle}s to edit or view.
 * Read-only if current trip.
 *<P>
 * Called from {@link ChangeDriverOrVehicle}
 * with {@link Activity#startActivityForResult(android.content.Intent, int)}.
 *<P>
 * If any changes were made to vehicles, will call <tt>{@link #setResult(int) setResult(}
 * {@link ChangeDriverOrVehicle#RESULT_CHANGES_MADE})</tt>
 * or <tt>({@link ChangeDriverOrVehicle#RESULT_ADDED_NEW})</tt> before returning.
 * Otherwise, will call setResult({@link Activity#RESULT_OK}).
 *<P>
 * If somehow no vehicles are found in the database, will call setResult({@link Activity#RESULT_CANCELED}),
 * display a "not found" toast, and return immediately.
 *
 * @author jdmonin
 */
public class VehiclesEdit extends AppCompatActivity
	implements OnItemClickListener
{
	/** tag for debug logging */
	@SuppressWarnings("unused")
	private static final String TAG = "RTR.VehiclesEdit";

	/**
	 * Activity {@code requestCode} to show we've called {@link VehicleEntry} to add a vehicle.
	 * @since 0.9.92
	 */
	private static final int REQUEST_VEHICLE_ADD = 1;

	/**
	 * Activity {@code requestCode} to show we've called {@link VehicleEntry} to edit a vehicle.
	 * @since 0.9.92
	 */
	private static final int REQUEST_VEHICLE_EDIT = 1;

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
	    setSupportActionBar((Toolbar) findViewById(R.id.rt_toolbar));
	    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

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

		Vehicle currV = Settings.getCurrentVehicle(db, false);		
		if ((null != currV) && (null != VehSettings.getCurrentTrip(db, currV, false)))
		{
			setTitle(R.string.view_vehicles);
			findViewById(R.id.vehicles_edit_new).setVisibility(View.INVISIBLE);
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
	 * Nav arrow handler for AppCompat's action bar: Call {@link #onBackPressed()}.
	 * @since 0.9.92
	 */
	@Override
	public boolean onSupportNavigateUp() {
		onBackPressed();
		return true;
	}

	/**
	 * List the vehicles currently available.
	 * Fill {@link #veh}.  Call {@link #lvVeh}.{@link ListView#setAdapter(android.widget.ListAdapter)}.
	 * @return true if {@link Vehicle}s found, false otherwise
	 */
	private boolean populateVehiclesList(RDBAdapter db)
	{
		Vehicle[] allV = Vehicle.getAll(db, 0);
		veh = allV;
		if (allV == null)
			return false;
		lvVeh.setAdapter(new ArrayAdapter<Vehicle>(this, R.layout.vehicles_list_item, allV)
			{
				public View getView(final int pos, View convertView, final android.view.ViewGroup parent)
				{
					if (convertView == null)
						convertView = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE))
							.inflate(R.layout.vehicles_list_item, null);

					TextView vtxt = (TextView) convertView.findViewById(R.id.vehicles_listitem_text);
					ImageView vstat = (ImageView) convertView.findViewById(R.id.vehicles_listitem_status);
					if ((pos >= 0) && (pos < veh.length))
					{
						Vehicle v = veh[pos];
						convertView.setTag(v);
						vtxt.setText(v.toString());
						vstat.setImageResource(v.isActive()
							? android.R.drawable.presence_online : android.R.drawable.presence_offline);
					}
					return convertView;
				};
			} );
		return true;
	}

	/**
	 * 'New' button was clicked: call {@link VehicleEntry} activity to do that.
	 * Calls {@code startActivityForResult(Intent, REQUEST_VEHICLE_ADD)}.
	 * See {@link #onActivityResult(int, int, Intent)} for callback with activity result.
	 */
	public void onClick_BtnNewVehicle(View v)
	{
    	Intent i = new Intent(this, VehicleEntry.class);
		i.putExtra(VehicleEntry.EXTRAS_FLAG_ASKED_NEW, true);
		startActivityForResult(i, REQUEST_VEHICLE_ADD);
	}

	/**
	 * When an item is selected in the list, call {@link VehicleEntry} activity to edit its ID.
	 * Calls {@code startActivityForResult(Intent, REQUEST_VEHICLE_EDIT)}.
	 * See {@link #onActivityResult(int, int, Intent)} for callback with activity result.
	 */
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		if ((veh == null) || (position >= veh.length))
			return;  // unlikely, but just in case

		Vehicle v = veh[position];
    	Intent i = new Intent(this, VehicleEntry.class);
		i.putExtra(VehicleEntry.EXTRAS_INT_EDIT_ID, v.getID());
		startActivityForResult(i, REQUEST_VEHICLE_EDIT);
	}

	/** 'Done' button was clicked: finish this activity. */
	public void onClick_BtnDone(View v)
	{
		// setResult was already called.
    	finish();
	}

	/**
	 * Callback from {@link VehicleEntry} via {@link Activity#startActivityForResult(Intent, int)}.
	 * If a vehicle was added or edited:
	 * Repopulate vehicles list.
	 * Set our result to {@link ChangeDriverOrVehicle#RESULT_CHANGES_MADE}
	 * or {@link ChangeDriverOrVehicle#RESULT_ADDED_NEW}.
	 * If a new vehicle was added, CDOV will ask whether to change the current vehicle.
	 *
	 * @param requestCode  Request code used in {@code startActivityForResult(..)}:
	 *     {@code REQUEST_VEHICLE_EDIT} when editing a vehicle, {@code REQUEST_VEHICLE_ADD} when adding a new one.
	 * @param resultCode  Activity result; {@link Activity#RESULT_CANCELED} returns immediately.
	 * @param idata  intent containing extra int "_id" with the
	 *     ID of the added or edited vehicle
	 */
	@Override
	public void onActivityResult(final int requestCode, final int resultCode, Intent idata)
	{
		if (resultCode == RESULT_CANCELED)
			return;
		final boolean added = (requestCode == REQUEST_VEHICLE_ADD);
		if ((requestCode != REQUEST_VEHICLE_EDIT) && ! added)
			return;
		if ((idata == null) || (0 == idata.getIntExtra("_id", 0)))
			return;

		if (added) {
			Intent i = getIntent();
			i.putExtra("_id", idata.getIntExtra("_id", 0));
			setResult(ChangeDriverOrVehicle.RESULT_ADDED_NEW, i);
		} else {
			setResult(ChangeDriverOrVehicle.RESULT_CHANGES_MADE);
		}

		if (db == null)
			db = new RDBOpenHelper(this);
		populateVehiclesList(db);		
	}

}
