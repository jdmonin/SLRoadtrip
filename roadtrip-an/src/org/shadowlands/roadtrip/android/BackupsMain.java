/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2011,2013-2014 Jeremy D Monin <jdmonin@nand.net>
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
import java.util.ArrayList;
import java.util.Calendar;

import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.android.util.DBBackup;
import org.shadowlands.roadtrip.android.util.AnFileUtils;
import org.shadowlands.roadtrip.android.util.Misc;
import org.shadowlands.roadtrip.db.AppInfo;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.RDBKeyNotFoundException;
import org.shadowlands.roadtrip.db.RDBVerifier;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.Trip;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Backup and Restore activity.
 * Information and button to back up now, list of previous backups.
 * There is a button to change to a different folder and restore from there, which defaults to the user's Download folder.
 * Tapping a backup in the list goes to {@link BackupsRestore} to show more details, validate the backup, and confirm.
 *
 * @author jdmonin
 */
public class BackupsMain extends Activity
	implements OnItemClickListener
{
	// no RDBAdapter field: db is not kept open, so that we can backup/restore it.

	/**
	 * Activity result to indicate a backup was restored; used from {@link BackupsRestore}.
	 * @since 0.9.40
	 */
	public static final int RESULT_BACKUP_RESTORED = Activity.RESULT_FIRST_USER;

	/** Free-space additional margin (64 kB) for {@link #checkFreeSpaceForBackup()}. */
	final private static int FREE_SPACE_MARGIN = 64 * 1024;

	/** Can we write?  Set in {@link #onResume()}. */
	private boolean isSDCardWritable = false;

	/** Most recent trip timestamp in current data, or -1 if none; set in {@link #readDBLastTripTime(RDBAdapter)} */
	private int lastTripDataChange = -1;

	/**
	 * If browsing a different directory to restore from, the full path of that directory. Null otherwise.
	 * @since 0.9.20
	 */
	private String restoreFromDirectory = null;

	/** If true, we've already checked {@link AppInfo#KEY_DB_BACKUP_THISDIR} for {@link #restoreFromDirectory} during our first onResume. */
	private boolean checkedDBBackupDir = false;

	private Button btnBackupNow;
	private TextView tvTimeOfLastBkup, tvTimeOfLastTrip;
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
	    tvTimeOfLastBkup = (TextView) findViewById(R.id.backups_main_timeOfLastBkup);
	    tvTimeOfLastTrip = (TextView) findViewById(R.id.backups_main_timeOfLastTrip);
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

		if (! checkedDBBackupDir)
		{
			try
			{
				// set restoreFromDirectory from optional backup dir db entry, if any
				AppInfo thisBkdir_rec = new AppInfo(db, AppInfo.KEY_DB_BACKUP_THISDIR);
				String bkDir = thisBkdir_rec.getValue();
				tryDirPath(bkDir, false);
				// TODO use it for isSDCardWritable etc
			}
			catch (RDBKeyNotFoundException e) { }
			catch (IllegalStateException e) { }

			checkedDBBackupDir = true;
		}

		isSDCardWritable =
			Environment.MEDIA_MOUNTED.equals
			(Environment.getExternalStorageState());
		final boolean isSDCardReadable =
			isSDCardWritable ||
			Environment.MEDIA_MOUNTED_READ_ONLY.equals
			(Environment.getExternalStorageState());

		readDBLastBackupTime(db, -1);
		final boolean dbContainsData = readDBLastTripTime(db);
		btnBackupNow.setEnabled(isSDCardWritable && dbContainsData);

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
	 * Look in AppInfo db-table for last backup time, update {@link #tvTimeOfLastBkup}.
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
			tvTimeOfLastBkup.setText(R.string.backups_main_no_previous);
		} else {
			if (fmt_dow_shortdate == null)
				fmt_dow_shortdate = Misc.buildDateFormatDOWShort(this, false);
			StringBuffer sb = new StringBuffer(getResources().getString(R.string.backups_main_last_bkuptime));
			sb.append(' ');
			sb.append(DateFormat.format(fmt_dow_shortdate, lasttime * 1000L));
			tvTimeOfLastBkup.setText(sb);
		}
	}

	/**
	 * Read most recent current trip, update {@link #lastTripDataChange}.
	 * @return true if the DB contains data (current vehicle setting exists); return added in v0.9.20.
	 */
	private boolean readDBLastTripTime(RDBAdapter db) {
		try
		{
		final int currVID = Settings.getInt(db, Settings.CURRENT_VEHICLE, -1);
		final Trip mostRecent = Trip.recentInDB(db);
		if (mostRecent == null)
		{
			// lastTripDataChange remains -1.
			return (currVID != -1);
		}

		lastTripDataChange = mostRecent.readLatestTime();
		if (fmt_dow_shortdate == null)
			fmt_dow_shortdate = Misc.buildDateFormatDOWShort(this, false);
		StringBuffer sb = new StringBuffer(getResources().getString(R.string.backups_main_last_triptime));
		sb.append(' ');
		sb.append(DateFormat.format(fmt_dow_shortdate, lastTripDataChange * 1000L));
		tvTimeOfLastTrip.setText(sb);

		return (currVID != -1);

		}
		catch (RuntimeException e) {
			// problem in Settings.getInt or Trip.recentInDB
			return false;
		}
	}

	/**
	 * List the backups currently on the SD card, or at {@link #restoreFromDirectory} if not null.
	 * If the card is not readable, just put a dummy entry to that effect.
	 * Sets the activity titlebar if browsing in a different folder than the default.
	 * @param isSDCardReadable as determined from {@link Environment#getExternalStorageState()}
	 */
	private void populateBackupsList(final boolean isSDCardReadable) {
		String[] bklist;
		if (! isSDCardReadable)
		{
			bklist = new String[1];
			bklist[0] = getResources().getString(R.string.sdcard_not_mounted);
		} else {
			ArrayList<String> bkfiles = DBBackup.getBkFiles(this, restoreFromDirectory);
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

		if (restoreFromDirectory != null)
		{
			final String backups = getResources().getString(R.string.backups);
			setTitle(backups + ": " + restoreFromDirectory);
		} else {
			setTitle(R.string.backups_main_title);
		}
	}

	/**
	 * Check SD-card free space against the current size of the database.
	 * If the check fails, pop up a message to let the user know how much is needed.
	 * @return true if disk-space check passed 
	 * @since 0.9.20
	 */
	private boolean checkFreeSpaceForBackup()
	{
		final String dbBackupsPath =
			(restoreFromDirectory != null) ? restoreFromDirectory : DBBackup.getDBBackupPath(getApplicationContext());
		if (dbBackupsPath == null)
			return false;  // just in case; same conditions are checked elsewhere in this activity

		RDBAdapter db = new RDBOpenHelper(this);
		final String dbFilePath = db.getFilenameFullPath();
		db.close();

		final long dbSize = new File(dbFilePath).length() + FREE_SPACE_MARGIN;
		final long sdFree = AnFileUtils.getFreeSpace(dbBackupsPath);

		if (dbSize <= sdFree)
			return true;

		final long needFreeKB = (dbSize + 1023) / 1024;
		final String sdFreeMsg = getResources().getString
			(R.string.backups_main_cannot_backup_need_free_space__fmt, needFreeKB);

		new AlertDialog.Builder(this).setMessage(sdFreeMsg).setTitle(R.string.backups_main_not_enough_free_space)
			.setCancelable(true).setNegativeButton(android.R.string.cancel, new AlertDialog.OnClickListener() {				
				public void onClick(DialogInterface dialog, int which) { }
			}).show();
		return false;
	}

	/**
	 * Perform all "quick" levels of DB validation
	 *     ({@link RDBVerifier#LEVEL_PHYS} - {@link RDBVerifier#LEVEL_MDATA})
	 * and, if failed, ask the user if should continue with the backup anyway.
	 * @return true if validation passed
	 */
	private boolean doDBValidationAskIfFailed()
	{
		int res = 0;

		/**
		 * Level for successive calls to {@link RDBVerifier}
		 * on {@link #verifCache} from {@link #doDBValidation()}
		 */
		int verifiedLevel = 0;

		RDBOpenHelper db = new RDBOpenHelper(this);

		/** Cached verifier object, for successive manual calls from {@link #doDBValidation()} */
		RDBVerifier verifCache = new RDBVerifier(db);

		// do "quick validation" levels (below LEVEL_TDATA)
		int chkLevel;
		for (chkLevel = RDBVerifier.LEVEL_PHYS; chkLevel < RDBVerifier.LEVEL_TDATA; ++chkLevel)
		{			
			if (verifiedLevel < chkLevel)
			{
				res = verifCache.verify(chkLevel);
				if (res == 0)
					verifiedLevel = chkLevel;
				else
					break;
			}
		}
		verifCache.release();
		db.close();

		if (res == 0)
		{
			return true;  // <--- Early return: all OK ---
		}

		String vLevel;
		switch(chkLevel)
		{
		case RDBVerifier.LEVEL_PHYS:
			vLevel = "Physical (level 1)";
			break;
		case RDBVerifier.LEVEL_MDATA:
			vLevel = "Master-data (level 2)";
			break;
		// case RDBVerifier.LEVEL_TDATA not used here; happens in LogbookShow background task
		default:
			vLevel = "";  // to satisfy compiler
		}
		vLevel += " validation failed at level " + chkLevel + ", back up anyway?";

    	AlertDialog.Builder alert = new AlertDialog.Builder(this);

    	alert.setTitle(R.string.validation_failed);
    	alert.setMessage(vLevel);
    	alert.setPositiveButton(R.string.backup_now, new DialogInterface.OnClickListener() {
			  public void onClick(DialogInterface dialog, int whichButton) {
				  backupNow();
			  }
	    	});
    	alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
	    	  public void onClick(DialogInterface dialog, int whichButton) { }
	    	});
    	alert.show();

    	return false;
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}

	/**
	 * Prompt for a folder path to restore from  If null, default to user's Download directory.
	 * @param v  ignored
	 */
	public void onClick_BtnChangeFolder(View v)
	{
    	AlertDialog.Builder alert = new AlertDialog.Builder(this);

    	final EditText etDirPath = new EditText(this);
    	if (restoreFromDirectory != null)
    		etDirPath.setText(restoreFromDirectory);
    	else
    		etDirPath.setText(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString());

    	alert.setMessage(R.string.backups_main_enter_browse_path);
    	alert.setView(etDirPath);
    	alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
	    	  public void onClick(DialogInterface dialog, int whichButton) { }
	    	});
    	alert.setPositiveButton(R.string.change, new DialogInterface.OnClickListener() {
			  public void onClick(DialogInterface dialog, int whichButton) {
				  boolean doRescan = tryDirPath(etDirPath.getText().toString().trim(), true);
				  if (doRescan)
					  populateBackupsList(true);  // ? isSDCardReadable  -- TODO
			  }
	    	});
    	alert.show();

	}

	/**
	 * Check disk space, do a quick validation of db structure by calling {@link #doDBValidationAskIfFailed()},
	 * and if OK, call {@link #backupNow()}.
	 * @param v  ignored
	 */
	public void onClick_BtnBackupNow(View v)
	{
		if (! checkFreeSpaceForBackup())
			return;

		if (! doDBValidationAskIfFailed())
			return;

		backupNow();
	}

	/**
	 * Checks a directory path to see if it's valid for reading or writing backups.
	 * If so, sets {@link #restoreFromDirectory}.
	 * @param path  Proposed path, or "" for the default ({@link DBBackup#getDBBackupPath(android.content.Context)})
	 * @param showMessage  If true, and there is a problem with {@code path}, show an AlertDialog
	 * @return  True if {@code path} is valid and caller should call {@link #populateBackupsList(boolean)} with the new {@link #restoreFromDirectory}
	 * @since 0.9.20
	 */
	private boolean tryDirPath(String path, final boolean showMessage)
	{
		boolean doRescan = false;

		if (path.length() == 0)
		  {
			  doRescan = (restoreFromDirectory != null);
			  restoreFromDirectory = null;
		  } else {
			  if ((path.length() > 1) && path.endsWith(File.separator))
				  path = path.substring(0, path.length() - 1);  // remove trailing '/'

			  final int errorTextId;

			  File dir = new File(path);
			  if (! dir.exists())
				  errorTextId = R.string.backups_main_folder_was_not_found;
			  else if (! dir.isDirectory())
				  errorTextId = R.string.backups_main_path_is_not_folder;
			  else
				  errorTextId = 0;

			  if (errorTextId == 0)
			  {
				if (! path.equals(restoreFromDirectory))
				{
					doRescan = true;
					restoreFromDirectory = path;
				}
			  } else if (showMessage) {
				AlertDialog.Builder alertErr = new AlertDialog.Builder(BackupsMain.this);
				alertErr.setIcon(android.R.drawable.ic_dialog_alert);  // TODO doesn't show without title text
				alertErr.setMessage(errorTextId);
				alertErr.setCancelable(true);
				alertErr.setNegativeButton(android.R.string.cancel, new AlertDialog.OnClickListener() {				
					public void onClick(DialogInterface dialog, int which) { }
				});
				alertErr.show();
			  }
		  }

		return doRescan;
	}

	/**
	 * Make a backup to sdcard of the database; assumes {@link #doDBValidationAskIfFailed()} already called.
	 */
	private void backupNow()
	{
		StringBuffer sb = new StringBuffer("filename: ");
		final int bktime = (int) (System.currentTimeMillis() / 1000L);
		String bkfile = DBBackup.makeDBBackupFilename(bktime); 
		sb.append(bkfile);
		Toast.makeText(this, sb, Toast.LENGTH_SHORT).show();

		try
		{
			DBBackup.backupCurrentDB(this, restoreFromDirectory);
			Toast.makeText(this, "Backup successful.", Toast.LENGTH_SHORT).show();
			readDBLastBackupTime(null, bktime);
			// TODO how to refresh the list of backups?
		} catch (IOException e)
		{
			Toast.makeText(this, "IOException while saving:\n" + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	// btnSettings is currently not in the layout
	/*
	public void onClick_BtnSettings(View v)
	{
		;  // TODO	show settings window instead of just schema-vers popup
		Toast.makeText(this, "Current db schema version: " + RDBSchema.DATABASE_VERSION, Toast.LENGTH_SHORT).show();
	}
	*/

	/**
	 * When a backup is selected in the list, open its file, show basic info,
	 * close it, and call {@link BackupsRestore} to show more details.
	 * That intent will validate the backup, and ask the user whether to restore from it.
	 */
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		String basePath = (restoreFromDirectory != null) ? restoreFromDirectory : DBBackup.getDBBackupPath(this);

		if (basePath == null)
		{
			Toast.makeText(this, R.string.sdcard_not_mounted, Toast.LENGTH_SHORT).show();
			return;
		}
		if (basePath.equals("/"))
			basePath = "";  // the next line will re-add '/'
		final String bkPath = basePath + File.separator + ((TextView) view).getText();

		File bkFile = new File(bkPath);
		if (bkFile.isDirectory())
		{
			if (bkFile.canRead())
			{
				restoreFromDirectory = bkPath;
				populateBackupsList(true);  // ? isSDCardReadable  -- TODO
			} else {
				Toast.makeText(this, R.string.backups_main_cannot_browse_folder, Toast.LENGTH_SHORT).show();
			}
			return;  // <--- Early return: Tapped a subdirectory ---
		}

		int bkupSchemaVersion = 0;
		try {
			// Use generic open, not RDBOpenHelper, to avoid auto-upgrading the backup file itself
			bkupSchemaVersion = RDBOpenHelper.readSchemaVersion(bkPath);
		} catch (NumberFormatException e) {
			Toast.makeText(this, "Cannot read appinfo(DB_CURRENT_SCHEMAVERSION)", Toast.LENGTH_SHORT).show();
		} catch (ArrayIndexOutOfBoundsException e) {
			Toast.makeText(this, "Opened but cannot read appinfo(DB_CURRENT_SCHEMAVERSION)", Toast.LENGTH_SHORT).show();
		} catch (SQLiteException e) {
			Toast.makeText(this, "Cannot open: " + e, Toast.LENGTH_SHORT).show();
		}

		if (bkupSchemaVersion != 0)
		{
			Intent i = new Intent(this, BackupsRestore.class);
			i.putExtra(BackupsRestore.KEY_FULL_PATH, bkPath);
			i.putExtra(BackupsRestore.KEY_SCHEMA_VERS, bkupSchemaVersion);
			i.putExtra(BackupsRestore.KEY_LAST_TRIPTIME, lastTripDataChange);

			startActivityForResult(i, R.id.backups_main_list);
		}
	}

	/**
	 * Result from {@link BackupsRestore}.
	 * If a backup was restored (result code {@link #RESULT_BACKUP_RESTORED}),
	 * finish this activity which will take us back to {@link Main}.
	 *
	 * @param requestCode  {@link R.id#backups_main_list} when calling {@link BackupsRestore}
	 * @param resultCode  {@link #RESULT_BACKUP_RESTORED} or {@link Activity#RESULT_CANCELED}
	 * @param idata  intent containing any result extras; ignored here
	 */
	@Override
	public void onActivityResult(final int requestCode, final int resultCode, Intent idata)
	{
		if ((resultCode != RESULT_BACKUP_RESTORED) || (requestCode != R.id.backups_main_list))
			return;

		finish();
	}

}
