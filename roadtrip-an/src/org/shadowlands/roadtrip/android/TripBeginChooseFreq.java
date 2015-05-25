/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010,2012,2014-2015 Jeremy D Monin <jdmonin@nand.net>
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
import org.shadowlands.roadtrip.db.Location;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.VehSettings;
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
 * When user has pressed "Begin Frequent Trip" button in the {@link Main} activity,
 * {@link TripBegin} initially comes here to choose a {@link FreqTrip}.
 *<P>
 * Called from {@link TripBegin} with {@link Activity#startActivityForResult(android.content.Intent, int)}.
 * When it returns to <tt>TripBegin</tt> with the result, its intent should contain
 * an int extra, with key = <tt>"_id"</tt>, that's the chosen FreqTrip.
 * If there are no available freqtrips, a Toast is presented to tell the user,
 * and {@link Activity#RESULT_CANCELED} is returned.
 *<P>
 * If the starting location is known, pass its id as a bundle extra with
 * the key {@link VehSettings#PREV_LOCATION}.  (This does not affect the actual setting.)
 * Otherwise, {@link VehSettings#getCurrentArea(RDBAdapter, Vehicle, boolean)} will be called.
 *<P>
 * If it's a roadtrip, start this activity with {@link TripBegin#EXTRAS_FLAG_NONLOCAL}
 * just as when starting TripBegin.
 *
 * @author jdmonin
 */
public class TripBeginChooseFreq extends Activity
	implements OnItemClickListener
{
	/** tag for debug logging */
	private static final String TAG = "RTR.TripBeginChooseFreq";

	private RDBAdapter db = null;

	private boolean isRoadtrip;
	/** available freqtrips; {@link #freqTrips} contents  */
	private ListView lvFreqTripsList;
	/** available freqtrips; contents of {@link #lvFreqTripsList}, once {@link #populateTripsList(RDBAdapter)} is called */
	private Vector<FreqTrip> freqTrips;
	/** if known, location ID passed into our bundle at create time; 0 otherwise. */
	private int locID = 0;

	/** Called when the activity is first created.
	 * See {@link #onResume()} for remainder of init work,
	 * which includes {@link #populateTripsList(RDBAdapter)}.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.trip_begin_choose_freq);

	    db = new RDBOpenHelper(this);

	    lvFreqTripsList = (ListView) findViewById(R.id.trip_begin_choosefreq_list);
	    lvFreqTripsList.setOnItemClickListener(this);
	    freqTrips = null;

		Intent i = getIntent();
		if (i != null)
		{
			isRoadtrip = i.getBooleanExtra(TripBegin.EXTRAS_FLAG_NONLOCAL, false);
			locID = i.getIntExtra(VehSettings.PREV_LOCATION, 0);
		}

		// see onResume for rest of initialization.
	}

	@Override
	public void onPause()
	{
		super.onPause();
		if (db != null)
			db.close();
	}

	/**
	 * Populate freq-stops list.
	 * If there are none: Toast, set our result to {@link #RESULT_CANCELED} and finish this activity.
	 */
	@Override
	public void onResume()
	{
		super.onResume();

		final boolean hadAny = populateTripsList(db);
		if (! hadAny)
		{
			String locText = null;
			if (locID != 0)
			{
				try
				{
					Location lo = new Location(db, locID);
					locText = lo.getLocation();
				} catch (Throwable t) { }
			}

			if (locText == null)
			{
				Toast.makeText(this, R.string.no_frequent_trips_found, Toast.LENGTH_SHORT).show();				
			} else {
				StringBuffer sb = new StringBuffer();
				sb.append(getResources().getString(R.string.no_frequent_trips_found_from));
				sb.append(' ');
				sb.append(locText);
				Toast.makeText(this, sb, Toast.LENGTH_SHORT).show();				
			}
	    	setResult(RESULT_CANCELED);
			finish();
		}
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		if (db != null)
			db.close();
	}

	/**
	 * List the frequent trips currently available, from {@link #locID} if known, or for current area.
	 * @return true if {@link FreqTrip}s found, false otherwise
	 */
	private boolean populateTripsList(RDBAdapter db)
	{
		Vector<FreqTrip> fts;
		if (locID != 0)
		{
			fts = FreqTrip.tripsForLocation(db, locID, false, false);  // TODO isLocal / isRoadtrip
		} else {
			final Vehicle currV = Settings.getCurrentVehicle(db, false);
			if (currV == null) {
				freqTrips = null;
				return false;
			}
			GeoArea currA = VehSettings.getCurrentArea(db, currV, false);
			Log.d(TAG, "no locID, Checking freqtrips for area " + currA);
			if (currA != null)
				fts = FreqTrip.tripsForArea(db, currA.getID(), false, false);  // TODO isLocal / isRoadtrip
			else
				fts = null;
		}
		if (fts == null)
		{
			freqTrips = null;
			return false;
		}
		freqTrips = fts;
		lvFreqTripsList.setAdapter(new ArrayAdapter<FreqTrip>(this, R.layout.list_item, fts));
		return true;
	}

	/** 'Cancel' button was clicked: setResult({@link #RESULT_CANCELED}), finish this activity. */
	public void onClick_BtnCancel(View v)
	{
    	setResult(RESULT_CANCELED);
    	finish();
	}

	/** When a FreqTrip is selected in the list, finish the activity with its ID. */
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		if ((freqTrips == null) || (position >= freqTrips.size()))
			return;  // unlikely, but just in case

		FreqTrip ft = freqTrips.elementAt(position);
		Toast.makeText(this, "got freqtrip id " + ft.getID(),
			Toast.LENGTH_SHORT).show();
		Intent i = getIntent();
    	i.putExtra("_id", ft.getID());
    	setResult(RESULT_OK, i);
		finish();
	}

}
