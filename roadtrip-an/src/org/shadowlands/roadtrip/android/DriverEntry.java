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
import org.shadowlands.roadtrip.db.GeoArea;
import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Enter information about a driver.
 * Read-only if current trip.
 *<P>
 * <b>When no intent extras are used:</b><BR>
 * When OK is pressed, check if CURRENT_VEHICLE is set.
 * If not set, go to {@link VehicleEntry Vehicle Entry}.
 * Otherwise, go to Main.
 * Also set CURRENT_DRIVER if not already set.
 *<P>
 * <b>When {@link #EXTRAS_FLAG_ASKED_NEW} is set:</b><BR>
 * Wait for the new driver to be entered.
 * Finish this activity and return to what the user was previously doing.
 * The Result code will be set to RESULT_OK, and the Intent will get
 * an int extra called "_id" with the ID of the newly added driver.
 *<P>
 * <b>When {@link #EXTRAS_INT_EDIT_ID} is set:</b><BR>
 * Edit the specified person.
 * Finish this activity and return to what the user was previously doing.
 * The Result code will be set to RESULT_OK, and the Intent will get
 * an int extra called "_id" with the ID of the edited person.
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

	/** If true, don't hide the Local GeoArea info prompt. */
	private boolean doingInitialSetup;

	/**
	 * If true, {@link #EXTRAS_FLAG_ASKED_NEW} was set.
	 */
	private boolean cameFromAskNew;

	/**
	 * Are we on a trip? Is the {@link Settings#getCurrentTrip(RDBAdapter, boolean)} != null?
	 */
	private boolean hasCurrentTrip;

	/**
	 * If not null, {@link #EXTRAS_INT_EDIT_ID} was set to this person's ID,
	 * they was in the database, and we're editing them.
	 */
	private Person cameFromEdit_person = null;

	private EditText name, comment;
	private RDBAdapter db;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.driver_entry);
        name = (EditText) findViewById(R.id.driver_entry_name);
        comment = (EditText) findViewById(R.id.driver_entry_comment);

        db = new RDBOpenHelper(this);

        int cameFromEdit_id;
		Intent i = getIntent();
		if (i != null)
		{
			cameFromAskNew = i.getBooleanExtra(EXTRAS_FLAG_ASKED_NEW, false);
			cameFromEdit_id = i.getIntExtra(EXTRAS_INT_EDIT_ID, 0);
		} else {
	        cameFromAskNew = false;
	        cameFromEdit_id = 0;
		}

		cameFromEdit_person = null;
		if ((cameFromEdit_id != 0) && ! cameFromAskNew)
		{
			try
			{
				cameFromEdit_person = new Person(db, cameFromEdit_id);
				name.setText(cameFromEdit_person.getName());
				String s = cameFromEdit_person.getComment();
				if ((s != null) && (s.length() > 0))
					comment.setText(s);
			} catch (Throwable e) {
				// should not happen
				Toast.makeText(this, R.string.not_found, Toast.LENGTH_SHORT).show();
				setResult(Activity.RESULT_CANCELED);
				finish();
				return;   // <--- Early return: Could not load from db to view fields ---
			}
		}

		// Unless we're doing initial setup, hide local-geoarea name field
		doingInitialSetup = (cameFromEdit_person == null) && ! cameFromAskNew;
		if (! doingInitialSetup)
		{
			View v = findViewById(R.id.driver_entry_init_geoarea_row);
			if (v != null)
				v.setVisibility(View.GONE);
			v = findViewById(R.id.driver_entry_init_geoarea_prompt);
			if (v != null)
				v.setVisibility(View.GONE);
		}

		hasCurrentTrip = (null != Settings.getCurrentTrip(db, false));
		if (hasCurrentTrip)
		{
			setTitle(R.string.view_drivers);
			name.setEnabled(false);
			comment.setEnabled(false);
		}
    }

    /**
     * Validate values and either go to the next intent, or
     * set result if {@link #EXTRAS_FLAG_ASKED_NEW} was set.
     */
    public void onClick_BtnOK(View v)
    {
    	if (hasCurrentTrip)
    	{
        	finish();
        	return;
    	}

    	Editable value = name.getText();
    	if (value.length() == 0)
    	{
    		name.requestFocus();
    		Toast.makeText(this, R.string.driver_entry_prompt, Toast.LENGTH_SHORT).show();
    		return;
    	}

    	if (doingInitialSetup)
    	{
    		// Set our home area

    		EditText etHomeGeoArea = (EditText) findViewById(R.id.driver_entry_init_local_geoarea);
        	String name = etHomeGeoArea.getText().toString().trim();
        	if (name.length() == 0)
        	{
        		etHomeGeoArea.requestFocus();
        		Toast.makeText(this, R.string.driver_entry_init_geoarea_toast, Toast.LENGTH_SHORT).show();
        		return;        		
        	}
        	GeoArea homeArea;
        	GeoArea current = Settings.getCurrentArea(db, false);
        	if (current == null)
        	{
        		GeoArea[] all_one = GeoArea.getAll(db, -1);
	        	if (all_one == null)
	        	{
	        		homeArea = new GeoArea(name);
	        		homeArea.insert(db);
	        	} else {
	        		homeArea = all_one[0];
	        		homeArea.setName(name);
	        		homeArea.commit();
	        	}
        		Settings.setCurrentArea(db, homeArea);
        	} else {
        		homeArea = current;
        		homeArea.setName(name);
        		homeArea.commit();
        	}
    	}

    	String comm = comment.getText().toString().trim();
    	if (comm.length() == 0)
    		comm = null;

  		Person p;
  		if (cameFromEdit_person != null)
  		{
  			p = cameFromEdit_person;
  			p.setName(value.toString());
  			p.setComment(comm);
  			p.commit();
  		} else {
  			p = new Person(value.toString(), true, null, comm);
  			p.insert(db);
  		}

    	if (! Settings.exists(db, Settings.CURRENT_DRIVER))
    	{
    		Settings.setCurrentDriver(db, p);
    	}

    	// where to go next?
    	if (doingInitialSetup)
    	{
    		// Initial setup of app
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
	    	i.putExtra("_id", p.getID());
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