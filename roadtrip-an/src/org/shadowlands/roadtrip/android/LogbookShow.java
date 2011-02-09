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

	private RDBAdapter db = null;
	private TextView tvHeader, tvContent;
	private Vehicle currV;

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
		// Retrieve via appendRowAsTabbedString.
		LogbookTableModel ltm = new LogbookTableModel(currV, WEEK_INCREMENT, db);
		StringBuffer sbTrips = new StringBuffer();
		for (int r = 0; ltm.appendRowAsTabbedString(r, sbTrips); ++r )
			/* loops until append returns false */ ;
		if (sbTrips.length() < 5)
		{
			sbTrips.append("\nNo trips found for this Vehicle.");
		}

		tvContent.setText(sbTrips);
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
