/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *  This file Copyright (C) 2010-2017 Jeremy D Monin <jdmonin@nand.net>
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

package org.shadowlands.roadtrip.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.shadowlands.roadtrip.model.LogbookTableModel;  // strictly for COL_TSTOP_DESC and javadocs

/**
 * In-memory representation, and database access for, a Trip.
 * Trips can be local within a {@link GeoArea}, or a "roadtrip" between areas.
 * See schema file and its comments for details.
 * An ongoing local trip can be converted to a roadtrip with {@link #convertLocalToRoadtrip(TStop)}.
 * A roadtrip which never left its starting area will be converted to a local trip by
 * {@code VehSettings.endCurrentTrip}, see end of this javadoc section for details.
 *<P>
 * Several static methods of this class select trips from the
 * database by different criteria, see method list for details.
 *<P>
 * Some methods related to a trip's {@link TStop}s are static
 * methods in {@link TStop}, so look into that class too.
 *<P>
 * The starting and ending location are taken from the trip's TStops.
 *<P>
 * Starting location: If a Trip doesn't begin from the previous
 * trip's TStop, it must begin with a TStop with trip-odo = 0,
 * same odo_total as the trip's odo_start, and null time_stop.
 * (See {@link #readStartTStop(boolean)})
 *<P>
 * Ending location: Taken from the {@link #isEnded() completed trip}'s highest TStop id.
 * (See {@link #readLatestTStop()})
 *<P>
 * To end a trip, call {@link VehSettings#endCurrentTrip(RDBAdapter, Vehicle, int, int, int, TripCategory, int)}.
 * If all of a roadtrip's TStops are in its starting GeoArea, that method will convert it to a local trip.
 *<P>
 * "Undo" is available for some trip actions:
 *<UL>
 * <LI> If a trip is started by mistake, that new trip can be cancelled if it doesn't have any {@link TStop}s
 *      yet - {@link #cancelAndDeleteCurrentTrip()}.
 * <LI> Undo continuing travel from the most recent stop during the current trip - {@link #cancelContinueFromTStop()}.
 * <LI> Undo End Trip - {@link #cancelEndPreviousTrip(RDBAdapter)}.
 *</UL>
 *
 * @author jdmonin
 */
public class Trip extends RDBRecord
{
    private static final String TABNAME = "trip";

    /** The <tt>time_start</tt> db field; a trip's starting time */
    private static final String FIELD_TIME_START = "time_start";

    /** db table fields.
     * @see #buildInsertUpdate()
     */
    private static final String[] FIELDS =
        { "vid", "did", "catid", "odo_start", "odo_end", "aid", "tstopid_start",
		  FIELD_TIME_START, "time_end", "start_lat", "start_lon", "end_lat", "end_lon",
		  "freqtripid", "comment", "passengers", "roadtrip_end_aid", "has_continue" };

    private static final String[] FIELDS_AND_ID =
	    { "vid", "did", "catid", "odo_start", "odo_end", "aid", "tstopid_start",
		  FIELD_TIME_START, "time_end", "start_lat", "start_lon", "end_lat", "end_lon",
		  "freqtripid", "comment", "passengers", "roadtrip_end_aid", "has_continue", "_id" };

    /**
     * The two table fields for trip's starting and ending time; {@code time_start} is required (not null),
     * {@code time_end} is optional.
     * Used in {@link #getDBEarliestLatestTripTimes(RDBAdapter)}.
     * @since 0.9.50
     */
    private static final String[] FIELDS_TIMES = { "time_start", "time_end" };

    /**
     * Field names/where-clause for use in {@link #recentTripForVehicle(RDBAdapter, Vehicle, boolean, boolean)}.
     * @since 0.9.50
     */
    private static final String WHERE_VID =
	"_id=(select max(_id) from trip where vid = ?)";

    /** Field names/where-clause for use in {@link #recentTripForVehicle(RDBAdapter, Vehicle, boolean, boolean)}. */
    private static final String WHERE_VID_AND_NOT_ROADTRIP =
	"_id=(select max(_id) from trip where vid = ? and roadtrip_end_aid is null)";

    /** Field names/where-clause for use in {@link #recentTripForVehicle(RDBAdapter, Vehicle, boolean, boolean)}. */
    private static final String WHERE_VID_AND_IS_ROADTRIP =
	"_id=(select max(_id) from trip where vid = ? and roadtrip_end_aid is not null)";

    /** Where-clause for use in {@link #tripsForVehicle(RDBAdapter, Vehicle, int, int, boolean, boolean, boolean)}  */
    private static final String WHERE_TIME_START_AND_VID =
    	"(time_start >= ?) and (time_start <= ?) and vid = ?";

    /** Where-clause for use in {@link #tripsForVehicle_searchBeyond(RDBAdapter, String, int, int, int, boolean)  */
    private static final String WHERE_TIME_START_AFTER_AND_VID =
    	"(time_start > ?) and vid = ?";

    /** Where-clause for use in {@link #tripsForVehicle_searchBeyond(RDBAdapter, String, int, int, int, boolean)  */
    private static final String WHERE_TIME_START_BEFORE_AND_VID =
    	"(time_start < ?) and vid = ?";

    /** Where-clause for use in {@link #tripsForLocation(RDBAdapter, int, int, int, boolean)} */
    private static final String WHERE_LOCID =
    	"_id in ( select distinct tripid from tstop where locid = ? order by tripid desc limit ? )";

    /** Where-clause for use in {@link #tripsForLocation(RDBAdapter, int, int, int, boolean)} */
    private static final String WHERE_LOCID_AND_TRIPID_BEFORE =
    	"_id in ( select distinct tripid from tstop where locid = ? and tripid < ? order by tripid desc limit ? )";

    /** Where-clause for use in {@link #tripsForLocation(RDBAdapter, int, int, int, boolean)} */
    private static final String WHERE_LOCID_AND_TRIPID_AFTER =
    	"_id in ( select distinct tripid from tstop where locid = ? and tripid > ? order by tripid desc limit ? )";

    private static final int WEEK_IN_SECONDS = 7 * 24 * 60 * 60;

    private int vehicleid;
    private int driverid;
    /** Optional {@link TripCategory} ID.  0 is empty/unused (null in db). */
    private int catid;
    /** Starting odometer, in tenths of a unit */
    private int odo_start;
    /** Ending odometer, in tenths of a unit, or 0 if trip is still in progress */
    private int odo_end;

    /** Area ID.  0 is empty/unused. */
    private int a_id;

    /**
     * Previous trip's last {@link TStop}, which gives the starting {@link Location} (descr and locid) for this trip.
     * If this field is 0, this trip's first TStop (with lowest {@code TStop._id}, trip odometer 0, and total
     * odometer == {@link #odo_start}) gives the starting location.
     */
    private int tstopid_start;

    /**
     * Cached <tt>tstopid_start</tt>, or null; this gives the starting location (descr and locid) for this trip.
     * This may possibly be the first TStop of this trip, not the last tstop of the previous trip,
     * if {@link #readStartTStop(boolean) readStartTStop(true)} was called.
     */
    private transient TStop tstop_start;

    /** optional time field; see sql schema for date fmt. 0 if unused. */
    private int time_start, time_end;
    /** may be null */
    private String start_lat, start_lon, end_lat, end_lon;
    /** This trip's {@link FreqTrip} ID; 0 if unused */
    private int freqtripid;
    private String comment;
    /** This trip's optional passenger count, or -1 if unused */
    private int passengers = -1;
    /** ending area ID if roadtrip, 0 otherwise. If trip in progress, may change. See {@link #getRoadtripEndAreaID()} */
    private int roadtrip_end_aid;
    private boolean has_continue;

    /** null unless {@link #readAllTStops()} called */
    private transient Vector<TStop> allStops;

    /**
     * false until first call to {@link #addCommittedTStop(TStop)}
     * or to {@link #readAllTStops()}.
     * Used when we've been read from db, but {@link #readAllTStops()} hasn't
     * been called.  Used to prevent {@link #addCommittedTStop(TStop)} from
     * building a partial list of {@link #allStops}.
     */
    private boolean hasCheckedStops = false, hasUnreadStops;

    /**
     * Retrieve all Trips for a Vehicle.
     * @param db  db connection
     * @param veh  vehicle to look for, or null for all trips in database
     * @param alsoTStops  If true, call {@link #readAllTStops()} for each trip found
     * @return Trips for this Vehicle, sorted by time_start, or null if none
     * @throws IllegalStateException if db not open
     * @see #tripsForVehicle(RDBAdapter, Vehicle, int, int, boolean, boolean, boolean)
     */
    public static List<Trip> tripsForVehicle(RDBAdapter db, Vehicle veh, final boolean alsoTStops)
        throws IllegalStateException
    {
    	if (db == null)
    		throw new IllegalStateException("db null");
    	Vector<String[]> sv;
    	if (veh != null)
    	    sv = db.getRows
    	    (TABNAME, "vid", Integer.toString(veh.getID()), FIELDS_AND_ID, "_id", 0);
    	else
    	    sv = db.getRows
    	    (TABNAME, null, (String[]) null, FIELDS_AND_ID, "time_start", 0);
    	if (sv == null)
    		return null;

	return parseStringsToTrips(db, alsoTStops, sv);
    }

    /**
     * Retrieve all Trips within a date range for a Vehicle.
     * @param db  db connection
     * @param veh  vehicle to look for; not null
     * @param timeStart  Starting date/time of trip range, in Unix format
     * @param weeks   Retrieve this many weeks past timeStart
     * @param searchBeyondWeeks  If true, and if no trips found within
     *          <tt>weeks</tt>, keep searching until a trip is found
     * @param towardsNewer  If true, retrieve <tt>timeStart</tt> and newer;
     *          otherwise retrieve <tt>timeStart</tt> and older.
     * @param alsoTStops  If true, call {@link #readAllTStops()} for each trip found
     * @return Trips for this Vehicle, sorted by time_start, or null if none
     * @throws IllegalStateException if db not open
     * @see #tripsForVehicle(RDBAdapter, Vehicle, boolean)
     */
    public static TripListTimeRange tripsForVehicle
    	(RDBAdapter db, Vehicle veh, final int timeStart, final int weeks,
    	 final boolean searchBeyondWeeks, final boolean towardsNewer, final boolean alsoTStops)
        throws IllegalStateException
    {
    	if (db == null)
    		throw new IllegalStateException("db null");

    	// Start and end of time_start search range;
    	// will expand the range in loop before querying.
    	int t0 = timeStart, t1 = timeStart;

    	Vector<String[]> sv = null;
    	final String vIDstr = Integer.toString(veh.getID());

    	/**
    	 * Search in the range t0 to t1 for trips.
    	 * If searchBeyondWeeks, try 2 more times
    	 * by moving further into the past/future.
    	 */
    	for (int tries = 0; (sv == null) && (tries <= 2) && searchBeyondWeeks; ++tries)
    	{
			if (towardsNewer)
				t1 += (weeks * WEEK_IN_SECONDS);
			else
				t0 -= (weeks * WEEK_IN_SECONDS);    			
			final String[] whereArgs = {
				Integer.toString(t0), Integer.toString(t1), vIDstr
			};
			sv = db.getRows(TABNAME, WHERE_TIME_START_AND_VID, whereArgs, FIELDS_AND_ID, "_id", 0);
    	}

		final boolean searchedBeyond;
		if ((sv == null) && searchBeyondWeeks)
		{
			sv = tripsForVehicle_searchBeyond
				(db, vIDstr, t0, t1, weeks, towardsNewer);
			searchedBeyond = true;
		} else {
			searchedBeyond = false;
		}

    	if (sv == null)
    	{
    		return null;
    	}

	final List<Trip> trips = parseStringsToTrips(db, alsoTStops, sv);
	if (trips == null)
    	{
    		return null;
    	} else {
    		if (searchedBeyond)
    		{
    			// Make sure the range covers the actual trip times
    			if (towardsNewer)
				t1 = trips.get(trips.size() - 1).readLatestTime();
    			else
				t0 = trips.get(0).getTime_start();
    		}

		return new TripListTimeRange(t0, t1, trips);
    	}
    }

	/**
	 * Search for trips beyond this range.
	 * Uses min or max to ensure any newer or older data is found,
	 * no matter how long the time difference is.
	 *
	 * @param db  db connection
	 * @param vIDstr   Vehicle ID to look for, as Integer.toString (for sql)
	 * @param tt0  Early end of time range
	 * @param tt1  Late end of time range
	 * @param weeks   Retrieve this many weeks past t0 or t1
	 * @param towardsNewer  If true, retrieve newer past t1;
	 *            otherwise return older past t0.
	 * @return Trip rows from sql SELECT, or null if none found
	 */
	private static final Vector<String[]> tripsForVehicle_searchBeyond
		(RDBAdapter db, String vIDstr, final int tt0, final int tt1,
		 final int weeks, final boolean towardsNewer)
	{
		/**
		 * Find the next trip id for this vehicle, and load
		 * its trip (sTrip0), to find the starting time (timeStart)
		 * for the time range to load.
		 */
		final int tripID;
		if (towardsNewer)
			tripID = db.getRowIntField(TABNAME,
				"min(_id)",
				WHERE_TIME_START_AFTER_AND_VID,
				new String[]{ Integer.toString(tt1), vIDstr }, -1 );
		else
			tripID = db.getRowIntField(TABNAME,
				"max(_id)",
				WHERE_TIME_START_BEFORE_AND_VID,
				new String[]{ Integer.toString(tt0), vIDstr }, -1 );
		if (tripID == -1)
		{
			return null;  // <--- nothing found ---
		}
		final int timeStart = db.getRowIntField(TABNAME, tripID, FIELD_TIME_START, 0);

		/**
		 * Now, load weeks beyond that starting time.
		 */
		final int t0, t1;
		if (towardsNewer)
		{
			t0 = timeStart;
			t1 = timeStart + (weeks * WEEK_IN_SECONDS);
		} else {
			t1 = timeStart;
			t0 = timeStart - (weeks * WEEK_IN_SECONDS);
		}
		final String[] whereArgs = {
			Integer.toString(t0), Integer.toString(t1), vIDstr
		};
		Vector<String[]> sv = db.getRows
			(TABNAME, WHERE_TIME_START_AND_VID, whereArgs, FIELDS_AND_ID, "_id", 0);

		return sv;
	}

    /**
     * Retrieve a range of Trips that include a given Location.
     * @param db  db connection
     * @param locID  Location to look for
     * @param veh  vehicle to look for, or null for all vehicles
     * @param prevTripID  Previous end of trip range: A trip newer or older than the
     *          ones to load, or 0 to get the latest trips
     * @param towardsNewer  If true, retrieve newer than <tt>prevTripID</tt>;
     *          otherwise retrieve older than <tt>prevTripID</tt>.
     * @param limit  Maximum number of trips to return; cannot be 0, for 'no limit' use a large number.
     * @param alsoTStops  If true, call {@link #readAllTStops()} for each trip found
     * @return Trips for this Location, sorted by <tt>time_start</tt>, or null if none
     * @throws IllegalArgumentException if limit is &lt;= 0,
     *           or if <tt>towardsNewer</tt> true, but <tt>laterTripID</tt> == 0
     * @throws IllegalStateException if db not open
     */
    public static TripListTimeRange tripsForLocation
    	(RDBAdapter db, final int locID, final Vehicle veh,
    	 final int prevTripID, final boolean towardsNewer, final int limit, 
    	 final boolean alsoTStops)
        throws IllegalArgumentException, IllegalStateException
    {
		if (towardsNewer && (prevTripID == 0))
			throw new IllegalArgumentException("towardsNewer, prevTripID 0");
    	if (db == null)
    		throw new IllegalStateException("db null");
    	if (limit <= 0)
    		throw new IllegalArgumentException("limit");

    	Vector<String[]> sv = null;

		final String[] whereArgs;  // Length must match number of ? in whereClause
		if (prevTripID == 0)
		{
			whereArgs = new String[ (veh != null) ? 3 : 2 ];
			// [0] init is below
			whereArgs[1] = Integer.toString( limit );
			if (veh != null)
				whereArgs[2] = Integer.toString( veh.getID() );
		} else {
			whereArgs = new String[ (veh != null) ? 4 : 3 ];
			// [0] init is below
			whereArgs[1] = Integer.toString( prevTripID );
			whereArgs[2] = Integer.toString( limit );
			if (veh != null)
				whereArgs[3] = Integer.toString( veh.getID() );
		}
		whereArgs[0] = Integer.toString(locID);

		String whereClause;
		final String orderClause;
		if (prevTripID != 0)
		{
			if (towardsNewer)
				whereClause = WHERE_LOCID_AND_TRIPID_AFTER;
			else
				whereClause = WHERE_LOCID_AND_TRIPID_BEFORE;
		} else {
			whereClause = WHERE_LOCID;
		}
		if (veh != null) {
			whereClause = whereClause + " and vid = ?";
			orderClause = "_id";
		} else {
			orderClause = FIELD_TIME_START;
		}
		sv = db.getRows(TABNAME, whereClause, whereArgs, FIELDS_AND_ID, orderClause, 0);

    	if (sv == null)
    	{
    		return null;
    	}

	final List<Trip> trips = parseStringsToTrips(db, alsoTStops, sv);
	if (trips == null)
    		return null;
	else
    		return new TripListTimeRange(trips, locID);
    }

    /** parse String[] to Trips, optionally also call {@link #readAllTStops()} */
	private static final List<Trip> parseStringsToTrips
		(RDBAdapter db, final boolean alsoTStops, Vector<String[]> sv)
	{
		final List<Trip> trips = new ArrayList<Trip>(sv.size());
		try
		{
			Trip t;
	    	for (int i = 0; i < sv.size(); ++i)
	    	{
	    		t = new Trip(db, sv.elementAt(i));
	    		if (alsoTStops)
	    			t.readAllTStops();
			trips.add(t);
	    	}
		} catch (RDBKeyNotFoundException e) { }

		return trips;
	}

    /**
     * Get the most recent trip, local trip, or roadtrip for this vehicle, if any.
     * Does not call {@link #readAllTStops()} on the returned trip.
     * @param db  db connection
     * @param veh  vehicle to look for; not null
     * @param localOnly  If true get most recent local trip, exclude roadtrips
     * @param roadtripOnly  If true get most recent roadtrip, exclude local trips
     * @return Most recent Trip for this Vehicle, sorted by _id descending, or null if none
     * @throws IllegalStateException if db not open
     * @throws IllegalArgumentException if both {@code localOnly} and {@code roadtripOnly}
     * @since 0.9.03
     * @see #recentInDB(RDBAdapter)
     */
    public static Trip recentTripForVehicle
	(RDBAdapter db, Vehicle veh, final boolean localOnly, final boolean roadtripOnly)
	throws IllegalStateException, IllegalArgumentException
    {
    	if (db == null)
    		throw new IllegalStateException("db null");
    	if (localOnly && roadtripOnly)
    		throw new IllegalArgumentException();
    	final String where =
		(localOnly) ? WHERE_VID_AND_NOT_ROADTRIP
			    : (roadtripOnly) ? WHERE_VID_AND_IS_ROADTRIP : WHERE_VID;
		// "_id = (select max(_id) from trip where vid = ? and roadtrip_end_aid is null" or similar

    	Vector<String[]> sv = db.getRows
    		(TABNAME, where, new String[]{ Integer.toString(veh.getID()) }, FIELDS_AND_ID, "_id DESC", 1);
    		// LIMIT 1
    	if (sv == null)
    		return null;
    	try
    	{
    		return new Trip(db, sv.firstElement());
    	}
    	catch (RDBKeyNotFoundException e)
    	{
		return null;  // not thrown but required by constructor
    	}
    }

    /**
     * Get the most recent trip in the database, if any.
     * Does not call {@link #readAllTStops()} on the returned trip.
     * @param db  db connection
     * @return the newest trip, or null if none in the database
     * @throws IllegalStateException if db not open
     * @see #recentTripForVehicle(RDBAdapter, Vehicle, boolean)
     * @since 0.9.07
     */
    public static Trip recentInDB(RDBAdapter db)
		throws IllegalStateException
    {
    	if (db == null)
    		throw new IllegalStateException("db null");
    	final int tripID = db.getRowIntField(TABNAME, "MAX(_id)", null, (String[]) null, 0);
    	if (tripID == 0)
    		return null;
    	try {
			return new Trip(db, tripID);
		} catch (RDBKeyNotFoundException e) {
			return null;  // required by compiler, but we know the ID exists
		}
    }

    /**
     * Retrieve the earliest and latest trip timestamps in the database.
     * Uses {@code min(_id), max(_id)} to get earliest and latest trip;
     * with multiple vehicles and if trip({@code max(_id)}) hasn't finished,
     * there may be a more recent completed trip. This method is accurate
     * enough for an overall range of trip dates in the database.
     * Does not examine {@link TStop} times.
     * @param db  db connection
     * @return  The earliest trip's starting time, and the latest trip's ending time
     *     if available or starting time if not.  If no trips, returns null.
     *     All timestamps are in Unix format.
     * @throws IllegalStateException if db not open
     * @since 0.9.50
     */
    public static int[] getDBEarliestLatestTripTimes(RDBAdapter db)
	throws IllegalStateException
    {
	if (db == null)
		throw new IllegalStateException("db null");

	int[] ret = new int[2];

	final int minId = db.getRowIntField(TABNAME, "min(_id)", null, (String[]) null, -1);
	if (minId == -1)
		return null;  // <--- Early return: No trips ---

	final int maxId = db.getRowIntField(TABNAME, "max(_id)", null, (String[]) null, -1);  // has min -> has max

	// start time of earliest trip
	ret[0] = db.getRowIntField(TABNAME, minId, FIELD_TIME_START, 0);

	// times of latest trip
	String[] row = db.getRow(TABNAME, maxId, FIELDS_TIMES);
	if (row[1] != null)
		ret[1] = Integer.parseInt(row[1]);  // ending time
	if (ret[1] == 0)
		ret[1] = Integer.parseInt(row[0]);  // starting time

	return ret;
    }

    /**
     * Cancel ending the previous (most recent) trip of the current vehicle.
     * That trip will again become the vehicle's current trip, and this method
     * will call {@link #setOdo_end(int) setOdo_end(0)} and commit the trip,
     * then call {@link #cancelContinueFromTStop() currT.cancelContinueFromTStop()}
     * to be stopped at the trip's final TStop and location.
     *<P>
     * The vehicle must not already have a current trip.
     * The previous trip will be queried with
     * {@link #recentTripForVehicle(RDBAdapter, Vehicle, boolean, boolean) recentTripForVehicle(db, currV, false, false)},
     * not {@link Vehicle#getLastTripID() currV.getLastTripID()}.
     *<P>
     * The vehicle must already be the current vehicle, in case db contains data from an old version
     * and some vehicles don't have {@link VehSettings} entries. Changing the current vehicle
     * 'upgrades' that vehicle's old data into {@code VehSettings} entries.
     * @param db  db connection
     * @return  True if the previous trip's end was cancelled and it's the current trip again;
     *     false if no trips were found for current vehicle or if
     *     {@code Settings#getCurrentVehicle(RDBAdapter, boolean)} == null.
     * @throws IllegalStateException if missing any of the conditions listed above,
     *     or if null != {@link VehSettings#getCurrentTrip(RDBAdapter, Vehicle, boolean)},
     *     or if {@code dbConn} is null or not open.
     * @since 0.9.50
     */
    public static boolean cancelEndPreviousTrip(RDBAdapter db)
	throws IllegalStateException
    {
	final Vehicle currV = Settings.getCurrentVehicle(db, false);
	if (currV == null)
		return false;

	if (null != VehSettings.getCurrentTrip(db, currV, false))
		throw new IllegalStateException("Vehicle " + currV.getID() + " has currT");

	// query db for previous trip, in case Vehicle.getLastTripID() is somehow outdated
	final Trip tr = recentTripForVehicle(db, currV, false, false);
	if (tr == null)
		return false;

	tr.setOdo_end(0);
	tr.setTime_end(0);
	tr.commit();

	VehSettings.setCurrentTrip(db, currV, tr);
	tr.cancelContinueFromTStop();  // find and update CURRENT_TSTOP, PREV_LOCATION, etc

	return true;
    }

    /**
     * Retrieve an existing trip, by id, from the database.
     *
     * @param db  db connection
     * @param id  id field
     * @throws IllegalStateException if db not open
     * @throws RDBKeyNotFoundException if cannot retrieve this ID
     */
    public Trip(RDBAdapter db, final int id)
        throws IllegalStateException, RDBKeyNotFoundException
    {
    	super(db, id);
    	String[] rec = db.getRow(TABNAME, id, FIELDS);
    	if (rec == null)
    		throw new RDBKeyNotFoundException(id);

    	initFields(rec);
    }

    /**
     * Existing record: Fill our obj fields from db-record string contents.
     * @param db  connection
     * @param rec  Record's field contents, as returned by db.getRows({@link #FIELDS_AND_ID}); last element is _id
     * @throws RDBKeyNotFoundException not thrown, but required due to super call
     */
    private Trip(RDBAdapter db, final String[] rec) throws RDBKeyNotFoundException
    {
    	super(db, Integer.parseInt(rec[FIELDS.length]));
    	initFields(rec);
    }

    /**
     * Fill our obj fields from db-record string contents.
     * <tt>id</tt> is not filled; the constructor has filled it already.
     * @param rec  Record's field contents, as returned by db.getRow({@link #FIELDS}) or db.getRows({@link #FIELDS_AND_ID})
     */
	private void initFields(String[] rec)
	{
		vehicleid = Integer.parseInt(rec[0]);  // FK
    	driverid = Integer.parseInt(rec[1]);  // FK
    	if (rec[2] != null)
    		catid = Integer.parseInt(rec[2]);  // FK
    	odo_start = Integer.parseInt(rec[3]);
    	if (rec[4] != null)
    		odo_end = Integer.parseInt(rec[4]);
    	if (rec[5] != null)
    		a_id = Integer.parseInt(rec[5]);
    	if (rec[6] != null)
    		tstopid_start = Integer.parseInt(rec[6]);  // FK
    	if (rec[7] != null)
    		time_start = Integer.parseInt(rec[7]);
    	if (rec[8] != null)
    		time_end = Integer.parseInt(rec[8]);
    	start_lat = rec[9];
    	start_lon = rec[10];
    	end_lat = rec[11];
    	end_lon = rec[12];
    	if (rec[13] != null)
    		freqtripid = Integer.parseInt(rec[13]);  // FK
    	comment = rec[14];
    	if (rec[15] != null)
    		passengers = Integer.parseInt(rec[15]);
    	else
    		passengers = -1;
    	if (rec[16] != null)
    		roadtrip_end_aid = Integer.parseInt(rec[16]);
    	has_continue = ("1".equals(rec[17]));
	}

    /**
     * Create a new trip, but don't yet write to the database.
     * When ready to write (after any changes you make to this object),
     * call {@link #insert(RDBAdapter)}.
     *<P>
     * To set the optional trip category or passenger count,
     * call {@link #setTripCategoryID(int)} or {@link #setPassengerCount(int)} before <tt>insert</tt>.
     *
     * @param veh    Vehicle used on this trip
     * @param driver Driver for the trip
     * @param odo_start Starting odometer; required; not 0, unless this vehicle's total odometer really is 0.0.
     * @param odo_end   Ending odometer, or 0 if still in progress
     * @param a_id      GeoArea ID, or 0; for roadtrips, the starting area
     * @param tstop_start  Starting TStop from end of previous trip, or null;
     *                     if null, create this trip's starting {@link TStop} soon for
     *                     data consistency (see schema for its required field contents).
     * @param time_start  Starting date/time of trip, in Unix format, or 0 if unused
     * @param time_end    Ending date/time of trip, or 0 if still in progress or unused
     * @param start_lat  Starting latitude, or null
     * @param start_lon  Starting longitude, or null
     * @param end_lat    Ending latitude, or null
     * @param end_lon    Ending longitude, or null
     * @param freqtrip  Frequent trip, or null
     * @param comment    Comment, or null
     * @param roadtrip_end_aid  For roadtrips, the ending GeoArea ID; 0 for local trips
     * @param has_continue      Does this trip continue to another trip?
     *
     * @throws IllegalArgumentException  if ! driver.isDriver();
     *         or if tstop_start != null and odo_start != its non-zero getOdo_total().
     *         If this odo_start problem would happen, but they differ only in the final (tenths) digit,
     *         the new Trip's odo_start is changed to match tstop.odo_total.
     */
    public Trip(Vehicle veh, Person driver, int odo_start, final int odo_end, final int a_id,
		TStop tstop_start, final int time_start, final int time_end,
		final String start_lat, final String start_lon, final String end_lat, final String end_lon,
		final FreqTrip freqtrip, final String comment, final int roadtrip_end_aid, final boolean has_continue)
        throws IllegalArgumentException
    {
    	super();
    	if (! driver.isDriver())
    		throw new IllegalArgumentException("person.isDriver false: " + driver.getName());

    	vehicleid = veh.getID();
    	driverid = driver.getID();
    	catid = 0;
        if (tstop_start != null)
        {
        	final int ts_odo = tstop_start.getOdo_total();
        	if ((ts_odo != 0) && (ts_odo != odo_start))
        	{
        		if ((ts_odo / 10) == (odo_start / 10))
        			odo_start = ts_odo;
        		else
        			throw new IllegalArgumentException("tstop_start.getOdo_total mismatch: trip odo_start " + odo_start + ", tstop.total " + ts_odo);
        	}
        	tstopid_start = tstop_start.getID();
        	this.tstop_start = tstop_start;
        }
        this.odo_start = odo_start;
        this.odo_end = odo_end;
        this.a_id = a_id;
        this.time_start = time_start;
        this.time_end = time_end;
        this.start_lat = start_lat;
        this.start_lon = start_lon;
        this.end_lat = end_lat;
        this.end_lon = end_lon;
        if (freqtrip != null)
        	freqtripid = freqtrip.getID();
        else
        	freqtripid = 0;
    	this.comment = comment;
        this.roadtrip_end_aid = roadtrip_end_aid;
        this.has_continue = has_continue;
        hasCheckedStops = true;  // since it's a new trip,
        hasUnreadStops = false;  // we know there are no stops.
    }

    /**
     * Retrieve all stops for this Trip.
     * Cached after the first read.
     * Also sets <tt>tstop_start</tt>.
     *<P>
     * Note that {@link #readStartTStop(boolean) readStartTStop(false)} is called and cached,
     * but is not added to the list returned here.
     * Call that method to get the starting TStop if needed.
     *<P>
     * If you add a new TStop to this Trip after calling this
     * method, please call {@link #addCommittedTStop(TStop)} to
     * keep the cached list consistent.
     *
     * @return  ordered list of stops, or null if none
     * @throws IllegalStateException if the db connection is closed
     * @see #readAllTStops(boolean, boolean)
     * @see #isStartTStopFromPrevTrip()
     */
    public Vector<TStop> readAllTStops()
        throws IllegalStateException
    {
	return readAllTStops(false, false);
    }

    /**
     * Retrieve all stops for this Trip, except possibly the one
     * at the destination which ends the trip.
     * Cached after the first read, unless <tt>ignoreTripEndStop</tt> is true.
     * Also sets <tt>tstop_start</tt>.
     *<P>
     * If this trip didn't continue from the previous trip's end location,
     * the starting TStop will be first item in the list returned.
     * Its odo_total matches the trip's {@link Trip#getOdo_start()},
     * and its odo_trip is 0.
     *<P>
     * Note that {@link #readStartTStop(boolean)} is called and cached,
     * but is not added to the list returned here.
     * Call that method to get the starting TStop if needed.
     *<P>
     * If you add a new TStop to this Trip after calling this
     * method, please call {@link #addCommittedTStop(TStop)} to
     * keep the cached list consistent.
     *
     * @param  ignoreTripEndStop  If true, and {@link #isEnded()} is true,
     *         don't include the TStop at the destination which ends the trip.
     * @param  bypassCache  If true, ignore the previously cached list of
     *         the trip's TStops, and don't cache the new result.
     *         (added in v0.9.50)
     * @return  ordered list of stops, or null if none
     * @throws IllegalStateException if the db connection is closed
     * @see #hasIntermediateTStops()
     */
    public Vector<TStop> readAllTStops(boolean ignoreTripEndStop, final boolean bypassCache)
	    throws IllegalStateException
	{
    	if (! isEnded())
    		ignoreTripEndStop = false;

    	if (bypassCache || (allStops == null) || ignoreTripEndStop)
    	{
	    	if (dbConn == null)
	    		throw new IllegalStateException("dbConn null");
	    	Vector<TStop> ts = TStop.stopsForTrip(dbConn, this);
	    	if (! ignoreTripEndStop)
	    	{
			if (! bypassCache)
			{
				allStops = ts;    // cache it
				hasCheckedStops = true;
				hasUnreadStops = false;
			}
	    	} else {
	    		// Remove that ending stop
	    		if ((ts != null) && ! ts.isEmpty())
	    		{
	    			TStop fin = ts.lastElement();
	    			if ((odo_end != 0) && (odo_end == fin.getOdo_total()))
	    			{
	    				ts.removeElementAt(ts.size() - 1);
	    				if (ts.isEmpty())
	    					ts = null;
	    			}
	    		}
	    	}
	    	readStartTStop(false);  // sets the field
	    	return ts;
    	} else {
    		return allStops;
    	}
    }

    /**
     * If this trip's starting stop is from the previous trip,
     * get its ID. Note package access, not public; intended for tstop.startingStopWithinTrip.
     * @return the <tt>tstop_start</tt> id, or 0
     * @see #readStartTStop(boolean)
     * @see #isStartTStopFromPrevTrip()
     */
    int getStartTStopID() { return tstopid_start; }

    /**
     * Does this trip continue from the previous trip's
     * location, and from that trip's ending {@link TStop}?
     * @return true if continues from previous trip, false if the
     *         trip starts from a different location
     * @see #readStartTStop(boolean)
     */
    public boolean isStartTStopFromPrevTrip() { return (tstopid_start != 0); }

    /**
     * Retrieve this Trip's starting stop which is from the previous trip, if any.
     * This gives the starting location (descr and locid) for this trip.
     *<P>
     * Cached after the first read.  The cache ignores whether <tt>orFirstTStop</tt>
     * was true during the first read.
     *<P>
     * Note that if a TStop with _id = tstopid_start isn't found in the database,
     * the field is cleared and {@link #isDirty()} is set.
     *
     * @param  orFirstTStop  if true, and if <tt>tstopid_start</tt> is empty,
     *     then the trip's starting stop will be selected from the TStop
     *     table (<tt>tripid</tt> matches, <tt>trip_odo</tt> == 0).
     * @return  that stop, or null if this trip didn't begin where the
     *     vehicle's previous trip ended
     * @throws IllegalStateException if the db connection is closed
     * @see #readAllTStops()
     * @see #readLatestTStop()
     * @see #isStartTStopFromPrevTrip()
     */
    public TStop readStartTStop(final boolean orFirstTStop)
        throws IllegalStateException
    {
    	if (tstop_start != null)
    	{
    		// cached
    		if (orFirstTStop)
    			return tstop_start;  // might be from this or previous trip
    		else
    			return (tstopid_start != 0) ? tstop_start : null;
    	}

    	if (tstopid_start != 0)
    	{
	    	if (dbConn == null)
	    		throw new IllegalStateException("dbConn null");
	    	try {
				tstop_start = new TStop(dbConn, tstopid_start);
			} catch (RDBKeyNotFoundException e) {
				tstopid_start = 0;  // bad key or inconsistency
				dirty = true;
			}
			if (tstop_start != null)
				return tstop_start;
    	}
 
    	// Assert: tstopid_start == 0.
    	if (! orFirstTStop)
    		return null;

    	// orFirstTStop is true.
    	// Look it up by matching trip ID, trip_odo==0.
    	tstop_start = TStop.readStartingStopWithinTrip(dbConn, this);
    	return tstop_start;
    }

    /**
     * Retrieve this Trip's latest stop (or its ending TStop), if any.
     *<P>
     * If the trip has no intermediate stops yet:
     *<UL>
     *<LI> If the trip started at the previous trip's final TStop, null is returned.
     *<LI> Otherwise, this trip's starting point will be returned.
     *</UL>
     * If the trip {@link #isEnded() is completed}, it will have an ending TStop,
     * and that will be the one returned.
     *
     * @return that stop, or null if none yet on this trip
     * @throws IllegalStateException if the db connection is closed
     * @see #readAllTStops()
     * @see #readStartTStop(boolean)
     * @see TStop#latestStopForTrip(RDBAdapter, int, boolean)
     */
    public TStop readLatestTStop()
        throws IllegalStateException
    {
    	return TStop.latestStopForTrip(dbConn, id, false);
    }

    /** Array to fill and return from readHighestOdometer. */
    private int[] readhighest_ret = null;

    /**
     * For a trip in progress, look at the trip's most recent stops, to calculate the
     * most current odometer values.
     * Where possible, use the highest trip-odo (more accurate) to drive the total-odo.
     * If the trip has no stops yet, "total" is the starting value and "trip" is 0.
     * To read the total odometer without calculation, use {@link #readHighestOdoTotal()} instead.
     *<P>
     * Uses and re-uses an array which is private to the trip; not thread-safe when sharing the same
     * Trip object, but safe when different threads use different Trips.
     *
     * @return array of [total,trip] odometer; the array is reused,
     *     so copy out the values before calling this again.
     * @see #readHighestOdometers(TStop)
     */
    public int[] readHighestOdometers()
    {
    	return readHighestOdometers(null);
    }

    /**
     * For a trip in progress, look at the trip's most recent stops, to calculate the
     * most current odometer values.
     * Where possible, use the highest trip-odo (more accurate) to drive the total-odo.
     * If the trip has no stops yet, "total" is the starting value and "trip" is 0.
     * To read the total odometer without calculation, use {@link #readHighestOdoTotal()} instead.
     *<P>
     * Uses and re-uses an array which is private to the trip; not thread-safe when sharing the same
     * Trip object, but safe when different threads use different Trips.
     *
     * @param ignoreStop  a TStop to ignore if found, or null;
     *     this allows the latest stop (for example) to be ignored during the calculation.
     * @return array of [total,trip] odometer; the array is reused,
     *     so copy out the values before calling this again.
     * @see #readHighestOdometers()
     */
    public int[] readHighestOdometers(final TStop ignoreStop)
    {
    	int oTrip = 0, oTotal;

    	Vector<TStop> stops = readAllTStops();
    	if (stops != null)
    	{
    		oTotal = 0;
    		int i = stops.size();  // at least 1, because stops != null
    		TStop ts;
    		do
    		{
    			--i;
    			ts = stops.elementAt(i);
    			if ((ignoreStop != null) && (ignoreStop.id == ts.id))
    				continue;
	    		oTotal = ts.getOdo_total();
	    		oTrip = ts.getOdo_trip();
    		} while ((oTotal == 0) && (oTrip == 0) && (i > 0));
    		if (oTotal == 0)
    			oTotal = odo_start + oTrip;  // if oTrip==0 too, that's ok
    		else if (oTrip == 0)
    			oTrip = oTotal - odo_start;
    		// else, they're both nonzero already.
    	} else {
    		oTotal = odo_start;
    	}

    	if (readhighest_ret == null)
    		readhighest_ret = new int[2];
    	readhighest_ret[0] = oTotal;
    	readhighest_ret[1] = oTrip;
    	return readhighest_ret;
    }

    /**
     * Retrieve this Trip's maximum recorded odo_total within a stop.
     * If no TStops on this trip yet with a recorded odo_total, returns odo_start.
     * Unlike {@link #readHighestOdometers()}, no odo_trip addition is done if latest tstops are missing odo_total.
     * @return that total-odometer value, in tenths of a unit
     * @throws IllegalStateException if the db connection is closed
     * @see TStop#readHighestTStopOdoTotalWithinTrip(RDBAdapter, int)
     * @since 0.9.07
     */
    public int readHighestOdoTotal()
    	throws IllegalStateException
	{
    	final int totalFromOdo = TStop.readHighestTStopOdoTotalWithinTrip(dbConn, id);
    	if (totalFromOdo != 0)
    		return totalFromOdo;
    	else
    		return odo_start;
	}

    /**
     * Read the latest timestamp of this trip.
     * Look in this order of availability:
     *<UL>
     *<LI> Trip's ending time
     *<LI> Trip's latest stop having a continue time
     *     or (if missing continue time) a stop time
     *<LI> Trip's starting time
     *</UL>
     * @return that time
     * @throws IllegalStateException if the db connection is closed
     */
    public int readLatestTime()
        throws IllegalStateException
    {
    	if (time_end != 0)
    		return time_end;  // trip's ending time

    	final Vector<TStop> stops = readAllTStops(false, true);  // ignore allStops cache & don't cache this query
    	if (stops == null)
    		return time_start;  // no stops yet: trip's starting time

    	for (int i = stops.size() - 1; i >= 0; --i)
    	{
    		final TStop ts = stops.elementAt(i);
    		int t = ts.getTime_continue();
    		if (t == 0)
    			t = ts.getTime_stop();

    		if (t != 0)
    			return t;
    	}

    	// no stops with times yet: trip's starting time
    	return time_start;
    }

    /**
     * Insert a new record based on field and value.
	 * Clears dirty field; sets id and dbConn fields.
     * @return new record's primary key (_id)
     * @throws IllegalStateException if the insert fails
     */
    public int insert(RDBAdapter db)
        throws IllegalStateException
    {
    	id = db.insert(TABNAME, FIELDS, buildInsertUpdate(), true);
		dirty = false;
    	dbConn = db;
    	return id;
    }

    /**
	 * Commit changes to an existing record.
	 * Commits to the database; clears dirty field.
	 *<P>
	 * For new records, <b>do not call commit</b>:
	 * use {@link #insert(RDBAdapter)} instead.
     * @throws IllegalStateException if the update fails
     * @throws NullPointerException if dbConn was null because
     *     this is a new record, not an existing one
	 */
	public void commit()
        throws IllegalStateException, NullPointerException
	{
		dbConn.update(TABNAME, id, FIELDS, buildInsertUpdate());
		dirty = false;
	}

	/**
	 * Fill the db fields into an array with same
	 * contents/order as {@link #FIELDS}.
	 * @return field contents, ready for db update via insert() or commit() 
	 */
	private String[] buildInsertUpdate()
	{
		/*
    private static final String[] FIELDS =
        { "vid", "did", "catid", "odo_start", "odo_end", "aid", "tstopid_start",
          FIELD_TIME_START, "time_end", "start_lat", "start_lon", "end_lat", "end_lon",
          "freqtripid", "comment", "passengers", "roadtrip_end_aid", "has_continue" };
		 */
		String[] fv =
		    {
			Integer.toString(vehicleid), Integer.toString(driverid),
			(catid != 0 ? Integer.toString(catid) : null),
			Integer.toString(odo_start), (odo_end != 0 ? Integer.toString(odo_end) : null),
			(a_id != 0 ? Integer.toString(a_id) : null),
			(tstopid_start != 0 ? Integer.toString(tstopid_start) : null),
			Integer.toString(time_start), (time_end != 0 ? Integer.toString(time_end) : null),
			start_lat, start_lon, end_lat, end_lon,
			(freqtripid != 0 ? Integer.toString(freqtripid) : null),
			comment,
			(passengers != -1 ? Integer.toString(passengers) : null),
			(roadtrip_end_aid != 0 ? Integer.toString(roadtrip_end_aid) : null),
			(has_continue ? "1" : "0")
		    };
		return fv;
	}

	public int getDriverID() {
		return driverid;
	}

	public void setDriverID(Person driver)
	    throws IllegalArgumentException
	{
    	if (! driver.isDriver())
    		throw new IllegalArgumentException("person.isDriver false: " + driver.getName());
		driverid = driver.getID();
		dirty = true;
	}

	public int getVehicleID() {
		return vehicleid;
	}

	/**
	 * Get this trip's optional {@link TripCategory} id.
	 * @return the trip category ID, or 0 for none
	 */
	public int getTripCategoryID() {
		return catid;
	}

	/**
	 * Set or clear this trip's optional {@link TripCategory} id.
	 * @param newCatID  trip category ID, or 0 for none
	 */
	public void setTripCategoryID(final int newCatID)
	{
		if (catid == newCatID)
			return;
		catid = newCatID;
		dirty = true;
	}

	/**
	 * Get the starting total-odometer value (trip-odo is 0.0 here) of this trip.
	 * @see #getOdo_end()
	 */
	public int getOdo_start() {
		return odo_start;
	}

	/**
	 * Get the ending total-odometer value (not trip-odo) of this trip, or 0 if still in progress.
	 * @see #isEnded()
	 * @see #getOdo_start()
	 */
	public int getOdo_end() {
		return odo_end;
	}

	public void setOdo_end(int odoEnd)
	{
		odo_end = odoEnd;
		dirty = true;
	}

	/**
	 * Get the {@link GeoArea} ID, or for roadtrips the starting area ID.
	 * @see #isRoadtrip()
	 */
	public int getAreaID() {
		return a_id;
	}

	/**
	 * For roadtrips, get the ending {@link GeoArea} ID; for local trips, returns 0.
	 * If the Trip is in progress, this value was likely set by the first stop outside
	 * the starting area ({@link #getAreaID()}) and may change with further
	 * travel; see {@link #convertLocalToRoadtrip(TStop)}.
	 *<P>
	 * Before v0.9.50, a roadtrip's end area was known because the user had to choose it
	 * when starting a trip, instead of converting from local during the trip.
	 *
	 * @see #isRoadtrip()
	 */
	public int getRoadtripEndAreaID() {
		return roadtrip_end_aid;
	}

	/**
	 * Check roadtrip's ending {@link GeoArea} and its {@link TStop}s' other areas;
	 * optionally update its {@link #getRoadtripEndAreaID()}, or convert trip to local
	 * if all stops are in its starting area ({@link #getAreaID()}).
	 *<P>
	 * Does not commit the updated Trip record, only updates fields.
	 *<P>
	 * Does nothing if ! {@link #isRoadtrip()}, will not check a local trip's TStop GeoAreas
	 * because that field is unused in local trip TStops.
	 *<P>
	 * Used by {@link VehSettings#endCurrentTrip(RDBAdapter, Vehicle, int, int, int, TripCategory, int)}.
	 *
	 * @param updateEndArea  If true, update the trip's {@link #getRoadtripEndAreaID()} field if needed:
	 *   <UL>
	 *     <LI> If all TStops are local to the starting area, clear {@link #isRoadtrip()}
	 *     <LI> If the most recent TStop (highest {@code _id}) is in a GeoArea that isn't the end area,
	 *       update the trip's end area from that TStop's
	 *   </UL>
	 *   Also sets {@link #isDirty()} if the area field is changed.
	 *   <P>
	 *   After a roadtrip is converted to local, its TStops will all still have the starting GeoArea in
	 *   their geoarea field. Local trips' TStops do not use this field, and would have null there
	 *   if the trip started as local. For the converted trip, clearing the TStops' geoarea field is
	 *   not required for data consistency.
	 * @return  True if all tstops in the trip are within its starting area,
	 *   or if the ending TStop's area was different from {@link #getRoadtripEndAreaID()}
	 * @throws IllegalStateException if the roadtrip's final TStop is in geoarea 0 (none)
	 * @since 0.9.50
	 */
	boolean checkRoadtripTStops(final boolean updateEndArea)
		throws IllegalStateException
	{
		if (! isRoadtrip())
			return false;

		final int startAreaID = a_id;
		final Vector<TStop> allTS = readAllTStops();
		if (allTS == null)
			return false;  // unlikely: all trips end at a TStop
		boolean wentOutsideStartArea = false;
		for (TStop ts : allTS)
		{
			if (ts.getAreaID() != startAreaID)
			{
				wentOutsideStartArea = true;
				break;
			}
		}

		final TStop endTS = allTS.lastElement();  // never null: readAllTStops() won't return an empty list
		final int endAreaID = endTS.getAreaID();
		if (endAreaID == 0)
			throw new IllegalArgumentException
				("Ending tstop is in area 0 (none): ts id " + endTS.getID());

		boolean differentAID = false;
		if (! wentOutsideStartArea)
		{
			differentAID = true;
			if (updateEndArea)
			{
				// convert roadtrip to local trip
				roadtrip_end_aid = 0;
				dirty = true;
			}
		}
		else if (endAreaID != roadtrip_end_aid)
		{
			differentAID = true;
			if (updateEndArea)
			{
				roadtrip_end_aid = endAreaID;
				dirty = true;
			}
		}

		return differentAID;
	}

	/** Trip's optional starting time (unix format) if set, or 0 */
	public int getTime_start() {
		return time_start;
	}

	/**
	 * Trip's optional ending time (unix format) if set, or 0; 0 if still in progress.
	 * @see #readLatestTime()
	 */
	public int getTime_end()
	{
		return time_end;
	}

	/**
	 * Set or clear trip's optional ending time.
	 * @param timeEnd  ending time (unix format) if set, or 0; 0 if still in progress
	 */
	public void setTime_end(int timeEnd)
	{
		time_end = timeEnd;
		dirty = true;
	}

	public boolean hasContinue()
	{
		return has_continue;
	}

	public void setVehicleID(Vehicle v)
	{
		vehicleid = v.getID();
		dirty = true;
	}

	/**
	 * Check settings to be sure this trip hasn't ended and is the current trip for the current vehicle.
	 * Also checks that the Trip is already a record in the database (not a newly created Trip).
	 * @param doThrow  If true, throw {@link IllegalStateException} with text giving the reason it's not current.
	 * @return  True if current trip for the current vehicle, false (or throws an exception) otherwise.
	 * @throws IllegalStateException if the trip isn't the current trip ID for the current vehicle,
	 *     or has an ending odometer (and thus has ended and isn't current),
	 *     or if there is no {@link Settings#getCurrentVehicle(RDBAdapter, boolean)} record,
	 *     or if {@link #dbConn} is {@code null} because {@link #insert(RDBAdapter)} hasn't been called
	 *     for this new Trip.
	 * @see #isEnded()
	 * @since 0.9.50
	 */
	private boolean isCurrentTrip(final boolean doThrow)
		throws IllegalStateException
	{
		if (dbConn == null)
			if (doThrow)
				throw new IllegalStateException("Trip is new, null dbConn");
			else
				return false;

		if (odo_end != 0)
			if (doThrow)
				throw new IllegalStateException("Trip has ended: odo_end != 0");
			else
				return false;

		final Vehicle currV = Settings.getCurrentVehicle(dbConn, false);
		if (currV == null)
			if (doThrow)
				throw new IllegalStateException("Not current trip: No current vehicle");
			else
				return false;

		Trip currT = VehSettings.getCurrentTrip(dbConn, currV, false);
		if (this.id != currT.id)
			if (doThrow)
				throw new IllegalStateException("Not current trip of vehicle " + currV.getID());
			else
				return false;

		return true;
	}

	/**
	 * Is this trip ended?  Completed trips have a nonzero
	 * total-odometer value in {@link #getOdo_end()}.
	 * @return true if the trip is completed
	 * @see #readLatestTStop()
	 */
	public final boolean isEnded()
	{
		return (odo_end != 0);
	}

	/**
	 * Is this trip a road trip, between 2 {@link GeoArea}s?
	 * @return true if {@link #getRoadtripEndAreaID()} != 0
	 */
	public boolean isRoadtrip()
	{
		return (roadtrip_end_aid != 0);
	}

	/**
	 * Is this trip based on a {@link FreqTrip}?
	 * @see #getFreqTripID()
	 */
	public boolean isFrequent()
	{
		return (freqtripid != 0);
	}

	/**
	 * Get the {@link FreqTrip} ID, if any.
	 * @return the freqtrip ID, or 0 if not a frequent trip
	 * @see #isFrequent()
	 */
	public int getFreqTripID()
	{
		return freqtripid;
	}

	/**
	 * Set the {@link FreqTrip} ID, if any.
	 * @param newID freqtrip ID, or 0 if not a frequent trip
	 */
	public void setFreqTripID(final int newID)
	{
		if (newID == freqtripid)
			return;
		freqtripid = newID;
		dirty = true;
	}

	/**
	 * Get the trip comment, if any, or null.  Typically TStop comments are used instead;
	 * use {@link Trip#readLatestTStop()} to get the tstop (and possible comment) nearest
	 * the end of the trip.
	 * @see #readLatestComment()
	 */
	public String getComment() {
		return comment;
	}

	/**
	 * Set or clear the trip comment.  Typically TStop comments are used instead;
	 * use {@link Trip#readLatestTStop()} to get the tstop (and possible comment) nearest
	 * the end of the trip.
	 * @param comment  Comment string, or null; please use null for a 0-length (empty) string.
	 */
	public void setComment(String comment) {
		this.comment = comment;
		dirty = true;
	}

	/**
	 * Get the trip's comment or latest non-blank tstop comment.
	 * @return  The trip comment, if not blank, or the comment from the most recent TStop with one, or null
	 * @throws IllegalStateException if need to read TStops but the db connection is closed
	 * @see #getComment()
	 * @since 0.9.20
	 */
	public String readLatestComment()
		throws IllegalStateException
	{
		if ((comment != null) && (comment.length() > 0))  // almost always null
			return comment;

		// Go through all stops to find a comment
		final Vector<TStop> ts = readAllTStops();
		for (int i = ts.size() - 1; i >= 0; --i)
		{
			final String tsComm = ts.elementAt(i).getComment();
			if ((tsComm != null) && (tsComm.length() > 0))
				return tsComm;
		}

		return null;
	}

	/**
	 * Get this trip's optional passenger count (not including driver).
	 * @return the passenger count, or -1 if unused;
	 *   will be 0 if count is known and driver is the only occupant
	 * @since 0.9.20
	 */
	public int getPassengerCount() {
		return passengers;
	}

	/**
	 * Set or clear this trip's optional passenger count.
	 * @param newPax  Passenger count (not including driver), or -1 to clear;
	 *          will be 0 if count is known and driver is the only occupant
	 * @since 0.9.20
	 */
	public void setPassengerCount(final int newPax)
	{
		if (passengers == newPax)
			return;
		passengers = newPax;
		dirty = true;
	}

	/**
	 * Delete an existing record.
	 *
     * @throws NullPointerException if dbConn was null because
     *     this is a new record, not an existing one
	 */
	public void delete()
	    throws NullPointerException
	{
		dbConn.delete(TABNAME, id);
		deleteCleanup();
	}

	/**
	 * Delete this trip, if the current trip, from the database.
	 * Requiring the current trip simplifies the assumptions and
	 * maintains database history and integrity.
	 *<P>
	 * To be cancelled, the trip must not have any {@link TStop}s yet.
	 * Use {@link #hasIntermediateTStops()} to check before calling this method.
	 *<P>
	 * Checks {@link VehSettings#getCurrentTrip(RDBAdapter, Vehicle, boolean)}, but
	 * does not clear {@link VehSettings#CURRENT_TRIP} or other settings:
	 * <B>Caller must do so</B> after calling this method.
	 *
	 * @throws IllegalStateException if the trip has intermediate stops,
	 *   other than its start, or isn't the current trip ID,
	 *   or if the trip has an ending odometer
	 *   (and thus the trip has ended and isn't current).
	 */
	public void cancelAndDeleteCurrentTrip()
		throws IllegalStateException
	{
		// Ensure current trip (odo_end == 0, Settings.getCurrentVehicle, VehSettings.getCurrentTrip).
		// Throws IllegalStateException if not.
		isCurrentTrip(true);

		// Any intermediate stops?
		// The same code is in hasIntermediateTStops().
		// Check both places when updating this code.
		Vector<TStop> ts = readAllTStops(false, true);  // don't use cached allStops field
		if (ts != null)
		{
			int nstop = ts.size();
			if ((tstopid_start == 0) && (nstop == 1))  // tstopid_start == 0 implies nstop > 0;
			                                           // check both to avoid crash if inconsistent.
				// if nstop > 1, has other stops so don't need to check whether first is intermediate.
			{
				TStop ts0 = ts.firstElement();
				if ((ts0.getOdo_trip() == 0) && (ts0.getOdo_total() == odo_start))
					--nstop;  // ignore the starting tstop
			}

			if (nstop > 0)
				throw new IllegalStateException("Has intermediate stops");

			// Delete starting TStop with our trip ID, if any
			for (int i = ts.size() - 1; i >= 0; --i)
				ts.elementAt(i).delete();
		}

		// Finally, delete the trip
		this.delete();
	}

	/**
	 * In this current trip, cancel continuing travel from the most recent {@link TStop},
	 * as if the vehicle is still stopped there and the "Continue" button was never pressed.
	 *<P>
	 * To cancel, this trip must have these conditions:
	 *<UL>
	 * <LI> {@link #isCurrentTrip(boolean)} for current vehicle
	 * <LI> No current TStop
	 * <LI> {@link #hasIntermediateTStops()}, because the most recent will become the current TStop again
	 *</UL>
	 *<P>
	 * Note that since we've already resumed travel from that stop, we've cleared any flags noting
	 * new Locations, ViaRoutes, etc created at that stop ({@link TStop#TEMPFLAG_CREATED_LOCATION}, ...);
	 * if they are renamed again while stopped there, new records will be created for them.
	 *
	 * @throws IllegalStateException if trip is missing any of the conditions listed above
	 *     or if {@code dbConn} is null or not open
	 * @since 0.9.50
	 */
	public void cancelContinueFromTStop()
		throws IllegalStateException
	{
		// Ensure is current trip (odo_end == 0, Settings.getCurrentVehicle, VehSettings.getCurrentTrip).
		// Throws IllegalStateException if not.
		isCurrentTrip(true);

		final Vehicle currV = Settings.getCurrentVehicle(dbConn, false);
			// not null, because isCurrentTrip didn't throw anything

		if (VehSettings.getCurrentTStop(dbConn, currV, false) != null)
			throw new IllegalStateException("CURRENT_TSTOP != null");

		Vector<TStop> ts = readAllTStops(true, true);  // don't use cached allStops field
		final TStop latestTS = (ts != null) ? ts.lastElement() : null;
		if ((latestTS == null)
		    || ((tstopid_start == 0) && (ts.size() == 1)
		        && (latestTS.getOdo_trip() == 0) && (latestTS.getOdo_total() == odo_start)))
			throw new IllegalStateException("Has no intermediate TStops");

		// now we have latestTS; clear its continued-at time and commit to db, make it current again
		latestTS.setTime_continue(0, true);
		VehSettings.setCurrentTStop(dbConn, currV, latestTS);

		// invalidate cached list of stops;
		// that's easier than checking list's consistency rules in these conditions.
		allStops = null;

		// update PREV_LOCATION
		int prevlocid = 0;
		final int L = ts.size();
		if (L >= 2)
		{
			prevlocid = ts.get(L - 2).getLocationID();
		} else if (tstopid_start != 0) {
			// get previous trip's ending loc, if any
			final TStop tstopStart = readStartTStop(true);
			if (tstopStart != null)
				prevlocid = tstopStart.getLocationID();
		}
		Location loc = null;
		if (prevlocid != 0)
			try
			{
				loc = new Location(dbConn, prevlocid);
			}
			catch (RDBKeyNotFoundException e) {}

		VehSettings.setPreviousLocation(dbConn, currV, loc);
	}

	/**
	 * Convert a local trip to a roadtrip, based on a newly added {@link TStop}
	 * outside the trip's {@link GeoArea}, and update the database.
	 *<P>
	 * Since it's a local trip, its previous {@link TStop}s (if any) are all in its
	 * starting area ({@link #getAreaID()}). A new {@code TStop} has just been added,
	 * which is in a different area or not in any area.
	 *<P>
	 * When calling this method, {@code newOtherAreaStop} must already be committed
	 * to the db, and this trip must have been inserted already and have a {@link #getID()},
	 * but the trip record's fields may have uncommitted changes. Those will be committed
	 * by this method along with the geoarea fields.
	 *<P>
	 * This method updates the trip by setting a proposed {@link #getRoadtripEndAreaID()}
	 * from {@code newOtherAreaStop}'s area if any, otherwise from {@link #getAreaID()} as a
	 * non-zero placeholder. It sets the geoarea field of the trip's earlier TStops to the starting area
	 * without changing {@code newOtherAreaStop}'s geoarea.  These updates are
	 * immediately committed to the db.
	 *<P>
	 * Later when the trip is actually ended, its final TStop's GeoArea will be
	 * used as the trip's ending area.
	 *<P>
	 * <B>Lifecycle:</B> An ongoing local trip can be converted to a roadtrip at any TStop.
	 * A roadtrip can be converted to local at the trip's completion, when we are sure
	 * there won't be any new TStops added in other areas, by
	 * {@link VehSettings#endCurrentTrip(RDBAdapter, Vehicle, int, int, int, TripCategory, int)}.
	 *
	 * @param newOtherAreaStop Newest stop of the trip, this stop is in a different geoarea.
	 *     Must already be committed to the db.
	 * @throws IllegalStateException if this trip is already a roadtrip
	 * @throws IllegalArgumentException  If {@code newOtherAreaStop} is not yet committed to DB,
	 *     or is in this trip's starting geoarea {@link #getAreaID()} which would not be a roadtrip.
	 * @throws NullPointerException if {@code newOtherAreaStop} is null
	 * @since 0.9.50
	 */
	public void convertLocalToRoadtrip(final TStop newOtherAreaStop)
		throws IllegalStateException, IllegalArgumentException, NullPointerException
	{
		if (isRoadtrip())
			throw new IllegalStateException("trip is already a roadtrip");
		if (newOtherAreaStop.getID() <= 0)
			throw new IllegalArgumentException("newStop.id not committed");

		int endAreaID = newOtherAreaStop.getAreaID();
		if (endAreaID == a_id)
			throw new IllegalArgumentException("newStop is in starting area id " + endAreaID);
		if (endAreaID == 0)
			endAreaID = a_id;

		roadtrip_end_aid = endAreaID;
		dirty = true;

		// update the TStops, then commit the changed trip data.
		// TODO add transaction support
		TStop.tripUpdateTStopsGeoArea(this, newOtherAreaStop, allStops);
		commit();
	}

	/**
	 * A new stop has been added to this trip; now that
	 * it's committed to the database, add it to our
	 * cached list of TStops, if we already have that list.
	 *
	 * @param newStop  New stop to add
	 * @throws IllegalArgumentException  New stop is not yet committed to DB
	 * @see #updateCachedCurrentTStop(TStop)
	 */
	public void addCommittedTStop(TStop newStop)
		throws IllegalArgumentException
	{
		if (newStop.getID() <= 0)
			throw new IllegalArgumentException("newStop.id not committed");

		if (! hasCheckedStops)
		{
			if (dbConn == null)
				return;  // unable to check
			final int allscount = (allStops != null) ? allStops.size() : 0;
			final int stopcount = dbConn.getCount(TStop.TABNAME, TStop.FIELD_TRIPID, id);
			hasUnreadStops = (stopcount != allscount);
			hasCheckedStops = true;
		}
		if (hasUnreadStops)
			return;  // not safe to add

		if (allStops == null)
			allStops = new Vector<TStop>();
		allStops.addElement(newStop);
	}

	/**
	 * Update cached data fields for the trip's current (most recent) TStop, to prevent stale cached info.
	 * The entire cached TStop object will be replaced by {@code currTS} within the cache,
	 * instead of copying all its fields' contents.
	 * Please call {@link TStop#commit() currTS.commit()} before this method.
	 * If the TStop isn't the most recent cached stop, does nothing.
	 * @param currTS  Updated current (most recent) TStop within this trip
	 * @throws IllegalArgumentException if {@link TStop#getTripID() currTS.getTripID()} != this trip's ID
	 * @throws NullPointerException if {@code currTS} is null
	 * @see #addCommittedTStop(TStop)
	 * @since 0.9.43
	 */
	public void updateCachedCurrentTStop(TStop currTS)
		throws IllegalArgumentException, NullPointerException
	{
		final int currTSTrip = currTS.getTripID();
		if (currTSTrip != id)
			throw new IllegalArgumentException
				("wrong trip id: this is " + id + ", tstop trip is " + currTSTrip);
		if (allStops == null)
			return;

		synchronized (allStops)
		{
			final int L = allStops.size();
			if (L == 0)
				return;
			TStop currCached = allStops.lastElement();
			if (currCached.getID() == currTS.getID())
			{
				allStops.remove(L - 1);
				allStops.add(currTS);
			}
		}
	}

	/**
	 * Get this {@link TStop}, only if currently cached from reading all TStops of the trip:
	 * Convenience method. Does not query the DB if stop isn't currently cached.
	 * @param tsID  ID of a {@code TStop} on this trip
	 * @return  The TStop if cached, or {@code null}
	 * @since 0.9.51
	 */
	public TStop getCachedTStop(final int tsID)
	{
		if (allStops == null)
			return null;

		synchronized (allStops)
		{
			final int L = allStops.size();
			for (int i = 0; i < L; ++i)
			{
				TStop ts = allStops.get(i);
				if (ts.id == tsID)
					return ts;
			}
		}

		return null;
	}

	/**
	 * Does this trip have TStops, other than its start and end?
	 * Queries the database, not a cached list of stops.
	 * @return true if any TStops exist in the middle of the trip
	 */
	public boolean hasIntermediateTStops()
	{
		// The same code is in cancelAndDeleteCurrentTrip().
		// Check both places when updating this code.

		Vector<TStop> ts = readAllTStops(true, true);  // don't use cached allStops field
		if (ts == null)
			return false;

		int nstop = ts.size();
		if ((tstopid_start == 0) && (nstop == 1))  // tstopid_start == 0 implies nstop > 0;
		                                           // check both to avoid crash if inconsistent.
			// if nstop > 1, has other stops so don't need to check whether first is intermediate.
		{
			TStop ts0 = ts.firstElement();
			if ((ts0.getOdo_trip() == 0) && (ts0.getOdo_total() == odo_start))
				--nstop;  // ignore the starting tstop
		}

		return (nstop > 0);
	}

	/**
	 * Trips within a range of time; used by
	 * {@link org.shadowlands.model.LogbookTableModel LogbookTableModel} and by
	 * {@link #tripsForVehicle(RDBAdapter, Vehicle, int, int, boolean, boolean, boolean)}.
	 *<P>
	 * Trips added to this Range are available as text through {@link #tText}
	 * or by calling {@link #getTripListRowsTabbed()}.
	 */
	public static class TripListTimeRange
	{
		/** Starting/ending date/time of trip range, in Unix format */
		public final int timeStart, timeEnd;

		/** For {@link LogbookTableModel}'s Location Mode, the matching Location ID;
		 *  otherwise -1.
		 *  @see #tMatchedRows
		 *  @since 0.9.50
		 */
		public final int matchLocID;

		/**
		 * Trips found within this range of time.
		 * Public for read-only access from {@link LogbookTableModel}; please always treat as read-only.
		 * If trip data is rendered to strings, those are held in {@link #tText} and {@link #trBeginTextIdx}.
		 */
		public final List<Trip> tr;

		/**
		 * Holds beginning index (row) within {@link #tText} of each trip in {@link #tr}, or {@code null}.
		 * Public for access and update from {@link LogbookTableModel}; please treat as read-only otherwise.
		 * @see #tstopTextIdx
		 * @since 0.9.51
		 */
		public int[] trBeginTextIdx;

		/**
		 * Holds index (row) within {@link #tText} of each {@link TStop} ID in {@link #tr}, or {@code null}.
		 * Does not contain trips' starting {@code TStop}s, only intermediate and final stops,
		 * because starting TStops' comments aren't rendered in {@code tr}.
		 * Public for access and update from {@link LogbookTableModel}; please treat as read-only otherwise.
		 * @see #trBeginTextIdx
		 * @since 0.9.51
		 */
		public HashMap<Integer, Integer> tstopTextIdx;

		/**
		 * Holds each rendered data row, not including the 1 empty-string row at the end.
		 *<P>
		 * A 0-length tText is not allowed; use null instead.
		 * Each row's length is <tt>{@link org.shadowlands.roadtrip.model.LogbookTableModel#COL_HEADINGS LogbookTableModel.COL_HEADINGS}.length</tt>.
		 *<P>
		 * Initially null.  Filled by
		 * {@link org.shadowlands.roadtrip.model.LogbookTableModel LogbookTableModel}
		 * constructor or
		 * {@link org.shadowlands.roadtrip.model.LogbookTableModel#addEarlierTrips(RDBAdapter) LogbookTableModel.addEarlierTrips(RDBAdapter)}.
		 * @see #tMatchedRows
		 * @see #trBeginTextIdx
		 */
		public Vector<String[]> tText;

		/**
		 * For {@link LogbookTableModel}'s Location Mode, the optional set of row numbers of {@link TStop}s
		 * matching the Location {@link #matchLocID} within {@link #tText}; {@code null} in other modes
		 * or when no matches found.
		 *<P>
		 * Used by {@link #getTripListRowsTabbed()} to highlight matching TStops.
		 * @since 0.9.50
		 */
		public Set<Integer> tMatchedRows;

		/** Are there no trips beyond this range? False if unknown. */
		public boolean noneEarlier, noneLater;

		/**
		 * Constructor from a list of trips.
		 * @param time_start First trip's start time, from {@link Trip#getTime_start()}
		 * @param time_end  Last trip's <b>start</b> time (not end time)
		 * @param trips  List of trips
		 */
		public TripListTimeRange(int time_start, int time_end, List<Trip> t)
		{
			this(time_start, time_end, t, -1);
		}

		/**
		 * Constructor from a list of trips, with optional search location ID.
		 * @param trips  List of trips
		 * @param matchLocID  Optional Location ID (for results of searching by location), or -1;
		 *     if provided, this range will track which {@link TStop}s use this Location ID.
		 * @since 0.9.50
		 */
		public TripListTimeRange(List<Trip> trips, final int matchLocID)
		{
			this(trips.get(0).getTime_start(), trips.get(trips.size() - 1).getTime_start(),
			     trips, matchLocID);
		}

		private TripListTimeRange(int time_start, int time_end, List<Trip> t, final int matchLocID)
		{
			timeStart = time_start;
			timeEnd = time_end;
			tr = t;
			tText = null;
			this.matchLocID = matchLocID;
		}

		/**
		 * For StringBuilder output, create and return tab-delimited (\t)
		 * contents of each trip's text row(s) from {@link #tText}.
		 *<P>
		 * In Location Mode, as a temporary measure until more subtle match highlighting can be done,
		 * {@link TStop}s at a Location matched in {@link #tMatchedRows} are shown in ALL CAPS.
		 *<P>
		 * Before v0.9.51, this method was {@code appendRowsAsTabbedString(StringBuffer)}.
		 *
		 * @return List of StringBuilders, 1 per trip in {@link #tr}, or {@code null} if none in {@link #tText}.
		 */
		public List<StringBuilder> getTripListRowsTabbed()
		{
			if ((tText == null) || tText.isEmpty())
				return null;

			final int L = tr.size();
			List<StringBuilder> tlist = new ArrayList<StringBuilder>(L);

			for (int i = 0; i < L; ++i)
				tlist.add(getTripRowsTabbed_idx(i));

			return tlist;
		}

		/**
		 * Create and return tab-delimited (\t) contents of one trip's text row(s)
		 * from {@link #tText} and {@link #tr}.
		 * @param tripID  {@link Trip} ID in the database of a Trip in this TripListTimeRange
		 * @return The Trip's text rows as a StringBuilder,
		 *     or {@code null} if trip not in {@link #trBeginTextIdx}.
		 * @since 0.9.51
		 */
		public StringBuilder getTripRowsTabbed(final int tripID)
		{
			if ((tText == null) || (trBeginTextIdx == null))
				return null;

			for (int i = 0; i < trBeginTextIdx.length; ++i)
				if (tr.get(i).id == tripID)
					return getTripRowsTabbed_idx(i);

			return null;
		}

		/**
		 * Get a Trip's rows by trip index within this TripListTimeRange.
		 * @param i  Index into {@link #trBeginTextIdx} and {@link #tr}
		 * @return  StringBuilder for the Trip
		 * @since 0.9.51
		 */
		private StringBuilder getTripRowsTabbed_idx(final int i)
		{
			final boolean chkMatches = (matchLocID != -1)
				&& (tMatchedRows != null) && ! tMatchedRows.isEmpty();

			StringBuilder sb = new StringBuilder();
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
						if (chkMatches && (c == LogbookTableModel.COL_TSTOP_DESC)
						    && tMatchedRows.contains(Integer.valueOf(r)))
							str = str.toUpperCase();

						sb.append(str);
					}
				}

				if (r < (rNextTr - 1))
					sb.append('\n');
			}

			return sb;
		}

	}  // public static nested class TripListTimeRange

}  // public class Trip
