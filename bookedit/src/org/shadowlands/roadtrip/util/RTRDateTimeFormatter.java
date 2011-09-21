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

package org.shadowlands.roadtrip.util;

import java.text.DateFormat;  // for JavaImpl
import java.util.Date;        // for JavaImpl

/**
 * Wrapper around Java-type or Android-specific date/time formatting.
 * For android-specific date/time formats, use child class util.android.RTRAndroidDateTimeFormatter instead.
 * @author jdmonin
 */
public class RTRDateTimeFormatter
{
	/** Format for date */
	protected java.text.DateFormat dfd;

	/** Format for time of day */
	protected java.text.DateFormat dft;

	/**
	 * Constructor for java locale-generic formatting.
	 * For android-specific date/time formats, use child class util.android.RTRAndroidDateTimeFormatter instead.
	 */
	public RTRDateTimeFormatter()
	{
		dfd = java.text.DateFormat.getDateInstance(DateFormat.MEDIUM);
		dft = java.text.DateFormat.getTimeInstance(DateFormat.SHORT);
	}

	public String formatDate(final long millis)
	{
		return dfd.format(new Date(millis));
	}

	public String formatDate(final Date dt)
	{
		return dfd.format(dt);
	}

	public String formatTime(final long millis)
	{
		return dft.format(new Date(millis));
	}

	public String formatTime(final Date tm)
	{
		return dft.format(tm);
	}
}
