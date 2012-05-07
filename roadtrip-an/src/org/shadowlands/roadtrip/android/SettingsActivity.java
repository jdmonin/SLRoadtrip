/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2012 Jeremy D Monin <jdmonin@nand.net>
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
import org.shadowlands.roadtrip.db.*;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.os.Bundle;
import android.widget.CheckBox;

/**
 * Settings activity.
 *
 * @author jdmonin
 * @since 0.9.12
 */
public class SettingsActivity extends Activity
{
	private RDBAdapter db = null;

	/** Checkbox for <tt>REQUIRE_TRIPCAT</tt> */
	private CheckBox cbReqTripCat;

	/** Checkbox for <tt>HIDE_FREQTRIP</tt> */
	private CheckBox cbHideFreqtrip;

	/** Checkbox for <tt>HIDE_VIA</tt> */
	private CheckBox cbHideVia;

	/** Called when the activity is first created.
	 * See {@link #onResume()} for remainder of init work,
	 * which includes checking the current settings
	 * and updating widgets on-screen as appropriate.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings_activity);

		cbHideFreqtrip = (CheckBox) findViewById(R.id.cb_set_hide_freqtrip);
		cbHideVia = (CheckBox) findViewById(R.id.cb_set_hide_via);
		cbReqTripCat = (CheckBox) findViewById(R.id.cb_req_tripcat); 
		db = new RDBOpenHelper(this);

		// see onResume for rest of initialization.
	}

	/**
	 * Check Settings table for <tt>REQUIRE_TRIPCAT</tt>.  Set {@link #cbReqTripCat}.
	 * Also <tt>HIDE_FREQTRIP</tt> and <tt>HIDE_VIA</tt>.
	 */
	@Override
	public void onResume()
	{
		super.onResume();
		boolean b = Settings.getBoolean(db, Settings.HIDE_FREQTRIP, false);
		cbHideFreqtrip.setChecked(b);
		b = Settings.getBoolean(db, Settings.HIDE_VIA, false);
		cbHideVia.setChecked(b);
		b = Settings.getBoolean(db, Settings.REQUIRE_TRIPCAT, false);
		cbReqTripCat.setChecked(b);
	}

	/**
	 * Update <tt>REQUIRE_TRIPCAT</tt> in db, if different from {@link #cbReqTripCat}.
	 * Also <tt>HIDE_FREQTRIP</tt> and <tt>HIDE_VIA</tt>.
	 */
	@Override
	public void onPause()
	{
		super.onPause();

		final boolean db_hideFreqtrip = Settings.getBoolean(db, Settings.HIDE_FREQTRIP, false);
		final boolean cb_hideFreqtrip = cbHideFreqtrip.isChecked();
		if (db_hideFreqtrip != cb_hideFreqtrip)
			Settings.setBoolean(db, Settings.HIDE_FREQTRIP, cb_hideFreqtrip);

		final boolean db_hideVia = Settings.getBoolean(db, Settings.HIDE_VIA, false);
		final boolean cb_hideVia = cbHideVia.isChecked();
		if (db_hideVia != cb_hideVia)
			Settings.setBoolean(db, Settings.HIDE_VIA, cb_hideVia);

		final boolean db_reqTripCat = Settings.getBoolean(db, Settings.REQUIRE_TRIPCAT, false);
		final boolean cb_reqTripCat = cbReqTripCat.isChecked();
		if (db_reqTripCat != cb_reqTripCat)
			Settings.setBoolean(db, Settings.REQUIRE_TRIPCAT, cb_reqTripCat);

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
