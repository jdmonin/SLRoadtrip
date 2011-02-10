/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  Copyright (C) 2010-2011 Jeremy D Monin <jdmonin@nand.net>
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
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;
import org.shadowlands.roadtrip.model.LogbookTableModel;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Present an unformatted view of the current vehicle's trip log.
 * @author jdmonin
 */
public class LogbookShow extends Activity
{
	/**
	 * Increment in weeks when loading newer/older trips from the database,
	 * or 0 to load all (This may run out of memory).
	 */
	public static final int WEEK_INCREMENT = 2;

	/** tag for android logging */
	private static final String TAG = "RTR.LogbookShow";

	/** for use by {@link #onClick_BtnEarlier(View)}. Width <tt>FILL_PARENT</tt>, height <tt>WRAP_CONTENT</tt>. */
	private static ViewGroup.LayoutParams TS_ROW_LP = null;

	private RDBAdapter db = null;
	private TextView tvHeader, tvContent;
	private Vehicle currV;
	private LogbookTableModel ltm;

	/** Used by {@link #onClick_BtnEarlier(View)} */
	private LinearLayout tripListParentLayout = null;

	/** Used by {@link #onClick_BtnEarlier(View)} */
	private int tripListBtnEarlierPosition = -1;

	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.logbook_show);

	    tvHeader = (TextView) findViewById(R.id.logbook_show_header);  // TODO show date range
	    tvContent = (TextView) findViewById(R.id.logbook_show_textview);
		db = new RDBOpenHelper(this);

		if ( ! checkCurrentVehicleSetting())
		{
        	Toast.makeText(getApplicationContext(),
                "Current vehicle not found in db",
                Toast.LENGTH_SHORT).show();
	    	finish();
	    	return;  // <--- early ret: no vehicle ---
		}
		setTitle(getTitle() + ": " + currV.toString());

		// Read and semi-format the trips for this vehicle.
		// (The LogbookTableModel constructor calls ltm.addRowsFromTrips.)
		ltm = new LogbookTableModel(currV, WEEK_INCREMENT, db);
		StringBuffer sbTrips = new StringBuffer();
		ltm.getRange(0).appendRowsAsTabbedString(sbTrips);
		if (sbTrips.length() < 5)
		{
			sbTrips.append("\nNo trips found for this Vehicle.");
		}

		tvContent.setText(sbTrips);

		// Find the trip layout linearlayout, for onClick_BtnEarlier's benefit.
		tripListParentLayout = (LinearLayout) findViewById(R.id.logbook_show_triplist_parent);
		if (tripListParentLayout != null)
		{
			View v = findViewById(R.id.logbook_show_btn_earlier);
			if (v != null)
				tripListBtnEarlierPosition = tripListParentLayout.indexOfChild(v);
		}
		if ((tripListBtnEarlierPosition == -1) || (tripListParentLayout == null))
		{
			// shouldn't happen, but just in case
			Log.e(TAG, "layout items not found");
			Toast.makeText(this, "L110: internal error: layout items not found", Toast.LENGTH_SHORT).show();
			View btnEarlier = findViewById(R.id.logbook_show_btn_earlier);
			if (btnEarlier != null)
				btnEarlier.setVisibility(View.GONE);
		}
	}

	/**
	 * Check Settings table for <tt>CURRENT_VEHICLE</tt>.
	 * Set {@link #currV}.
	 *
	 * @return true if settings exist and are OK, false otherwise.
	 */
	private boolean checkCurrentVehicleSetting()
	{
		currV = Settings.getCurrentVehicle(db, false);
		return (currV != null);
	}

	/**
	 * Load a few weeks of earlier trips from the database.
	 * @param v  ignored
	 */
	public void onClick_BtnEarlier(View v)
	{
		// TODO if too many ranges loaded, consider clear out most recent ones
		if (! ltm.addEarlierTripWeeks(db))
		{
			View btnEarlier = findViewById(R.id.logbook_show_btn_earlier);
			if (btnEarlier != null)
				btnEarlier.setVisibility(View.GONE);
			Toast.makeText
				(this, R.string.no_earlier_trips_found, Toast.LENGTH_SHORT).show();
			return;
		}
		StringBuffer sbTrips = new StringBuffer();
		ltm.getRange(0).appendRowsAsTabbedString(sbTrips);

		if (TS_ROW_LP == null)
			TS_ROW_LP = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		TextView tv = new TextView(this);
		tv.setLayoutParams(TS_ROW_LP);
		tv.setText(sbTrips);
		tripListParentLayout.addView(tv, tripListBtnEarlierPosition + 1);
	}

	@Override
	public void onPause()
	{
		super.onPause();
		if (db != null)
			db.close();
	}

	// TODO javadoc
	@Override
	public void onResume()
	{
		super.onResume();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		if (db != null)
			db.close();
	}

}
