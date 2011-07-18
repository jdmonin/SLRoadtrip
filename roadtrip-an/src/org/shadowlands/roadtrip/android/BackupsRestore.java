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

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.android.util.DBBackup;
import org.shadowlands.roadtrip.android.util.FileUtils;
import org.shadowlands.roadtrip.android.util.Misc;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.RDBSchema;
import org.shadowlands.roadtrip.db.RDBVerifier;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Restore file details activity.
 * Called from {@link BackupsMain}.
 * Validate the backup, show info about it, and ask the user whether to restore from it.
 *<P>
 * Required Intent Extras:
 *<UL>
 *<LI> The file full path - <tt>intent.putExtra({@link #KEY_FULL_PATH}, String)</tt>.
 *<LI> The file's schema version - <tt>intent.putExtra({@link #KEY_SCHEMA_VERS}, int)</tt>,
 *   from <tt>DB_CURRENT_SCHEMAVERSION</tt> in the <tt>appinfo</tt> table of the db.
 *<LI> The current data's most recent trip time - <tt>intent.putExtra({@link #KEY_LAST_TRIPTIME}, int)</tt>,
 *   from current data which would be overwritten, NOT from the backup data.
 *</UL>
 *
 * @author jdmonin
 */
public class BackupsRestore extends Activity
{
	// db is not kept open, so we can restore, so there is no RDBAdapter field in this activity.

	/** Use this intent bundle key to give the full path (String) to the backup file. */
	public static final String KEY_FULL_PATH = "backupsrestore.fullpath";

	/** Schema version of the backup file being restored */
	public static final String KEY_SCHEMA_VERS = "backupsrestore.schemavers";

	/** Most recent trip timestamp (in current data, not data being restored), or -1 if none */
	public static final String KEY_LAST_TRIPTIME = "backupsrestore.triptime";

	/** tag for Log debugs */
	private static final String TAG = "Roadtrip.BackupsRestore";

	private String bkupFullPath = null;

	/** if true, delete the temp-copy at finish */
	private boolean bkupIsTempCopy = false;

	/** Schema version of backup, from {@link RDBSchema}. If -1, too old (very early beta) to restore. */
	private int bkupSchemaVers = 0;

	/** Backup file's timestamp */
	private int bkupAtTime = -1;

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
	 * Gets the backup full path via <tt>getIntent().getStringExtra({@link #KEY_FULL_PATH})</tt>.
	 * Gets the backup schema version via <tt>getIntent().getIntExtra({@link #KEY_SCHEMA_VERS})</tt>.
	 *<P>
	 * If the backup's schema version is old, it is copied here and upgraded to current before continuing.
	 *<P>
	 * See {@link #onResume()} for remainder of init work,
	 * which includes updating the fields on-screen, and calling the verifier.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.backups_restore);

	    final Intent in = getIntent(); 
	    bkupFullPath = in.getStringExtra(KEY_FULL_PATH);
	    bkupSchemaVers = in.getIntExtra(KEY_SCHEMA_VERS, 0);
	    if ((bkupFullPath == null) || (bkupSchemaVers == 0)
	    	|| (0 == in.getIntExtra(KEY_LAST_TRIPTIME, 0)))
	    {
	    	Toast.makeText(this, R.string.internal__missing_required_bundle, Toast.LENGTH_SHORT).show();
	    	finish();  // <--- End this activity ---
	    	return;
	    }
	    
	    btnRestore = (Button) findViewById(R.id.backups_restore_btn_restore);

	    TextView tvPath = (TextView) findViewById(R.id.backups_restore_filepath);
	    tvPath.setText(bkupFullPath);

	    if (bkupSchemaVers < RDBSchema.DATABASE_VERSION)
	    {
	    	// If less than current, copy from bkupFullPath to getCacheDir(),
	    	//   adjust bkupFullPath, and upgrade it after verif(LEVEL_PHYS).
	    	copyAndUpgradeTempFile();
	    }

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
				else if (bkupSchemaVers != -1)
					vfield.setText(R.string.backups_restore_validation_error);
				else
					vfield.setText(R.string.backups_restore_too_old_beta);
			}	
			btnRestore.setEnabled(validatedOK);
		}
	}

	/**
	 * For a backup whose schema version is less than current,
	 * copy from {@link #bkupFullPath} to the cache directory,
	 * verify it {@link RDBVerifier#verify(int) verify}({@link RDBVerifier#LEVEL_PHYS LEVEL_PHYS}),
	 * {@link RDBSchema#upgradeToCurrent(RDBAdapter, int, boolean) upgrade} it,
	 * and update {@link #bkupFullPath} and {@link #bkupIsTempCopy}.
	 *<P>
	 * Called from {@link #onCreate(Bundle)}.
	 */
	private void copyAndUpgradeTempFile()
	{
		boolean ok = false;

		final File cacheDir = this.getCacheDir();
    	// TODO Check disk space vs size before copy, sdcard fallback?

		final File srcBkupFile = new File(bkupFullPath);
		File destTempFile = null;
    	try
    	{
        	destTempFile = File.createTempFile("tmpdb-", ".upg", cacheDir);
    		FileUtils.copyFile(srcBkupFile, destTempFile);
    		bkupIsTempCopy = true;
    		bkupFullPath = destTempFile.getAbsolutePath();

    		// Open db & validate(LEVEL_PHYS)
			RDBAdapter bkupDB = new RDBOpenHelper(this, bkupFullPath);
			RDBVerifier v = new RDBVerifier(bkupDB);
			ok = (0 == v.verify(RDBVerifier.LEVEL_PHYS));
			if (! ok)
			{
				// Maybe it's disk space?
				// TODO fallback to sdcard and retry.
				Toast.makeText(this, R.string.backups_restore_validation_error, Toast.LENGTH_SHORT).show();
			}
			v.release();

    		// upgradeToCurrent
			if (ok)
			{
				try {
					Log.i(TAG, "Calling upgradeToCurrent(\"" + bkupFullPath + "\", " + bkupSchemaVers + ", true)");
					if (RDBOpenHelper.dbSQLRsrcs == null)
				    	RDBOpenHelper.dbSQLRsrcs = getApplicationContext().getResources();
					RDBSchema.upgradeToCurrent(bkupDB, bkupSchemaVers, false);
					Log.i(TAG, "Completed upgradeToCurrent");
				} catch (IllegalStateException e) {
					Toast.makeText(this, R.string.backups_restore_too_old_beta, Toast.LENGTH_LONG).show();
					bkupSchemaVers = -1;
					ok = false;
					Log.e(TAG, "Failed upgradeToCurrent", e);
				} catch (Throwable e) {
					// TODO Toast or something
					ok = false;
					Log.e(TAG, "Failed upgradeToCurrent", e);
				}
			}

			bkupDB.close();
    		// next, if ok, continue with validating it in onResume

    	} catch (IOException e)
    	{
    		// TODO ? Fallback to sdcard and retry?
			Log.e(TAG, "copyAndUpgradeTempFile ioexception: Failed during copy & validation", e);
			Toast.makeText(this, R.string.backups_restore_validation_error, Toast.LENGTH_SHORT).show();
    	}

    	if (! ok)
    	{
    		alreadyValidated = true;
    		validatedOK = false;
    		if (bkupIsTempCopy && (destTempFile != null))
    		{
    			try
    			{
    				if (destTempFile.exists())
    					destTempFile.delete();
    			} catch (Throwable e) {}
    		}

    		TextView vfield = (TextView) findViewById(R.id.backups_restore_validating);
    		if (vfield == null)
    			return;
    		vfield.setText(R.string.backups_restore_validation_error);
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

	/**
	 * Check for recent trip data which would be lost,
	 * and popup to confirm before calling {@link #restoreFromBackupFile()}.
	 * If none, go ahead and call {@link #restoreFromBackupFile()} now,
	 * which will {@link #finish()} this activity.
	 */
	private void checkActivityAndRestoreFromBackupFile()
	{
		final int lastTrip = getIntent().getIntExtra(KEY_LAST_TRIPTIME, 0);
		if (lastTrip <= bkupAtTime)
		{
			restoreFromBackupFile();
			return;  // <--- Early return: Go ahead and restore now ---
		}

    	AlertDialog.Builder alert = new AlertDialog.Builder(this);

    	alert.setTitle(R.string.confirm);
    	alert.setMessage(R.string.backups_restore_more_recent_are_you_sure);
    	alert.setPositiveButton(R.string.restore, new DialogInterface.OnClickListener() {
			  public void onClick(DialogInterface dialog, int whichButton) {
				  restoreFromBackupFile();
			  }
	    	});
    	alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
	    	  public void onClick(DialogInterface dialog, int whichButton) { }
	    	});
    	alert.show();
	}

	/**
	 * Attempt to restore from {@link #bkupFullPath}.
	 * Assumes backup is already validated, and already confirmed by the user.
	 * A popup message will indicate success or failure.
	 * Either way, this method will {@link #finish()} the activity.
	 */
	private void restoreFromBackupFile()
	{
		String msg;
		int titleID;
		try
		{
			DBBackup.restoreCurrentDB(this, bkupFullPath);
			Settings.clearSettingsCache();  // clear currV, etc.
			Log.i(TAG, "Restored db from " + bkupFullPath);
			titleID = R.string.success;
			msg = getResources().getString(R.string.backups_restore_db_successfully_restored);
		} catch (Throwable e) {
			Log.e(TAG, "Error restoring " + bkupFullPath, e);
			titleID = R.string.error;
			msg = "Error restoring " + bkupFullPath + ": " + e;
		}

		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle(titleID);
		alert.setMessage(msg);
		alert.setNeutralButton(android.R.string.ok,  new DialogInterface.OnClickListener() {
			  public void onClick(DialogInterface dialog, int whichButton) {
				BackupsRestore.this.finish();
			  }
	    	});
		alert.show();
	}


	@Override
	public void onDestroy()
	{
		super.onDestroy();
		if (bkupIsTempCopy)
		{
			try
			{
				File tf = new File(bkupFullPath);
				if (tf.exists())
					tf.delete();
			} catch (Throwable e) {}
		}
	}

	public void onClick_BtnRestore(View v)
	{
    	AlertDialog.Builder alert = new AlertDialog.Builder(this);

    	alert.setTitle(R.string.confirm);
    	alert.setMessage(R.string.backups_restore_are_you_sure);
    	alert.setPositiveButton(R.string.restore, new DialogInterface.OnClickListener() {
			  public void onClick(DialogInterface dialog, int whichButton) {
				  checkActivityAndRestoreFromBackupFile();
			  }
	    	});
    	alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
	    	  public void onClick(DialogInterface dialog, int whichButton) { }
	    	});
    	alert.show();
	}

	/** If Cancel is clicked, cancel the validatingTask and finish the activity. */
    public void onClick_BtnCancel(View v)
    {
    	setResult(RESULT_CANCELED);
    	if ((validatingTask != null) && ! validatingTask.isCancelled())
    		validatingTask.cancel(true);
    	finish();
    }

    /** Check with user for {@link KeyEvent#KEYCODE_BACK}, handle it with {@link #onClick_BtnCancel(View)} */
	@Override
	public boolean onKeyDown(final int keyCode, KeyEvent event)
	{
	    if ((keyCode == KeyEvent.KEYCODE_BACK)
	    	&& (event.getRepeatCount() == 0))
	    {
	    	onClick_BtnCancel(null);
	        return true;  // Don't pass to next receiver
	    }

	    return super.onKeyDown(keyCode, event);
	}

	/** Check with user for {@link KeyEvent#KEYCODE_BACK} */
	@Override
	public boolean onKeyUp(final int keyCode, KeyEvent event)
	{
	    if (keyCode == KeyEvent.KEYCODE_BACK)
	    {
	    	// Deal with this key during onKeyDown, not onKeyUp.
	        return true;  // Don't pass to next receiver
	    }
	    return super.onKeyUp(keyCode, event);
	}

	/** Temporary reference code for reading db fields; not used yet in this activity. */
	@SuppressWarnings("unused")
	private void temp_readSomeFields()
	{
		// TODO move this data-gathering into onResume
		// TODO more than this (ask for restore? popup date/time info?)
		final String bkPath = DBBackup.getDBBackupPath(this) + "/somedb.bak";
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
	private class ValidateDBTask extends AsyncTask<String, Integer, Boolean>
	{
		protected Boolean doInBackground(final String... bkupFullPath)
		{
			RDBAdapter bkupDB = new RDBOpenHelper(BackupsRestore.this, bkupFullPath[0]);

			// TODO encapsulate this into AppInfo:
			final int bktime = bkupDB.getRowIntField("appinfo", "aifield", "DB_BACKUP_THISTIME", "aivalue", -1);
			if (bktime != -1)
				bkupAtTime = bktime;
			// TODO else, something's missing from the backup.

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
			publishProgress(new Integer(100));
			Log.d(TAG, "verify: rc = " + rc);

			validatedOK = ok;
			alreadyValidated = true;
			return ok ? Boolean.TRUE : Boolean.FALSE;
		}

		protected void onProgressUpdate(Integer... progress) {
			updateValidateProgress(progress[0]);
	    }

		protected void onPostExecute(Boolean v)
		{
			TextView vfield = (TextView) findViewById(R.id.backups_restore_validating);
			if (vfield != null)
			{
				if (validatedOK)
					vfield.setText(R.string.backups_restore_validation_ok);
				else
					vfield.setText(R.string.backups_restore_validation_error);
			}
	
			vfield = (TextView) findViewById(R.id.backups_restore_bkuptime);
			if (vfield != null)
			{
				if (fmt_dow_shortdate == null)
					fmt_dow_shortdate = Misc.buildDateFormatDOWShort(BackupsRestore.this, false);
				vfield.setText(DateFormat.format(fmt_dow_shortdate, bkupAtTime * 1000L));
			}

			btnRestore.setEnabled(validatedOK);
	    }
	}
}
