/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  Copyright (C) 2011 Jeremy D Monin <jdmonin@nand.net>
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
import org.shadowlands.roadtrip.db.RDBVerifier;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Restore file details activity.
 * Called from {@link BackupsMain}.
 * Validate the backup, show info about it, and ask the user whether to restore from it.
 *<P>
 * The file full path is passed in via <tt>intent.putExtra({@link #KEY_FULLPATH}, value)</tt>.
 *
 * @author jdmonin
 */
public class BackupsRestore extends Activity
{
	// db is not kept open, so we can backup/restore, so no RDBAdapter field.

	/** Use this intent bundle key to give the full path (String) to the backup file. */
	public static final String KEY_FULLPATH = "backupsrestore.fullpath";

	/** tag for Log debugs */
	@SuppressWarnings("unused")
	private static final String TAG = "Roadtrip.BackupsRestore";
	
	private String bkupFullPath = null;
	private boolean alreadyValidated = false;
	private boolean validatedOK = false;
	private ValidateDBTask validatingTask = null;

	private Button btnRestore; // , btnRestoreCancel;

	/**
	 * date formatter for use by {@link DateFormat#format(CharSequence, Calendar)},
	 * initialized via {@link Misc#buildDateFormatDOWShort(Context, boolean)}.
	 */
	private StringBuffer fmt_dow_shortdate;

	/** Called when the activity is first created.
	 * Gets the backup full path via <tt>getIntent().getStringExtra({@link #KEY_FULLPATH})</tt>.
	 * See {@link #onResume()} for remainder of init work,
	 * which includes updating the last-backup time,
	 * checking the SD Card status, etc.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.backups_restore);

	    bkupFullPath = getIntent().getStringExtra(KEY_FULLPATH);
	    if (bkupFullPath == null)
	    {
	    	Toast.makeText(this, R.string.internal__missing_required_bundle, Toast.LENGTH_SHORT).show();
	    	finish();  // <--- End this activity ---
	    	return;
	    }
	    
	    btnRestore = (Button) findViewById(R.id.backups_restore_btn_restore);

	    TextView tvPath = (TextView) findViewById(R.id.backups_restore_filepath);
	    tvPath.setText(bkupFullPath);

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
//		RDBAdapter db = new RDBOpenHelper(this);

//		readDBLastBackupTime(db, -1);

//		db.close();

		if (! alreadyValidated)
		{
			btnRestore.setEnabled(false);
			validatingTask = new ValidateDBTask();
			validatingTask.execute(bkupFullPath);
			// task will update the UI when validation is complete.

		} else {

			TextView vfield = (TextView) findViewById(R.id.backups_restore_validating);
			if (vfield != null)
			{
				if (validatedOK)
					vfield.setText(R.string.backups_restore_validation_ok);
				else
					vfield.setText(R.string.backups_restore_validation_error);
			}	
			btnRestore.setEnabled(validatedOK);
		}
	}

	/** Set the "validating..." textfield to show % percent */
	protected void updateValidateProgress(int percent)
	{
		TextView vfield = (TextView) findViewById(R.id.backups_restore_validating);
		if (vfield == null)
			return;
		String t = getResources().getString(R.string.backups_restore_validating_file)
			+ " " + percent + "%";
		vfield.setText(t);
	}

	/**
	 * Look in AppInfo db-table for last backup time, update {@link #tvTimeOfLast}.
	 * @param db  db conn to read, if <tt>lasttime</tt> == -1
	 * @param lasttime -1 if want to read db; otherwise the last time if known
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
	 */

	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}

	public void onClick_BtnRestore(View v)
	{
		;  // TODO	show settings window instead of just schema-vers popup
		Toast.makeText(this, "Clicked Restore for " + bkupFullPath, Toast.LENGTH_SHORT).show();
	}

    public void onClick_BtnCancel(View v)
    {
    	setResult(RESULT_CANCELED);
    	if ((validatingTask != null) && ! validatingTask.isCancelled())
    		validatingTask.cancel(true);
    	finish();
    }

	/** When a backup is selected in the list */
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		// TODO move this data-gathering into onResume
		// TODO more than this (ask for restore? popup date/time info?)
		final String bkPath = DBBackup.getDBBackupPath(this) + "/" + ((TextView) view).getText();
		SQLiteDatabase bkupDB = null;
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
						Toast.makeText(this, "Schema version: " + Integer.parseInt(c.getString(0)), Toast.LENGTH_SHORT).show();
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
	}

	/** Run db validation on a separate thread. */
	private class ValidateDBTask extends AsyncTask<String, Integer, Void>
	{
		protected Void doInBackground(final String... bkupFullPath)
		{
			RDBAdapter bkupDB = new RDBOpenHelper(BackupsRestore.this, bkupFullPath[0]);
			//		doSomething(bkupDB);  // TODO.  Gather info for fields. See onItemClick, readDBLastBackupTime.

			RDBVerifier v = new RDBVerifier(bkupDB);
			int rc = v.verify(RDBVerifier.LEVEL_MDATA);
			if (rc == 0)
			{
				publishProgress(new Integer(30));
				rc = v.verify(RDBVerifier.LEVEL_TDATA);
			}
			final boolean ok = (0 == rc);  // TODO progress bar
			v.release();
			bkupDB.close();
			Log.d(TAG, "verify: rc = " + rc);

			validatedOK = ok;
			alreadyValidated = true;
			return null;
		}

		protected void onProgressUpdate(Integer... progress) {
			updateValidateProgress(progress[0]);
	    }

		protected void onPostExecute()
		{
			TextView vfield = (TextView) findViewById(R.id.backups_restore_validating);
			if (vfield != null)
			{
				if (validatedOK)
					vfield.setText(R.string.backups_restore_validation_ok);
				else
					vfield.setText(R.string.backups_restore_validation_error);
			}
	
			btnRestore.setEnabled(validatedOK);
	    }
	}
}
