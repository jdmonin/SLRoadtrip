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

package org.shadowlands.roadtrip.util.android;

import java.util.Date;

import org.shadowlands.roadtrip.android.util.Misc;
import org.shadowlands.roadtrip.util.RTRDateTimeFormatter;

import android.content.Context;
import android.text.format.DateFormat;

/**
 * Android-specific date/time formatting, based on user preference settings.
 * @author jdmonin
 */
public class RTRAndroidDateTimeFormatter
	extends RTRDateTimeFormatter
{
	/**
	 * date formatter for use by {@link DateFormat#format(CharSequence, long)},
	 * initialized via {@link Misc#buildDateFormatDOWShort(Context, boolean)}.
	 */
	private StringBuffer fmt_dow_meddate;

	/**
	 * Use the Android-specific date/time formats.
	 * Date format will be: Sat 9 Jun 2001 (YMD order depends on user settings).
	 * @param ctx App context for date/time preferences, from {@link android.app.Activity#getApplicationContext()}
	 */
	public RTRAndroidDateTimeFormatter(Context ctx)
	{
		// dfd is unused; was = android.text.format.DateFormat.getDateFormat(ctx),
		//   but getDateFormat always gives ##/##/## or ##/##/####, never day of week.
		fmt_dow_meddate = Misc.buildDateFormatDOWMed(ctx);
		dft = android.text.format.DateFormat.getTimeFormat(ctx);
	}

	/**
	 * Date format will be: Sat 9 Jun 2001 (YMD order depends on user settings).
	 */
	public String formatDate(final long millis)
	{
		return DateFormat.format(fmt_dow_meddate, millis).toString();
	}

	/**
	 * Date format will be: Sat 9 Jun 2001 (YMD order depends on user settings).
	 */
	public String formatDate(final Date dt)
	{
		return DateFormat.format(fmt_dow_meddate, dt.getTime()).toString();
	}

}
