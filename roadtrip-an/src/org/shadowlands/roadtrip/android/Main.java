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

import org.shadowlands.roadtrip.AndroidStartup;
import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.db.FreqTrip;
import org.shadowlands.roadtrip.db.GeoArea;
import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.RDBKeyNotFoundException;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.TStop;
import org.shadowlands.roadtrip.db.Trip;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Main activity/screen of the application.
 * Go here after CURRENT_DRIVER and CURRENT_VEHICLE are set.
 * If these aren't found, go back to AndroidStartup activity.
 *
 * @author jdmonin
 */
public class Main extends Activity
{
	private RDBAdapter db = null;

	/** Current vehicle; updated in {@link #updateDriverVehTripTextAndButtons()} */
	Vehicle currV = null;

	private TextView tvCurrentSet;

	private Button localTrip, roadTrip, freqLocal, freqRoad,
	    changeDriverOrVeh, endTrip, stopContinue;

	/** Called when the activity is first created.
	 * See {@link #onResume()} for remainder of init work,
	 * which includes checking the current driver/vehicle/trip
	 * and hiding/showing buttons as appropriate.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.main);

	    tvCurrentSet = (TextView) findViewById(R.id.main_text_current); 
		db = new RDBOpenHelper(this);

		localTrip    = (Button) findViewById(R.id.main_btn_local_trip);
		roadTrip     = (Button) findViewById(R.id.main_btn_road_trip);
		freqLocal    = (Button) findViewById(R.id.main_btn_freq_local);
		freqRoad     = (Button) findViewById(R.id.main_btn_freq_roadtrip);
		endTrip      = (Button) findViewById(R.id.main_btn_end_trip);
		stopContinue = (Button) findViewById(R.id.main_btn_stop_continue);
		changeDriverOrVeh = (Button) findViewById(R.id.main_btn_change_driver_vehicle);

		// Allow long-press on Frequent buttons
		registerForContextMenu(freqLocal);
		registerForContextMenu(freqRoad);

		// see onResume for rest of initialization.
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    case R.id.menu_main_backup:
	    	startActivity(new Intent(Main.this, BackupsMain.class));
	        return true;

	    default:
	        return super.onOptionsItemSelected(item);
	    }
	}

	/** Create long-press menu for Frequent buttons */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {		
		if (v == freqLocal)
			menu.add(Menu.NONE, R.id.main_btn_freq_local, Menu.NONE, R.string.main_create_freq_from_recent);			
		else if (v == freqRoad)
			menu.add(Menu.NONE, R.id.main_btn_freq_roadtrip, Menu.NONE, R.string.main_create_freq_from_recent);
		else
			super.onCreateContextMenu(menu, v, menuInfo);
	}

	/** Handle long-press on Frequent buttons */
	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
		case R.id.main_btn_freq_local:
			listRecentTripsForMakeFreq(true);
			break;
		case R.id.main_btn_freq_roadtrip:
			listRecentTripsForMakeFreq(false);
			break;
		default:
			return super.onContextItemSelected(item);
		}
		return true;
	}

	/**
	 * Check Settings table for <tt>CURRENT_DRIVER</tt>, <tt>CURRENT_VEHICLE</tt>.
	 * Set {@link #currD} and {@link #currV}.
	 * If there's an inconsistency between Settings and Vehicle/Person tables, delete the Settings entry.
	 * <tt>currD</tt> and <tt>currV</tt> will be null unless they're set consistently in Settings.
	 *
	 * @return true if settings exist and are OK, false otherwise.
	 */
	private boolean checkCurrentDriverVehicleSettings()
	{
		return (Settings.getCurrentDriver(db, true) != null)
			&& (Settings.getCurrentVehicle(db, true) != null);
	}

	/**
	 * Update the text about current driver, vehicle and trip;
	 * hide or show start-stop buttons as appropriate.
	 */
	private void updateDriverVehTripTextAndButtons()
	{
		// TODO string resources, not hardcoded
		GeoArea currA = Settings.getCurrentArea(db, false);
		Person currD = Settings.getCurrentDriver(db, false);
		currV = Settings.getCurrentVehicle(db, false);
		Trip currT = Settings.getCurrentTrip(db, false);
		TStop currTS = ((currT != null) ? Settings.getCurrentTStop(db, false) : null);
		FreqTrip currFT = Settings.getCurrentFreqTrip(db, false);

		StringBuffer txt = new StringBuffer("Current driver: ");
		txt.append("Current driver: ");
		txt.append(currD.toString());
		txt.append("\nCurrent vehicle: ");
		txt.append(currV.toString());
		if (currT == null)
		{
			txt.append("\nCurrent area: ");
			txt.append(currA.toString());
			txt.append("\n\nNo current trip.");
			changeDriverOrVeh.setText(R.string.change_driver_vehicle);
		} else {
			final int destAreaID = currT.getRoadtripEndAreaID();
			if ((currT != null) && (destAreaID != 0))
				txt.append("\nRoadtrip start area: ");
			else
				txt.append("\nCurrent area: ");
			txt.append(currA.toString());

			if (destAreaID == 0)
			{
				txt.append("\n\nTrip in Progress.");
			} else {
				txt.append("\n\nRoadtrip in progress.\nDestination area: ");
				try {
					txt.append(new GeoArea(db, destAreaID).getName());
				} catch (IllegalStateException e) {
					// shouldn't happen, db is open
				} catch (RDBKeyNotFoundException e) {
					// shouldn't happen
					Toast.makeText(this, "L132: Internal error, area " + destAreaID + " not found in DB", Toast.LENGTH_LONG).show();
				}
			}
			if (currFT != null)
			{
				txt.append("\nFrom frequent trip: ");
				txt.append(currFT.toString());
			}
			changeDriverOrVeh.setText(R.string.change_driver);
			if (currTS != null)
				stopContinue.setText(R.string.continu_from_stop);
			else
				stopContinue.setText(R.string.stop_continue);
		}

		tvCurrentSet.setText(txt);

		final int visTrip, visNotTrip;
		if (Settings.getCurrentTrip(db, false) != null)
		{
			visTrip = View.VISIBLE;
			visNotTrip = View.INVISIBLE;
		} else {			
			visTrip = View.INVISIBLE;
			visNotTrip = View.VISIBLE;
		}
		localTrip.setVisibility(visNotTrip);
		roadTrip.setVisibility(visNotTrip);
		freqLocal.setVisibility(visNotTrip);
		freqRoad.setVisibility(visNotTrip);
	    endTrip.setVisibility(visTrip);
	    stopContinue.setVisibility(visTrip);
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
		if (! checkCurrentDriverVehicleSettings())
		{
        	Toast.makeText(getApplicationContext(),
                "Current driver/vehicle not found in db",
                Toast.LENGTH_SHORT).show();
	    	startActivity(new Intent(Main.this, AndroidStartup.class));
	    	finish();
	    	return;
		}

		// Give status
		updateDriverVehTripTextAndButtons();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		if (db != null)
			db.close();
	}

	public void onClick_BtnLocalTrip(View v)
	{
		beginTrip(true, false);
	}

	public void onClick_BtnRoadTrip(View v)
	{
		beginTrip(false, false);	
	}

	public void onClick_BtnFreqLocal(View v)
	{
		beginTrip(true, true);
	}

	public void onClick_BtnFreqRoadtrip(View v)
	{
		beginTrip(false, true);
	}

	public void onClick_BtnEndTrip(View v)
	{
		Intent tbi = new Intent(Main.this, TripTStopEntry.class);
		tbi.putExtra(TripTStopEntry.EXTRAS_FLAG_ENDTRIP, true);
		startActivity(tbi);				
	}

	/**
	 * Go to {@link TripTStopEntry} to begin or finish a TStop.
	 */
	public void onClick_BtnStopContinue(View v)
	{
		startActivity(new Intent(Main.this, TripTStopEntry.class));
		// Afterward, onResume will set or clear currTS, update buttons, etc.
	}

	public void onClick_BtnChangeDriverVehicle(View v)
	{
		startActivityForResult
		   (new Intent(Main.this, ChangeDriverOrVehicle.class),
			R.id.main_btn_change_driver_vehicle);
	}

	public void onClick_BtnShowLogbook(View v)
	{
		startActivity(new Intent(Main.this, LogbookShow.class));		
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, Intent data)
	{
		if (resultCode == RESULT_CANCELED)
			return;

		if (requestCode == R.id.main_btn_change_driver_vehicle)
			updateDriverVehTripTextAndButtons();			
	}

	/** Start the {@link TripBegin} activity with these flags */
	private void beginTrip(final boolean isLocal, final boolean isFrequent)
	{
		Intent tbi = new Intent(Main.this, TripBegin.class);
		if (! isLocal)
			tbi.putExtra(TripBegin.EXTRAS_FLAG_NONLOCAL, true);
		if (isFrequent)
			tbi.putExtra(TripBegin.EXTRAS_FLAG_FREQUENT, true);
		startActivity(tbi);		
	}

	/**
	 * To make a new frequent trip from the most recent trip,
	 * check the db and start the {@link TripCreateFreq} activity.
	 * If the most-recent trip is already based on frequent,
	 * ask the user to confirm first.
	 */
	private void listRecentTripsForMakeFreq(final boolean wantsLocal)
	{
		Trip t = Trip.recentTripForVehicle(db, currV, wantsLocal);
		if (t == null)
		{
			final int stringid = (wantsLocal)
				? R.string.main_no_recent_local_trips_for_vehicle
				: R.string.main_no_recent_roadtrips_for_vehicle;
			Toast.makeText(this, stringid, Toast.LENGTH_SHORT).show();
			return;
		}
		// TODO some listing activity for them,
		//   instead of just most-recent
		Intent tcfi = new Intent(Main.this, TripCreateFreq.class);
		tcfi.putExtra("_id", t.getID());
		if (! t.isFrequent())
		{
			startActivity(tcfi);
		} else {
			final Intent i = tcfi;  // req'd for inner class use
	    	AlertDialog.Builder alert = new AlertDialog.Builder(this);

	    	alert.setTitle(R.string.confirm);
	    	alert.setMessage(R.string.main_trip_based_on_frequent);
	    	alert.setPositiveButton(R.string.continu, new DialogInterface.OnClickListener() {
		    	  public void onClick(DialogInterface dialog, int whichButton)
		    	  {
		    		  startActivity(i);
		    	  }
		    	});
	    	alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
	    	public void onClick(DialogInterface dialog, int whichButton) { }
		    	});
	    	alert.show();
		}
	}

}
