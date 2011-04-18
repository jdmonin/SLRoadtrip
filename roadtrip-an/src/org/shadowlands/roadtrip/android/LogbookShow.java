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
import org.shadowlands.roadtrip.db.GeoArea;
import org.shadowlands.roadtrip.db.Location;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;
import org.shadowlands.roadtrip.model.LogbookTableModel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Present an unformatted view of the current vehicle's trip log.
 *<P>
 * Optionally, can filter to show only trips that include a given {@link #EXTRAS_LOCID location ID}
 * (Location Mode, same as in {@link LogbookTableModel}).
 * @author jdmonin
 */
public class LogbookShow extends Activity
{
	/**
	 * Increment in weeks when loading newer/older trips from the database,
	 * or 0 to load all (This may run out of memory).
	 * @see #LOCID_TRIP_INCREMENT
	 */
	public static final int WEEK_INCREMENT = 2;

	/**
	 * For Location filtering mode, increment in trips when loading from the database.
	 * @see #WEEK_INCREMENT
	 */
	public static final int LOCID_TRIP_INCREMENT = 10;

	/**
	 * Location Mode: If added to intent extras, show only trips including
	 * this locID; for {@link Intent#putExtra(String, int)}
	 */
	public static final String EXTRAS_LOCID = "LogbokShow.locID";

	/** tag for android logging */
	private static final String TAG = "RTR.LogbookShow";

	/** for use by {@link #onClick_BtnEarlier(View)}. Width <tt>FILL_PARENT</tt>, height <tt>WRAP_CONTENT</tt>. */
	private static ViewGroup.LayoutParams TS_ROW_LP = null;

	private RDBAdapter db = null;
	private TextView tvHeader, tvContent;
	private ScrollView sv;
	private Vehicle currV;
	private LogbookTableModel ltm;

	/** Used by {@link #onClick_BtnEarlier(View)} */
	private LinearLayout tripListParentLayout = null;

	/** Used by {@link #onClick_BtnEarlier(View)} */
	private int tripListBtnEarlierPosition = -1;

	/** Used by #askLocationAndShow(Activity, RDBAdapter); null if no location selected. */
	private static Location askLocationAndShow_locObj = null;

	/**
	 * Logbook in Location Mode: Show a popup with the current GeoArea's locations, the user picks
	 * one and the LogbookShow activity is launched for it.
	 * @param fromActivity  Current activity; will call {@link Activity#startActivity(Intent)} on it
	 * @param db  Connection to use
	 * @see #showTripsForLocation(int, Activity)
	 */
	public static final void askLocationAndShow(final Activity fromActivity, final RDBAdapter db)
	{
		// TODO should be a separate dialog, allow dropdown for another GeoArea

		/** all locations in the current area, or null */
		final GeoArea currA = Settings.getCurrentArea(db, false);
		if (currA == null)
			return;
		final AutoCompleteTextView loc;
		Location[] areaLocs = Location.getAll(db, currA.getID());
		if (areaLocs != null)
		{
			loc = new AutoCompleteTextView(fromActivity);
			ArrayAdapter<Location> adapter = new ArrayAdapter<Location>(fromActivity, R.layout.list_item, areaLocs);
			loc.setAdapter(adapter);
			loc.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				public void onItemClick(AdapterView<?> parent, View clickedOn, int position, long rowID)
				{
					ListAdapter la = loc.getAdapter();
					if (la == null)
						return;
					askLocationAndShow_locObj = (Location) la.getItem(position);
				}
				});
		} else {
			Toast.makeText(fromActivity, R.string.logbook_show__no_locs_in_area, Toast.LENGTH_SHORT).show();
			return;
		}

		AlertDialog.Builder alert = new AlertDialog.Builder(fromActivity);
		alert.setTitle(R.string.logbook_show__trips_to_location);
		alert.setMessage(R.string.location);
		alert.setView(loc);
		alert.setPositiveButton(R.string.view, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton)
			{
				if (askLocationAndShow_locObj != null)
				{
					showTripsForLocation(askLocationAndShow_locObj.getID(), fromActivity);
				} else {
					final int text;
					if (loc.getText().length() == 0)
						text = R.string.please_enter_the_location;
					else
						text = R.string.please_choose_existing_location;

					Toast.makeText(fromActivity, text, Toast.LENGTH_SHORT).show();
				}
			}
		});
		alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton)
			{
				askLocationAndShow_locObj = null;
			}
		});
		askLocationAndShow_locObj = null;
		areaLocs = null;  // free the reference
		alert.show();
	}

	/**
	 * Start this activity in Location Mode: Only show trips including a given location.
	 * @param locID  Location ID
	 * @param fromActivity  Current activity; will call {@link Activity#startActivity(Intent)} on it
	 */
	public static final void showTripsForLocation(final int locID, final Activity fromActivity)
	{
		Intent i = new Intent(fromActivity, LogbookShow.class);
		i.putExtra(EXTRAS_LOCID, locID);
		fromActivity.startActivity(i);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.logbook_show);

	    tvHeader = (TextView) findViewById(R.id.logbook_show_header);  // TODO show date range or location
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
		int locID = -1;
		{
			Intent i = getIntent();
			if (i != null)
				locID = i.getIntExtra(EXTRAS_LOCID, -1);
			// TODO set the title to include that,
			//      or tvHeader text to include it
		}

		if (locID == -1)
			ltm = new LogbookTableModel(currV, WEEK_INCREMENT, db);
		else
			ltm = new LogbookTableModel(currV, locID, LOCID_TRIP_INCREMENT, db);
		StringBuffer sbTrips = new StringBuffer();
		ltm.getRange(0).appendRowsAsTabbedString(sbTrips);
		if (sbTrips.length() < 5)
		{
			if (locID == -1)
				sbTrips.append("\nNo trips found for this Vehicle.");
			else
				sbTrips.append("\nNo trips found to that Location for this Vehicle.");
		}
		if (ltm.hasCurrentTrip())
		{
			sbTrips.append("\n\t\t(Current Trip in progress)");
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

		// Scroll to bottom (most recent)
		sv = (ScrollView) findViewById(R.id.logbook_show_triplist_scroll);
		if (sv != null)
			sv.post(new Runnable() {
				public void run() {
					sv.fullScroll(ScrollView.FOCUS_DOWN);
				}
			});
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
    public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.logbook_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    case R.id.menu_logbook_recent_gas:
	    	startActivity(new Intent(this, LogbookRecentGas.class));
	        return true;

		case R.id.menu_logbook_filter_location:
			askLocationAndShow(this, db);
			return true;

	    default:
	        return super.onOptionsItemSelected(item);
	    }
	}

	/**
	 * Load a few weeks of earlier trips from the database.
	 * @param v  ignored
	 */
	public void onClick_BtnEarlier(View v)
	{
		// TODO if too many ranges loaded, consider clear out most recent ones
		if (! ltm.addEarlierTrips(db))
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
		final TextView tv = new TextView(this);
		tv.setLayoutParams(TS_ROW_LP);
		tv.setText(sbTrips);
		tripListParentLayout.addView(tv, tripListBtnEarlierPosition + 1);

		// Once layout is done, scroll to the bottom of the newly added tv
		// so that what's currently visible, stays visible.
		if (sv != null)
			sv.post(new Runnable() {
				public void run() {
					sv.scrollTo(0, tv.getMeasuredHeight());
				}
			});
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
