/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2012,2014 Jeremy D Monin <jdmonin@nand.net>
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

import java.io.DataInputStream;
import java.io.InputStream;

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
import org.shadowlands.roadtrip.db.TripCategory;
import org.shadowlands.roadtrip.db.VehSettings;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Main activity/screen of the application.
 * Go here after CURRENT_DRIVER and CURRENT_VEHICLE are set.
 * If these aren't found, go back to AndroidStartup activity.
 *<P>
 * Also contains the About box dialog.
 *
 * @author jdmonin
 */
public class Main extends Activity
{
	private RDBAdapter db = null;

	/** Current vehicle; updated in {@link #updateDriverVehTripTextAndButtons()} */
	Vehicle currV = null;

	/**
	 * Holds 'Trip in Progress' status text; updated in {@link #updateDriverVehTripTextAndButtons()}.
	 * For current roadtrip, show source and destination GeoAreas.
	 * For categorized trip, show the category.
	 */
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

		if (Settings.getBoolean(db, Settings.HIDE_FREQTRIP, false))
		{
			LayoutParams vlp = localTrip.getLayoutParams();
			if (vlp instanceof TableRow.LayoutParams)
			{
				((TableRow.LayoutParams) vlp).span = 2;
				localTrip.setLayoutParams(vlp);
			}

			vlp = roadTrip.getLayoutParams();
			if (vlp instanceof TableRow.LayoutParams)
			{
				((TableRow.LayoutParams) vlp).span = 2;
				roadTrip.setLayoutParams(vlp);
			}

			freqLocal.setVisibility(View.GONE);
			freqRoad.setVisibility(View.GONE);
		}

		// see onResume for rest of initialization.
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main_menu, menu);
		return true;
	}

	/** If current trip, enable "cancel" menu item */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		MenuItem item = menu.findItem(R.id.menu_main_canceltrip);
		if (item != null)
			item.setEnabled(null != VehSettings.getCurrentTrip(db, currV, false));

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    case R.id.menu_main_backup:
			// this activity's onPause will call db.close(),
	    	// so it's safe if we restore db from BackupsRestore.
	    	startActivity(new Intent(Main.this, BackupsMain.class));
	        return true;

	    case R.id.menu_main_canceltrip:
	    	confirmCancelCurrentTrip();
	    	return true;

		case R.id.menu_main_settings:
			startActivity(new Intent(Main.this, SettingsActivity.class));
			return true;

	    case R.id.menu_main_about:
        	showDialog(R.id.menu_main_about);
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
	 * Create the About dialog.
	 * The version number is determined from app resources.
	 * The build number is read from res/raw/svnversion.txt which is manually
	 * updated by the developer before building an APK.
	 */
	@Override
	protected Dialog onCreateDialog(int id)
	{
		Dialog dialog;
		switch(id)
		{
		case R.id.menu_main_about:
			{
				// R.string.app_about is the multi-line text.
				final TextView tv_about_text = new TextView(this);
				final SpannableString about_str = 
					new SpannableString(getText(R.string.app_about));
				Linkify.addLinks(about_str, Linkify.WEB_URLS);
				tv_about_text.setText(about_str);
				tv_about_text.setMovementMethod(LinkMovementMethod.getInstance());

				AlertDialog.Builder aboutBuilder = new AlertDialog.Builder(this);
				aboutBuilder.setView(tv_about_text)   
				  .setCancelable(true)
				  .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.dismiss();
					}
				});

				// get our version dynamically.
				// title format: About Shadowlands Roadtrip v0.9.07
				StringBuffer title = new StringBuffer(getResources().getString(R.string.about));
				title.append(' ');
				title.append(getResources().getString(R.string.app_name));
				boolean hadVersName = false;
				try {
					PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
					if (pInfo != null)
					{
						String versName = pInfo.versionName;
						if ((versName != null) && (versName.length() > 0))
						{
							title.append(" v");
							title.append(versName);
							hadVersName = true;
						}
					}
				} catch (NameNotFoundException e) { }

				// Now try to get build number, from res/raw/svnversion.txt ; ignore "?"
				InputStream s = null;
				try {
					s = getApplicationContext().getResources().openRawResource(R.raw.svnversion);
					DataInputStream dsql = new DataInputStream(s);
					String svnversion = dsql.readLine();
					dsql.close();
					s.close();
					if ((svnversion != null)
						&& (svnversion.length() > 0)
						&& (! svnversion.equals("?")))
					{
						if (hadVersName)
							title.append('.');
						else
							title.append(" build ");  // do not externalize this string
						title.append(svnversion);
					}
				} catch (Throwable th) {
					if (s != null)
					{
						try {  s.close(); }
						catch (Throwable t2 ) {}
					}
				}

				aboutBuilder.setTitle(title);
				dialog = aboutBuilder.create();
			}
			break;

		default:
			dialog = null;
		}

		return dialog;
	}

	/**
	 * Prompt user if wants to cancel the current trip (if that's possible).
	 * If they confirm, delete it and clear current-trip settings.
	 */
	public void confirmCancelCurrentTrip()
	{
		boolean canCancel = true;
		final Trip currT = VehSettings.getCurrentTrip(db, currV, false);
		TStop currTS = ((currT != null) ? VehSettings.getCurrentTStop(db, currV, false) : null);
		if (currTS != null)
		{
			canCancel = false;
		} else if (currT != null) {
			// Any TStops?
			canCancel = ! currT.hasIntermediateTStops();
		}

		if (! canCancel)
		{
			Toast.makeText(this, R.string.main_cancel_cannot_with_stops, Toast.LENGTH_SHORT).show();
			return;  // <--- Early return: Cannot cancel ---
		}

		// Prompt user if wants to revert back to locObjOrig.
    	AlertDialog.Builder alert = new AlertDialog.Builder(this);

    	alert.setTitle(R.string.confirm);
    	alert.setMessage(R.string.main_cancel_are_you_sure);
    	alert.setPositiveButton(R.string.cancel_trip, new DialogInterface.OnClickListener() {
	    	  public void onClick(DialogInterface dialog, int whichButton)
	    	  {
	    		  final boolean isFreq = currT.isFrequent();
	    		  try
	    		  {
		    		  currT.cancelAndDeleteCurrentTrip();
		    		  VehSettings.setCurrentTrip(db, currV, null);
		    		  if (isFreq)
		    			  VehSettings.setCurrentFreqTrip(db, currV, null);		    		  
	    		  } catch (IllegalStateException e) {}
	    		  checkCurrentDriverVehicleSettings();
	    		  updateDriverVehTripTextAndButtons();
	    	  }
	    	});
    	alert.setNegativeButton(R.string.continu, new DialogInterface.OnClickListener() {
	    	  public void onClick(DialogInterface dialog, int whichButton)
	    	  { }
	    	});
    	alert.show();
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
	 * Sets text of {@link #tvCurrentSet}, {@link #changeDriverOrVeh},
	 * {@link #stopContinue}, etc.
	 * For current roadtrip, show source and destination GeoAreas.
	 * For categorized trip, show the category.
	 */
	private void updateDriverVehTripTextAndButtons()
	{
		GeoArea currA = Settings.getCurrentArea(db, false);
		Person currD = Settings.getCurrentDriver(db, false);
		currV = Settings.getCurrentVehicle(db, false);
		Trip currT = VehSettings.getCurrentTrip(db, currV, true);
		TStop currTS = ((currT != null) ? VehSettings.getCurrentTStop(db, currV, false) : null);
		FreqTrip currFT = VehSettings.getCurrentFreqTrip(db, currV, false);

		final Resources res = getResources();		
		StringBuffer txt = new StringBuffer(res.getString(R.string.driver));
		txt.append(": ");
		txt.append(currD.toString());
		txt.append("\n");
		txt.append(res.getString(R.string.vehicle));
		txt.append(": ");
		txt.append(currV.toString());
		if (currT == null)
		{
			txt.append("\n");
			txt.append(res.getString(R.string.area__colon));
			txt.append(' ');
			txt.append(currA.toString());
			txt.append("\n\n");
			txt.append(res.getString(R.string.main_no_current_trip));
			changeDriverOrVeh.setText(R.string.change_driver_vehicle);
		} else {
			final int destAreaID = currT.getRoadtripEndAreaID();
			txt.append("\n");
			if ((currT != null) && (destAreaID != 0))
				txt.append(res.getString(R.string.main_roadtrip_start_area));
			else
				txt.append(res.getString(R.string.area__colon));
			txt.append(' ');
			txt.append(currA.toString());

			String currTCateg = null;
			if (currT.getTripCategoryID() != 0)
			{
				try
				{
					TripCategory tc = new TripCategory(db, currT.getTripCategoryID());
					currTCateg = " [" + tc.getName() + "]";
				} catch (Throwable th) {}
			}

			txt.append("\n\n");
			if (destAreaID == 0)
			{
				txt.append(res.getString(R.string.main_trip_in_progress));
				if (currTCateg != null)
					txt.append(currTCateg);
			} else {
				txt.append(res.getString(R.string.main_roadtrip_in_progress));
				if (currTCateg != null)
					txt.append(currTCateg);
				txt.append("\n");
				txt.append(res.getString(R.string.main_destination_area));
				txt.append(' ');
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
				txt.append("\n");
				txt.append(res.getString(R.string.main_from_frequent_trip));
				txt.append(' ');
				txt.append(currFT.toString());
			}
			changeDriverOrVeh.setText(R.string.view_drivers_vehicles);
			if (currTS != null)
				stopContinue.setText(R.string.continu_from_stop);
			else
				stopContinue.setText(R.string.stop_continue);
		}

		tvCurrentSet.setText(txt);

		final int visTrip, visNotTrip;
		if (VehSettings.getCurrentTrip(db, currV, false) != null)
		{
			visTrip = View.VISIBLE;
			visNotTrip = View.INVISIBLE;
		} else {			
			visTrip = View.INVISIBLE;
			visNotTrip = View.VISIBLE;
		}
		localTrip.setVisibility(visNotTrip);
		roadTrip.setVisibility(visNotTrip);
		if (Settings.getBoolean(db, Settings.HIDE_FREQTRIP, false))
		{
			freqLocal.setVisibility(View.GONE);
			freqRoad.setVisibility(View.GONE);
		} else {
			freqLocal.setVisibility(visNotTrip);
			freqRoad.setVisibility(visNotTrip);			
		}
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
		// If we have a current trip, ChangeDriverOrVehicle is view-only.
		startActivityForResult
		   (new Intent(Main.this, ChangeDriverOrVehicle.class),
			R.id.main_btn_change_driver_vehicle);
	}

	public void onClick_BtnShowLogbook(View v)
	{
		startActivity(new Intent(Main.this, LogbookShow.class));		
	}

	public void onClick_BtnBackups(View v)
	{
		startActivity(new Intent(Main.this, BackupsMain.class));
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
