/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010,2012 Jeremy D Monin <jdmonin@nand.net>
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
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.widget.EditText;

// TODO only handles the 1st driver, right now

/**
 * Enter information about a driver.
 *<P>
 * <b>When {@link #EXTRAS_FLAG_ASKED_NEW} is not used:</b><BR>
 * When OK is pressed, check if CURRENT_VEHICLE is set.
 * If not set, go to Vehicle Entry.
 * Otherwise, go to Main.
 * Also set CURRENT_DRIVER if not already set.
 *<P>
 * <b>When {@link #EXTRAS_FLAG_ASKED_NEW} is set:</b><BR>
 * Wait for the new driver to be entered.
 * Finish this activity and return to what the user was previously doing.
 * The Result code will be set to RESULT_OK, and the Intent will get
 * an int extra called "_id" with the ID of the newly added driver.
 */
public class DriverEntry extends Activity
{
	/** Flag to show we already have a driver entered,
	 *  but the user asked to enter a new driver;
	 *  for {@link Intent#putExtra(String, boolean)}.
	 */
	public static final String EXTRAS_FLAG_ASKED_NEW = "new";

	/**
	 * Int extra for a person ID to edit here;
	 * for {@link Intent#putExtra(String, int)}.
	 */
	public static final String EXTRAS_INT_EDIT_ID = "edit";

	/**
	 * If true, {@link #EXTRAS_FLAG_ASKED_NEW} was set.
	 */
	private boolean cameFromAskNew;

	private boolean isTextFromDB = false;
	private Person cameFromEdit_person = null;  // only valid if isTextFromDB

	private EditText name;
	private RDBAdapter db;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.driver_entry);
        name = (EditText) findViewById(R.id.driver_entry_name);
        db = new RDBOpenHelper(this);

		Intent i = getIntent();
		if (i != null)
		{
			cameFromAskNew = i.getBooleanExtra(EXTRAS_FLAG_ASKED_NEW, false);
		} else {
	        cameFromAskNew = false;
		}

        if ((name != null) && ! cameFromAskNew)
        {
        	try
        	{
        		Person[] drivers = Person.getAll(db, true);
	        	if (drivers != null)
	        	{
	        		// TODO edit selected, not edit first in db
	        		cameFromEdit_person = drivers[0];
	        		String txt = cameFromEdit_person.getName();
	        		isTextFromDB = true;
	            	name.setText(txt);
	        	}
        	}
        	catch (SQLiteException e)
        	{}

        }
    }

    /**
     * Validate values and either go to the next intent, or
     * set result if {@link #EXTRAS_FLAG_ASKED_NEW} was set.
     */
    public void onClick_BtnOK(View v)
    {
    	Editable value = name.getText();
    	if ((db != null) && (name != null) && (value.length() > 0))
    	{
      		if (isTextFromDB)
      		{
      			cameFromEdit_person.setName(value.toString());
      			cameFromEdit_person.commit();
      		} else {
      			cameFromEdit_person = new Person(value.toString(), true, null, null);
      			cameFromEdit_person.insert(db);
      		}
          	isTextFromDB = true;

        	if (! Settings.exists(db, Settings.CURRENT_DRIVER))
        	{
        		Settings.setCurrentDriver(db, cameFromEdit_person);
        	}
    	}

    	// where to go next?
    	if (! cameFromAskNew)
    	{
	    	Intent intent;
	    	if (Settings.exists(db, Settings.CURRENT_VEHICLE))
	    	{
				intent = new Intent(DriverEntry.this, Main.class);
	    	} else {
				// current vehicle not found
				intent = new Intent(DriverEntry.this, VehicleEntry.class);
	    	}
	    	startActivity(intent);
    	} else {
    		Intent i = getIntent();
	    	i.putExtra("_id", cameFromEdit_person.getID());
	    	setResult(RESULT_OK, i);
    	}
    	finish();
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