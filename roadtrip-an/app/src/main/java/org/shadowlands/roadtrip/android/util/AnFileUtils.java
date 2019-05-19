/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2014 Jeremy D Monin <jdmonin@nand.net>
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

import org.shadowlands.roadtrip.util.FileUtils;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

/**
 * File constants and utility methods: Android extension of shared {@link FileUtils}.
 *<P>
 * The Shadowlands Roadtrip directory on the SDcard is {@link #APP_SD_DIR}.
 */
public class AnFileUtils extends FileUtils
{
	/**
	 * App-specific dir to be placed within SDcard for app usage (backups, exports, etc).
	 *<P>
	 * Value format is <tt>"/app_shortname"</tt> (leading slash, no trailing slash).
	 * @see #getAppSDPath(Context, String)
	 * @see DBBackup#DB_SUBDIR
	 * @since 0.9.20
	 */
	public static final String APP_SD_DIR = "/SLRoadtrip";

	/**
	 * Given our app context, determine the <tt>subdirPath</tt> location, if sdcard and is mounted and writable.
	 * Does not guarantee this directory exists on the SD Card.
	 * Uses {@link #APP_SD_DIR}.
	 *
	 * @param c app context, from {@link Context#getApplicationContext()}
	 * @param subdirPath  Path to subdirectory within {@link #APP_SD_DIR}, such as "/backup".
	 *           To return {@link #APP_SD_DIR} itself, use "" here, not null.
	 * @return full path to the directory, such as <tt>"/sdcard/SLRoadtrip/backup"</tt>,
	 *   or null if SD isn't mounted or we can't get the information
	 * @throws IllegalArgumentException if <tt>subdirPath</tt> is null
	 * @since 0.9.20
	 */
	public static String getAppSDPath(Context appc, final String subdirPath)
		throws IllegalArgumentException
	{
		if (subdirPath == null)
			throw new IllegalArgumentException();
		if (! Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
			return null;
		File sddir = Environment.getExternalStorageDirectory();
		if (sddir == null)
			return null;
		return sddir.getAbsolutePath() + APP_SD_DIR + subdirPath;
	}

    /**
     * Get the amount of free space on this file's or directory's filesystem. 
     * @param fullpath  Full path to a directory or a file
     * @return  Amount of available free space (bytes) on {@code fullpath}'s filesystem, or 0 if the path doesn't exist
     * @since 0.9.20
     */
    public static final long getFreeSpace(final String fullpath)
    {
    	try
    	{
	    	final StatFs sfs = new StatFs(new File(fullpath).getPath());
	    	return sfs.getBlockSize() * (long) sfs.getAvailableBlocks();
    	} catch (Throwable th) {
    		// statfs constructor throws IllegalArgumentException if path doesn't exist,
    		//   this isn't mentioned in its javadoc (as of june 2013); catching general
    		//   throwable in case the undocumented exception ever changes.

    		return 0;
    	}
    }

}
