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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;

import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.android.util.DBBackup;
import org.shadowlands.roadtrip.android.util.Misc;
import org.shadowlands.roadtrip.db.AppInfo;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.RDBKeyNotFoundException;
import org.shadowlands.roadtrip.db.RDBSchema;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Backup and Restore activity.
 * Information and button to back up now, list of previous backups.
 *
 * @author jdmonin
 */
public class BackupsMain extends Activity
	implements OnItemClickListener
{
	// db is not kept open, so we can backup/restore, so no RDBAdapter field.

	/** Can we write?  Set in {@link #onResume()}. */
	private boolean isSDCardWritable = false;

	private Button btnBackupNow;
	private TextView tvTimeOfLast;
	private ListView lvBackupsList;

	/**
	 * date formatter for use by {@link DateFormat#format(CharSequence, Calendar)},
	 * initialized via {@link Misc#buildDateFormatDOWShort(Context, boolean)}.
	 */
	private StringBuffer fmt_dow_shortdate;

	/** Called when the activity is first created.
	 * See {@link #onResume()} for remainder of init work,
	 * which includes updating the last-backup time,
	 * checking the SD Card status, etc.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.backups_main);
	    setTitle(R.string.backups_main_title);

	    btnBackupNow = (Button) findViewById(R.id.backups_main_btn_backupnow);
	    tvTimeOfLast = (TextView) findViewById(R.id.backups_main_timeOfLast);
	    lvBackupsList = (ListView) findViewById(R.id.backups_main_list);
	    lvBackupsList.setOnItemClickListener(this);

		// see onResume for rest of initialization.
	}

	/**
	 * Set {@link #isSDCardWritable}, {@link #readDBLastBackupTime(RDBAdapter, int)},
	 * {@link #populateBackupsList(boolean)}, etc.
	 */
	@Override
	public void onResume()
	{
		super.onResume();
		RDBAdapter db = new RDBOpenHelper(this);

		isSDCardWritable =
			Environment.MEDIA_MOUNTED.equals
			(Environment.getExternalStorageState());
		final boolean isSDCardReadable =
			isSDCardWritable ||
			Environment.MEDIA_MOUNTED_READ_ONLY.equals
			(Environment.getExternalStorageState());

		btnBackupNow.setEnabled(isSDCardWritable);
		readDBLastBackupTime(db, -1);

		if (! isSDCardReadable)
		{
			populateBackupsList(false);
			Toast.makeText(this, R.string.sdcard_not_mounted, Toast.LENGTH_SHORT).show();
		} else {
			if (! isSDCardWritable)
			{
				Toast.makeText(this, R.string.sdcard_read_only, Toast.LENGTH_LONG).show();
			}
			if (lvBackupsList.getChildCount() < 2)  // refresh if 1 item (previous error msg)
				populateBackupsList(true);
		}
		db.close();
	}

	/**
	 * Look in AppInfo db-table for last backup time, update {@link #tvTimeOfLast}.
	 * @param db  db conn to read, if <tt>lasttime</tt> == -1
	 * @param lasttime -1 if want to read db; otherwise the last time if known
	 */
	private void readDBLastBackupTime(RDBAdapter db, int lasttime) {
		if (lasttime == -1)
		{
			try {
				final AppInfo bktime_rec = new AppInfo(db, AppInfo.KEY_DB_BACKUP_THISTIME);
				lasttime = Integer.parseInt(bktime_rec.getValue());
			}
			catch (RDBKeyNotFoundException e) { }
			catch (NumberFormatException e) { }
		}

		if (lasttime == -1)
		{
			tvTimeOfLast.setText(R.string.backups_main_no_previous);
		} else {
			if (fmt_dow_shortdate == null)
				fmt_dow_shortdate = Misc.buildDateFormatDOWShort(this, false);
			StringBuffer sb = new StringBuffer(getResources().getString(R.string.backups_main_last_bkuptime));
			sb.append(' ');
			sb.append(DateFormat.format(fmt_dow_shortdate, lasttime * 1000L));
			tvTimeOfLast.setText(sb);
		}
	}

	/**
	 * List the backups currently on the SD card.
	 * If the card is not readable, just put a dummy entry to that effect.
	 * @param isSDCardReadable as determined from {@link Environment#getExternalStorageState()}
	 */
	private void populateBackupsList(final boolean isSDCardReadable) {
		String[] bklist;
		if (! isSDCardReadable)
		{
			bklist = new String[1];
			bklist[0] = getResources().getString(R.string.sdcard_not_mounted);
		} else {
			ArrayList<String> bkfiles = DBBackup.getBkFiles(this);
			if (bkfiles == null)
			{
				bklist = new String[1];
				bklist[0] = getResources().getString(R.string.backups_main_folder_nonefound);
			} else {
				bklist = new String[bkfiles.size()];
				bkfiles.toArray(bklist);
			}
		}
		lvBackupsList.setAdapter(new ArrayAdapter<String>(this, R.layout.list_item, bklist));
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}

	/**
	 * Make a backup to sdcard of the database.
	 * @param v  ignored
	 */
	public void onClick_BtnBackupNow(View v)
	{
		StringBuffer sb = new StringBuffer("filename: ");
		final int bktime = (int) (System.currentTimeMillis() / 1000L);
		String bkfile = DBBackup.makeDBBackupFilename(bktime); 
		sb.append(bkfile);
		Toast.makeText(this, sb, Toast.LENGTH_SHORT).show();

		try
		{
			DBBackup.backupCurrentDB(this);
			Toast.makeText(this, "Backup successful.", Toast.LENGTH_SHORT).show();
			readDBLastBackupTime(null, bktime);
			// TODO how to refresh the list of backups?
		} catch (IOException e)
		{
			Toast.makeText(this, "IOException while saving:\n" + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	public void onClick_BtnSettings(View v)
	{
		;  // TODO	show settings window instead of just schema-vers popup
		Toast.makeText(this, "Current db schema version: " + RDBSchema.DATABASE_VERSION, Toast.LENGTH_SHORT).show();
	}

	/**
	 * When a backup is selected in the list, open its file, show basic info,
	 * close it, and call {@link BackupsRestore} to show more details.
	 * That intent will validate the backup, and ask the user whether to restore from it.
	 */
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		final String basePath = DBBackup.getDBBackupPath(this);
		if (basePath == null)
		{
			Toast.makeText(this, R.string.sdcard_not_mounted, Toast.LENGTH_SHORT).show();
			return;
		}
		final String bkPath = basePath + "/" + ((TextView) view).getText();
		boolean looksOK = false;
		SQLiteDatabase bkupDB = null;
		int bkupSchemaVersion = 0;
		Cursor c = null;

		try {
			bkupDB = SQLiteDatabase.openDatabase
				(bkPath, null, SQLiteDatabase.OPEN_READONLY);

			final String[] cols = { "aivalue" };
			c = bkupDB.query("appinfo", cols, "aifield = 'DB_BACKUP_THISTIME' or aifield = 'DB_CURRENT_SCHEMAVERSION'",
					null, null, null, "aifield");
			if (c.moveToFirst())
			{
				java.util.Date bkupDate = new java.util.Date(1000L * c.getLong(0));
				Toast.makeText(this, "Opened. Backup time was: " + bkupDate.toLocaleString(), Toast.LENGTH_SHORT).show();
				if (c.moveToNext())
				{
					try {
						bkupSchemaVersion = Integer.parseInt(c.getString(0));
						Toast.makeText(this, "Schema version: " + bkupSchemaVersion, Toast.LENGTH_SHORT).show();
						looksOK = true;
					} catch (NumberFormatException e) {
						Toast.makeText(this, "Cannot read appinfo(DB_CURRENT_SCHEMAVERSION)", Toast.LENGTH_SHORT).show();
					}
				} else {
					Toast.makeText(this, "Cannot read appinfo(DB_CURRENT_SCHEMAVERSION)", Toast.LENGTH_SHORT).show();
				}
			} else {
				Toast.makeText(this, "Opened but cannot read appinfo(DB_BACKUP_THISTIME)", Toast.LENGTH_SHORT).show();
			}
		} catch (SQLiteException e) {
			Toast.makeText(this, "Cannot open: " + e, Toast.LENGTH_SHORT).show();
		}
		if (c != null)
			c.close();
		if (bkupDB != null)
			bkupDB.close();

		if (looksOK)
		{
			Intent i = new Intent(this, BackupsRestore.class);
			i.putExtra(BackupsRestore.KEY_FULL_PATH, bkPath);
			i.putExtra(BackupsRestore.KEY_SCHEMA_VERS, bkupSchemaVersion);
			startActivity(i);
		}
	}

}
