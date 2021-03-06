/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2014 Jeremy D Monin <jdmonin@nand.net>
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

package org.shadowlands.roadtrip.android.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.shadowlands.roadtrip.db.AppInfo;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.RDBKeyNotFoundException;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.format.DateFormat;

/**
 * Utilities for DB backup/restore.
 * @author jdmonin
 */
public class DBBackup {

	/**
	 * db backup dir within {@link AnFileUtils#APP_SD_DIR} directory.
	 * Used by {@link #getDBBackupPath(Context)}.
	 *<P>
	 * Value format is "/backup".
	 */
	public static final String DB_SUBDIR = "/backup";

	/**
	 * Given our app context, determine the backup location, if sdcard and is mounted and writable.
	 * Does not guarantee this directory exists on the SD Card.
	 * Uses {@link AnFileUtils#APP_SD_DIR} and {@link #DB_SUBDIR}.
	 *
	 * @param c app context, from {@link Context#getApplicationContext()}
	 * @return path to a backup dir, such as <tt>"/sdcard/SLRoadtrip/db"</tt>,
	 *   or null if SD isn't mounted or we can't get the information.
	 *   <BR>
	 * Early beta versions returned <tt>"/sdcard/app_back/org.shadowlands.roadtrip/db"</tt>.
	 */
	public static String getDBBackupPath(Context appc)
	{
		return AnFileUtils.getAppSDPath(appc, DB_SUBDIR);
	}

	/** prefix "db-" */;
	public static final String DBBACKUP_FILENAME_PREFIX = "db-";

	/** yyyymmdd-hhmm suitable for {@link android.text.format.DateFormat} */
	public static final String DBBACKUP_FILENAME_TIMESTAMP = "yyyyMMdd-kkmm";

	/** suffix ".bak" */
	public static final String DBBACKUP_FILENAME_SUFFIX = ".bak";

	/**
	 * Generate a db backup filename, based on this date & time.
	 * Consists of {@link #DBBACKUP_FILENAME_PREFIX}, {@link #DBBACKUP_FILENAME_TIMESTAMP}, {@link #DBBACKUP_FILENAME_SUFFIX}.
	 * @param unixtime  date and time, in unix format (seconds of {@link System#currentTimeMillis()})
	 * @param sb  append the generated name to this buffer
	 */
	public static String makeDBBackupFilename(final int unixtime)
	{
		StringBuffer sb = new StringBuffer();
		sb.append(DBBACKUP_FILENAME_PREFIX);
		sb.append(DateFormat.format(DBBACKUP_FILENAME_TIMESTAMP, unixtime * 1000L));
		sb.append(DBBACKUP_FILENAME_SUFFIX);
		return sb.toString();
	}

	/**
	 * Copy the current database file to a new backup.
	 * Updates status fields in the database to indicate this,
	 * as directed in the schema's comments.
	 * The DB should be closed before calling this method.
	 *
	 * @param ctx  Context from which to obtain db info
	 * @param dirname  Full path of directory to write to, or {@code null} to use {@link #getDBBackupPath(Context)}
	 * @throws IllegalStateException if SDCard isn't mounted or isn't writeable
	 * @throws IOException if an error occurs
	 * @return  Full path and filename of the new backup file
	 */
	public static String backupCurrentDB(Context ctx, final String dirname)
		throws IllegalStateException, IOException
	{
		/**
		 * First, briefly open database, to get paths and update backup-related fields.
		 */
		RDBAdapter db = new RDBOpenHelper(ctx);
		String fromFilePath = db.getFilenameFullPath();

		final String toFileDir = (dirname != null) ? dirname : getDBBackupPath(ctx);
		if (toFileDir == null)
		{
			db.close();
			throw new IllegalStateException("not mounted or writeable");
		}
		StringBuffer toFilePath = new StringBuffer(toFileDir);
		// Make backup directory if needed
		{
			File fdir = new File(toFileDir);
			if (! fdir.exists())
			{
				if (! fdir.mkdirs())
					throw new IOException("Could not create directory: " + toFileDir);
			}
			else if (! fdir.isDirectory())
			{
				throw new IOException("Not a directory: " + toFileDir);				
			}
		}
		toFilePath.append(File.separatorChar);
		final int thistime = (int) (System.currentTimeMillis() / 1000L);
		final String bkupFile = makeDBBackupFilename(thistime);
		toFilePath.append(bkupFile);

		/**
		 * AppInfo table: Copy "this backup" info to "previous backup",
		 * and update "this backup" info to the one we're doing now.
		 */
		// First: Retrieve "previous backup" in case we need to save it.
		// If the keys aren't found, there is no previous (before most-recent) backup.
		int prevtime = -1, lasttime = -1;
		String prevBkupFile = null, lastBkupFile = null;
		AppInfo prevBkfile_rec = null, prevBktime_rec = null;
		try {
			prevBkfile_rec = new AppInfo(db, AppInfo.KEY_DB_BACKUP_PREVFILE);
			prevBkupFile = prevBkfile_rec.getValue();
			prevBktime_rec = new AppInfo(db, AppInfo.KEY_DB_BACKUP_PREVTIME);
			prevtime = Integer.parseInt(prevBktime_rec.getValue());
		}
		catch (RDBKeyNotFoundException e) { }
		catch (NumberFormatException e) { }

		// Next: Retrieve "this backup" info (most recent), and save it to "previous".
		// If the keys aren't found, there is no most-recent backup.
		AppInfo thisBkdir_rec = null, thisBkfile_rec = null, thisBktime_rec = null;
		String prevBkDir = null;
		try {
			thisBkfile_rec = new AppInfo(db, AppInfo.KEY_DB_BACKUP_THISFILE);
			lastBkupFile = thisBkfile_rec.getValue();
			thisBktime_rec = new AppInfo(db, AppInfo.KEY_DB_BACKUP_THISTIME);
			lasttime = Integer.parseInt(thisBktime_rec.getValue());

			try
			{
				// optional record
				thisBkdir_rec = new AppInfo(db, AppInfo.KEY_DB_BACKUP_THISDIR);
				prevBkDir = thisBkdir_rec.getValue();
			}
			catch (RDBKeyNotFoundException e) { }

			if (prevBkfile_rec != null)
			{
				prevBkfile_rec.setValue(lastBkupFile);
				prevBkfile_rec.commit();
			} else {
				prevBkfile_rec = new AppInfo(AppInfo.KEY_DB_BACKUP_PREVFILE, lastBkupFile);
				prevBkfile_rec.insert(db);
			}
			if (prevBktime_rec != null)
			{
				prevBktime_rec.setValue(thisBktime_rec.getValue());
				prevBktime_rec.commit();
			} else {
				prevBktime_rec = new AppInfo(AppInfo.KEY_DB_BACKUP_PREVTIME, thisBktime_rec.getValue());
				prevBktime_rec.insert(db);
			}
		}
		catch (RDBKeyNotFoundException e) { }
		catch (NumberFormatException e) { }

		// Next: Set the current ones to now
		if (thisBkdir_rec != null)
		{
			thisBkdir_rec.setValue((dirname != null) ? dirname : "");
			thisBkdir_rec.commit();
		} else {
			thisBkdir_rec = new AppInfo(AppInfo.KEY_DB_BACKUP_THISDIR, (dirname != null) ? dirname : "");
			thisBkdir_rec.insert(db);
		}

		if (thisBkfile_rec != null)
		{
			thisBkfile_rec.setValue(bkupFile);
			thisBkfile_rec.commit();
		} else {
			thisBkfile_rec = new AppInfo(AppInfo.KEY_DB_BACKUP_THISFILE, bkupFile);
			thisBkfile_rec.insert(db);
		}

		if (thisBktime_rec != null)
		{
			thisBktime_rec.setValue(Integer.toString(thistime));
			thisBktime_rec.commit();			
		} else {
			thisBkfile_rec = new AppInfo(AppInfo.KEY_DB_BACKUP_THISTIME, Integer.toString(thistime));
			thisBkfile_rec.insert(db);			
		}

		db.close();
		db = null;
		// Note: Can't use prevBkfile_rec or prevBktime_rec beyond this point.

		/**
		 * Do the actual backup.
		 */
		try
		{
			final String toFilePathStr = toFilePath.toString(); 
			AnFileUtils.copyFile(fromFilePath, toFilePathStr, false);

			// notify MediaScanner we created a new file that the user may want to copy off the device
			Intent iBkupFile = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
			iBkupFile.setData(Uri.fromFile(new File(toFilePathStr)));
			ctx.sendBroadcast(iBkupFile);

		} catch (IOException e)
		{
			/**
			 * Backup failed; undo changes to backup-related AppInfo fields.
			 * Then throw the exception.
			 */
			db = new RDBOpenHelper(ctx);

			if (prevtime != -1)
			{
				AppInfo.insertOrUpdate(db, AppInfo.KEY_DB_BACKUP_PREVFILE, prevBkupFile);
				AppInfo.insertOrUpdate(db, AppInfo.KEY_DB_BACKUP_PREVTIME, Integer.toString(prevtime));				
			}
			if (lasttime != -1)
			{
				AppInfo.insertOrUpdate(db, AppInfo.KEY_DB_BACKUP_THISFILE, lastBkupFile);
				AppInfo.insertOrUpdate(db, AppInfo.KEY_DB_BACKUP_THISTIME, Integer.toString(lasttime));
			}
			if (prevBkDir != null)
			{
				AppInfo.insertOrUpdate(db, AppInfo.KEY_DB_BACKUP_THISDIR, prevBkDir);
			}
			else if (thisBkdir_rec != null)
			{
				// Delete the entry we created. Don't reuse thisBkdir_rec: db helper obj is different now
				try
				{
					AppInfo newBkdir_rec = new AppInfo(db, AppInfo.KEY_DB_BACKUP_THISDIR);
					newBkdir_rec.delete();
				}
				catch (RDBKeyNotFoundException ek) { }
			}

			// Done undoing AppInfo backup-status field changes.
			db.close();
			db = null;

			throw e;  // <--- Problem occurred ---
		}

		return toFilePath.toString();
	}

	/**
	 * Restore the current database file from a validated backup.
	 * Before calling, use RDBVerifier to validate the backup, and confirm
	 * with the user that it's OK to overwrite the current data.
	 * The DBs should be closed before calling this method.
	 *
	 * @param fromBackupFilePath Full path to source backup-database file; db must not be open.
	 * @param ctx  Context from which to obtain db info
	 * @throws IOException if an error occurs
	 */
	public static void restoreCurrentDB(Context ctx, final String fromBackupFilePath)
		throws IOException
	{
		/**
		 * First, briefly open database, to get paths and update backup-related fields.
		 */
		RDBAdapter db = new RDBOpenHelper(ctx);
		final String defaultDbFilePath = db.getFilenameFullPath();
		db.close();
		db = null;

		// Confirm backup readable
		File fbak = new File(fromBackupFilePath);
		if (! fbak.exists())
		{
			throw new IOException("Cannot read " + fromBackupFilePath);
		}

		/**
		 * Do the actual restore.
		 */
		AnFileUtils.copyFile(fromBackupFilePath, defaultDbFilePath, true);
		
		// May throw IOException
	}

	/**
	 * Given our app context, get the SD Card backup files list, if any.
	 * Names will be sorted alphabetically if {@code dirname} != {@code null},
	 * otherwise reverse-alphabetically to place the most recent backup at the top of the list.
	 * @param appc  app context, from {@link Context#getApplicationContext()}
	 * @param dirname  Directory to search, or {@code null} to use {@link #getDBBackupPath(Context)}
	 * @return list of filenames, or null if none found or if SD isn't mounted
	 */
	public static ArrayList<String> getBkFiles(Context appc, String dirname)
	{
		final boolean useDBBackupPath = (dirname == null);
		if (useDBBackupPath)
		{
			dirname = getDBBackupPath(appc);
			if (dirname == null)
				return null;
		}
		return AnFileUtils.getFileNames(dirname, null, (useDBBackupPath) ? -1 : 1);
    }

}
