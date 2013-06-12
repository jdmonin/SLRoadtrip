/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2013 Jeremy D Monin <jdmonin@nand.net>
 *
 *  Portions of this file Copyright (C) 2010 Miklos Keresztes (miklos.keresztes@gmail.com)
 *  via the AndiCar project (GPLv3) - see
 *  https://code.google.com/p/andicar/source/browse/src/org/andicar/persistence/FileUtils.java
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.PatternSyntaxException;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

/**
 * File constants and utility methods.
 *<P>
 * The Shadowlands Roadtrip directory on the SDcard is {@link #APP_SD_DIR}.
 *<P>
 * {@link #getFileNames(String, String, int)}, {@link #copyFile(File, File)} and {@link #copyFile(String, String, boolean)} are
 * adapted from the AndiCar project; you can see their version at
 * <A href="https://code.google.com/p/andicar/source/browse/src/org/andicar/persistence/FileUtils.java"
 *  >https://code.google.com/p/andicar/source/browse/src/org/andicar/persistence/FileUtils.java</A> .
 */
public class FileUtils
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
	 * Get a list of filenames in this folder.
	 * @param folder  Full path of directory 
	 * @param fileNameFilterPattern  Regular expression suitable for {@link String#matches(String)}, or null.
	 *      See {@link java.util.regex.Pattern} for more details.
	 * @param sort  Use 1 for {@link String#CASE_INSENSITIVE_ORDER}, 0 for no sort, -1 for reverse sort
	 * @return list of filenames (the names only, not the full path),
	 *     or <tt>null</tt> if <tt>folder</tt> doesn't exist or isn't a directory,
	 *     or if nothing matches <tt>fileNameFilterPattern</tt>
	 * @throws PatternSyntaxException if <tt>fileNameFilterPattern</tt> is non-null and isn't a
	 *     valid Java regular expression
	 */
    public static ArrayList<String> getFileNames
        (final String folder, final String fileNameFilterPattern, final int sort)
    	throws PatternSyntaxException
    {
        ArrayList<String> myData = new ArrayList<String>();
        File fileDir = new File(folder);
        if(!fileDir.exists() || !fileDir.isDirectory()){
            return null;
        }

        String[] files = fileDir.list();

        if(files.length == 0){
            return null;
        }
        for (int i = 0; i < files.length; i++) {
            if(fileNameFilterPattern == null ||
                    files[i].matches(fileNameFilterPattern))
            myData.add(files[i]);
        }
        if (myData.size() == 0)
        	return null;

        if (sort != 0)
        {
        	Collections.sort(myData, String.CASE_INSENSITIVE_ORDER);
        	if (sort < 0)
        		Collections.reverse(myData);
        }

        return myData;
    }

    /**
     * Copy a file's contents.
     * @param fromFilePath  Full path to source file
     * @param toFilePath    Full path to destination file
     * @param overwriteExisting if true, toFile will be deleted before the copy
     * @return true if OK, false if couldn't copy (SecurityException, etc)
     * @throws IOException if an error occurred when opening, closing, reading, or writing;
     *     even after an exception, copyFile will close the files before returning.
     */
    public static boolean copyFile
    	(String fromFilePath, String toFilePath, final boolean overwriteExisting)
    	throws IOException
    {
        try{
            File fromFile = new File(fromFilePath);
            File toFile = new File(toFilePath);
            if(overwriteExisting && toFile.exists())
                toFile.delete();
            return copyFile(fromFile, toFile);
        }
        catch(SecurityException e){
            return false;
        }
    }

    /**
     * Copy a file's contents.
     *TODO per API lookup: FileOutputStream(file) will overwrite desti if exists
     * @param fromFilePath  Full path to source file; should not be open.
     * @param toFilePath    Full path to destination file; should not be open.
     * @return true if OK, false if couldn't copy (SecurityException, etc)
     * @throws IOException if an error occurred when opening, closing, reading, or writing;
     *     even after an exception, copyFile will close the files before returning.
     */
    public static boolean copyFile(File source, File dest)
    	throws IOException
    {
        FileChannel in = null;
        FileChannel out = null;
        try {
            in = new FileInputStream(source).getChannel();
            out = new FileOutputStream(dest).getChannel();

            long size = in.size();
            MappedByteBuffer buf = in.map(FileChannel.MapMode.READ_ONLY, 0, size);

            out.write(buf);
            
            if (in != null)
                in.close();
            if (out != null)
                out.close();
            return true;
        } 
        catch(IOException e){
        	try {
	            if (in != null)
	                in.close();
        	} catch (IOException e2) {}
        	try {
	            if (out != null)
	                out.close();
        	} catch (IOException e2) {}
        	throw e;
        }
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
