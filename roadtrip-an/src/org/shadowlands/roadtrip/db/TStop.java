/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  Copyright (C) 2010 Jeremy D Monin <jdmonin@nand.net>
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
 * In-memory representation, and database access for, a Trip Stop.
 *<P>
 * To indicate this TStop has related side tables such as {@link TStopGas},
 * there is a <tt>flag_sides</tt> field where flag bits such as
 * {@link #FLAG_GAS} can be set.
 *
 * @author jdmonin
 */
public class TStop extends RDBRecord
{
	/** Table name for TStop in the database.  Access is package, not private, for Trip's use */
    static final String TABNAME = "tstop";

    /** Foreign-key field name for TStop.tripID in the database.  Access is package, not private, for Trip's use */
    static final String FIELD_TRIPID = "tripid";

    /** Field names/where-clause for use in {@link #readStartingStopWithinTrip(RDBAdapter, Trip)} */
    private static final String WHERE_TRIPID_AND_ODOTRIP = FIELD_TRIPID + " = ? AND odo_trip = 0";

    /** db table fields.
     * The "descr" field is now used for the "location" of the stop.
     * @see #buildInsertUpdate()
     * @see #initFields(String[])
     */
    private static final String[] FIELDS =
        { FIELD_TRIPID, "odo_total", "odo_trip", "time_stop", "time_continue",
    	  "locid", "a_id", "geo_lat", "geo_lon", "flag_sides",
    	  "descr", "via_route", "via_id", "comment" };
    private static final String[] FIELDS_AND_ID =
	    { FIELD_TRIPID, "odo_total", "odo_trip", "time_stop", "time_continue",
		  "locid", "a_id", "geo_lat", "geo_lon", "flag_sides",
		  "descr", "via_route", "via_id", "comment", "_id" };
    private final static String[] FIELD_TIME_CONTINUE_ARR =
    	{ "time_continue" };

    // All temporary flags have values 0x80 or lower,
    // and are cleared before continuing the trip from the stop
    // (or, if this stop ends the trip, before ending it).

    /** Temporary flag to indicate a new Location was created for this TStop */
    public static final int TEMPFLAG_CREATED_LOCATION = 0x01;

    /** Temporary flag to indicate a new ViaRoute was created for this TStop */
    public static final int TEMPFLAG_CREATED_VIAROUTE = 0x02;

    /** (unused) Flag to (eventually) indicate this TStop has a related {@link TStopGas} record */
    public static final int FLAG_GAS = 0x100;

    private int tripid;  // FK

    /** may be blank (0) */
    private int odo_total, odo_trip;

    /**
     * if true, this is the start of a trip, so trip-odometer 0
     * should be stored, instead of null.
     */
    private boolean odo_trip_0_beginTrip;

    /** see sql schema for date fmt.  0 is considered unused. */
    private int time_stop, time_continue;

    /** Location ID.  0 is empty/unused. */
    private int locid;

    /** GeoArea ID.  0 is empty/unused. */
    private int areaid;

    /** may be null */
    private String geo_lat, geo_lon;

    /**
     * Flags and temporary flags, such as {@link #FLAG_GAS} and {@link #TEMPFLAG_CREATED_VIAROUTE}.
     *<P>
     * All temporary flags have values 0x80 or lower,
     * and are cleared before continuing the trip from the stop
     * (or, if this stop ends the trip, before ending it).
     */
    private int flag_sides;

    /** location text ('<tt>descr</tt>' field); will be null if {@link #locid} is used instead. */
    private String locat;

    /** route/comment; may be null */
    private String via_route, comment;

    /** via route; 0 is empty/unused */
    private int via_id;

    /**
     * Cached contents of some fields as string.
     * null unless {@link #toString()} was called.
     * See toString javadoc for contents.
     */
    private transient String toString_descr;

    /**
     * Retrieve all stops for a Trip; the ending TStop is included, but if
     * the trip started at the previous trip's ending stop, then the starting TStop won't be,
     * because its trip id is that previous trip's id.
     * Otherwise, the starting TStop will be included as the first element.
     * Its odo_total matches the trip's {@link Trip#getOdo_start()},
     * and its odo_trip is 0.
     *
     * @param db  db connection
     * @param trip  trip to look for
     * @return TStops for this Trip, ordered by id, or null if none
     * @throws IllegalStateException if db not open
     * @see Trip#readStartTStop(boolean)
     */
    public static Vector<TStop> stopsForTrip(RDBAdapter db, Trip trip)
        throws IllegalStateException
    {
    	if (db == null)
    		throw new IllegalStateException("db null");
    	Vector<String[]> sv = db.getRows
    	    (TABNAME, FIELD_TRIPID, Integer.toString(trip.getID()), FIELDS_AND_ID, "_id");
    	if (sv == null)
    		return null;

    	Vector<TStop> vv = new Vector<TStop>(sv.size());
		try
		{
	    	for (int i = 0; i < sv.size(); ++i)
	    		vv.addElement(new TStop(db, sv.elementAt(i)));
		} catch (RDBKeyNotFoundException e) { }
    	return vv;
    }

    /**
     * Retrieve this Trip's starting TStop, if the trip doesn't continue from the
     * previous one's ending tstop.  The starting TStop has <tt>odo_trip</tt> == 0.
     * (Remember that empty <tt>odo_trip</tt> is stored as null, not as 0.)
     *<P>
     * Note package access, not public; intended for use by Trip.readStartTStop.
     *
     * @param  db  db connection
     * @param  t  trip to find the TStop for
     * @return the TStop, or null if not found (this would be an inconsistency)
     * @throws IllegalArgumentException  if {@link Trip#getStartTStopID()} != 0
     * @throws IllegalStateException if the db connection is closed
     * @see Trip#isStartTStopFromPrevTrip()
     */
    static TStop readStartingStopWithinTrip(RDBAdapter db, Trip t)
    	throws IllegalArgumentException, IllegalStateException
    {
    	if (t.getStartTStopID() != 0)
    		throw new IllegalArgumentException("t.tstopid_start != 0");

    	Vector<String[]> ts = db.getRows
    		(TABNAME,
    		 WHERE_TRIPID_AND_ODOTRIP, new String[]{ Integer.toString(t.id) },
    		 FIELDS_AND_ID, "_id");
    	if (ts == null)
    		return null;  // Inconsistency, should not occur; likewise, size > 1 should not occur

    	try {
			return new TStop(db, ts.firstElement());
		} catch (RDBKeyNotFoundException e) {
			// won't happen, but required due to declaration
			return null;
		}
    }

    /**
     * Retrieve this Trip's latest stop, if any.
     * If the trip has no intermediate stops yet:
     *<UL>
     *<LI> If the trip started at the previous trip's final TStop, null is returned.
     *<LI> Otherwise, this trip's starting point will be returned.
     *</UL>
     * If the trip is completed, it will have an ending TStop,
     * and that will be the one returned.
     *
     * @return that stop, or null if none yet on this trip
     * @throws IllegalStateException if the db connection is closed
     * @see #readAllTStops()
     */
    public static TStop latestStopForTrip(RDBAdapter db, final int tripID)
    	throws IllegalStateException
	{
		if (db == null)
			throw new IllegalStateException("dbConn null");

		// TODO: "LIMIT 1"
		Vector<String[]> sv = db.getRows
		    (TABNAME, FIELD_TRIPID, Integer.toString(tripID), FIELDS_AND_ID, "_id DESC");
		if (sv == null)
			return null;

		try
		{
			return new TStop(db, sv.firstElement());
		} catch (RDBKeyNotFoundException e)
		{
			return null;
		}
	}

    /**
     * Retrieve an existing trip stop, by id, from the database.
     *
     * @param db  db connection
     * @param id  id field
     * @throws IllegalStateException if db not open
     * @throws RDBKeyNotFoundException if cannot retrieve this ID
     */
    public TStop(RDBAdapter db, final int id)
        throws IllegalStateException, RDBKeyNotFoundException
    {
    	super(db, id);
    	String[] rec = db.getRow(TABNAME, id, FIELDS);
    	if (rec == null)
    		throw new RDBKeyNotFoundException(id);

    	initFields(rec);  // null descr shouldn't occur (IllegalArgumentException)
    }

    /**
     * Existing record: Fill our obj fields from db-record string contents.
     * @param db  connection
     * @param rec  field contents, as returned by db.getRows(FIELDS_AND_ID); last element is _id
     * @throws RDBKeyNotFoundException not thrown, but required due to super call
     * @throws IllegalArgumentException if descr is null; descr is rec[9]
     */
    private TStop(RDBAdapter db, final String[] rec)
    	throws RDBKeyNotFoundException, IllegalArgumentException
    {
    	super(db, Integer.parseInt(rec[FIELDS.length]));
    	initFields(rec);
    }

    /**
     * Fill our obj fields from db-record string contents.
     * @param rec  field contents, as returned by db.getRow(FIELDS) or db.getRows(FIELDS_AND_ID)
     * @throws IllegalArgumentException if locat is null; locat is rec[10]
     */
    private void initFields(final String[] rec)
    	throws IllegalArgumentException
    {
		tripid = Integer.parseInt(rec[0]);  // FK
    	if (rec[1] != null)
    		odo_total = Integer.parseInt(rec[1]);
    	if (rec[2] != null)
    		odo_trip = Integer.parseInt(rec[2]);
    	if (rec[3] != null)
    		time_stop = Integer.parseInt(rec[3]);
    	if (rec[4] != null)
    		time_continue = Integer.parseInt(rec[4]);
    	if (rec[5] != null)
    		locid = Integer.parseInt(rec[5]);
    	if (rec[6] != null)
    		areaid = Integer.parseInt(rec[6]);
    	geo_lat = rec[7];
    	geo_lon = rec[8];
    	flag_sides = Integer.parseInt(rec[9]);
    	locat = rec[10];
    	via_route = rec[11];
    	if (rec[12] != null)
    		via_id = Integer.parseInt(rec[12]);
    	comment = rec[13];

    	if (rec.length == 15)
    		id = Integer.parseInt(rec[14]);
	}

    /**
     * Create a new trip stop, but don't yet write to the database.
     * When ready to write (after any changes you make to this object),
     * call {@link #insert(RDBAdapter)}.
     *<P>
     * If this TStop is to begin a new trip (with <tt>trip_odo</tt> == 0),
     * call {@link #TStop(Trip, int, int, Location, String, String)
     * instead of this constructor.
     *
     * @param trip   Trip containing this stop; it must be committed, with a valid tripID
     * @param odo_total  Total odometer, or 0 if unknown
     * @param odo_trip   Trip odometer, or 0 if unknown
     * @param time_stop   Travel-stop time (arrival time; unix format), or 0
     * @param time_continue   Travel-Start time (resuming travel after the stop), or 0
     * @param locid      Location ID, not empty (not 0 or -1)
     * @param areaid     GeoArea ID, or 0 for null; local trips use null, and so do
     *                     stops "in nowhere" during roadtrips.
     *                   A roadtrip's ending tstop's areaid should be the ending area.
     *                   A roadtrip's starting tstop's areaid is ignored, because it could be the
     *                   ending tstop of a local trip.
     * @param geo_lat    Latitude, or null
     * @param geo_lon    Longitude, or null
     * @param flag_sides  Side-table flags, or 0
     * @param via_id     Street via_route ID from previous tstop's location, or 0
     * @param comment    Comment/description, or null
     * @throws IllegalArgumentException if <tt>trip</tt> or <tt>locid</tt> or <tt>locat</tt> is bad
     */
    public TStop(Trip trip, final int odo_total, final int odo_trip,
		final int time_stop, final int time_continue, final int locid, final int areaid,
		final String geo_lat, final String geo_lon, final int flag_sides,
		final int via_id, final String comment)
    	throws IllegalArgumentException
    {
    	super();
    	tripid = trip.getID();
    	if (tripid <= 0)
    		throw new IllegalArgumentException("empty trip.id");
    	this.odo_total = odo_total;
    	this.odo_trip = odo_trip;
    	this.time_stop = time_stop;
    	this.time_continue = time_continue;
    	this.locid = locid;
    	if (locid <= 0)
    		throw new IllegalArgumentException("empty locid");
    	this.areaid = areaid;
    	this.geo_lat = geo_lat;
    	this.geo_lon = geo_lon;
    	this.flag_sides = flag_sides;
    	this.via_id = via_id;
    	this.comment = comment;
    }

    /**
     * Create the new trip stop which represents the start of a trip,
     * but don't yet write to the database.  <tt>trip_odo</tt> will be 0.
     * When ready to write (after any changes you make to this object),
     * call {@link #insert(RDBAdapter)}.
     *
     * @param trip   Trip containing this stop
     * @param odo_total  Total odometer, not 0
     * @param time_continue   Start time, or 0
     * @param loc        Location, not null
     * @param geo_lat    Latitude, or null
     * @param geo_lon    Longitude, or null
     */
    public TStop(Trip trip, final int odo_total,
    		final int time_continue, final Location loc,
    		final String geo_lat, final String geo_lon)
    {
    	this(trip, odo_total, 0, 0, time_continue, loc.getID(),
    		(trip.getRoadtripEndAreaID() > 0 ? trip.getAreaID() : 0),  // only roadtrips use areaID here
    		geo_lat, geo_lon, 0, 0, null);
    	odo_trip_0_beginTrip = true;
    }

	/**
     * Insert a new record based on the current field values.
	 * Clears dirty field; sets id and dbConn fields.
	 *<P>
	 * After calling this, if this stop is the highest mileage
	 * on a Trip in progess, please call
	 * {@link Trip#addCommittedTStop(TStop)} to keep the
	 * Trip object consistent.
	 *
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
        { "tripid", "odo_total", "odo_trip", "time_stop", "time_continue",
    	  "locid", "a_id", "geo_lat", "geo_lon", "flag_sides",
    	  "descr", "via_route", "via_id", "comment" };
		 */
		String tripOdo;
		if (odo_trip_0_beginTrip)
			tripOdo = "0";
		else
			tripOdo = (odo_trip != 0 ? Integer.toString(odo_trip) : null);

		String[] fv =
		    {
			Integer.toString(tripid),
			(odo_total != 0 ? Integer.toString(odo_total) : null),
			tripOdo,
			(time_stop != 0 ? Integer.toString(time_stop) : null),
			(time_continue != 0 ? Integer.toString(time_continue) : null),
			(locid != 0 ? Integer.toString(locid) : null),
			(areaid != 0 ? Integer.toString(areaid) : null),
			geo_lat, geo_lon,
			Integer.toString(flag_sides),
			locat, via_route,
			(via_id != 0 ? Integer.toString(via_id) : null),
			comment
		    };
		return fv;
	}

	public int getTripID() {
		return tripid;
	}

	/** Get the vehicle's overall total odometer at this tstop. */
	public int getOdo_total() {
		return odo_total;
	}

	/**
	 * Set or clear the vehicle's odometers at this tstop.
	 * Remember that the ending TStop's odometers must not be blank.
	 * @param odoTotal new overall total-odo value, or 0 if blank/unused
	 * @param odoTrip new trip-odo value, or 0 if blank/unused
	 * @see #setOdo_trip(int)
	 */
	public void setOdos(final int odoTotal, final int odoTrip) {
		if (odo_trip != odoTrip)
			toString_descr = null;
		odo_total = odoTotal;
		odo_trip = odoTrip;
		dirty = true;
	}

	/** Get the distance within this trip at this tstop, or 0 if blank. */
	public int getOdo_trip() {
		return odo_trip;
	}

	/** Get the trip's stop time */
	public int getTime_stop() {
		return time_stop;
	}

	/** Get the trip's continue-time (when the trip resumes), or 0 if none */
	public int getTime_continue() {
		return time_continue;
	}

	/**
	 * Set the travel-start time (ending the stop) for this TStop.
	 * @param sTime Start time (unix format), or 0
	 * @param commit Also commit this field change (ONLY!) to db right now;
	 *               if false, only set {@link #isDirty()}.
	 */
	public void setTime_continue(final int sTime, final boolean commitNow)
	{
		time_continue = sTime;
		if (! commitNow)
		{
			dirty = true;
			return;
		}
		String[] newTStart = { Integer.toString(sTime) };
		dbConn.update(TABNAME, id, FIELD_TIME_CONTINUE_ARR, newTStart);
	}

	/**
	 * Get the location text field. (Stored in db as 'descr').
	 * This is usually <tt>null</tt> for TStops created in 0.9.05 or newer.
	 * @return <tt>descr</tt> field contents, or null if <tt>locid</tt> is used instead.
	 * @see #readLocationText()
	 */
	public String getLocationDescr() {
		return locat;
	}

	/** Get the location ID, or 0 if empty/unused. */
	public int getLocationID() {
		return locid;
	}

    /**
     * Convenience method to retrieve this TStop's {@link Location} record,
     * if any, from the database (<tt>locid</tt> field).
     * The Location isn't cached here.
     *<P>
     * Note that if a Location with _id = locid isn't found in the database,
     * the field is cleared and {@link #isDirty()} is set.
     *
     * @return that location, or null
     * @throws IllegalStateException if the db connection is closed
     * @see #readLocationText()
     */
    public Location readLocation()
        throws IllegalStateException
    {
    	if (locid == 0)
    		return null;
    	if (dbConn == null)
    		throw new IllegalStateException("dbConn null");
    	Location lo = null;
    	try {
			lo = new Location(dbConn, locid);
		} catch (RDBKeyNotFoundException e) {
			locid = 0;  // bad key
			dirty = true;
		}
    	return lo;
    }

    /**
     * Convenience method to get this TStop's location text:
     * either from the <tt>descr</tt> field, or if that is null,
     * from the database (<tt>locid</tt> field).
     * The returned text isn't cached here.
     *<P>
     * Note that if a Location with _id = locid isn't found in the database,
     * the field is cleared and {@link #isDirty()} is set.
     *
     * @return that location text, or null if both <tt>descr</tt> and <tt>locid</tt> are unused.
     * @throws IllegalStateException if the db connection is closed
     * @since 0.9.05
     */
    public String readLocationText()
    	throws IllegalStateException
    {
    	if (locat != null)
    		return locat;
    	if (locid == 0)
    		return null;
    	Location lo = readLocation();
    	if (lo == null)
    		return null;
    	return lo.getLocation();
    }

    /**
	 * Set the <tt>locid</tt> Location-ID field.
	 * Don't forget to also call {@link #setLocationDescr(String)}}.
	 * @param locID  New Location ID, or 0 for null
	 * */
	public void setLocationID(final int locID) {
		locid = locID;
		dirty = true;
		if (toString_descr != null)
			toString_descr = null;
	}

	/** Get the GeoArea ID, or 0 if empty/unused. */
	public int getAreaID() {
		return areaid;
	}

	/**
	 * Set the GeoArea field.
	 * @param a_id  New GeoArea ID, or 0 for null
	 */
	public void setAreaID(final int a_id)
		throws IllegalArgumentException
	{
		areaid = a_id;
		dirty = true;
	}

	/**
	 * Get the via route, or null if unused.
	 * @see #getVia_id()
	 * @see #readVia()
	 */
	public String getVia_route() {
		return via_route;
	}

	public void setVia_route(final String via) {
		via_route = via;
		dirty = true;
	}

	/**
	 * Get the via_route id, or 0 if empty/unused.
	 * @see #readVia()
	 */
	public int getVia_id() {
		return via_id;
	}

	/**
	 * Set the via_route id.
	 * @param viaID id, or 0 if empty/unused.
	 */
	public void setVia_id(final int viaID) {
		via_id = viaID;
		dirty = true;
	}

    /**
     * Convenience method to retrieve this TStop's {@link ViaRoute} record,
     * if any, (from via_id) from the database.
     * The ViaRoute isn't cached here.
     *<P>
     * Note that if a ViaRoute with _id = via_id isn't found in the database,
     * the field is cleared and {@link #isDirty()} is set.
     *
     * @return that via, or null
     * @throws IllegalStateException if the db connection is closed
     */
    public ViaRoute readVia()
        throws IllegalStateException
    {
    	if (via_id == 0)
    		return null;
    	if (dbConn == null)
    		throw new IllegalStateException("dbConn null");
    	ViaRoute v = null;
    	try {
			v = new ViaRoute(dbConn, via_id);
		} catch (RDBKeyNotFoundException e) {
			via_id = 0;  // bad key
			dirty = true;
		}
    	return v;
    }

    /**
     * Get the sum of TStop's flags and temporary flags, such as
     * {@link #FLAG_GAS} and {@link #TEMPFLAG_CREATED_VIAROUTE}.
     *<P>
     * All temporary flags have values <tt>0x80</tt> or lower,
     * and are cleared before continuing the trip from the stop
     * (or, if this stop ends the trip, before ending it).
     *
     * @return sum of flag values, or 0 if none
     * @see #isSingleFlagSet(int)
     * @see #setFlagSingle(int)
     * @see #clearFlagSingle(int)
     */
    public int getFlags()
    {
    	return flag_sides;
    }

    /**
     * Is this flag bit, or temporary flag bit, set within <tt>flag_sides</tt>?
     * @param flag  A flag or temporary flag, such as
     *   {@link #FLAG_GAS} or {@link #TEMPFLAG_CREATED_VIAROUTE}
     * @see #getFlags()
     */
    public boolean isSingleFlagSet(final int flag)
    {
    	return (0 != (flag_sides & flag));
    }

    /**
     * Set a single flag bit, along with the currently set flags.
     * @param flag  A flag or temporary flag, such as
     *   {@link #FLAG_GAS} or {@link #TEMPFLAG_CREATED_VIAROUTE}
     * @see #clearFlagSingle(int)
     * @see #getFlags()
     * @see #isSingleFlagSet(int)
     */
    public void setFlagSingle(final int flag)
    {
    	final int newMask = flag_sides | flag;
    	if (flag_sides == newMask)
    		return;
    	flag_sides = newMask;
    	dirty = true;
    }

    /**
     * Clear a single flag bit, from the currently set flags.
     * @param flag  A flag or temporary flag, such as
     *   {@link #FLAG_GAS} or {@link #TEMPFLAG_CREATED_VIAROUTE}
     * @see #clearTempFlags()
     * @see #setFlagSingle(int)
     * @see #getFlags()
     * @see #isSingleFlagSet(int)
     */
    public void clearFlagSingle(final int flag)
    {
    	final int newMask = flag_sides & (~ flag);
    	if (flag_sides == newMask)
    		return;
    	flag_sides = newMask;
    	dirty = true;
    }

    /**
     * Clear any temporary flags which are currently set (<tt>0x80</tt> or less).
     * @see #clearFlagSingle(int)
     */
    public void clearTempFlags()
    {
    	if (0 == (flag_sides & 0xFF))
    		return;
    	flag_sides &= (~ 0xFF);
    	dirty = true;
    }

    /** Get the description/comment, or null. */
	public String getComment() {
		return comment;
	}

	/**
	 * Set the description/comment field.
	 * @param comment new value, or null
	 */
	public void setComment(final String comment) {
		this.comment = comment;
		dirty = true;
		if (toString_descr != null)
			toString_descr = null;
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
	 * For calculating the distance to the latest TStop from the
	 * previous one during a trip.  If the trip doesn't yet have
	 * any previous stops, calculate from the start of the trip (0).
	 * @param prevLoc  Previous stop's location; not null
	 * @param trip TODO
	 * @return  the previous TStop's trip_odo if can be determined, or -1
	 * @throws IllegalStateException if trip.db not open
	 */
	public static int tripReadPrevTStopOdo(Trip trip, final Location prevLoc, final TStop currTS)
		throws IllegalStateException
	{
		if (prevLoc == null)
			return -1;
		if (trip.dbConn == null)
			throw new IllegalStateException("db null");
	
		Vector<TStop> tsv = stopsForTrip(trip.dbConn, trip);  // may throw IllegalStateException
			// TODO limit 2
		if (tsv == null)
		{
			// no previous stops; use 0 for start of trip
			return 0;
		}
		// Any previous stops at all?
		TStop ts = tsv.lastElement();
		if ((currTS != null) && (ts.id == currTS.id))
		{
			final int sz = tsv.size();
			if (sz < 2)
				return 0;  // No previous stop; use 0 for start of trip
			ts = tsv.elementAt(sz - 2);  // stop before currTS
		}
		// Does its loc match prevLoc?
		// If so, return its trip odo.
		if ((ts.odo_trip != 0) && (ts.locid > 0)
			&& (ts.locid == prevLoc.getID()))
		{
			return ts.odo_trip;
		}
		return -1;  // Unexpected location for previous tstop
	}

	/**
	 * A FreqTripTStop's toString is
	 * "(" trip-odo ")" location "via" the via (if any).
	 * The database connection should be available to fill these fields.
	 * If not, toString will fall back to just using the IDs.
	 * Main text is cached after first call; via text is not.
	 * @param lookupLoc  If true, and <tt>locat</tt> is null, look up our Location text from the database
	 * @param lookupVia  If true, also look up our ViaRoute text from the database, and append it.
	 */
	public String toString(final boolean lookupLoc, final boolean lookupVia)
	{
		if ((toString_descr != null) && ((via_id == 0) || ! lookupVia))
			return toString_descr;

		StringBuffer sb = new StringBuffer();
		if (odo_trip != 0)
		{
		    sb.append('(');
		    sb.append((int) (odo_trip / 10));
		    sb.append('.');
		    sb.append(odo_trip % 10);
		    sb.append(") ");
		}
		if (locat != null)
		{
			sb.append(locat);
		} else if (lookupLoc && (locid > 0))
		{
			String lo = readLocationText();
			if (lo != null)
				sb.append(lo);
		}
		if (comment != null)
		{
			sb.append(' ');
			sb.append(comment);
		}

		toString_descr = sb.toString();

		if (lookupVia && (via_id != 0) && (dbConn != null))
		{
			sb.append(" via ");
			ViaRoute vr = null;
			try {
				vr = new ViaRoute(dbConn, via_id);
			} catch (Throwable e) { } // RDBKeyNotFoundException
			if (vr != null)
				sb.append(vr.getDescr());
			else
				sb.append(via_id);

			return sb.toString();
		}
		else
		{
			return toString_descr;
		}
	}


}  // public class TStop
