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
import java.util.HashSet;
import java.util.List;

import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.android.util.Misc;
import org.shadowlands.roadtrip.db.GeoArea;
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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Dialog builder for {@link LogbookShow} to show detail about one {@link Trip}.
 *<P>
 * Includes a list of the Trip's {@link TStop}s which can be clicked to show their
 * details in {@link TripTStopEntry}, which has a callback if a TStop's comment is
 * modified. Because the dialog isn't an Activity, the Activity which created this
 * dialog receives the {@link Activity#onActivityResult(int, int, Intent)}.
 * For callback contents details see {@link TripTStopEntry}.
 *<P>
 * Any TStops whose contents are changed by the user from this dialog are tracked
 * in {@link #getUpdatedTStopIDs()}, which can be called after the dialog is dismissed
 * or cancelled: See {@link LogbookShowTripDetailDialogBuilder.DetailDialogListener}
 * for callback interface.
 *
 * @since 0.9.60
 */
public class LogbookShowTripDetailDialogBuilder
	implements AdapterView.OnItemClickListener,
	    DialogInterface.OnCancelListener, DialogInterface.OnDismissListener
{
	/** Calling context, for resources and {@link Activity#startActivityForResult(Intent, int)} */
	private final Activity caller;

	/** Optional listener */
	private final DetailDialogListener lsnr;

	/** Caller's arbitrary "request code" given to constructor, for callbacks */
	private final int callbackReqCode;

	private final LogbookTableModel ltm;
	private final RDBAdapter db;

	/** Trip being shown in this dialog. Its TStops are in {@link #allTS}. */
	public final Trip tr;

	/**
	 * ListView of {@link TStop}s on this trip ({@link #allTS}); may be empty. Set in {@link #create()}.
	 * Adapter is {@link #lvTSAdapter} or {@code null}.
	 */
	private ListView lvTStopsList;

	/** Adapter for {@link #lvTStopsList}, or {@code null} if list is empty. */
	private ArrayAdapter<TStopText> lvTSAdapter;

	/**
	 * List of non-starting {@link TStop}s in {@link #tr}, presented in {@link #lvTStopsList},
	 * or {@code null} if none.
	 */
	private List<TStop> allTS;

	/**
	 * Set of {@link TStop} IDs whose comments were changed by the user from this dialog,
	 * or {@code null} if none yet.
	 */
	private HashSet<Integer> updatedTSID;

	/**
	 * date formatter for use by {@link DateFormat#format(CharSequence, Calendar)},
	 * initialized via {@link Misc#buildDateFormatDOWMed(Context)}
	 */
	private StringBuffer fmt_dow_meddate;

	/** For hh:mm output, android-specific DateTimeFormatter for locale and user prefs */
	private RTRAndroidDateTimeFormatter dtf;

	/**
	 * Create a Builder. All params except {@code lsnr} must be non-null.
	 * After calling this constructor, call {@link #create()} to get the AlertDialog
	 * and then {@link AlertDialog#show()}.
	 * @param callbackReqCode Arbitrary "request code" to give caller with
	 *     {@link Activity#onActivityResult(int, int, Intent)} if a TStop detail is modified.
	 */
	public LogbookShowTripDetailDialogBuilder
		(final Activity caller, final int callbackReqCode, final DetailDialogListener lsnr,
		 final Trip tr, final LogbookTableModel ltm, final RDBAdapter db)
	{
		this.caller = caller;
		this.callbackReqCode = callbackReqCode;
		this.lsnr = lsnr;
		this.tr = tr;
		this.ltm = ltm;
		this.db = db;
		dtf = new RTRAndroidDateTimeFormatter(caller.getApplicationContext());
	}

	/**
	 * Build the AlertDialog, to call {@link AlertDialog#show()}.
	 * @return a newly built AlertDialog
	 * @see #updateTStopText(int)
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

		if (tr.isRoadtrip())
		{
			tv = (TextView) itms.findViewById(R.id.logbook_show_popup_trip_detail_starting_area);
			try
			{
				GeoArea geo = new GeoArea(db, tr.getAreaID());
				tv.setText(geo.getName());
			} catch (Exception e) {}

			tv = (TextView) itms.findViewById(R.id.logbook_show_popup_trip_detail_desti_area);
			try
			{
				GeoArea geo = new GeoArea(db, tr.getRoadtripEndAreaID());
				tv.setText(geo.getName());
			} catch (Exception e) {}
		} else {
			View v = itms.findViewById(R.id.logbook_show_popup_trip_detail_starting_area_row);
			v.setVisibility(View.GONE);

			v = itms.findViewById(R.id.logbook_show_popup_trip_detail_desti_area_row);
			v.setVisibility(View.GONE);
		}

		lvTStopsList = (ListView) itms.findViewById(R.id.logbook_show_popup_trip_detail_tstop_list);
		if (lvTStopsList != null)
		{
			allTS = tr.readAllTStops();
			if ((allTS != null) && ! allTS.isEmpty())
			{
				if (! tr.isStartTStopFromPrevTrip())
				{
					// starting location already shown: remove it from stop list,
					// but don't modify model's cached allTS
					allTS = new ArrayList<TStop>(allTS);
					allTS.remove(0);
				}

				if (! allTS.isEmpty())
				{
					final Resources res = caller.getResources();
					List<TStopText> allTT = new ArrayList<TStopText>(allTS.size());
					for (TStop ts : allTS)
						allTT.add(new TStopText(ts, res));

					lvTSAdapter = new ArrayAdapter<TStopText>
					    (caller, R.layout.list_item, allTT);
					lvTStopsList.setAdapter(lvTSAdapter);
					lvTStopsList.setOnItemClickListener(this);
				} else {
					allTS = null;
				}
			}

			if (lvTSAdapter == null)
			{
				// TStop list is empty (new trip in progress): change list heading text
				tv = (TextView) itms.findViewById(R.id.logbook_show_popup_trip_detail_tstop_list_head);
				if (tv != null)
					tv.setText(R.string.logbook_show_popup_trip_detail__trip_stops_none);
			}
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
		if (lsnr != null)
		{
			bld.setOnCancelListener(this);
			bld.setOnDismissListener(this);
		}

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
		caller.startActivityForResult(i, callbackReqCode);
	}

	/**
	 * Requery DB and update the rendered text for this TStop in the list, after a change to the TStop's fields.
	 * If the data and text were different and were updated here, {@code tsID} is added to the set kept in
	 * {@link #getUpdatedTStopIDs()}.
	 * @param tsID  ID of a TStop displayed in the AlertDialog's stop list
	 */
	public void updateTStopText(final int tsID)
	{
		if ((lvTSAdapter == null) || (allTS == null))
			return;

		final int S = allTS.size();
		int i;
		for (i = 0; i < S; ++i)
			if (allTS.get(i).getID() == tsID)
				break;
		if (i >= S)
			return;

		TStopText tt = lvTSAdapter.getItem(i);
		if (tt.requeryRenderedText())
		{
			if (updatedTSID == null)
				updatedTSID = new HashSet<Integer>();
			updatedTSID.add(Integer.valueOf(tsID));

			lvTSAdapter.notifyDataSetChanged();
		}
	}

	/**
	 * Get the set of TStop IDs whose comments were changed by the user from this dialog
	 * via {@link #updateTStopText(int)}, if any.
	 * @return  Updated TStop IDs, or {@code null} if none
	 */
	public HashSet<Integer> getUpdatedTStopIDs()
	{
		return updatedTSID;
	}

	/** Callback for when dialog is canceled: Call our {@link DetailDialogListener}, if any. */
	public void onCancel(DialogInterface dialog)
	{
		if (lsnr != null)
			lsnr.onDetailDialogDismissed(this);
	}

	/** Callback for when dialog is dismissed: Call our {@link DetailDialogListener}, if any. */
	public void onDismiss(DialogInterface dialog)
	{
		if (lsnr != null)
			lsnr.onDetailDialogDismissed(this);
	}

	/**
	 * Optional callback interface for when the dialog is dismissed or canceled.
	 * Passed to {@link LogbookShowTripDetailDialogBuilder} constructor.
	 */
	public static interface DetailDialogListener
	{
		/**
		 * Dialog has been dismissed or canceled. {@code src} can be used to obtain more info such as
		 * {@link LogbookShowTripDetailDialogBuilder#getUpdatedTStopIDs() getUpdatedTStopIDs()}.
		 * @param src The dialog builder
		 */
		public void onDetailDialogDismissed(LogbookShowTripDetailDialogBuilder src);
	}

	/** TStop's data and rendered text for list adapter */
	private final static class TStopText
	{
		private TStop ts;

		/** Location, from {@link TStop#readLocationText() ts.readLocationText()} */
		final public String tsLocText;

		String str;  // set by updateRenderedText()
		final private Resources res;

		public TStopText(final TStop ts, final Resources res)
		{
			this.ts = ts;
			this.res = res;
			tsLocText = ts.readLocationText();
			updateRenderedText();
		}

		/**
		 * Update the {@link #toString()} text by requerying this TStop from the database by its ID.
		 * @return true if query changed the text, false otherwise
		 * @see #updateRenderedText()
		 */
		public boolean requeryRenderedText()
		{
			if (ts.requeryComment())
			{
				updateRenderedText();
				return true;
			}

			return false;
		}

		/**
		 * Update the {@link #toString()} text from the TStop object's fields and {@link #tsLocText}.
		 * @see #requeryRenderedText()
		 */
		public void updateRenderedText()
		{
			StringBuilder sb = new StringBuilder();
			int odo = ts.getOdo_trip();
			if (odo != 0)
			{
				// "(12.3)" with auto-localization of decimal separator
				sb.append(res.getString(R.string.value__odo__parens_float, odo / 10f));
				sb.append(' ');
			}
			sb.append(tsLocText);
			String comm = ts.getComment();
			if (comm != null)
				sb.append(" [" + comm + "]");

			str = sb.toString();
		}

		/**
		 * Get the text rendered from the TStop's data and {@link #tsLocText}.
		 * @see #requeryRenderedText()
		 */
		public String toString() { return str; }
	}
}
