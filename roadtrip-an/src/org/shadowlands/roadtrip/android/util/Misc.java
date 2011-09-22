/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  Copyright (C) 2010-2011 Jeremy D Monin <jdmonin@nand.net>
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

import java.util.Calendar;
import java.util.Vector;

import android.content.Context;
import android.text.format.DateFormat;
import android.util.Log;

/** Miscellaneous common static methods for Roadtrip activities. */
public abstract class Misc
{

	/**
	 * DateFormatter (day-of-week + short date) for use by {@link DateFormat#format(CharSequence, Calendar)}.
	 * Format will be: Day-of-week\nshort-date  or  Day-of-week short-date
	 *<P>
	 * @param ctx calling context, to get user's current date format
	 * @param twoLines  If true, format includes <tt>\n</tt> between day of week and short date.
	 * @return a StringBuffer usable in <tt>DateFormat.format</tt>
	 * @see org.shadowlands.roadtrip.util.android.RTRAndroidDateTimeFormatter
	 */
	public static StringBuffer buildDateFormatDOWShort(Context ctx, final boolean twoLines)
	{
		// note use of android.text.format.DateFormat,
		// not java.text.DateFormat, throughout.

		StringBuffer fmt_dow_shortdate = new StringBuffer();
		final char da = DateFormat.DAY;
		final char qu = DateFormat.QUOTE;
		fmt_dow_shortdate.append(da);
		fmt_dow_shortdate.append(da);
		fmt_dow_shortdate.append(da);
		fmt_dow_shortdate.append(da);
		fmt_dow_shortdate.append(qu);
		if (twoLines)
			fmt_dow_shortdate.append('\n');
		else
			fmt_dow_shortdate.append(' ');
		fmt_dow_shortdate.append(qu);
		// year-month-date will be 3 chars: yMd, Mdy, etc
		final char[] ymd_order = DateFormat.getDateFormatOrder(ctx);
		for (char c : ymd_order)
		{
			fmt_dow_shortdate.append(c);
			fmt_dow_shortdate.append(c);
			if (c == DateFormat.YEAR)
			{
				fmt_dow_shortdate.append(c);
				fmt_dow_shortdate.append(c);
			}
			if (c != ymd_order[2])
				fmt_dow_shortdate.append("/");
		}

		return fmt_dow_shortdate;
	}

	/**
	 * DateFormatter (day-of-week + medium date) for use by {@link DateFormat#format(CharSequence, long)}.
	 * Format will be: Sat 9 Jun 2001 (YMD order depends on user settings).
	 *<P>
	 * @param ctx calling context, to get user's current date format
	 * @return a StringBuffer usable in <tt>DateFormat.format</tt>
	 * @see org.shadowlands.roadtrip.util.android.RTRAndroidDateTimeFormatter
	 */
	public static StringBuffer buildDateFormatDOWMed(Context ctx)
	{
		// note use of android.text.format.DateFormat,
		// not java.text.DateFormat, throughout.

		StringBuffer fmt_dow_shortdate = new StringBuffer();
		final char da = DateFormat.DAY;
		final char qu = DateFormat.QUOTE;
		fmt_dow_shortdate.append(da);
		fmt_dow_shortdate.append(qu);
		fmt_dow_shortdate.append(' ');
		fmt_dow_shortdate.append(qu);
		// year-month-date will be 3 chars: yMd, Mdy, etc
		final char[] ymd_order = DateFormat.getDateFormatOrder(ctx);
		for (char c : ymd_order)
		{
			fmt_dow_shortdate.append(c);
			if (c == DateFormat.MONTH)
			{
				fmt_dow_shortdate.append(c);  // for MMM
				fmt_dow_shortdate.append(c);
			}
			else if (c == DateFormat.YEAR)
			{
				fmt_dow_shortdate.append(c);  // for YYYY
				fmt_dow_shortdate.append(c);
				fmt_dow_shortdate.append(c);
			}
			if (c != ymd_order[2])
			{
				fmt_dow_shortdate.append(qu);
				fmt_dow_shortdate.append(' ');
				fmt_dow_shortdate.append(qu);
			}
		}

		return fmt_dow_shortdate;
	}

	/**
	 * Log these strings as Info, using {@link Log#i(String, String)}. 
	 * @param tag  Tag to use for logging
	 * @param msgv  Messages to log; null or empty is OK
	 * @param lastIsWarning  If true, call {@link Log#w(String, String)}
	 *     instead of <tt>Log.i</tt> for the last element of <tt>msgv</tt>.
	 *     If <tt>msgv</tt> contains 1 string, that string will be logged as a warning, not an info.
	 */
	public static void logInfoMessages
		(final String tag, Vector<String> msgv, final boolean lastIsWarning)
	{
		if (msgv == null)
			return;
		int L = msgv.size();
		if (L == 0)
			return;
		if (lastIsWarning)
			--L;
		for (int i = 0; i < L; ++i)
			Log.i(tag, msgv.elementAt(i));
		if (lastIsWarning)
			Log.w(tag, msgv.lastElement());
	}
}
