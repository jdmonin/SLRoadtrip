/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010,2012,2014 Jeremy D Monin <jdmonin@nand.net>
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

package org.shadowlands.roadtrip;

import org.shadowlands.roadtrip.android.BackupsMain;
import org.shadowlands.roadtrip.android.DriverEntry;
import org.shadowlands.roadtrip.android.Main;
import org.shadowlands.roadtrip.android.VehicleEntry;
import org.shadowlands.roadtrip.android.util.Misc;
import org.shadowlands.roadtrip.db.GeoArea;
import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.RDBSchema;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.VehSettings;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Create db/schema if needed.
 * Check database for preferences and current settings:
 * Check for geoarea, driver, and vehicle already in db.
 * If this data is missing, show buttons to Continue to enter these, or Restore from Backup.
 * Otherwise, go immediately to {@link Main} activity.
 *
 * @author jdmonin
 */
public class AndroidStartup extends Activity
{
	/** android log tag */
	public static final String TAG = "Roadtrip.AndroidStartup";

	private RDBAdapter db;
	private TextView tv;
	boolean missingSettings = false;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.android_startup);
        tv = (TextView) findViewById(R.id.textview_tmpText);

        // See onResume for the rest of initialization.
    }

    /**
     * See if the db is missing any settings.  If not, go to {@link Main} activity.
     *<P>
     * Called when first created, or from the Back button from {@link BackupsMain}
     * (which might have restored the db from a backup).
     */
	@Override
	public void onResume()
	{
		super.onResume();

        // pointer to retrieve schema sql text, if needed
    	RDBOpenHelper.dbSQLRsrcs = getApplicationContext().getResources();
        db = new RDBOpenHelper(this);

        // read from DB; this will call back to create or upgrade the schema if needed.
        // Check for current settings, to prompt for data entry if needed.
        // TODO move default-geoarea creation to a generic android class
        {
        	RDBSchema.SettingsCheckResult rv
        		= RDBSchema.checkSettings(db, RDBSchema.SettingsCheckLevel.SETT_VEHICLE);
        	int ret = rv.result;
        	if (ret == RDBSchema.SettingsCheckLevel.SETT_GEOAREA)
        	{
        		// create it and re-check
        		Vehicle currV = Settings.getCurrentVehicle(db, true);
        		if (currV != null)
        		{
	        		final String homearea = getResources().getString(R.string.home_area);
	        		GeoArea a = new GeoArea(homearea);
	        		a.insert(db);
	        		VehSettings.setCurrentArea(db, currV, a);
	        		rv = RDBSchema.checkSettings(db, RDBSchema.SettingsCheckLevel.SETT_VEHICLE);
	        		ret = rv.result;
        		}
        	}

        	missingSettings = (ret > RDBSchema.SettingsCheckLevel.SETT_OK);
        	final boolean recovered =
        		(ret < RDBSchema.SettingsCheckLevel.SETT_OK);
        	if (rv.messages != null) {
        		Misc.logInfoMessages(TAG, rv.messages, recovered);
        	}
        	if (ret == RDBSchema.SettingsCheckLevel.SETT_RECOV_GUESSED) {
        		// TODO which one(s)?
				Toast.makeText
				  (this, getResources().getString(R.string.androidstartup_verifyCurrV),
				   Toast.LENGTH_SHORT).show();
				Toast.makeText
			      (this, getResources().getString(R.string.androidstartup_verifyCurrD),
			       Toast.LENGTH_SHORT).show();
        	}
        }

        if (RDBOpenHelper.dbSQLRsrcs != null)
        {
        	// done using it by now, if we needed it at all
        	RDBOpenHelper.dbSQLRsrcs = null;
        }

        if (missingSettings)
        {
        	String txt = getResources().getString(R.string.androidstartup_pleaseCreate);
        	tv.setText(txt);

        	// See onClick_BtnContinue for next data-entry intent when user hits Continue button
        } else {
			Intent intent = new Intent(AndroidStartup.this, Main.class);
			startActivity(intent);
			finish();
        }
        
    }

    /** Continue button: Begin entering initial information */
    public void onClick_BtnContinue(View v)
    {
    	Intent intent;  // where to go?
    	if (null == Person.getMostRecent(db, true))
    		intent = new Intent(AndroidStartup.this, DriverEntry.class);
    	else if (! Settings.exists(db, Settings.CURRENT_VEHICLE))
    		intent = new Intent(AndroidStartup.this, VehicleEntry.class);
    	else
    		intent = new Intent(AndroidStartup.this, Main.class);

    	startActivity(intent);
    	if (missingSettings)
    		finish();
    }

    /** Restore button: Restore an earlier backup */
    public void onClick_BtnRestore(View v)
    {
    	startActivity(new Intent(this, BackupsMain.class));
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