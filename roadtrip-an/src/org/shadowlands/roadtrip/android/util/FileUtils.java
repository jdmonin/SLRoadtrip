/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  Copyright (C) 2010 Jeremy D Monin <jdmonin@nand.net>
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

/**
 * File utility methods.
 *<P>
 * {@link #getFileNames(String, String, int)}, {@link #copyFile(File, File)} and {@link #copyFile(String, String, boolean)} are
 * adapted from the AndiCar project; you can see their version at
 * <A href="https://code.google.com/p/andicar/source/browse/src/org/andicar/persistence/FileUtils.java"
 *  >https://code.google.com/p/andicar/source/browse/src/org/andicar/persistence/FileUtils.java</A> .
 */
public class FileUtils
{
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
     * @throws IOException if an error occurred when opening, closing, reading, or writing
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
     * @param fromFilePath  Full path to source file
     * @param toFilePath    Full path to destination file
     * @return true if OK, false if couldn't copy (SecurityException, etc)
     * @throws IOException if an error occurred when opening, closing, reading, or writing
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

}
