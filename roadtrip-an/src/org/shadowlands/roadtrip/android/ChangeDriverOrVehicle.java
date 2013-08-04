/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2013 Jeremy D Monin <jdmonin@nand.net>
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
import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
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
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;

/**
 * Change the current driver or the current vehicle
 * ({@link Settings#CURRENT_DRIVER}, {@link Settings#CURRENT_VEHICLE}.
 * If the vehicle changes, also clear {@link Settings#PREV_LOCATION}.
 *<P>
 * Will call {@link #setResult(int) setResult}<tt>(RESULT_OK)</tt> if changed,
 *    <tt>setResult(RESULT_CANCELED)</tt> otherwise.
 *<P>
 * The vehicle can't be changed during a Trip.
 * Changing the current vehicle will switch to a different current trip.
 * The driver can't be changed during a Trip yet.
 *
 * @author jdmonin
 */
public class ChangeDriverOrVehicle extends Activity
{
	/**
	 * Activity result to indicate changes were made; used in callback from {@link VehiclesEdit} to here.
	 */
	public static final int RESULT_CHANGES_MADE = Activity.RESULT_FIRST_USER;

	private RDBAdapter db = null;
	private Spinner driver, veh;
	private int currDID = 0, currVID = 0;
	private Vehicle currV;
	private boolean hasCurrentTrip;
	private Trip currT;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.change_vehicle_or_driver);

	    hasCurrentTrip = false;

		db = new RDBOpenHelper(this);
		currDID = Settings.getCurrentDriver(db, false).getID();
		currV = Settings.getCurrentVehicle(db, false);
		currVID = currV.getID();
		currT = Settings.getCurrentTrip(db, false);
		hasCurrentTrip = (null != currT);

		driver = (Spinner) findViewById(R.id.change_cvd_driver);
		veh = (Spinner) findViewById(R.id.change_cvd_vehicle);
		SpinnerDataFactory.setupDriversSpinner(db, this, driver, currDID);

		if (hasCurrentTrip)
		{
			setTitle(R.string.view_drivers_vehicles);
			driver.setEnabled(false);
			findViewById(R.id.change_cvd_driver_new).setEnabled(false);
			((Button) findViewById(R.id.change_cvd_drivers_edit)).setText(R.string.view_drivers);
			((Button) findViewById(R.id.change_cvd_vehicles_edit)).setText(R.string.view_vehicles);
		}

		SpinnerDataFactory.setupVehiclesSpinner(db, true, this, veh, currVID);
	}

	/**
	 * Change current driver/vehicle, {@link #finish()} the activity.
	 * Set our result to {@link #RESULT_OK} if any current setting was changed,
	 * {@link #RESULT_CANCEL} otherwise (even if drivers/vehicles were edited).
	 */
	public void onClick_BtnChange(View v)
	{
    	boolean anyChange = false;

    	if (! hasCurrentTrip)
    	{
	    	Person d = (Person) driver.getSelectedItem();
	    	if (d != null)
	    	{
	    		final int newID = d.getID();
	    		if (newID != currDID)
	    		{
	    			anyChange = true;
	    			Settings.setCurrentDriver(db, d);
	    		}
	    	}

	    	// eventually will allow change driver within trip, but not yet.
    	}

    	Vehicle ve = (Vehicle) veh.getSelectedItem();
    	if ((ve != null))
    	{
    		final int newID = ve.getID();
    		if (newID != currVID)
    		{
    			anyChange = true;

    			if (hasCurrentTrip)
    			{
    				// before changing, update old current-vehicle with its current-trip info
    				currV.setOdometerCurrentAndLastTrip
    					(currV.getOdometerCurrent(), currT, true);

    				TStop currTS = Settings.getCurrentTStop(db, false); // CURRENT_TSTOP
					if (currTS != null)
					{
						currTS.setFlagSingle(TStop.TEMPFLAG_CURRENT_TSTOP_AT_CURRV_CHANGE);
						currTS.commit();

						// Will soon call Settings.setCurrentTStop based on new vehicle.
					}
    			}

    			int tripAreaIDCheck = -1;  // area ID to check, current area might change with vehicle

    			final Trip newCurrT = ve.getTripInProgress();
    			hasCurrentTrip = (newCurrT != null);
    			Settings.setCurrentTrip(db, newCurrT);

    			Settings.setCurrentVehicle(db, ve);
    			if (hasCurrentTrip)
    			{
    				// try to find vehicle's previous location and current TStop, if any

    				final boolean isStopped;
    				TStop ts = newCurrT.readLatestTStop();  // tstop may have a different trip id
    				if (ts == null)
    				{
    					isStopped = false;   // no TStops yet for this trip
    					ts = newCurrT.readStartTStop(true);
    				} else {
    					isStopped = (newCurrT.getID() == ts.getTripID())
    						&& ts.isSingleFlagSet(TStop.TEMPFLAG_CURRENT_TSTOP_AT_CURRV_CHANGE);
    				}
    				Location lo = null;
    				if (ts != null)
    				{
    					// if not stopped, then ts.getLocationID is the trip's previous location.
    					// If stopped, need to find the previous TStop to get the prev location.
    					int locID;
    					if (! isStopped) {
    						locID = ts.getLocationID();
    					} else {
    						locID = 0;
    						TStop tsPrev = TStop.readPreviousTStopWithinTrip(db, newCurrT, ts);
    						if (tsPrev != null)
    							locID = tsPrev.getLocationID();
    						if (locID == 0)
    							locID = ts.getLocationID();  // fallback
    					}
    					if (locID != 0) {	    						
	    					try {
								lo = new Location(db, locID);
							} catch (Exception e) {  /* not found: db closed or inconsistent: TODO */  }
    					}
    				}
    				Settings.setPreviousLocation(db, lo);
    				if (isStopped) {
    					Settings.setCurrentTStop(db, ts);
    					tripAreaIDCheck = newCurrT.getAreaID();
	    				if (newCurrT.isRoadtrip()) {
	    					final int tsAID = ts.getAreaID();
	    					if (tsAID != 0)
	    						tripAreaIDCheck = tsAID;
	    				}
    				} else {
    					Settings.setCurrentTStop(db, null);
	    				if (newCurrT.isRoadtrip()) {
	    					tripAreaIDCheck = newCurrT.getRoadtripEndAreaID();
	    					if (lo != null) {
		    					final int locAID = lo.getAreaID();
		    					if (locAID != 0)
		    						tripAreaIDCheck = locAID;
	    					}
	    				} else {
	    					tripAreaIDCheck = newCurrT.getAreaID();
	    				}
    				}

    				final int newDID = newCurrT.getDriverID();
    				if (newDID != currDID)
    				{
    					// can't change driver within trip -> make sure current trip sets current driver

						try {
							final Person newCurrDriver = new Person(db, newDID);
	    					Settings.setCurrentDriver(db, newCurrDriver);
	    					// no need to call driver.setSelection, because activity is finishing
						} catch (Exception e) {  /* not found: db closed or inconsistent: TODO */  }
    				}
    			} else {
    				// no current trip

    				Settings.setCurrentTStop(db, null);

    				Location prevLoc = null;
    				final int lastTID = ve.getLastTripID();
    				if (lastTID != 0)
    				{
    					try {
    						final Trip lastTrip = new Trip(db, lastTID);
    	    				if (lastTrip.isRoadtrip())
    	    					tripAreaIDCheck = lastTrip.getRoadtripEndAreaID();
    	    				else
    	    					tripAreaIDCheck = lastTrip.getAreaID();

    	    				TStop endTS = lastTrip.readLatestTStop();
    	    				if (endTS != null)
    	    					prevLoc = endTS.readLocation();

    					} catch (Exception e) {  /* not found: db closed or inconsistent: TODO */  }
    				}

    				Settings.setPreviousLocation(db, prevLoc);
    			}

    			if (tripAreaIDCheck != -1) {
    				// did current area change?
    				final int currA = Settings.getInt(db, Settings.CURRENT_AREA, 0);
    				if (currA != tripAreaIDCheck)
    				{
						try {
	    					Settings.setCurrentArea(db, new GeoArea(db, tripAreaIDCheck));
						} catch (Exception e) {  /* not found: db closed or inconsistent: TODO */  }
    				}
    			}
    		}
    	}

    	setResult(anyChange ? RESULT_OK : RESULT_CANCELED);
    	finish();
    }

    /**
     * Don't change the current driver/vehicle, and {@link #finish()} the activity.
     * Result will be {@link #RESULT_CANCEL},even if drivers/vehicles were edited.
     */
    public void onClick_BtnCancel(View v)
    {
    	setResult(RESULT_CANCELED);
    	finish();
    }

    public void onClick_BtnDriverNew(View v)
    {
    	Intent i = new Intent(this, DriverEntry.class);
		i.putExtra(DriverEntry.EXTRAS_FLAG_ASKED_NEW, true);
		startActivityForResult(i, R.id.change_cvd_driver_new);
    }

    public void onClick_BtnVehicleNew(View v)
    {
    	Intent i = new Intent(this, VehicleEntry.class);
		i.putExtra(VehicleEntry.EXTRAS_FLAG_ASKED_NEW, true);
		startActivityForResult(i, R.id.change_cvd_vehicle_new);
    }

    public void onClick_BtnDriversEdit(View v)
    {
    	Intent i = new Intent(this, DriversEdit.class);
    	startActivityForResult(i, R.id.change_cvd_drivers_edit);
    }

    public void onClick_BtnVehiclesEdit(View v)
    {
    	Intent i = new Intent(this, VehiclesEdit.class);
    	startActivityForResult(i, R.id.change_cvd_vehicles_edit);
    }

    /**
	 * Callback from {@link DriverEntry}, {@link VehicleEntry}, {@link DriversEdit} or {@link VehiclesEdit}.
	 *<P>
	 * If changes were made, also changes our "Cancel" button to "Done" to reduce confusion.
	 *
	 * @param idata  intent containing extra int "_id" with the
	 *     ID of the newly added driver or vehicle (for New only),
	 *     or with result code {@link #RESULT_CHANGES_MADE}
	 *     if any vehicle or driver was edited.
	 */
	@Override
	public void onActivityResult(final int requestCode, final int resultCode, Intent idata)
	{
		if (resultCode == RESULT_CANCELED)
			return;

		boolean changed = true;

		switch (requestCode)
		{
		case R.id.change_cvd_driver_new:
			spinnerAddNewItem_Ask(true, driver, idata);  break;

		case R.id.change_cvd_vehicle_new:
			spinnerAddNewItem_Ask(false, veh, idata);    break;

		case R.id.change_cvd_drivers_edit:
			if (resultCode == RESULT_CHANGES_MADE)
			{
				if (db == null)
					db = new RDBOpenHelper(this);
				SpinnerDataFactory.setupDriversSpinner(db, this, driver, currDID);
			} else {
				changed = false;
			}
			break;

		case R.id.change_cvd_vehicles_edit:
			if (resultCode == RESULT_CHANGES_MADE)
			{
				if (db == null)
					db = new RDBOpenHelper(this);
				SpinnerDataFactory.setupVehiclesSpinner(db, true, this, veh, currVID);
			} else {
				changed = false;
			}
			break;
		}

		if (changed)
		{
			Button b = (Button) findViewById(R.id.change_cvd_btn_cancel);
			b.setText(R.string.done);
		}
	}

	/**
	 * Ask whether to change the current driver/vehicle after adding a new one.
	 * Ask only if no current trip.
	 * When dialog is answered, will call {@link #spinnerAddNewItem(boolean, Spinner, boolean, int)} 
	 * to update the Driver or Vehicle spinner contents, including the current value.
	 * @param isDriver  True for driver, false for vehicle
	 * @param  sp  Spinner to update from <tt>idata</tt>'s data
	 */
    private void spinnerAddNewItem_Ask(final boolean isDriver, final Spinner sp, Intent idata)
    {
    	final int newID = idata.getIntExtra("_id", 0);
    	if (newID == 0)
    		return;
    	if (null != Settings.getCurrentTrip(db, false))
    		return;

    	final int toastMsg =
    		isDriver ? R.string.change_vehicle_driver_ask_chg_new_d
    				 : R.string.change_vehicle_driver_ask_chg_new_v;

    	AlertDialog.Builder alert = new AlertDialog.Builder(this);

    	alert.setTitle(R.string.change_vehicle_driver_ask_chg_title);
    	alert.setMessage(toastMsg);
    	alert.setPositiveButton(R.string.change, new DialogInterface.OnClickListener() {
	    	  public void onClick(DialogInterface dialog, int whichButton) {
		    	  spinnerAddNewItem(isDriver, sp, true, newID);
	    	  }
	    	});
    	alert.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
	    	  public void onClick(DialogInterface dialog, int whichButton) {
    			spinnerAddNewItem(isDriver, sp, false, newID);
	    	  }
	    	});
    	alert.show();
	}

	/**
	 * Update the Driver or Vehicle spinner contents after adding a new one.
	 * @param isDriver  True for driver, false for vehicle
	 * @param  sp  Spinner to update
	 * @param  doChange  True to change current, false to keep it
	 * @param newID  newly added item's ID
	 */
    private void spinnerAddNewItem(final boolean isDriver, final Spinner sp, final boolean doChange, final int newID)
    {
    	if (doChange)
    	{
    		try
    		{
	    		if (isDriver)
	    		{
	    			Settings.setCurrentDriver(db, new Person(db, newID));
	    		} else {
	    			Settings.setCurrentVehicle(db, new Vehicle(db, newID));	    			
	    		}
    		} catch (Throwable th) {}
    	}

    	try
		{
	    	if (isDriver)
	    	{
				currDID = Settings.getCurrentDriver(db, false).getID();
				SpinnerDataFactory.setupDriversSpinner(db, this, driver, currDID);
			} else {
				currVID = Settings.getCurrentVehicle(db, false).getID();
				SpinnerDataFactory.setupVehiclesSpinner(db, true, this, veh, currVID);
			}
		} catch (Throwable th) {
			return;
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
}
