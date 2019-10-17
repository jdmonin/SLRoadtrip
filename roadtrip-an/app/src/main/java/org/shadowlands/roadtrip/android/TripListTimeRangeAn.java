/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *  This file Copyright (C) 2019 Jeremy D Monin <jdmonin@nand.net>
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

import java.util.List;

import org.shadowlands.roadtrip.db.TStop;
import org.shadowlands.roadtrip.db.Trip;
import org.shadowlands.roadtrip.db.Trip.TripListTimeRange;
import org.shadowlands.roadtrip.model.LogbookTableModel;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;

/**
 * Subclass of {@link TripListTimeRange} for android, which uses
 * {@link SpannableStringBuilder} to highlight location matches
 * for {@link #getTripRowsTabbed(int)}.
 *<P>
 * To use this class, be sure to set {@link TripListTimeRange#factory}
 * to a {@link TripListTimeRangeAn.FactoryAn}.
 *<P>
 * When updating this code or the {@link TripListTimeRange} data structures it uses,
 * be sure its fallback still works by temporarily changing {@code getTripRowsTabbed_idx(..)}
 * to return {@code super.getTripRowsTabbed_idx(..)}.
 *
 * @since 0.9.90
 */
class TripListTimeRangeAn extends TripListTimeRange
{
	/**
	 * Constructor from a list of trips.
	 * Public callers use the factory.
	 * @param time_start First trip's start time, from {@link Trip#getTime_start()}
	 * @param time_end  Last trip's <b>start</b> time (not end time)
	 * @param trips  List of trips
	 */
	protected TripListTimeRangeAn(int time_start, int time_end, List<Trip> trips)
	{
		super(time_start, time_end, trips);
	}

	/**
	 * Constructor from a list of trips, with optional search location ID.
	 * Public callers use the factory.
	 * @param trips  List of trips
	 * @param matchLocID  Optional Location ID (for results of searching by location), or -1;
	 *	if provided, this range will track which {@link TStop}s use this Location ID.
	 */
	protected TripListTimeRangeAn(List<Trip> trips, final int matchLocID)
	{
		super(trips, matchLocID);
	}

	/**
	 * For {@link #getTripRowsTabbed(int)},
	 * get a Trip's rows by trip index within this TripListTimeRange.
	 * Uses {@link SpannableStringBuilder} to highlight location matches.
	 * If not in Location mode, or if no matches, calls super implementation
	 * which returns a {@link StringBuilder}.
	 *
	 * @param i  Index into {@link #trBeginTextIdx} and {@link #tr}
	 * @return  Formatted string for the Trip, with rows separated by \n
	 *     and columns separated by \t
	 */
	protected CharSequence getTripRowsTabbed_idx(final int i)
	{
		final boolean chkMatches = (matchLocID != -1)
		     && (tMatchedRows != null) && ! tMatchedRows.isEmpty();

		if (! chkMatches)
			return super.getTripRowsTabbed_idx(i);

		SpannableStringBuilder sb = new SpannableStringBuilder();
		final int rNextTr;
		if ((i + 1) < trBeginTextIdx.length)
			rNextTr = trBeginTextIdx[i + 1];
		else
			rNextTr = tText.size();

		for (int r = trBeginTextIdx[i]; r < rNextTr; ++r)
		{
			final String[] rstr = tText.elementAt(r);
			if (rstr[0] != null)
				sb.append(rstr[0]);

			// append rest of non-blank columns; don't append trailing tabs
			int last = rstr.length - 1;
			while ((rstr[last] == null) && (last > 0))
				--last;
			for (int c = 1; c <= last; ++c)
			{
				sb.append('\t');
				if (rstr[c] != null)
				{
					String str = rstr[c];
					final boolean doHighlight =
					    (chkMatches && (c == LogbookTableModel.COL_TSTOP_DESC)
					     && tMatchedRows.contains(Integer.valueOf(r)));
					if (doHighlight)
					{
						int idx0 = sb.length();
						if (tMatchedRowLocNameOffset != null)
						{
							Integer offs = tMatchedRowLocNameOffset.get(Integer.valueOf(r));
							if (offs != null)
								idx0 += offs;
						}
						sb.append(str);
						sb.setSpan
                                                        (new BackgroundColorSpan(Color.YELLOW),
                                                         idx0, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
					} else {
						sb.append(str);
					}
				}
			}

			if (r < (rNextTr - 1))
				sb.append('\n');
		}

		return sb;
	}

	/**
	 * Factory which constructs {@link TripListTimeRangeAn}s instead of their parent class {@link TripListTimeRange}.
	 */
	public static class FactoryAn implements Trip.TLTRFactory
	{
		public TripListTimeRange newTripListTimeRange(int time_start, int time_end, List<Trip> trips)
		{
			return new TripListTimeRangeAn(time_start, time_end, trips);
		}

		public TripListTimeRange newTripListTimeRange(List<Trip> trips, final int matchLocID)
		{
			return new TripListTimeRangeAn(trips, matchLocID);
		}
	}

}
