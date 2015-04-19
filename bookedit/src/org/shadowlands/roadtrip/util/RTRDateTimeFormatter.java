/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2011,2015 Jeremy D Monin <jdmonin@nand.net>
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
 *<P>
 * One purpose of this formatter is to display chronological lists of events, such as in
 * {@link org.shadowlands.roadtrip.model.LogbookTableModel}.  In that model the time of day is shown for each event.
 * The date is shown only when it changes from the previous event, using the
 * {@link #formatDateTimeInSeq(long, DateAndTime)} method.
 *
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

	/**
	 * Format separate date and time strings for a sequence of events: The time is always formatted,
	 * the date is skipped (null) unless it's changed from the previous event's date.  Fields in the
	 * provided {@code dt} structure track the previous month and day, and hold the formatted time and date.
	 *<P>
	 * Always sets {@link DateAndTime#fmtTime dt.fmtTime},
	 * {@link DateAndTime#month dt.month}, and {@link DateAndTime#mday dt.mday}.
	 * @param millis  Date and time of the current event
	 * @param dt   Structure being used to track the month and day of this sequence of events,
	 *          and hold the formatted time (and maybe date) of the current event.
	 *          dt.month, dt.day should be filled in from the previous event,
	 *          or 0 to set them and fmtDate from on the month and day in {@code millis}.
	 * @return true if the month or day changed, and {@link DateAndTime#fmtDate dt.fmtDate}
	 *          was formatted; false otherwise
	 * @since 0.9.40
	 */
	public boolean formatDateTimeInSeq(final long millis, DateAndTime dt)
	{
		final Date dobj = new Date(millis);
		final int dMonth = dobj.getMonth(), dMDay = dobj.getDate();

		final boolean changed = ((dMonth != dt.month) || (dMDay != dt.mday));
		if (changed)
		{
			dt.fmtDate = formatDate(dobj);
			dt.month = dMonth;
			dt.mday = dMDay;
		} else {
			dt.fmtDate = null;
		}
		dt.fmtTime = formatTime(dobj);
		return changed;
	}

	/**
	 * Structure to track and format the month and day of events in a sequence.
	 * Used with {@link RTRDateTimeFormatter#formatDateTimeInSeq(long, DateAndTime)}.
	 * @since 0.9.40
	 */
	public static class DateAndTime
	{
		/** Formatted date, as from {@link RTRDateTimeFormatter#formatDate(Date)}, or null if unchanged */
		public String fmtDate;

		/** Formatted time, as from {@link RTRDateTimeFormatter#formatTime(Date)} */
		public String fmtTime;

		/**
		 * Month/day of month of the time rendered to {@link #fmtTime} and maybe {@link #fmtDate},
		 * used by {@code formatDateTimeInSeq(..)} to determine when they've changed from the previous call. 
		 */
		public int month, mday;
	}

}
