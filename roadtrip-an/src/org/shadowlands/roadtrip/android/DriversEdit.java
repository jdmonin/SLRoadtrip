/*
 *  Drivers (people) Editor list.
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
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

/**
 * List of {@link Person} (people, drivers) to edit or view.
 *<P>
 * Called from {@link ChangeDriverOrVehicle}
 * with {@link Activity#startActivityForResult(android.content.Intent, int)}.
 * Read-only if current trip.
 *<P>
 * If any changes were made to people, will call <tt>{@link #setResult(int) setResult(}
 * {@link ChangeDriverOrVehicle#RESULT_CHANGES_MADE})</tt> before returning.
 * Otherwise, will call setResult({@link Activity#RESULT_OK}).
 *<P>
 * If somehow no people are found in the database, will call setResult({@link Activity#RESULT_CANCELED}),
 * display a "not found" toast, and return immediately.
 *
 * @author jdmonin
 */
public class DriversEdit extends Activity
	implements OnItemClickListener
{
	/** tag for debug logging */
	@SuppressWarnings("unused")
	private static final String TAG = "RTR.DriversEdit";

	private RDBAdapter db = null;

	/** available people; {@link #people} contents  */
	private ListView lvPeople;
	/** available people; contents of {@link #lvPeople} */
	private Person[] people;

	/** Called when the activity is first created.
	 * Populate {@link #people} and {@link #lvPeople}.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.drivers_edit);

	    db = new RDBOpenHelper(this);

	    lvPeople = (ListView) findViewById(R.id.list);
	    lvPeople.setOnItemClickListener(this);

		if (! populatePeopleList(db))
		{
			Toast.makeText(this, R.string.not_found, Toast.LENGTH_SHORT).show();
	    	setResult(RESULT_CANCELED);
			finish();
		} else {
			setResult(RESULT_OK);  // No vehicle changes made yet
		}

		if (null != Settings.getCurrentTrip(db, false))
		{
			setTitle(R.string.view_drivers);
			findViewById(R.id.drivers_edit_new).setVisibility(View.INVISIBLE);
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
	 * List the drivers currently available.
	 * Fill {@link #people}.  Call {@link #lvPeople}.{@link ListView#setAdapter(android.widget.ListAdapter)}.
	 * @return true if {@link Vehicle}s found, false otherwise
	 */
	private boolean populatePeopleList(RDBAdapter db)
	{
		Person[] allP = Person.getAll(db, true);  // TODO false for non-drivers when implemented
		people = allP;
		if (allP == null)
			return false;
		lvPeople.setAdapter(new ArrayAdapter<Person>(this, R.layout.list_item, allP));
		return true;
	}

	/** 'New' button was clicked: activity for that */
	public void onClick_BtnNewPerson(View v)
	{
    	Intent i = new Intent(this, DriverEntry.class);
		i.putExtra(DriverEntry.EXTRAS_FLAG_ASKED_NEW, true);
		startActivityForResult(i, R.id.drivers_edit_new);		
	}

	/** When an item is selected in the list, edit its ID. */
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		if ((people == null) || (position >= people.length))
			return;  // unlikely, but just in case

		Person p = people[position];
    	Intent i = new Intent(this, DriverEntry.class);
		i.putExtra(DriverEntry.EXTRAS_INT_EDIT_ID, p.getID());
		startActivityForResult(i, R.id.list);
	}

	/** 'Done' button was clicked: finish this activity. */
	public void onClick_BtnDone(View v)
	{
		// setResult was already called.
    	finish();
	}

    /**
	 * Callback from {@link DriverEntry}.
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
		if ((requestCode != R.id.drivers_edit_new) && (requestCode != R.id.list))
			return;
		if ((idata == null) || (0 == idata.getIntExtra("_id", 0)))
			return;

		setResult(ChangeDriverOrVehicle.RESULT_CHANGES_MADE);
		if (db == null)
			db = new RDBOpenHelper(this);
		populatePeopleList(db);		
	}

}
