/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010,2014-2015 Jeremy D Monin <jdmonin@nand.net>
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

import java.util.List;

import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.db.FreqTrip;
import org.shadowlands.roadtrip.db.FreqTripTStop;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.VehSettings;
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

/**
 * During a {@link FreqTrip frequent trip}, choose a {@link FreqTripTStop}
 * (or another stop specific to this current trip).
 *<P>
 * Called from {@link TripTStopEntry} with {@link Activity#startActivityForResult(android.content.Intent, int)}.
 * When it returns to {@code TripTStopEntry} with the result, its intent should contain
 * an int extra with key {@code "_id"} for the chosen FreqTripTStop ID, or 0 if the 'Other Stop...' button
 * was pressed and {@link TripTStopEntry} will be used to pick a non-frequent {@link TStop}.
 *
 * @author jdmonin
 */
public class TripTStopChooseFreq extends Activity
	implements OnItemClickListener
{
	private RDBAdapter db = null;

	/** Available frequent stops; contents of {@link #freqStops} */
	private ListView lvFreqStopsList;
	/** frequent stops; contents of {@link #lvFreqStopsList}, once {@link #populateStopsList(RDBAdapter)} is called */
	private List<FreqTripTStop> freqStops;

	/** Called when the activity is first created.
	 * See {@link #onResume()} for remainder of init work,
	 * which includes updating the last-backup time,
	 * checking the SD Card status, etc.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.trip_tstop_choose_freq);

		db = new RDBOpenHelper(this);

		lvFreqStopsList = (ListView) findViewById(R.id.trip_tstop_choosefreq_list);
		lvFreqStopsList.setOnItemClickListener(this);
		freqStops = null;

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
	 * If there are none, set our result to {@link #RESULT_CANCELED} and finish this activity.
	 */
	@Override
	public void onResume()
	{
		super.onResume();

		final boolean hadAny = populateStopsList(db);
		if (! hadAny)
		{
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
	 * List the frequent trip TStops currently available for the current vehicle.
	 * @return true if {@link FreqTripTStop}s found, false otherwise
	 */
	private boolean populateStopsList(RDBAdapter db)
	{
		final Vehicle currV = Settings.getCurrentVehicle(db, false);
		if (currV == null)
			return false;

		freqStops = VehSettings.getCurrentFreqTripTStops(db, currV, false);
		if (freqStops == null)
			return false;
		lvFreqStopsList.setAdapter(new ArrayAdapter<FreqTripTStop>(this, R.layout.list_item, freqStops));

		return true;
	}

	/**
	 * The 'other stop' button has been clicked.
	 * Finish the activity with tstop id 0.
	 * @param v  ignored
	 */
	public void onClick_BtnOtherStop(View v)
	{
		finish(0);
	}

	/** When a FreqTripTStop is selected in the list, finish the activity with its ID. */
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		if ((freqStops == null) || (position >= freqStops.size()))
			return;  // unlikely, but just in case

		FreqTripTStop stop = freqStops.get(position);
		finish(stop.getID());
	}

	/**
	 * Finish the activity with this {@link FreqTripTStop} ID:
	 * call putExtra({@code "_id"}), setResult({@link Activity#RESULT_OK RESULT_OK}), and {@link #finish()}.
	 * @param freqtsID  The {@code FreqTripTStop} id, or 0 for 'other stop'
	 */
	private void finish(final int freqtsID)
	{
		Intent i = getIntent();
		i.putExtra("_id", freqtsID);
		setResult(RESULT_OK, i);

		finish();
	}

}
