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

package org.shadowlands.roadtrip.db;

import java.util.Vector;

/**
 * In-memory representation, and database access for, a Trip.
 * See schema file for details.
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
 * Ending location: Taken from the {@link #isEnded() completed trip}'s highest tstop id.
 * (See {@link #readLatestTStop()})
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
        { "vid", "did", "odo_start", "odo_end", "aid", "tstopid_start",
		  FIELD_TIME_START, "time_end", "start_lat", "start_lon", "end_lat", "end_lon",
		  "freqtripid", "comment", "roadtrip_end_aid", "has_continue" };

    private static final String[] FIELDS_AND_ID =
	    { "vid", "did", "odo_start", "odo_end", "aid", "tstopid_start",
		  FIELD_TIME_START, "time_end", "start_lat", "start_lon", "end_lat", "end_lon",
		  "freqtripid", "comment", "roadtrip_end_aid", "has_continue", "_id" };

    /** Field names/where-clause for use in {@link #recentTripForVehicle(RDBAdapter, Vehicle, boolean)} */
    private static final String WHERE_VID_AND_NOT_ROADTRIP =
    	"vid = ? and roadtrip_end_aid is null";

    /** Field names/where-clause for use in {@link #recentTripForVehicle(RDBAdapter, Vehicle, boolean)} */
    private static final String WHERE_VID_AND_IS_ROADTRIP =
    	"vid = ? and roadtrip_end_aid is not null";

    /** Where-clause for use in {@link #tripsForVehicle(RDBAdapter, Vehicle, int, int, boolean, boolean, boolean)}  */
    private static final String WHERE_TIME_START_AND_VID =
    	"(time_start >= ?) and (time_start <= ?) and vid = ?";

    /** Where-clause for use in {@link #tripsForVehicle_searchBeyond(RDBAdapter, String, int, int, int, boolean)  */
    private static final String WHERE_TIME_START_AFTER_AND_VID =
    	"(time_start > ?) and vid = ?";

    /** Where-clause for use in {@link #tripsForVehicle_searchBeyond(RDBAdapter, String, int, int, int, boolean)  */
    private static final String WHERE_TIME_START_BEFORE_AND_VID =
    	"(time_start < ?) and vid = ?";

    private static final int WEEK_IN_SECONDS = 7 * 24 * 60 * 60;

    private int vehicleid;
    private int driverid;
    /** Starting odometer, in tenths of a unit */
    private int odo_start;
    /** Ending odometer, in tenths of a unit, or 0 if trip is still in progress */
    private int odo_end;

    /** Area ID.  0 is empty/unused. */
    private int a_id;

    /** Previous trip's last TStop; this gives the starting location (descr and locid) for this trip. */
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
    /** ending area ID if roadtrip, 0 otherwise */
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
     * @param veh  vehicle to look for
     * @param alsoTStops  If true, call {@link #readAllTStops()} for each trip found
     * @return Trips for this Vehicle, sorted by time_start, or null if none
     * @throws IllegalStateException if db not open
     * @see #tripsForVehicle(RDBAdapter, Vehicle, int, int, boolean, boolean, boolean)
     */
    public static Vector<Trip> tripsForVehicle(RDBAdapter db, Vehicle veh, final boolean alsoTStops)
        throws IllegalStateException
    {
    	if (db == null)
    		throw new IllegalStateException("db null");
    	Vector<String[]> sv = db.getRows
    	    (TABNAME, "vid", Integer.toString(veh.getID()), FIELDS_AND_ID, "time_start", 0);
    	if (sv == null)
    		return null;

		return tripsForVehicle_parse(db, alsoTStops, sv);
    }

    /**
     * Retrieve all Trips within a date range for a Vehicle.
     * @param db  db connection
     * @param veh  vehicle to look for
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
    	int t0, t1;
    	if (towardsNewer)
    	{
    		t0 = timeStart;
    		t1 = timeStart + (weeks * WEEK_IN_SECONDS);
    	} else {
    		t1 = timeStart;
    		t0 = timeStart - (weeks * WEEK_IN_SECONDS);
    	}

    	Vector<String[]> sv = null;
    	final String vIDstr = Integer.toString(veh.getID());

    	/**
    	 * Search in the range t0 to t1 for trips.
    	 * If searchBeyondWeeks, try 2 more times
    	 * by moving further into the past/future.
    	 */
    	for (int tries = 0; (sv == null) && (tries < 2); ++tries)
    	{
			final String[] whereArgs = {
				Integer.toString(t0), Integer.toString(t1), vIDstr
			};
			sv = db.getRows(TABNAME, WHERE_TIME_START_AND_VID, whereArgs, FIELDS_AND_ID, "time_start", 0);
			if (sv == null)
			{
				if (! searchBeyondWeeks)
					break;

				if (towardsNewer)
					t1 += (weeks * WEEK_IN_SECONDS);
				else
					t0 -= (weeks * WEEK_IN_SECONDS);
			}
		}

		if ((sv == null) && searchBeyondWeeks)
			sv = tripsForVehicle_searchBeyond
				(db, vIDstr, t0, t1, weeks, towardsNewer);

    	if (sv == null)
    	{
    		return null;
    	}

    	Vector<Trip> tv = tripsForVehicle_parse(db, alsoTStops, sv);
    	if (tv == null)
    		return null;
    	else
    		return new TripListTimeRange(t0, t1, tv);
    }

	/**
	 * Search for trips beyond this range.
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
		String tripIDstr;
		if (towardsNewer)
			tripIDstr = db.getRowField(TABNAME,
				"min(_id)",
				WHERE_TIME_START_AFTER_AND_VID,
				new String[]{ Integer.toString(tt1), vIDstr } );
		else
			tripIDstr = db.getRowField(TABNAME,
				"max(_id)",
				WHERE_TIME_START_BEFORE_AND_VID,
				new String[]{ Integer.toString(tt0), vIDstr } );
		if (tripIDstr == null)
		{
			return null;  // <--- nothing found ---
		}
		String timeStartStr = db.getRowField(TABNAME, "_id", tripIDstr, FIELD_TIME_START);
		if (timeStartStr == null)
			return null;  // shouldn't happen
		final int timeStart;
		try {
			timeStart = Integer.parseInt(timeStartStr);
		} catch (NumberFormatException e) {
			return null;  // shouldn't happen
		}

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
			(TABNAME, WHERE_TIME_START_AND_VID, whereArgs, FIELDS_AND_ID, FIELD_TIME_START, 0);

		return sv;
	}

    /** parse String[] to Trips */
	private static final Vector<Trip> tripsForVehicle_parse
		(RDBAdapter db, final boolean alsoTStops, Vector<String[]> sv)
	{
		Vector<Trip> vv = new Vector<Trip>(sv.size());
		try
		{
			Trip t;
	    	for (int i = 0; i < sv.size(); ++i)
	    	{
	    		t = new Trip(db, sv.elementAt(i));
	    		if (alsoTStops)
	    			t.readAllTStops();
	    		vv.addElement(t);
	    	}
		} catch (RDBKeyNotFoundException e) { }
		return vv;
	}

    /**
     * Get the most recent local trip or roadtrip for this vehicle, if any.
     * Does not call {@link #readAllTStops()} on the returned trip.
     * @param db  db connection
     * @param veh  vehicle to look for
     * @param wantsLocal  If true, local trip only; if false, roadtrip only.
     * @return Most recent Trip for this Vehicle, sorted by time_start descending, or null if none
     * @throws IllegalStateException if db not open
     * @since 0.9.03
     */
    public static Trip recentTripForVehicle(RDBAdapter db, Vehicle veh, final boolean wantsLocal)
    	throws IllegalStateException
    {
    	if (db == null)
    		throw new IllegalStateException("db null");
    	final String where = (wantsLocal) ? WHERE_VID_AND_NOT_ROADTRIP : WHERE_VID_AND_IS_ROADTRIP;
    	Vector<String[]> sv = db.getRows
    		(TABNAME, where, new String[]{ Integer.toString(veh.getID()) }, FIELDS_AND_ID, "time_start DESC", 1);
    		// LIMIT 1
    	if (sv == null)
    		return null;
    	try
    	{
    		return new Trip(db, sv.firstElement());
    	}
    	catch (RDBKeyNotFoundException e)
    	{
    		return null;
    	}
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
     * @param rec, as returned by db.getRows(FIELDS_AND_ID); last element is _id
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
     * @param rec, as returned by db.getRow(FIELDS) or db.getRows(FIELDS_AND_ID)
     */
	private void initFields(String[] rec)
	{
		vehicleid = Integer.parseInt(rec[0]);  // FK
    	driverid = Integer.parseInt(rec[1]);  // FK
    	odo_start = Integer.parseInt(rec[2]);
    	if (rec[3] != null)
    		odo_end = Integer.parseInt(rec[3]);
    	if (rec[4] != null)
    		a_id = Integer.parseInt(rec[4]);
    	if (rec[5] != null)
    		tstopid_start = Integer.parseInt(rec[5]);  // FK
    	if (rec[6] != null)
    		time_start = Integer.parseInt(rec[6]);
    	if (rec[7] != null)
    		time_end = Integer.parseInt(rec[7]);
    	start_lat = rec[8];
    	start_lon = rec[9];
    	end_lat = rec[10];
    	end_lon = rec[11];
    	if (rec[12] != null)
    		freqtripid = Integer.parseInt(rec[12]);
    	comment = rec[13];
    	if (rec[14] != null)
    		roadtrip_end_aid = Integer.parseInt(rec[14]);
    	has_continue = ("1".equals(rec[15]));
	}

    /**
     * Create a new trip, but don't yet write to the database.
     * When ready to write (after any changes you make to this object),
     * call {@link #insert(RDBAdapter)}.
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
     * @see #isStartTStopFromPrevTrip()
     */
    public Vector<TStop> readAllTStops()
        throws IllegalStateException
    {
    	return readAllTStops(false);
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
     * @return  ordered list of stops, or null if none
     * @throws IllegalStateException if the db connection is closed
     * @see #hasIntermediateTStops()
     */
    public Vector<TStop> readAllTStops(boolean ignoreTripEndStop)
	    throws IllegalStateException
	{
    	if (! isEnded())
    		ignoreTripEndStop = false;

    	if ((allStops == null) || ignoreTripEndStop)
    	{
	    	if (dbConn == null)
	    		throw new IllegalStateException("dbConn null");
	    	Vector<TStop> ts = TStop.stopsForTrip(dbConn, this);
	    	if (! ignoreTripEndStop)
	    	{
		    	allStops = ts;    // cache it
		    	hasCheckedStops = true;
		    	hasUnreadStops = false;
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
     * @see #isStartTStopFromPrevTrip()
     */
    public TStop readStartTStop(final boolean orFirstTStop)
        throws IllegalStateException
    {
    	if (tstop_start != null)
    	{
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
     * Read the latest timestamp of this trip.
     * Look in this order of availability:
     *<UL>
     *<LI> Trip's ending time
     *<LI> Trip's latest stop's continue time
     *<LI> Latest stop's stop time
     *<LI> Trip's starting time
     *</UL>
     * @return that time
     * @throws IllegalStateException if the db connection is closed
     */
    public int readLatestTime()
        throws IllegalStateException
    {
    	if (time_end != 0)
    		return time_end;  // ending time
    	TStop latest = null;
    	if ((allStops != null) && (allStops.size() > 0))
    	{
    		latest = allStops.lastElement();
    	} else {
    		latest = TStop.latestStopForTrip(dbConn, id, true);
    	}
		if ((latest == null) || (latest.getTime_stop() == 0))  // no stops yet: starting time
			return time_start;
    	int t = latest.getTime_continue();  // continue time
    	if (t == 0)
    		t = latest.getTime_stop();  // stop time
    	return t;
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
        { "vid", "did", "odo_start", "odo_end", "aid", "tstopid_start",
    	  "time_start", "time_end", "start_lat", "start_lon", "end_lat", "end_lon",
    	  "freqtripid", "comment", "roadtrip_end_aid", "has_continue" };
		 */
		String[] fv =
		    {
			Integer.toString(vehicleid), Integer.toString(driverid),
			Integer.toString(odo_start), (odo_end != 0 ? Integer.toString(odo_end) : null),
			(a_id != 0 ? Integer.toString(a_id) : null), (tstopid_start != 0 ? Integer.toString(tstopid_start) : null),
			Integer.toString(time_start), Integer.toString(time_end),
			start_lat, start_lon, end_lat, end_lon,
			(freqtripid != 0 ? Integer.toString(freqtripid) : null),
			comment,
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

	public int getOdo_start() {
		return odo_start;
	}

	/** get the total-odometer value (not trip-odo) at the end of this trip */
	public int getOdo_end() {
		return odo_end;
	}

	public void setOdo_end(int odoEnd)
	{
		odo_end = odoEnd;
		dirty = true;
	}

	/** Get the area ID, or for roadtrips, the starting area ID. */
	public int getAreaID() {
		return a_id;
	}

	/** For roadtrips, get the ending area ID; for local trips, returns 0. */
	public int getRoadtripEndAreaID() {
		return roadtrip_end_aid;
	}

	/** Trip's optional starting time (unix format) if set, or 0 */
	public int getTime_start() {
		return time_start;
	}

	/** Trip's optional ending time (unix format) if set, or 0; 0 if still in progress */
	public int getTime_end()
	{
		return time_end;
	}

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
	 * Is this trip ended?  Completed trips have an odo-end value.
	 * @return if the trip is completed 
	 */
	public final boolean isEnded()
	{
		return (odo_end != 0);
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

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
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
	 * Checks {@link Settings#getCurrentTrip(RDBAdapter, boolean)}, but
	 * does not clear {@link Settings#CURRENT_TRIP} or other settings.
	 *
	 * @throws IllegalStateException if the trip has intermediate stops,
	 *   other than its start, or isn't the current trip ID,
	 *   or if the trip has an ending odometer
	 *   (and thus the trip has ended and isn't current).
	 */
	public void cancelAndDeleteCurrentTrip()
		throws IllegalStateException
	{
		if (odo_end != 0)
			throw new IllegalStateException("Trip has ended");
		Trip currT = Settings.getCurrentTrip(dbConn, false);
		if (this.id != currT.id)
			throw new IllegalStateException("Not current trip");

		// Any intermediate stops? / (TODO) same code as hasIntermediateTStops
		Vector<TStop> ts = readAllTStops(true);  // TODO can we use allStops field instead?
		if (ts != null)
		{
			int nstop = ts.size();
			if (tstopid_start == 0)
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
	 * A new stop has been added to this trip; now that
	 * it's committed to the database, add it to our
	 * cached list of TStops, if we already have that list.
	 *
	 * @param newStop  New stop to add
	 * @throws IllegalArgumentException  New stop is not yet committed to DB
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
	 * Does this trip have TStops, other than its start and end?
	 * <b>Note:</b> Queries the database, not a cached list of stops.
	 * @return true if any TStops exist in the middle of the trip
	 */
	public boolean hasIntermediateTStops()
	{
		Vector<TStop> ts = readAllTStops(true);  // TODO can we use allStops instead?
		if (ts == null)
			return false;
		int nstop = ts.size();
		if (tstopid_start == 0)
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
	 */
	public static class TripListTimeRange
	{
		/** Starting/ending date/time of trip range, in Unix format */
		public final int timeStart, timeEnd;

		/** Trips found within this range of time */
		public Vector<Trip> tr;

		/**
		 * Holds each rendered data row, not including the 1 empty-string row at the end.
		 *<P>
		 * A 0-length tText is not allowed; use null instead.
		 * Each row's length is <tt>{@link org.shadowlands.roadtrip.model.LogbookTableModel#COL_HEADINGS LogbookTableModel.COL_HEADINGS}.length</tt>.
		 *<P>
		 * Initially null.  Filled by
		 * {@link org.shadowlands.roadtrip.model.LogbookTableModel LogbookTableModel}
		 * constructor or
		 * {@link org.shadowlands.roadtrip.model.LogbookTableModel#addEarlierTripWeeks(RDBAdapter) LogbookTableModel.addEarlierTripWeeks(RDBAdapter)}.
		 */
		public Vector<String[]> tText;

		/** Are there no trips beyond this range? False if unknown. */
		public boolean noneEarlier, noneLater;

		public TripListTimeRange(int time_start, int time_end, Vector<Trip> t)
		{
			timeStart = time_start;
			timeEnd = time_end;
			tr = t;
			tText = null;
		}

		/**
		 * For StringBuffer output, append \n and then tab-delimited (\t)
		 * contents of each text row to the stringbuffer.
		 */
		public void appendRowsAsTabbedString(StringBuffer sb)
		{
			if (tText == null)
				return;

			final int S = tText.size();
			for (int r = 0; r < S; ++r)
			{
				final String[] rstr = tText.elementAt(r);
				sb.append('\n');
				if (rstr[0] != null)
					sb.append(rstr[0]);
				for (int c = 1; c < rstr.length; ++c)
				{
					sb.append('\t');
					if (rstr[c] != null)
						sb.append(rstr[c]);
				}
			}
		}

	}  // public static nested class TripListTimeRange

}  // public class Trip
