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

package org.shadowlands.roadtrip.android.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Vector;

import org.shadowlands.roadtrip.db.Trip;
import org.shadowlands.roadtrip.model.LogbookTableModel;

import android.content.Context;
import au.com.bytecode.opencsv.CSVWriter;

/**
 * Utilities for {@link LogbookTableModel} trip data export.
 * @author jdmonin
 * @since 0.9.20
 */
public class DBExport {

	/**
	 * db export dir within {@link FileUtils#APP_SD_DIR} directory.
	 * Used by {@link #getDBExportPath(Context)}.
	 *<P>
	 * Value format is "/export".
	 */
	public static final String EXP_SUBDIR = "/export";

	/**
	 * Given our app context, determine the export location, if sdcard and is mounted and writable.
	 * Does not guarantee this directory exists on the SD Card.
	 * Uses {@link FileUtils#APP_SD_DIR} and {@link #EXP_SUBDIR}.
	 *
	 * @param c app context, from {@link Context#getApplicationContext()}
	 * @return path to an export dir, such as <tt>"/sdcard/SLRoadtrip/export"</tt>,
	 *   or null if SD isn't mounted or we can't get the information.
	 */
	public static String getDBExportPath(Context appc)
	{
		return FileUtils.getAppSDPath(appc, EXP_SUBDIR);
	}

	/** Export file suffix ".csv" */
	public static final String DBEXPORT_FILENAME_SUFFIX = ".csv";

	/**
	 * Export the current trip data from <tt>ltm</tt> to a new file.
	 *
	 * @param ctx  Context from which to obtain db info
	 * @param ltm  Logbook with trip data to export; {@link LogbookTableModel#trip_simple_mode} must be true
	 * @param fname  Filename to create (short name only, not a path) within {@link #EXP_SUBDIR}.
	 *               Suggested suffix is {@link #DBEXPORT_FILENAME_SUFFIX}.
	 * @throws IllegalStateException if SDCard isn't mounted or isn't writeable, or if LTM not simple mode
	 * @throws IOException if an error occurs
	 */
	public static void exportTripData(Context ctx, LogbookTableModel ltm, final String fname)
		throws IllegalStateException, IOException
	{
		if (! ltm.trip_simple_mode)  // check flag, check ltm != null
			throw new IllegalStateException("LTM not simple mode");

		/**
		 * First, check paths
		 */
		final String toExpDir = getDBExportPath(ctx);
		if (toExpDir == null)
		{
			throw new IllegalStateException("not mounted or writeable");
		}
		StringBuffer toFilePath = new StringBuffer(toExpDir);
		// Make export directory if needed
		{
			File fdir = new File(toExpDir);
			if (! fdir.exists())
			{
				if (! fdir.mkdirs())
					throw new IOException("Could not create directory: " + toExpDir);
			}
			else if (! fdir.isDirectory())
			{
				throw new IOException("Not a directory: " + toExpDir);				
			}
		}
		toFilePath.append('/');
		toFilePath.append(fname);

		/**
		 * Do the actual export.
		 */
		try
		{
			CSVWriter writer = new CSVWriter(new FileWriter(toFilePath.toString()));
			writer.writeNext(LogbookTableModel.COL_HEADINGS_SIMPLE);
			final int L = ltm.getRangeCount();
			for (int i = 0; i < L; ++i)
			{
				Trip.TripListTimeRange ttr = ltm.getRange(i);
				Vector<String[]> tRows = ttr.tText;
				for (String[] row : tRows)
					writer.writeNext(row);
			}
			writer.close();
		} catch (IOException e)
		{
			throw e;  // <--- Problem occurred ---
		}
	}

}
