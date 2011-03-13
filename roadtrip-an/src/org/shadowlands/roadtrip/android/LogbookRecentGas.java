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

package org.shadowlands.roadtrip.android;

import gnu.trove.TIntObjectHashMap;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Vector;

import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.android.util.Misc;
import org.shadowlands.roadtrip.db.GasBrandGrade;
import org.shadowlands.roadtrip.db.Location;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.TStop;
import org.shadowlands.roadtrip.db.TStopGas;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;
import org.shadowlands.roadtrip.model.LogbookTableModel;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity listing recent gas stops for the current vehicle (up to 40).
 *
 * @author jdmonin
 */
public class LogbookRecentGas extends Activity
	implements OnItemClickListener
{
	// db is not kept open, so no RDBAdapter field.

	private TextView tvTopText;
	private ListView lvGasStopsList;

	/**
	 * {@link Location} cache. Each item is its own key.
	 * Also used in {@link LogbookTableModel}.
	 * TIntObjectHashMap is from <A href="http://trove4j.sourceforge.net/">trove</A> (LGPL).
	 * (<A href="http://trove4j.sourceforge.net/javadocs/">javadocs</a>)
	 */
	private TIntObjectHashMap<Location> locCache;

	/**
	 * {@link GasBrandGrade} cache. Each item is its own key.
	 */
	private TIntObjectHashMap<GasBrandGrade> gasCache;

	/**
	 * date formatter for use by {@link DateFormat#format(CharSequence, Calendar)},
	 * initialized via {@link Misc#buildDateFormatDOWShort(Context, boolean)}.
	 */
	private StringBuffer fmt_dow_shortdate;

	/**
	 * Get data for up to 40 recent gas stops.
	 * Called when the activity is first created.
	 * Calls {@link #populateRecentGasList(RDBAdapter, int)}.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.logbook_recent_gas);

	    tvTopText = (TextView) findViewById(R.id.logbook_recent_gas_toptext);
	    lvGasStopsList = (ListView) findViewById(R.id.logbook_recent_gas_list);
	    lvGasStopsList.setOnItemClickListener(this);

		RDBAdapter db = new RDBOpenHelper(this);
		populateRecentGasList(db, 40);  // LIMIT 40
		db.close();
	}

	/**
	 * List the recent gas stops for the current vehicle.
	 */
	private void populateRecentGasList(RDBAdapter db, final int limit)
	{
		String[] gaslist;
		Vehicle currV = Settings.getCurrentVehicle(db, false);
		/**
		 * 
sqlite> select g.*, ts.odo_total,ts.time_stop,ts.locid from tstop_gas g, tstop ts where g.vid=2 and g._id=ts._id order by g._id desc limit 5;
_id|quant|price_per|price_total|fillup|station|vid|gas_brandgrade_id|odo_total|time_stop|locid
731|8444|3559|3005|1||2|1|382201|1299345330|78
712|11639|3439|4003|1||2|2|379645|1299044297|131
707|3005|3479|1045|0||2|1|378730|1299027122|1
697|6006|3429|2059|0||2|1|377406|1298894179|1
657|12845|3269|4199|1||2|1|373170|1298142953|1
		 */
		ArrayList<String> gasRows = null;
		if (currV != null)
		{
			tvTopText.setText(currV.toString());

			Vector<TStopGas> gstop = TStopGas.recentGasForVehicle(db, currV, limit);
			if (gstop != null)
			{
				locCache = new TIntObjectHashMap<Location>();
				gasCache = new TIntObjectHashMap<GasBrandGrade>();
				if (fmt_dow_shortdate == null)
					fmt_dow_shortdate = Misc.buildDateFormatDOWShort(this, false);

				final int L = gstop.size();
				gasRows = new ArrayList<String>(L);
				StringBuffer sb = new StringBuffer();
				for (int i = 0; i < L; ++i)
				{
					final TStopGas tsg = gstop.elementAt(i);

					// Same gas-formatting code as LogbookTableModel.addRowsFromTrips
					final int gradeID = tsg.gas_brandgrade_id;
					if (gradeID != 0)
					{
						GasBrandGrade grade = gasCache.get(gradeID);
						if (grade == null)
						{
							try
							{
								grade = new GasBrandGrade(db, gradeID);
								gasCache.put(gradeID, grade);
							}
							catch (Throwable th) {}
						}
						if (grade != null)
							tsg.gas_brandgrade = grade;  // for toStringBuffer's use
					}

					final TStop ts = tsg.getTStop();

					sb.append(ts.getOdo_total() / 10);
					sb.append(' ');

					sb.append(tsg.toStringBuffer(currV));  // quant @ price-per [totalprice] [gas_brandgrade]
					if (gradeID != 0)
						tsg.gas_brandgrade = null;  // clear the reference

					sb.append("\n  ");

					int t = ts.getTime_stop();
					if (t == 0)
						t = ts.getTime_continue();
					if (t != 0)
					{
						CharSequence fmt = DateFormat.format(fmt_dow_shortdate, t * 1000L);
						sb.append(fmt);
						sb.append(' ');
					}

					sb.append(getTStopLocDescr(ts, db));

					if ((tsg.effic_dist != 0) && (tsg.effic_quant != 0))
					{
						// TODO pref to specify format: mpg, g/100mi, L/100km, km/L
						sb.append("\n");
						tsg.efficToStringBuffer(false, sb, currV);
						sb.append(" mpg, ");
						tsg.efficToStringBuffer(true, sb, currV);
						sb.append(" gal/100mi");
					}

					gasRows.add(sb.toString());

					sb.delete(0, sb.length());  // clear for next row
				}
			}
		} else {
			gasRows = null;
		}
		if (gasRows == null)
		{
			gaslist = new String[1];
			gaslist[0] = getResources().getString(R.string.logbook_recent_gas_nonefound);
		} else {
			gaslist = new String[gasRows.size()];
			gasRows.toArray(gaslist);
		}
		lvGasStopsList.setAdapter(new ArrayAdapter<String>(this, R.layout.list_item, gaslist));
	}

	/**
	 * Read this TStop's location description from text or from its associated Location.
	 * Attempts to read or fill {@link #locCache}.
	 * This is a copy of LogbookTableModel.getTStopLocDescr.
	 * @param conn  db connection to use
	 * @param ts  TStop to look at
	 * @return Location text, or null
	 */
	private final String getTStopLocDescr(TStop ts, RDBAdapter conn)
	{
		String locDescr = ts.getLocationDescr();
		if (locDescr == null)
		{
			final int locID = ts.getLocationID();
			if (locID != 0)
			{
				Location lo = locCache.get(locID);
				if (lo == null)
				{
					try
					{
						lo = new Location(conn, locID);
						locCache.put(locID, lo);
					} catch (Throwable e) { }  // RDBKeyNotFoundException
				}
				if (lo != null)
					locDescr = lo.getLocation();
			}
		}
		return locDescr;
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}

	/** When a gas stop is selected in the list */
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		// TODO more than this (show details from tstop?)
		Toast.makeText(this, ((TextView) view).getText(),
			Toast.LENGTH_SHORT).show();
	}

}
