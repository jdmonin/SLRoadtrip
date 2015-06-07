/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2015 Jeremy D Monin <jdmonin@nand.net>
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

package org.shadowlands.roadtrip.model;

import gnu.trove.TIntObjectHashMap;

import java.util.Date;
import java.util.Vector;

import org.shadowlands.roadtrip.db.GasBrandGrade;
import org.shadowlands.roadtrip.db.Location;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.TStop;
import org.shadowlands.roadtrip.db.TStopGas;
import org.shadowlands.roadtrip.db.Trip;
import org.shadowlands.roadtrip.db.TripCategory;
import org.shadowlands.roadtrip.db.VehSettings;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.ViaRoute;
import org.shadowlands.roadtrip.db.Trip.TripListTimeRange;
import org.shadowlands.roadtrip.util.RTRDateTimeFormatter;

/**
 * Renders and holds data for display or editing.
 * The last row is filled with the empty string; typing in this row
 * creates a new empty row under it.
 *<P>
 * Two modes: <UL>
 *<LI> Week Mode: Contains all trips for the vehicle, in ranges
 *   (increments) of several weeks.  In some cases, editable.
 *   Call {@link #getWeekIncrement()} to get the increment.
 *<LI> Location Mode: Contains only trips which contain a certain location.
 *   The ranges (increments) are several trips at a time, instead of
 *   several weeks.  Always read-only.
 *   Call {@link #getTripIncrement()} to get the increment.
 * </UL>
 *<P>
 * The data is loaded in "ranges" of several weeks or several trips,
 * depending on the mode.  You can either
 * retrieve them as a grid of cells, or can retrieve the ranges
 * by calling {@link #getRangeCount()} and {@link #getRange(int)}.
 * Load increments of earlier data by calling {@link #addEarlierTrips(RDBAdapter)}.
 *<P>
 * Assumes that data won't change elsewhere while displayed; for example,
 * cached ViaRoute object contents.
 *<P>
 * <B>Preferences:</B> Before creating the LTM, you can set {@link #trip_odo_delta_mode},
 * {@link #trip_simple_mode}, and/or {@link #render_comments_brackets}.
 *
 * @author jdmonin
 */
public class LogbookTableModel // extends javax.swing.table.AbstractTableModel
{
	/**
	 * Column headings for standard mode.
	 * The length of this array determines the number of columns.
	 * Rows with this format are filled in {@link #addRowsFromTrips_formatTripsStops(Vector, Vector, RDBAdapter)}.
	 * @see #trip_simple_mode
	 */
	public static final String[] COL_HEADINGS
	    = { "Date", "Time", "", "Odometer", "Trip-O", "Description", "Via", "Comment" };

	/**
	 * Column headings for simple mode (no TStops).  The length of this array determines the number of columns.
	 * Rows with this format are filled in {@link #addRowsFromTrips_formatTripsSimple(Vector, Vector, RDBAdapter)}.
	 * @see #trip_simple_mode
	 * @since 0.9.20
	 */
	public static final String[] COL_HEADINGS_SIMPLE
	    = { "Date", "Odo Start", "Odo End", "Time Start", "Time End", "From Location", "To Location", "Comment" };

	/**
	 * Optional Passengers count label.
	 * TODO: Make dynamic, for other languages
	 * @since 0.9.20
	 */
	public static final String TXT_PASSENGERS = "Passengers";

	private static final String[][] TEMPLATE_ADD_SIMPLE
	   = { { null, null, "/", "Start-odo", null, null, "Start at" },
		   { "Date", "Start-Time", },
		   { null , "End-Time", null, null, "Trip-odo", "via", "End at" },
		   { null, null, "\\", "End-odo" } };

	private static final String[][] TEMPLATE_ADD_WITHSTOPS
   = { { null, null, "/", "Start-odo", null, null, "Start at" },
	   { "Date", "Start-Time", },
	   { null, "Stop-time" },
	   { null, null, ">", "Stop-odo", "Stop-trip", "Route to stop", "Stop at" },
	   { null, "Resume-time" },
	   { null , "End-Time", null, null, "Trip-odo", "via", "End at" },
	   { null, null, "\\", "End-odo" } };

	/**
	 * Rendering preference for TStop comments:
	 * When true, {@code addRowsFromTrips} will place any {@link TStop#getComment()} text within [square brackets].
	 * True by default.
	 *<P>
	 * This preference is static because the constructor renders trip data immediately, and its value doesn't
	 * change within an app so it's not completely useful as a constructor parameter.
	 * @since 0.9.43
	 */
	public static boolean render_comments_brackets = true;

	/**
	 * Preference: When true, show trips in Simple mode (1 line per trip, no TStop details).
	 * The standard-mode headings are {@link #COL_HEADINGS}.
	 * The simple-mode headings are {@link #COL_HEADINGS_SIMPLE}.
	 * @since 0.9.20
	 */
	public static boolean trip_simple_mode = false;

	/**
	 * Preference: Trip odometer delta mode.
	 *<UL>
	 *<LI> 0: Show the trip odometer value (12.2)
	 *<LI> 1: Show the delta from previous stop (+8.5)
	 *<LI> 2: Show both (12.2; +8.5)
	 *</UL>
	 * If the previous stop has neither total nor trip odometer, the delta will be blank.
	 *<P>
	 * If using {@link #trip_simple_mode}, always show the total odometer, never the delta;
	 * <tt>trip_odo_delta_mode</tt> is ignored.
	 */
	public static int trip_odo_delta_mode = 2;

	/**
	 * The vehicle being displayed, or when {@link #filterLoc_showAllV}, being used for units and formatting.
	 * @see #filterLoc_showAllV
	 */
	private Vehicle veh;

	/**
	 * Mode indicator; the location ID to filter by in Location Mode, or 0 to use Week Mode.
	 * @see #tripIncr
	 * @see #weekIncr
	 */
	private final int filterLocID;

	/**
	 * In Location Mode, if true, show trips of all vehicles, not just {@link #veh}.
	 * <tt>Veh</tt> is still needed for unit-of-measurement preferences and formatting.
	 */
	private final boolean filterLoc_showAllV;

	/**
	 * For Location Mode, the increment in number of trips when loading older trips
	 * into {@link #tData}.
	 * Used only if {@link #filterLocID} != 0.
	 * @see #weekIncr
	 */
	private final int tripIncr;

	/**
	 * For Week Mode, the increment in weeks when loading newer/older trips into {@link #tData},
	 * or 0 to load all (This may run out of memory).
	 * Unused if {@link #filterLocID} != 0.
	 * @see #tripIncr
	 * @see #filterWeekModeStartDate
	 */
	private final int weekIncr;

	/**
	 * For Week Mode, the date (unix format) if filtering by start date; otherwise 0.
	 * Only used if {@link #tData} is empty in {@link #addEarlierTrips(RDBAdapter)};
	 * if {@link #tData} contains trips, they might be from dates earlier than this field.
	 * Unused in Location Mode.
	 * @see #LogbookTableModel(Vehicle, int, int, boolean, RTRDateTimeFormatter, RDBAdapter)
	 * @since 0.9.07
	 */
	private final int filterWeekModeStartDate;

	/**
	 * Holds each time range of trips; sorted from earliest to latest.
	 */
	private Vector<TripListTimeRange> tData;

	/**
	 * Length of all text rows from {@link #tData}'s tText[] arrays;
	 * does not include {@link #tAddedRows}.
	 */
	private int tDataTextRowCount;

	/**
	 * Rows being newly added (manual typing/edit mode), or null
	 * @see #addMode
	 */
	private Vector<String[]> tAddedRows;

	/**
	 * {@link Location} cache. Each item is its own key.
	 * TIntObjectHashMap is from <A href="http://trove4j.sourceforge.net/">trove</A> (LGPL).
	 * (<A href="http://trove4j.sourceforge.net/javadocs/">javadocs</a>)
	 */
	private TIntObjectHashMap<Location> locCache;

	/**
	 * {@link ViaRoute} cache. Each item is its own key.
	 */
	private TIntObjectHashMap<ViaRoute> viaCache;

	/**
	 * {@link GasBrandGrade} cache. Each item is its own key.
	 */
	private TIntObjectHashMap<GasBrandGrade> gasCache;

	/**
	 * {@link TripCategory} cache. Each item is its own key.
	 * Names are modified in the cache: "[" + catname + "]"
	 * @since 0.9.08
	 */
	private TIntObjectHashMap<TripCategory> tcatCache;

	/**
	 * Are we adding a new trip right now?
	 * @see #maxRowBeforeAdd
	 * @see #tAddedRows
	 * @see #beginAdd(boolean)
	 */
	private boolean addMode;

	/**
	 * Is there a current trip?
	 */
	private boolean hasCurrT;

	/** During {@link #addMode}, {@link Vector#size() tData.size()} before starting the add. */
	private int maxRowBeforeAdd;

	/** if non-null, listener for our changes. */
	private TableChangeListener listener;

	/** A {@link #tData}'s tText contents, or null; optimization for {@link #getValueAt(int, int)}. */
	private transient Vector<String[]> getValue_RangeText;

	/** {@link #getValue_RangeText}'s index within {@link #tData}; Optimization for {@link #getValueAt(int, int)} */
	private transient int getValue_RangeIndex;

	/** {@link #getValue_RangeText}'s low and high row numbers, or -1; Optimization for {@link #getValueAt(int, int)} */
	private transient int getValue_RangeRow0, getValue_RangeRowN;

	/**
	 * Our date & time format, for {@link #addRowsFromTrips(TripListTimeRange, RDBAdapter)}.
	 * May be null; initialized in <tt>addRowsFromTrips</tt>.
	 */
	private transient RTRDateTimeFormatter dtf;

	/**
	 * Common setup to all constructors (location mode, week mode).
	 * Set veh, tData, locCache, etc.
	 * Because of all the final fields for different modes, this isn't an actual private constructor.
	 * @param veh  vehicle
	 * @param dtFmt  date-time format for {@link #addRowsFromTrips(TripListTimeRange, RDBAdapter)}, or null for default
	 * @throws IllegalArgumentException if veh is null
	 */
	private final void initCommonConstruc(Vehicle veh, RTRDateTimeFormatter dtFmt)
		throws IllegalArgumentException
	{
		if (veh == null)
			throw new IllegalArgumentException("veh");
		tData = new Vector<TripListTimeRange>();
		tDataTextRowCount = 0;
		tAddedRows = null;
		locCache = new TIntObjectHashMap<Location>();
		viaCache = new TIntObjectHashMap<ViaRoute>();
		gasCache = new TIntObjectHashMap<GasBrandGrade>();
		tcatCache = new TIntObjectHashMap<TripCategory>();
		dtf = dtFmt;

		this.veh = veh;
		getValue_RangeRow0 = -1;
		getValue_RangeRowN = -1;
		hasCurrT = false;		
	}

	/**
	 * Copy constructor.  Copies references to each {@link TripListTimeRange}'s Trip vector.
	 * Re-renders all trip data rows as text.
	 * Useful for copying an LTM's data but changing its display to/from {@link #trip_simple_mode}.
	 * Does not copy the {@link TableChangeListener} field.
	 * @param ltm  LTM to copy from
	 * @param conn Use this connection for any db lookups
	 * @since 0.9.20
	 */
	public LogbookTableModel(LogbookTableModel ltm, RDBAdapter conn)
	{
		initCommonConstruc(ltm.veh, ltm.dtf);
		locCache.putAll(ltm.locCache);
		viaCache.putAll(ltm.viaCache);
		gasCache.putAll(ltm.gasCache);
		tcatCache.putAll(ltm.tcatCache);
		hasCurrT = ltm.hasCurrT;

		// copy fields not set in initCommonConstruc
		weekIncr = ltm.weekIncr;
		tripIncr = ltm.tripIncr;
		filterLocID = ltm.filterLocID;
		filterLoc_showAllV = ltm.filterLoc_showAllV;
		filterWeekModeStartDate = ltm.filterWeekModeStartDate;
		addMode = ltm.addMode;
		maxRowBeforeAdd = ltm.maxRowBeforeAdd;

		// format trip data text, update tDataTextRowCount
		if (! ltm.tData.isEmpty())
		{
			for (TripListTimeRange ttr : ltm.tData)
			{
				TripListTimeRange ttlocal = new TripListTimeRange(ttr.timeStart, ttr.timeEnd, ttr.tr);
				tData.add(ttlocal);
				addRowsFromTrips(ttlocal, conn);
			}

			if (ltm.tData.lastElement().noneLater)
				tData.lastElement().noneLater = true;
			if (ltm.tData.firstElement().noneEarlier)
				tData.firstElement().noneEarlier = true;
		}
	}

	/**
	 * Create in Week Mode, and populate with the most recent trip data.
	 *<P>
	 * You can add earlier trips afterwards by calling
	 * {@link #addEarlierTrips(RDBAdapter)}.
	 * @param veh  Vehicle
	 * @param weeks  Increment in weeks when loading newer/older trips from the database,
	 *          or 0 to load all (This may run out of memory).
	 *          The vehicle's most recent trips are loaded in this constructor.
	 * @param dtf  date-time format for {@link #addRowsFromTrips(TripListTimeRange, RDBAdapter)}, or null for default
	 * @param conn Add existing rows from this connection, via addRowsFromTrips.
	 * @throws IllegalArgumentException if veh is null
	 * @see #LogbookTableModel(Vehicle, int, int, boolean, RTRDateTimeFormatter, RDBAdapter)
	 */
	public LogbookTableModel(Vehicle veh, final int weeks, RTRDateTimeFormatter dtf, RDBAdapter conn)
		throws IllegalArgumentException
	{
		initCommonConstruc(veh, dtf);  // set veh, tData, locCache, etc

		filterLocID = 0;  // Week Mode
		filterLoc_showAllV = false;  // this field not used in Week Mode
		weekIncr = weeks;
		tripIncr = 0;
		filterWeekModeStartDate = 0;

		if (veh != null)
		{
			Trip currT = VehSettings.getCurrentTrip(conn, veh, false);
			TStop currTS = null;
			if (currT != null)
				currTS = VehSettings.getCurrentTStop(conn, veh, false);

			int ltime = 0;
			if (currTS != null)
			{
				ltime = currTS.getTime_stop();
				if (ltime == 0)
					currTS = null;
			}
			if (currTS == null)
				ltime = veh.readLatestTime(currT);  // don't call if we have currTS
			if ((ltime != 0) && (weekIncr != 0))
			{
				++ltime;  // addRowsFromDBTrips range is exclusive; make it include ltime
				addRowsFromDBTrips(ltime, weekIncr, true, false, conn);
				if (! tData.isEmpty())
					tData.lastElement().noneLater = true;  // we know it's the newest trip
			} else {
				addRowsFromDBTrips(conn);
			}
		}
	}

	/**
	 * Create in Week Mode, and populate with trip data around the specified starting time.
	 *<P>
	 * You can add earlier trips afterwards by calling
	 * {@link #addEarlierTrips(RDBAdapter)}.
	 * @param veh  Vehicle
	 * @param timeStart  Starting date/time of trip range, in Unix format.
	 *          If no trips found within <tt>weeks</tt> of this date,
	 *          keep searching until a trip is found.
	 * @param weeks  Increment in weeks when loading newer/older trips from the database.
	 *          Must be greater than 0.
	 * @param towardsNewer  If true, retrieve <tt>timeStart</tt> and newer;
	 *          otherwise retrieve <tt>timeStart</tt> and older.
	 * @param dtf  date-time format for {@link #addRowsFromTrips(TripListTimeRange, RDBAdapter)}, or null for default
	 * @param conn Add existing rows from this connection, via addRowsFromTrips.
	 * @throws IllegalArgumentException if veh is null, or weeks is &lt; 1
	 * @see #LogbookTableModel(Vehicle, int, RTRDateTimeFormatter, RDBAdapter)
	 */
	public LogbookTableModel
		(Vehicle veh, final int timeStart, final int weeks, final boolean towardsNewer, RTRDateTimeFormatter dtf, RDBAdapter conn)
		throws IllegalArgumentException
	{
		initCommonConstruc(veh, dtf);  // set veh, tData, locCache, etc
		if (weeks <= 0)
			throw new IllegalArgumentException("weeks");

		filterLocID = 0;  // Week Mode
		filterLoc_showAllV = false;  // this field not used in Week Mode
		weekIncr = weeks;
		tripIncr = 0;
		filterWeekModeStartDate = timeStart;

		addRowsFromDBTrips(timeStart, weekIncr, true, towardsNewer, conn);
	}

	/**
	 * Create in Location Mode, and populate with the most recent trip data.
	 *<P>
	 * You can add earlier trips afterwards by calling
	 * {@link #addEarlierTrips(RDBAdapter)}.
	 * @param veh  Show this vehicle's trips.  Even if <tt>showAllV</tt> is true,
	 *          you must supply a vehicle for unit-of-measurement preferences and formatting.
	 * @param showAllV  If true, show trips of all vehicles, not just <tt>veh</tt>.
	 * @param locID  Location ID to filter by
	 * @param tripIncr  Increment in trips to load (sql <tt>LIMIT</tt>).
	 *          The vehicle's most recent trips to <tt>locID</tt> are loaded in this constructor.
	 * @param dtf  date-time format for {@link #addRowsFromTrips(TripListTimeRange, RDBAdapter)}, or null for default
	 * @param conn Add existing rows from this connection, via addRowsFromTrips.
	 * @throws IllegalArgumentException if the required veh, locID or tripIncr are null or &lt;= 0
	 */
	public LogbookTableModel(Vehicle veh, final boolean showAllV, final int locID, final int tripIncr, RTRDateTimeFormatter dtf, RDBAdapter conn)
		throws IllegalArgumentException
	{
		if (locID <= 0)
			throw new IllegalArgumentException("locID");
		if (tripIncr <= 0)
			throw new IllegalArgumentException("tripIncr");

		initCommonConstruc(veh, dtf);  // set veh, tData, locCache, etc

		filterLocID = locID;  // Location Mode
		filterLoc_showAllV = showAllV;
		this.tripIncr = tripIncr;
		weekIncr = 0;
		filterWeekModeStartDate = 0;

		addRowsFromDBTrips(0, false, tripIncr, conn);
	}

	/**
	 * Is this vehicle currently on the current trip?
	 * Determined in constructor, not updated afterwards.
	 * @return whether this vehicle has the current trip
	 */
	public boolean hasCurrentTrip()
	{
		return hasCurrT;
	}

	/**
	 * Load vehicle trips earlier than those currently in the model.
	 * In Week Mode, looks back {@link #getWeekIncrement()} weeks.
	 * In Location Mode, looks back {@link #getTripIncrement()} trips.
	 *<P>
	 * The added trips will be a new {@link TripListTimeRange}
	 * inserted at the start of the range list; keep this
	 * in mind when calling {@link #getRange(int)} afterwards.
	 * (If no trips are found, no range is created.)
	 *
	 * @return true if trips were added from the database, false if none found
	 */
	public boolean addEarlierTrips(RDBAdapter conn)
	{
		final boolean tDataIsEmpty = tData.isEmpty();
		if (tDataIsEmpty && (filterWeekModeStartDate == 0))
			return false;  // No trips at all were previously found for this vehicle.

		int nAdded;
		if (filterLocID == 0)
		{
			// Week Mode
			final int loadToTime;
			if (tDataIsEmpty)
				loadToTime = filterWeekModeStartDate;
			else
				loadToTime = tData.firstElement().timeStart;
			nAdded = addRowsFromDBTrips(loadToTime, weekIncr, true, false, conn);
		} else {
			// Location Mode
			final int laterTripID = tData.firstElement().tr.firstElement().getID();
			nAdded = addRowsFromDBTrips(laterTripID, false, tripIncr, conn);
		}
		if ((nAdded != 0) && (listener != null))
			listener.fireTableRowsInserted(0, nAdded - 1);

		return (nAdded != 0);
	}

	/**
	 * Load vehicle trips later than those currently in the model.
	 * In Week Mode, looks forward {@link #getWeekIncrement()} weeks.
	 * In Location Mode, looks forward {@link #getTripIncrement()} trips.
	 *<P>
	 * The added trips will be a new {@link TripListTimeRange}
	 * inserted at the end of the range list; keep this
	 * in mind when calling {@link #getRange(int)} afterwards.
	 * (If no trips are found, no range is created.)
	 *
	 * @return true if trips were added from the database, false if none found
	 * @throws IllegalStateException if {@link #beginAdd(boolean)} (add mode) is active.
	 */
	public boolean addLaterTrips(RDBAdapter conn)
		throws IllegalStateException
	{
		if (addMode)
			throw new IllegalStateException();
		if (tData.isEmpty())
			return false;  // No trips at all were previously found for this vehicle.

		int nAdded;
		if (filterLocID == 0)
		{
			// Week Mode
			final int loadToTime = tData.lastElement().timeEnd;
			nAdded = addRowsFromDBTrips(loadToTime, weekIncr, true, true, conn);
		} else {
			// Location Mode
			final int earlierTripID = tData.lastElement().tr.lastElement().getID();
			nAdded = addRowsFromDBTrips(earlierTripID, true, tripIncr, conn);
		}
		// TODO Week mode: ensure previously-newest trip doesn't appear twice now
		if ((nAdded != 0) && (listener != null))
			listener.fireTableRowsInserted(tDataTextRowCount - nAdded, tDataTextRowCount - 1);

		return (nAdded != 0);
	}

	/**
	 * For Location Mode, add the vehicle's trips in this time range.
	 * Assumes is an end of previously loaded range (oldest beginning or newest end),
	 * or that (when prevTripID == 0) there is no previously loaded range.
	 *
	 * @param prevTripID  Previous end of trip range: A trip newer or older than the
	 *          ones to load, or 0 to get the latest trips
	 * @param towardsNewer  If true, retrieve newer than <tt>prevTripID</tt>;
	 *          otherwise retrieve older than <tt>prevTripID</tt>.
	 * @param limit  Retrieve at most this many trips
	 * @param conn  Add trips from this db connection
	 * @return Number of rows of text added to the table
	 * @throws IllegalArgumentException  if <tt>towardsNewer</tt> true, but <tt>prevTripID</tt> == 0
	 */
	private int addRowsFromDBTrips
		(final int prevTripID, final boolean towardsNewer, final int limit, RDBAdapter conn)
		throws IllegalArgumentException
	{
		if (towardsNewer && (prevTripID == 0))
			throw new IllegalArgumentException();

		TripListTimeRange ttr = Trip.tripsForLocation
			(conn, filterLocID, (filterLoc_showAllV ? null : veh),
			 prevTripID, towardsNewer, limit, true);

		if (ttr == null)
		{
			if (! tData.isEmpty())
				if (towardsNewer)
					tData.lastElement().noneLater = true;
				else
					tData.firstElement().noneEarlier = true;

			return 0;  // <--- nothing found ---
		}
		if (prevTripID == 0)
			ttr.noneLater = true;

		if (towardsNewer)
			tData.add(ttr);
		else
			tData.insertElementAt(ttr, 0);
		addRowsFromTrips(ttr, conn);
		getValue_RangeRow0 = -1;  // row#s changing, so reset getValue_* vars
		getValue_RangeRowN = -1;
		return ttr.tText.size();
	}

	/**
	 * For Week Mode, add the vehicle's trips in this time range.
	 * Assumes is beginning or ending of previously loaded range.
	 *<P>
	 * If not called from the constructor, you must call
	 * {@link TableChangeListener#fireTableRowsInserted(int, int)}
	 * after calling this method: Use the returned row count,
	 * and remember that {@link #tDataTextRowCount} will already
	 * include the inserted rows.
	 * 
     * @param timeStart  Starting exclusive date/time of trip range, in Unix format.
     *          The range will start just before or just after this date/time.
     *          To include <tt>timeStart</tt> in the range, increment or decrement
     *          it before calling this method.
     * @param weeks   Retrieve this many weeks past timeStart
     * @param searchBeyondWeeks  If true, and if no trips found within
     *          <tt>weeks</tt>, keep searching until a trip is found
     * @param towardsNewer  If true, retrieve newer than <tt>timeStart</tt>,
     *          and assume adding at the end of {@link #tData};
     *          otherwise retrieve older than <tt>timeStart</tt>,
     *          and add at the start of {@link #tData}.
	 * @param conn Add existing rows from this connection
	 * @return Number of rows of text added to the table
	 */
	private int addRowsFromDBTrips
		(int timeStart, final int weeks,
    	 final boolean searchBeyondWeeks, final boolean towardsNewer, RDBAdapter conn)
	{
		// TODO check tData for this time range already present

		// Adjust time because our range is exclusive, but tripsForVehicle range is inclusive
		if (towardsNewer)
			++timeStart;
		else
			--timeStart;
		TripListTimeRange ttr = Trip.tripsForVehicle
			(conn, veh, timeStart, weeks, searchBeyondWeeks, towardsNewer, true);
		if (ttr == null)
		{
			if (searchBeyondWeeks && ! tData.isEmpty())
				if (towardsNewer)
					tData.lastElement().noneLater = true;
				else
					tData.firstElement().noneEarlier = true;

			return 0;  // <--- nothing found ---
		}
		if (towardsNewer)
			tData.add(ttr);
		else
			tData.insertElementAt(ttr, 0);

		addRowsFromTrips(ttr, conn);
		getValue_RangeRow0 = -1;  // row#s changing, so reset getValue_* vars
		getValue_RangeRowN = -1;
		return ttr.tText.size();
	}

	/** For Week Mode, add all trip data for this vehicle. */
	private void addRowsFromDBTrips(RDBAdapter conn)
	{
		Vector<Trip> td = veh.readAllTrips(true);
		if (td == null)
			return;  // <--- nothing found ---
		TripListTimeRange ttr = new TripListTimeRange
			(td.firstElement().getTime_start(), td.lastElement().getTime_start(), td);
		ttr.noneEarlier = true;
		ttr.noneLater = true;
		tData.add(ttr);

		addRowsFromTrips(ttr, conn);
	}

	/**
	 * Add trip data as text to ttr.tText, looking up from the database as needed.
	 * Updates {@link #tDataTextRowCount} to include the new text rows.
	 * @param ttr  Query and add text of this TripTimeRange's contents
	 * @param conn  Add from this connection
	 */
	private void addRowsFromTrips(TripListTimeRange ttr, RDBAdapter conn)
	{
		Vector<Trip> td = ttr.tr;
		if (td == null)
		{
			return;  // <--- no trips found in ttr ---
		}

		if (dtf == null)
			dtf = new RTRDateTimeFormatter();
		final int tRowCount;  // rows in tText before add
		if (ttr.tText == null)
		{
			ttr.tText = new Vector<String[]>();
			tRowCount = 0;
		} else {
			tRowCount = ttr.tText.size();
		}
		Vector<String[]> tText = ttr.tText;

		if (trip_simple_mode)
			addRowsFromTrips_formatTripsSimple(td, tText, conn);
		else
			addRowsFromTrips_formatTripsStops(td, tText, conn);

		tDataTextRowCount += (tText.size() - tRowCount);
	}

	/**
	 * Add rows to strings from a list of {@link Trip}s and their {@link TStop}s.
	 * Columns of added rows line up with {@link #COL_HEADINGS}.
	 * @param td    Trip data to add to <tt>tText</tt>
	 * @param tText Append rows here from <tt>td</tt>
	 * @param conn  Add from this connection
	 */
	public void addRowsFromTrips_formatTripsStops
		(final Vector<Trip> td, Vector<String[]> tText, RDBAdapter conn)
	{
		Date prevTripStart = null;  // time of trip start

		// track and format month and day, show date only when day changes
		RTRDateTimeFormatter.DateAndTime prevShownDT = new RTRDateTimeFormatter.DateAndTime();

		final int L = td.size();
		final boolean doCommentBrackets = render_comments_brackets;  // shorter name, cache value

		// Does next trip continue from the same tstop and odometer?
		boolean nextTripUsesSameStop = false;  // Updated at bottom of loop.

		// Loop for each trip in td
		for (int i = 0; i < L; ++i)  // towards end of trip, must look at next trip
		{
			Trip t = td.elementAt(i);
			int odo_total;  // used only with trip_odo_delta_mode
			if (trip_odo_delta_mode != 0)
				odo_total = t.getOdo_start();
			else
				odo_total = 0;  // required for the compiler

			TStop ts_start = t.readStartTStop(false);  // may be null
			String[] tr;

			// first row of trip: date, if different from prev date
			final Date tstart = new Date(t.getTime_start() * 1000L);
			final int tstartMonth = tstart.getMonth(),
			          tstartMDay = tstart.getDate();
			if ((prevShownDT.mday != tstartMDay) || (prevShownDT.month != tstartMonth))
			{
				tr = new String[COL_HEADINGS.length];
				tr[0] = dtf.formatDate(tstart);
				tText.addElement(tr);

				prevShownDT.month = tstartMonth;
				prevShownDT.mday = tstartMDay;
			}
			prevTripStart = tstart;

			// next row of trip: location/odo, if different from previous trip's location/odo
			String[] firstrow;
			if (! nextTripUsesSameStop)
			{
				firstrow = new String[COL_HEADINGS.length];
				firstrow[2] = "/";
				firstrow[3] = Integer.toString((int) (t.getOdo_start() / 10.0f));
				if (ts_start != null)
					firstrow[6] = getTStopLocDescr(ts_start, conn);
				tText.addElement(firstrow);
			} else {
				firstrow = null;
			}

			// next row of trip: time; also "[category]" and/or passenger counts, if set
			tr = new String[COL_HEADINGS.length];
			tr[1] = dtf.formatTime(tstart);
			{
				String tr5 = null;  // content for tr[5]

				final int tcatID = t.getTripCategoryID();
				if (tcatID != 0)
				{
					TripCategory tcat = tcatCache.get(tcatID);
					if (tcat == null)
					{
						try
						{
							tcat = new TripCategory(conn, tcatID);
							tcat.setName("[" + tcat.getName() + "]");
							tcatCache.put(tcatID, tcat);
						}
						catch (Throwable th) {}
					}
					tr5 = tcat.getName();
				}

				final int pax = t.getPassengerCount();
				if (pax != -1)
				{
					StringBuilder sb = new StringBuilder();
					if (tr5 != null)
					{
						sb.append(tr5);
						sb.append(' ');
					}
					sb.append(TXT_PASSENGERS);  // "Passengers"
					sb.append(": ");
					sb.append(Integer.toString(pax));
					tr5 = sb.toString();
				}

				if (tr5 != null)
					tr[5] = tr5;
			}
			tText.addElement(tr);

			final int odo_end = t.getOdo_end();

			// All well-formed trips have 1 or more TStops.
			Vector<TStop> stops = t.readAllTStops();	// works for current, if addCommittedTStop was called
			final TStop lastStop = (stops != null) ? stops.lastElement() : null;
			if (stops != null)
			{
				boolean odo_trip_prev_known = true;  // When true, previous stop's trip-odo is accurate to 10ths.
					// When false, don't display 10ths because total-odo doesn't display those, so
					// the user can't verify their accuracy.
				int odo_trip = 0;  // used only with trip_odo_delta_mode

				for (TStop ts : stops)
				{
					if ((ts_start == null) && (ts.getOdo_trip() == 0) && (firstrow != null))
					{
						// this stop is the starting location
						ts_start = ts;
						firstrow[6] = getTStopLocDescr(ts, conn);
						continue;  // <-- doesn't get its own row, only firstrow --
					}

					final int ttstop = ts.getTime_stop();
					final int ttcont = ts.getTime_continue();

					// stop-time (if present)
					if (ttstop != 0)
					{
						tr = new String[COL_HEADINGS.length];
						dtf.formatDateTimeInSeq(ttstop * 1000L, prevShownDT);
						tr[0] = prevShownDT.fmtDate;  // may be null
						tr[1] = prevShownDT.fmtTime;
						tText.addElement(tr);
					}

					// stop info
					tr = new String[COL_HEADINGS.length];
					if (ttstop != 0)
					{
						if (ttcont != 0)
							tr[2] = ">";  // both stop,start time
						else
							tr[2] = "\\";  // only stop time
					} else if (ttcont != 0)
					{
						tr[2] = "/";  // only start time
					}

					int odo_delta = 0;  // distance from previous stop; used only when trip_odo_delta_mode != 0
					final int ts_ototal = ts.getOdo_total();
					if ((trip_odo_delta_mode != 0) && (ts_ototal != 0))
					{
						odo_delta = ts_ototal - odo_total;
						odo_total = ts_ototal;
					}
					final boolean is_last_stop = (ts_ototal != 0) && (ts_ototal == odo_end) && (ttcont == 0);
					if ((ts_ototal != 0) && ! is_last_stop)
						tr[3] = Integer.toString((int) (ts_ototal / 10.0f));
					final int ts_otrip = ts.getOdo_trip();
					if (ts_otrip != 0)
					{
						if (trip_odo_delta_mode != 0)
						{
							odo_delta = ts_otrip - odo_trip;
							odo_trip = ts_otrip;
							if (ts_ototal == 0)
								odo_total += odo_delta;   // estimate total for display at next stop
						}

						if (odo_delta == 0)
						{
							tr[4] = String.format("%.1f", ts_otrip / 10.0f);
						} else if (trip_odo_delta_mode == 2)
						{
							if (odo_trip_prev_known)
								tr[4] = String.format("%.1f; +%.1f", ts_otrip / 10.0f, odo_delta / 10.0f);
							else
								tr[4] = String.format("%.1f; +%d", ts_otrip / 10.0f, odo_delta / 10);
						} else {  // trip_odo_delta_mode == 1 because odo_delta != 0
							if (odo_trip_prev_known)
								tr[4] = String.format("+%.1f", odo_delta / 10.0f);
							else
								tr[4] = String.format("+%d", odo_delta / 10);
						}

						if (! is_last_stop)
							tr[4] = "(" + tr[4] + ")";

						odo_trip_prev_known = true;
					} else {
						// trip odo is 0.
						// If we're tracking delta mode, estimate it from our total odo delta (just above)
						// and display it. Don't display 10ths because total-odo doesn't display those, so
						// the user can't verify their accuracy.
						if ((trip_odo_delta_mode != 0) && (odo_delta != 0))
						{
							odo_trip += odo_delta;  // estimate trip for display at next stop
							if (! is_last_stop)
								tr[4] = String.format("(+%d)", odo_delta / 10);
							else
								tr[4] = String.format("+%d", odo_delta / 10);
						}
						odo_trip_prev_known = false;
					}

					// TODO if delta mode, but both odos were 0, how to track for next stop?
					//   Maybe keep odos of last-known stop, calc delta from there
					//   but then "delta" is since last-known, not since last stop; shouldn't show it

					// Via
					final int viaID = ts.getVia_id();
					ViaRoute vr = null;
					if (viaID > 0)
					{
						vr = viaCache.get(viaID);
						if (vr == null)
							try
							{
								vr = new ViaRoute(conn, viaID);
								viaCache.put(viaID, vr);
							} catch (Throwable e) { }  // RDBKeyNotFoundException
					}
					if (vr == null)
						tr[6] = ts.getVia_route();
					else
						tr[6] = vr.getDescr();
					if (tr[6] != null)
						tr[6] = "via " + tr[6];

					// Description
					StringBuffer desc = new StringBuffer(getTStopLocDescr(ts, conn));

					// Look for a gas tstop
					if (ts.isSingleFlagSet(TStop.FLAG_GAS))
					{
						try
						{
							TStopGas tsg = new TStopGas(conn, ts.getID());
							final int gradeID = tsg.gas_brandgrade_id;
							if (gradeID != 0)
							{
								GasBrandGrade grade = gasCache.get(gradeID);
								if (grade == null)
								{
									try
									{
										grade = new GasBrandGrade(conn, gradeID);
										gasCache.put(gradeID, grade);
									}
									catch (Throwable th) {}
								}
								if (grade != null)
									tsg.gas_brandgrade = grade;  // for toStringBuffer's use
							}
							StringBuffer gsb = new StringBuffer("* Gas: ");
							gsb.append(tsg.toStringBuffer(veh));
							if (gradeID != 0)
								tsg.gas_brandgrade = null;  // clear the reference
							if (desc.length() > 0)
								gsb.append(' ');
							desc.insert(0, gsb);
						}
						catch (Throwable th) {}
					}

					// If it's the very last stop, Arrow to indicate that
					if ((ts == lastStop) && (desc.length() > 0)
						&& (odo_end != 0))
						desc.insert(0, "-> ");  // Very last stop: "-> location"
					tr[5] = desc.toString();

					// Comment, if any
					String stopc = ts.getComment();
					if (stopc != null)
						tr[7] = (doCommentBrackets) ? ("[" + stopc + "]") : stopc;

					// Done with this row
					tText.addElement(tr);

					// start-time (if present)
					if (ttcont != 0)
					{
						tr = new String[COL_HEADINGS.length];
						dtf.formatDateTimeInSeq(ttcont * 1000L, prevShownDT);
						tr[0] = prevShownDT.fmtDate;  // may be null
						tr[1] = prevShownDT.fmtTime;
						tText.addElement(tr);
					}
				}
			}  // each TStop

			// last rows, only if completed trip
			if (odo_end != 0)
			{
				// does next trip continue from the same tstop and odometer?
				if ((i < (L-1)) && (lastStop != null))
				{
					Trip nt = td.elementAt(i+1);
					nextTripUsesSameStop =
						(t.getOdo_end() == nt.getOdo_start())
						 && nt.isStartTStopFromPrevTrip();
				} else {
					nextTripUsesSameStop = false;
				}

				// last row of trip: odo, comment/desc
				tr = new String[COL_HEADINGS.length];
				if (nextTripUsesSameStop)
					tr[2] = ">";
				else
					tr[2] = "\\";
				tr[3] = Integer.toString((int) (odo_end / 10.0f));
				tr[5] = t.getComment();
				tText.addElement(tr);
			}
		}
	}

	/**
	 * Convert n to a 2-digit string.
	 * @param n  A non-negative integer
	 * @return  {@code n} as a string with at least 2 digits, such as "51" or "09", including a leading 0 if needed
	 * @since 0.9.20
	 */
	private final static String digits2(final int n)
	{
		if (n > 9)
			return Integer.toString(n);
		else
			return "0" + Integer.toString(n);
	}

	/**
	 * Add rows (simple mode) to strings from a list of {@link Trip}s and their {@link TStop}s.
	 * Columns of added rows line up with {@link #COL_HEADINGS_SIMPLE}.
	 * @param td    Trip data to add to <tt>tText</tt>
	 * @param tText Append rows here from <tt>td</tt>
	 * @param conn  Add from this connection
	 */
	public void addRowsFromTrips_formatTripsSimple
		(final Vector<Trip> td, Vector<String[]> tText, RDBAdapter conn)
	{
		final int L = td.size();

		// Loop for each trip in td
		for (int i = 0; i < L; ++i)  // towards end of trip, must look at next trip
		{
			Trip t = td.elementAt(i);

			String[] tr = new String[COL_HEADINGS_SIMPLE.length];

			// trip starting date: yyyy-mm-dd (not localized date-time format)
			final long tstart = t.getTime_start() * 1000L;
			{
				final Date tstartDate = new Date(tstart);
				tr[0] = Integer.toString(tstartDate.getYear() + 1900)
					+ '-' + digits2(tstartDate.getMonth() + 1) + '-' + digits2(tstartDate.getDate());
			}

			// start,end odo
			tr[1] = Integer.toString((int) (t.getOdo_start() / 10.0f));
			final int odo_end = t.getOdo_end();
			if (odo_end > 0)
				tr[2] = Integer.toString((int) (odo_end / 10.0f));

			// start,end time
			tr[3] = dtf.formatTime(tstart);
			final int time_end = t.getTime_end();
			if (time_end != 0)
				tr[4] = dtf.formatTime(time_end * 1000L);

			// Remaining fields might require TStops, so read them and continue.
			// All well-formed trips have 1 or more TStops.
			Vector<TStop> stops = t.readAllTStops();	// works for current, if addCommittedTStop was called

			final TStop ts_start = t.readStartTStop(true);
			final TStop ts_end = (stops != null) ? stops.lastElement() : null;

			// start,end location
			if (ts_start != null)
				tr[5] = getTStopLocDescr(ts_start, conn);
			if (ts_end != null)
				tr[6] = getTStopLocDescr(ts_end, conn);

			// trip comment, or highest tstop comment:
			tr[7] = t.readLatestComment();

			// Done with this row
			tText.addElement(tr);


			/*
			final int tcatID = t.getTripCategoryID();
			if (tcatID != 0)
			{
				TripCategory tcat = tcatCache.get(tcatID);
				if (tcat == null)
				{
					try
					{
						tcat = new TripCategory(conn, tcatID);
						tcat.setName("[" + tcat.getName() + "]");
						tcatCache.put(tcatID, tcat);
					}
					catch (Throwable th) {}
				}
				tr5 = tcat.getName();
			}

			final int pax = t.getPassengerCount();
			if (pax != -1)
			{
				StringBuilder sb = new StringBuilder();
				if (tr5 != null)
				{
					sb.append(tr5);
					sb.append(' ');
				}
				sb.append(TXT_PASSENGERS);  // "Passengers"
				sb.append(": ");
				sb.append(Integer.toString(pax));
				tr5 = sb.toString();
			}
		 	*/


		}
	}

	/**
	 * Read this TStop's location description from text or from its associated Location.
	 * Attempts to read or fill {@link #locCache}.
	 * A copy of this method is in org.shadowlands.roadtrip.android.LogbookRecentGas.
	 *<P>
	 * Since this method is for display only, not further processing, it tries to give
	 * a human-readable message if the location data is missing (db inconsistency).
	 *
	 * @param conn  db connection to use
	 * @param ts  TStop to look at
	 * @return Location text, or if not found in db,
	 *     "(locID " + {@link TStop#getLocationID() ts.getLocationID()} + " not found)"
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

			if (locDescr == null)
				// unlikely except while unit-testing new development;
				// inconsistent DB. Avoid NullPointerException if it happens
				locDescr = "(locID " + locID + " not found)";
		}
		return locDescr;
	}

	/**
	 * Set up the grid to add a new simple trip or a trip with stops.
	 * @param withStops  Does this trip include stops, or is it a simple trip?
	 * @throws IllegalStateException if already in add mode
	 * @see #finishAdd()
	 * @see #cancelAdd()
	 */
	public void beginAdd(final boolean withStops)
	    throws IllegalStateException
	{
		if (addMode)
			throw new IllegalStateException("Already adding");

		maxRowBeforeAdd = tDataTextRowCount;
		addMode = true;

		/**
		 * Write the template to the new rows
		 * (TODO) incl highest-date, odo, etc from prev-rows
		 * (TODO) other template: complex
		 */
		if (tAddedRows == null)
			tAddedRows = new Vector<String[]>();
		final String[][] template =
			withStops ? TEMPLATE_ADD_WITHSTOPS : TEMPLATE_ADD_SIMPLE;
		int r;
		for (r = 0; r < template.length; ++r)
		{
			// create a new row
			String[] td = new String[COL_HEADINGS.length];
			for (int i = 0; i < COL_HEADINGS.length; ++i)
			{
				String content;
				if (i < template[r].length)
					content = template[r][i];  // may be null
				else
					content = null;
				if (content == null)
					td[i] = "";
				else
					td[i] = content;
			}
			tAddedRows.addElement(td);
		}
		if (listener != null)
			listener.fireTableRowsInserted(maxRowBeforeAdd, maxRowBeforeAdd + r);
	}

	/**
	 * Interpret and add data entered by the user since {@link #beginAdd(boolean)}.
	 * If <tt>beginAdd</tt> wasn't previously called, do nothing.
	 */
	public void finishAdd()  {}
	  // TODO interpret data from maxRowBeforeAdd, save to db, un-set mode
	  //   Allow user to enter any of the trip_odo_delta_mode formats

	/**
	 * Cancel and clear the data entered by the user since {@link #beginAdd(boolean)}.
	 * If <tt>beginAdd</tt> wasn't previously called, do nothing.
	 */
	public void cancelAdd()
	{
		if (! addMode)
			return;

		final int L = tAddedRows.size();
		if (L > 0)
		{
    		if (listener != null)
    			listener.fireTableRowsDeleted(maxRowBeforeAdd+1, maxRowBeforeAdd+L);
		}
		tAddedRows.clear();
		tAddedRows = null;
		addMode = false;
	}

	/** For Week Mode, get the week increment when moving to previous/next trips */
	public int getWeekIncrement() { return weekIncr; }

	/** For Location Mode, get the trip-count increment when moving to previous trips */
	public int getTripIncrement() { return tripIncr; }

	/** For Location Mode, get the location ID; for Week Mode, returns 0. */
	public int getLocationModeLocID() { return filterLocID; }

	/** For Location Mode, are we showing trips for all vehicles? */
	public boolean isLocationModeAllVehicles() { return filterLoc_showAllV; }

	/**
	 * Get the number of ranges currently loaded from the database.
	 * @see #getRange(int)
	 */
	public int getRangeCount() { return tData.size(); }

	/**
	 * Get a {@link TripListTimeRange} currently loaded from the database.
	 * @param i  index of this range, 0 to {@link #getRangeCount()} - 1
	 * @return the range at index <tt>i</tt>
	 * @throws ArrayIndexOutOfBoundsException  if i &lt; 0 or i >= {@link #getRangeCount()}
	 */
	public TripListTimeRange getRange(final int i)
		throws ArrayIndexOutOfBoundsException
	{
		return tData.elementAt(i);
	}

	/** column count; same as length of {@link #COL_HEADINGS} */
	public int getColumnCount() { return COL_HEADINGS.length; }

	/** occupied row count; during "Add Trip" mode, includes current data entry and blank line at end */
	public int getRowCount()
	{
		int rc = tDataTextRowCount;
		if (addMode)
			++rc;
		if (tAddedRows != null)
			rc += tAddedRows.size();
		return rc;
	}

	public String getColumnName(final int c) { return COL_HEADINGS[c]; }

	public Object getValueAt(final int r, final int c)
	{
		/**
		 * Because the text isn't one long array, but is broken up
		 * among tData's elements, determine the row number.
		 */
		int tr;
		if ((r >= getValue_RangeRow0) && (r < getValue_RangeRowN))
		{
			tr = r - getValue_RangeRow0;

		} else if (r >= tDataTextRowCount)
		{
			if (tAddedRows != null)
			{
				tr = r - tDataTextRowCount;
				if (tr < tAddedRows.size())
					return tAddedRows.elementAt(tr)[c];
				else
					return "";
			} else {
				return "";
			}

		} else if (r == getValue_RangeRowN)
		{
			// just beyond end of previous range
			boolean trFound;
			tr = -1;  // satisfy javac
			if (getValue_RangeIndex == tData.size())
			{
				trFound = false;
			} else {
				++getValue_RangeIndex;
				final TripListTimeRange ttr = tData.elementAt(getValue_RangeIndex);
				final Vector<String[]> tText = ttr.tText;
				if (tText == null)
				{
					trFound = false;
				} else {
					trFound = true;
					final int L = tText.size();   // TODO not 0
					getValue_RangeRow0 = r;
					getValue_RangeRowN = r + L;
					getValue_RangeText = tText;
					tr = 0;  // == r - getValue_RangeRow0
				}
			}
			if (! trFound)
			{
				getValue_RangeRow0 = -1;
				getValue_RangeRowN = -1;
				return "";				
			}

		} else if (r == (getValue_RangeRow0 - 1))
		{
			// just before start of previous range
			boolean trFound;
			tr = -1;  // satisfy javac
			if (getValue_RangeIndex == 0)
			{
				trFound = false;
			} else {
				--getValue_RangeIndex;
				final TripListTimeRange ttr = tData.elementAt(getValue_RangeIndex);
				final Vector<String[]> tText = ttr.tText;
				if (tText == null)
				{
					trFound = false;
				} else {
					trFound = true;
					final int L = tText.size();   // TODO not 0
					getValue_RangeRow0 -= L;
					getValue_RangeRowN = r + 1;
					getValue_RangeText = tText;
					tr = L - 1;  // == r - getValue_RangeRow0
				}
			}
			if (! trFound)
			{
				getValue_RangeRow0 = -1;
				getValue_RangeRowN = -1;
				return "";				
			}

		} else {
			int totalr = 0;        // Total text rows before this range
			tr = r;                // invariant: tr == r - totalr
			boolean trFound = false;
			final int tdSize = tData.size();
			for (int i = 0; i < tdSize; ++i)
			{
				final TripListTimeRange ttr = tData.elementAt(i);
				final Vector<String[]> tText = ttr.tText;
				if (tText == null)
					break;
				final int L = tText.size();
				if (tr < L)
				{
					trFound = true;
					getValue_RangeText = tText;
					getValue_RangeIndex = i;
					getValue_RangeRow0 = totalr;
					getValue_RangeRowN = totalr + L;
					break;
				}
				totalr += L;
				tr -= L;  // invariant: tr == r - totalr
			}
			if (! trFound)
				return "";
		}
		if (tr < getValue_RangeText.size())
			return getValue_RangeText.elementAt(tr)[c];
		else
			return "";
	}

	/**
	 * All added cells (not those loaded from the
	 * database) are editable, except the first 2 columns.
	 * @param r Row, from 0
	 * @param c Column, from 0
	 */
	public boolean isCellEditable(int r, int c)
	{
		return (r >= tDataTextRowCount) && (c >= 2) && (c < COL_HEADINGS.length);
	}

	/**
	 * Update the value in a cell (for AbstractTableModel).
	 * Only the added rows (not those loaded from the
	 * database) are editable. 
	 * @param r Row, from 0
	 * @param c Column, from 0
	 */
	public void setValueAt(Object newValue, final int r, final int c)
	{
		final int addRow = r - tDataTextRowCount;
		if ((addRow < 0) || (c >= COL_HEADINGS.length))
			return;
		boolean rowExists = (tAddedRows != null) && (addRow < tAddedRows.size());
		if (rowExists)
		{
			tAddedRows.elementAt(addRow)[c] = newValue.toString();
		} else {
			// create a new row
			String[] td = new String[COL_HEADINGS.length];
			for (int i = 0; i < COL_HEADINGS.length; ++i)
				if (i != c)
					td[i] = "";
				else
					td[i] = newValue.toString();
			tAddedRows.addElement(td);
		}
		if (listener != null)
		{
			listener.fireTableCellUpdated(r, c);
			if (! rowExists)
				listener.fireTableRowsInserted(r+1, r+1);
		}
	}

	/**
	 * Set (or clear) our listener.
	 * @param tcl  The only listener (does not allow multiple at once), or null to clear.
	 */
	public void setListener(TableChangeListener tcl)
	{
		listener = tcl;
	}

}  // public class LogbookTableModel
