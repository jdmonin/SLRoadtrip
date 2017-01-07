/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2017 Jeremy D Monin <jdmonin@nand.net>
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.android.util.Misc;
import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.TStop;
import org.shadowlands.roadtrip.db.Trip;
import org.shadowlands.roadtrip.db.TripCategory;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.model.LogbookTableModel;
import org.shadowlands.roadtrip.util.android.RTRAndroidDateTimeFormatter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Dialog builder for {@link LogbookShow} to show detail about one trip.
 *
 * @since 0.9.51
 */
public class LogbookShowTripDetailDialogBuilder
	implements AdapterView.OnItemClickListener
{
	/** Calling context, for resources */
	private final Activity caller;

	private final LogbookTableModel ltm;
	private final RDBAdapter db;

	/** Trip being shown in this dialog. */
	private final Trip tr;

	/** ListView of {@link TStop}s on this trip. Adapter is {@link #lvTSAdapter}. Set in {@link #create()}. */
	private ListView lvTStopsList;

	/** Adapter for {@link #lvTStopsList} */
	private ArrayAdapter<TStopText> lvTSAdapter;

	/**
	 * date formatter for use by {@link DateFormat#format(CharSequence, Calendar)},
	 * initialized via {@link Misc#buildDateFormatDOWMed(Context)}
	 */
	private StringBuffer fmt_dow_meddate;

	/** For hh:mm output, android-specific DateTimeFormatter for locale and user prefs */
	private RTRAndroidDateTimeFormatter dtf;

	/**
	 * Create a Builder. All params must be non-null.
	 * After calling this constructor, call {@link #create()} to get the AlertDialog
	 * and then {@link AlertDialog#show()}.
	 */
	public LogbookShowTripDetailDialogBuilder
		(final Activity caller, final Trip tr, final LogbookTableModel ltm, final RDBAdapter db)
	{
		this.caller = caller;
		this.tr = tr;
		this.ltm = ltm;
		this.db = db;
		dtf = new RTRAndroidDateTimeFormatter(caller.getApplicationContext());
	}

	/**
	 * Build the AlertDialog, to call {@link AlertDialog#show()}.
	 * @return a newly built AlertDialog
	 */
	public AlertDialog create()
	{
		if (fmt_dow_meddate == null)
			fmt_dow_meddate = Misc.buildDateFormatDOWMed(caller);

		final View itms = caller.getLayoutInflater().inflate(R.layout.logbook_show_popup_trip_detail, null);
		TextView tv;

		int tcatID = tr.getTripCategoryID();
		if (tcatID != 0)
		{
			tv = (TextView) itms.findViewById(R.id.logbook_show_popup_trip_detail_categ);
			if (tv != null)
			{
				TripCategory tcat = ltm.getCachedTripCategory(tcatID, db);
				if (tcat != null)
					tv.setText(tcat.getName());
				else
					tcatID = 0;
			}
		}
		if (tcatID == 0)
		{
			View catRow = itms.findViewById(R.id.logbook_show_popup_trip_detail_categ_row);
			catRow.setVisibility(View.GONE);
		}

		tv = (TextView) itms.findViewById(R.id.logbook_show_popup_trip_detail_veh);
		if (tv != null)
		{
			final int vid = tr.getVehicleID();
			Vehicle v = ltm.getVehicle();  // usually all trips' vehicle, except All Vehicles location mode
			if (v.getID() != vid)
			{
				v = null;
				try
				{
					v = new Vehicle(db, vid);
				}
				catch (Exception e) {}  // RDBKeyNotFoundException
			}
			if (v != null)
				tv.setText(v.toString());
		}

		tv = (TextView) itms.findViewById(R.id.logbook_show_popup_trip_detail_driver);
		if (tv != null)
		{
			try
			{
				tv.setText(new Person(db, tr.getDriverID()).toString());
			}
			catch (Exception e) {}  // RDBKeyNotFoundException
		}

		tv = (TextView) itms.findViewById(R.id.logbook_show_popup_trip_detail_distance);
		if (tv != null)
		{
			final int oEnd = tr.getOdo_end();
			if (oEnd == 0) {
				tv.setText(R.string.main_trip_in_progress);
			} else {
				final int oStart = tr.getOdo_start();
				tv.setText(Integer.toString((oEnd/10) - (oStart/10)) + " mi");  // TODO units MI/KM
			}
		}

		tv = (TextView) itms.findViewById(R.id.logbook_show_popup_trip_detail_starting_time);
		if (tv != null)
		{
			final int ts = tr.getTime_start();
			if (ts == 0)
				tv.setText(R.string.none__parens);
			else
				tv.setText(DateFormat.format(fmt_dow_meddate, ts * 1000L)
					   + " " + dtf.formatTime(ts * 1000L));
		}

		lvTStopsList = (ListView) itms.findViewById(R.id.logbook_show_popup_trip_detail_tstop_list);
		if (lvTStopsList != null)
		{
			List<TStop> allTS = tr.readAllTStops();
			if (! tr.isStartTStopFromPrevTrip())
				allTS.remove(0);  // shown as starting location: don't list it twice

			// TODO to show more fields or controls, custom list item layout
			lvTSAdapter =
				new ArrayAdapter<TStopText>
				    (caller, R.layout.list_item, TStopText.fromList(allTS, caller.getResources()));
			lvTStopsList.setAdapter(lvTSAdapter);
			lvTStopsList.setOnItemClickListener(this);
		}

		tv = (TextView) itms.findViewById(R.id.logbook_show_popup_trip_detail_starting_loc);
		if (tv != null)
		{
			String desc = ltm.getTStopLocDescr(tr.readStartTStop(true), db);
			tv.setText(desc);
		}

		AlertDialog bld = new AlertDialog.Builder(caller)
			.setPositiveButton(android.R.string.ok, null)
			.setView(itms)
			.create();
		return bld;
	}

	/** React when a {@link TStop} is tapped in the list. */
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		if (lvTSAdapter == null)
			return;

		TStopText tt = lvTSAdapter.getItem(position);
		if (tt == null)
			return;

		// view TStop details using TripTStopEntry
		Intent i = new Intent(caller, TripTStopEntry.class);
		i.putExtra(TripTStopEntry.EXTRAS_FIELD_VIEW_TSTOP_ID, tt.ts.getID());
		caller.startActivity(i);
	}

	/** TStop's data and rendered text for list adapter */
	private final static class TStopText
	{
		final public TStop ts;
		final public String tsLocText;
		final public String str;

		public TStopText(final TStop ts, final Resources res)
		{
			this.ts = ts;
			tsLocText = ts.readLocationText();

			StringBuilder sb = new StringBuilder();
			int odo = ts.getOdo_trip();
			if (odo != 0)
			{
				// "(12.3)" with auto-localization of decimal separator
				sb.append(res.getString
					    (R.string.logbook_show_popup_trip_detail__odo__parens_float, odo / 10f));
				sb.append(' ');
			}
			sb.append(tsLocText);
			String comm = ts.getComment();
			if (comm != null)
				sb.append(" [" + comm + "]");

			str = sb.toString();
		}

		public String toString() { return str; }

		public static List<TStopText> fromList(List<TStop> tsl, Resources res)
		{
			List<TStopText> li = new ArrayList<TStopText>(tsl.size());
			for (TStop ts : tsl)
				li.add(new TStopText(ts, res));

			return li;
		}
	}
}
