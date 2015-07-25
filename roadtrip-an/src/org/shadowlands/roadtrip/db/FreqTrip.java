/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2012,2015 Jeremy D Monin <jdmonin@nand.net>
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
 * In-memory representation, and database access for, a Frequent Trip.
 * Starting and ending points are {@link Location}s;
 * any TStops in the middle are {@link FreqTripTStop}s.
 * See schema file for details.
 * @see #createFromTrip(Trip, Vector, String, int, boolean, boolean)
 * @author jdmonin
 */
public class FreqTrip extends RDBRecord
{
	private static final String TABNAME = "freqtrip";

	/** db table fields.
	 * @see #buildInsertUpdate()
	 */
	private static final String[] FIELDS =
	{
		"a_id", "start_locid", "end_locid",
		"end_odo_trip", "roadtrip_end_aid", "descr", "end_via_id",
		"typ_timeofday", "flag_weekends", "flag_weekdays", "is_roundtrip", "catid"
	};

	private static final String[] FIELDS_AND_ID =
	{
		"a_id", "start_locid", "end_locid",
		"end_odo_trip", "roadtrip_end_aid", "descr", "end_via_id",
		"typ_timeofday", "flag_weekends", "flag_weekdays", "is_roundtrip", "catid", "_id"
	};

	/**
	 * Where Clause to select roadtrips ({@code roadtrip_end_aid} not null) starting in an area ID.
	 * @since 0.9.50
	 */
	private static final String WHERE_AREAID_AND_IS_ROADTRIP
		= "a_id = ? and roadtrip_end_aid is not null";

	/**
	 * Where Clause to select roadtrips ({@code roadtrip_end_aid} not null) starting at a location ID.
	 * @since 0.9.50
	 */
	private static final String WHERE_START_LOCID_AND_IS_ROADTRIP
		= "start_locid = ? and roadtrip_end_aid is not null";

	/** GeoArea ID.  -1 is empty/unused. */
	private int a_id;
	/** Optional {@link TripCategory} ID.  0 is empty/unused (null in db). */
	private int catid;
	/** For roadtrips, ending GeoArea ID.  0 is empty/unused (null in db). */
	private int roadtrip_end_aid;
	/** Starting/ending {@link Location} IDs. See {@link #start_loc}. */
	private int start_locid, end_locid;

	private int end_odo_trip;

	/** typical time of day (60*hours + minutes). -1 is empty/unused. */
	private int typ_timeofday;

	/** typical time of week */
	private boolean flag_weekends, flag_weekdays;

	/** Does this trip end at {@link #start_locid} after stopping at {@link #end_locid}? */
	private boolean is_roundtrip;

	/** may be null */
	private String descr;
	/** trip-ending via_id into via_route table; 0 if unused */
	private int end_via_id;

	/**
	 * null unless {@link #readAllTStops()} called, or
	 * unless {@link #createFromTrip(Trip, Vector, String, int, boolean, boolean)}
	 * was called (in that case, they aren't inserted into the db yet).
	 */
	private transient Vector<FreqTripTStop> allStops;
	private transient Location start_loc, end_loc;
	/**
	 * null unless {@link #toString()} was called.
	 * See toString javadoc for contents.
	 */
	private transient String toString_descr;

	/**
	 * Get the FreqTrips currently in the database.
	 * @param db  database connection
	 * @param alsoTStops  If true, call {@link FreqTrip#readAllTStops()} for each freqtrip found
	 * @return All frequent trips, unsorted, or null if none
	 * @throws IllegalStateException if db not open
	 * @see #tripsForArea(RDBAdapter, int, boolean, boolean)
	 */
	public static Vector<FreqTrip> getAll(RDBAdapter db, final boolean alsoTStops)
		throws IllegalStateException
	{
		if (db == null)
			throw new IllegalStateException("db null");
		Vector<String[]> sv = db.getRows(TABNAME, (String) null, (String[]) null, FIELDS_AND_ID, null, 0);  // TODO sorting?

		Vector<FreqTrip> vv = objVectorFromStrings(db, alsoTStops, sv);
		return vv;
	}

	/**
	 * Retrieve all FreqTrips for a starting location, or only frequent roadtrips from that location.
	 * @param db  db connection
	 * @param locID   Location ID; should not be 0.
	 * @param nonLocal  Roadtrips only, not all trips ({@code roadtrip_end_aid} not null)
	 * @param alsoTStops  If true, call {@link FreqTrip#readAllTStops()} for each freqtrip found
	 * @return FreqTrips for this location, unsorted, or null if none
	 * @throws IllegalStateException if db not open
	 * @see #tripsForArea(RDBAdapter, int, boolean, boolean)
	 */
	public static Vector<FreqTrip> tripsForLocation
		(RDBAdapter db, final int locID, final boolean nonLocal, final boolean alsoTStops)
		throws IllegalStateException
	{
		if (db == null)
			throw new IllegalStateException("db null");

		final Vector<String[]> sv;
		if (nonLocal)
		{
			final String[] whereArgs = { Integer.toString(locID) };
			sv = db.getRows(TABNAME, WHERE_START_LOCID_AND_IS_ROADTRIP, whereArgs, FIELDS_AND_ID, "_id", 0);
		} else {
			sv = db.getRows(TABNAME, "start_locid", Integer.toString(locID), FIELDS_AND_ID, "_id", 0);
		}
		// TODO sorting; currently by _id (chronological date-created)

		return objVectorFromStrings(db, alsoTStops, sv);
	}

	/**
	 * Retrieve all FreqTrips for an area, or only frequent roadtrips from that area.
	 * @param db  db connection
	 * @param areaID   GeoArea ID; should not be 0.
	 * @param nonLocal  Roadtrips only, not all trips ({@code roadtrip_end_aid} not null)
	 * @param alsoTStops  If true, call {@link FreqTrip#readAllTStops()} for each freqtrip found
	 * @return FreqTrips for this location, unsorted, or null if none
	 * @throws IllegalStateException if db not open
	 * @see #getAll(RDBAdapter, boolean)
	 * @see #tripsForLocation(RDBAdapter, int, boolean, boolean)
	 */
	public static Vector<FreqTrip> tripsForArea
		(RDBAdapter db, final int areaID, final boolean nonLocal, final boolean alsoTStops)
		throws IllegalStateException
	{
		if (db == null)
			throw new IllegalStateException("db null");

		final Vector<String[]> sv;
		if (nonLocal)
		{
			final String[] whereArgs = { Integer.toString(areaID) };
			sv = db.getRows(TABNAME, WHERE_AREAID_AND_IS_ROADTRIP, whereArgs, FIELDS_AND_ID, "_id", 0);
		} else {
			sv = db.getRows(TABNAME, "a_id", Integer.toString(areaID), FIELDS_AND_ID, "_id", 0);
		}
		// TODO sorting; currently by _id (chronological date-created)

		return objVectorFromStrings(db, alsoTStops, sv);
	}

	/**
	 * Parse and return a vector of FreqTrip objects from db.getRows strings.
	 * @param db  db connection
	 * @param alsoTStops  If true, call {@link FreqTrip#readAllTStops()} for each freqtrip found
	 * @param sv  The string values returned by db.getRows, or null if none
	 * @return Vector of FreqTrips, or null if {@code sv} is null
	 */
	private static Vector<FreqTrip> objVectorFromStrings
		(RDBAdapter db, final boolean alsoTStops, final Vector<String[]> sv)
	{
		if (sv == null)
			return null;

		Vector<FreqTrip> vv = new Vector<FreqTrip>(sv.size());
		try
		{
			FreqTrip t;
			for (int i = 0; i < sv.size(); ++i)
			{
				t = new FreqTrip(db, sv.elementAt(i));
				if (alsoTStops)
					t.readAllTStops();
				vv.addElement(t);
			}
		} catch (RDBKeyNotFoundException e) {}

		return vv;
	}

	/**
	 * Create a new, uncommitted FreqTrip from this Trip.
	 *<P>
	 * If {@code keepStops} != null, and its last entry is a "trip-ending" TStop
	 * (same odo_total as the trip's ending odo, null time_continue), use that
	 * tstop's {@code via_id}, {@code odo_trip} and {@code locID} to end the trip,
	 * instead of calling {@link Trip#readLatestTStop()}.
	 *
	 * @param t  The trip which will be copied to the new FreqTrip.  Its start and end location,
	 *            and ending trip odometer, must be set.
	 *           If this trip's {@link Trip#getTripCategoryID()} is set, it will be copied to the new FreqTrip.
	 * @param keepStops  Any intermediate stops from the trip which should be part of the FreqTrip;
	 *            can be 0-length or null.
	 *            The new FreqTrip will have uncommitted {@link FreqTripTStop}s
	 *            based on these {@link TStop}s.  When {@link #insert(RDBAdapter)}
	 *            is called on the new trip, the FreqTripTStops will also
	 *            be inserted into the database.
	 *<P>
	 * When ready to write (after any changes you make to this object),
	 * call {@link #insert(RDBAdapter)}.
	 *
	 * @param descr  Optional freqtrip description, or null
	 * @param typ_timeofday  24-hour typical time, or -1; format is hours * 60 + minutes.
	 * @param flag_weekends  Is this typically only on weekends?  Use false if unknown.
	 * @param flag_weekdays  Is this typically only on weekdays?  Use false if unknown.
	 * @throws IllegalArgumentException if the trip isn't ended, or isn't in the database ({@code _id} == -1),
	 *     or if {@code keepStops}'s "ending TStop" has an empty {@code odo_trip} or empty {@code locID}.
	 */
	public static FreqTrip createFromTrip
		(Trip t, Vector<TStop> keepStops, String descr,
		 final int typ_timeofday, final boolean flag_weekends, final boolean flag_weekdays)
		throws IllegalArgumentException
	{
		// TODO is_roundtrip

		if (t.getID() == -1)
			throw new IllegalArgumentException("trip not in db");
		if (! t.isEnded())
			throw new IllegalArgumentException("trip not ended");

		// Get starting and ending location:
		TStop tsStart = t.readStartTStop(true);
		Location startLoc = tsStart.readLocation();
		TStop tsEnd = null;
		if ((keepStops != null) && ! keepStops.isEmpty())
		{
			tsEnd = keepStops.lastElement();  // re-use the var, but it might not be the ending TStop
			if ((tsEnd.getOdo_total() != t.getOdo_end())
			    || (tsEnd.getTime_continue() != 0))
			{
				tsEnd = null;
			} else {
				if (tsEnd.getOdo_trip() == 0)
					throw new IllegalArgumentException("keepStops ending TStop has empty odo_trip");
				if (tsEnd.getLocationID() == 0)
					throw new IllegalArgumentException("keepStops ending TStop has empty locID");
				// Remove it from the list of intermediate stops
				keepStops.removeElementAt(keepStops.size() - 1);
			}
		}
		if (tsEnd == null)
			tsEnd = t.readLatestTStop();

		// Make the FreqTrip:
		FreqTrip ft = new FreqTrip
			(startLoc, tsEnd.readLocation(), tsEnd.getOdo_trip(), descr, tsEnd.getVia_id(),
			 typ_timeofday, flag_weekends, flag_weekdays);
		ft.catid = t.getTripCategoryID();

		// Make the stops:
		if ((keepStops != null) && (keepStops.size() > 0))
		{
			Vector<FreqTripTStop> fts = new Vector<FreqTripTStop>(keepStops.size());
			int prevLocID = startLoc.getID();
			int viaID, locID;
			for (int i = 0; i < keepStops.size(); ++i)
			{
				TStop ts = keepStops.elementAt(i);
				ViaRoute v = ts.readVia();
				// Validate the via: If some of the original
				// trip's tstops weren't included,
				// the via from-locations will differ.
				if ((v == null) || (v.getLocID_From() != prevLocID))
					viaID = 0;
				else
					viaID = v.getID();
				locID = ts.getLocationID();
				fts.addElement(new FreqTripTStop(ft, locID, viaID, ts.getOdo_trip()));

				// Prepare for next iteration:
				prevLocID = locID;
			}

			ft.allStops = fts;  // Will add to db during FreqTrip.insert
		}

		return ft;
	}

	/**
	 * Retrieve an existing freqtrip, by id, from the database.
	 *
	 * @param db  db connection
	 * @param id  id field
	 * @throws IllegalStateException if db not open
	 * @throws RDBKeyNotFoundException if cannot retrieve this ID
	 */
	public FreqTrip(RDBAdapter db, final int id)
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
	private FreqTrip(RDBAdapter db, final String[] rec) throws RDBKeyNotFoundException
	{
		super(db, Integer.parseInt(rec[FIELDS.length]));
		initFields(rec);
	}

	/**
	 * Fill our obj fields from db-record string contents.
	 * {@code id} is not filled; the constructor has filled it already.
	 * @param rec  Record's field contents, as returned by db.getRow({@link #FIELDS})
	 *    or db.getRows({@link #FIELDS_AND_ID})
	 */
	private void initFields(String[] rec)
	{
		/*
		private static final String[] FIELDS =
		{ "a_id", "start_locid", "end_locid",
		  "end_odo_trip", "roadtrip_end_aid", "descr", "end_via_id",
		  "typ_timeofday", "flag_weekends", "flag_weekdays", "is_roundtrip", "catid" };
		 */

		if (rec[0] != null)
			a_id = Integer.parseInt(rec[0]);  // FK
		else
			a_id = -1;
		start_locid = Integer.parseInt(rec[1]);  // FK
		end_locid = Integer.parseInt(rec[2]);  // FK
		end_odo_trip = Integer.parseInt(rec[3]);
		if (rec[4] != null)
			roadtrip_end_aid = Integer.parseInt(rec[4]);  // FK
		descr = rec[5];
		if (rec[6] != null)
			end_via_id = Integer.parseInt(rec[6]);  // FK
		if (rec[7] != null)
			typ_timeofday = Integer.parseInt(rec[7]);
		else
			typ_timeofday = -1;
		flag_weekends = ("1".equals(rec[8]));
		flag_weekdays = ("1".equals(rec[9]));
		is_roundtrip = ("1".equals(rec[10]));
		if (rec[11] != null)
			catid = Integer.parseInt(rec[11]);  // FK
	}

	/**
	 * Create a new freqtrip, but don't yet write to the database.
	 * The geoareas (a_id, roadtrip_end_aid) will be taken from those of
	 * {@code start_loc}, {@code end_loc}.
	 *<P>
	 * To set the optional trip category, call {@link #setTripCategoryID()} before {@code insert}.
	 *<P>
	 * When ready to write (after any changes you make to this object),
	 * call {@link #insert(RDBAdapter)}.
	 *
	 * @param start_loc  Starting location, not null
	 * @param end_loc    Ending location, not null
	 * @param start_lat  Starting latitude, or null
	 * @param start_lon  Starting longitude, or null
	 * @param end_lat    Ending latitude, or null
	 * @param end_lon    Ending longitude, or null
	 * @param end_odo_trip    Ending trip odometer
	 * @param descr      Description, or null
	 * @param end_via_id     Route to ending location (via_route id), or 0
	 * @param typ_timeofday  24-hour typical time, or -1; format is hours * 60 + minutes.
	 * @param flag_weekends  Is this typically only on weekends?  Use false if unknown.
	 * @param flag_weekdays  Is this typically only on weekdays?  Use false if unknown.
	 */
	public FreqTrip
		(Location start_loc, Location end_loc, final int end_odo_trip, final String descr, final int end_via_id,
		 final int typ_timeofday, final boolean flag_weekends, final boolean flag_weekdays)
	{
		super();

		this.a_id = start_loc.getAreaID();
		start_locid = start_loc.getID();
		end_locid = end_loc.getID();
		final int end_aID = end_loc.getAreaID();
		if (a_id != end_aID)
			roadtrip_end_aid = end_aID;
		else
			roadtrip_end_aid = 0;
		this.end_odo_trip = end_odo_trip;
		this.descr = descr;
		this.end_via_id = end_via_id;
		this.typ_timeofday = typ_timeofday;
		this.flag_weekends = flag_weekends;
		this.flag_weekdays = flag_weekdays;
		// TODO is_roundtrip
		catid = 0;
	}

	/**
	 * Retrieve all stops for this FreqTrip.
	 * Cached after the first read.
	 *
	 * @return  ordered list of stops, or null if none
	 * @throws IllegalStateException if the db connection is closed
	 */
	public Vector<FreqTripTStop> readAllTStops()
		throws IllegalStateException
	{
		if (allStops == null)
		{
			if (dbConn == null)
				throw new IllegalStateException("dbConn null");
			allStops = FreqTripTStop.stopsForTrip(dbConn, this);
			readLocations();  // sets the field
		}

		return allStops;
	}

	/**
	 * Retrieve this FreqTrip's starting and ending {@link Location}s.
	 * Cached after the first read.
	 *
	 * @throws IllegalStateException if the db connection is closed
	 * @see #readAllTStops()
	 */
	private void readLocations()
		throws IllegalStateException
	{
		if ((start_loc != null) && (end_loc != null))
			return;
		if (dbConn == null)
			throw new IllegalStateException("dbConn null");

		try
		{
			start_loc = new Location(dbConn, start_locid);
			end_loc = new Location(dbConn, end_locid);
		} catch (RDBKeyNotFoundException e) {
			// bad key; shouldn't happen
			start_loc = null;
			end_loc = null;
			dirty = true;
		}
	}

	/**
	 * Insert a new record based on field and value.
	 * Clears dirty field; sets id and dbConn fields.
	 * If this FreqTrip was created by calling
	 * {@link #createFromTrip(Trip, Vector, String, int, boolean, boolean)},
	 * its {@link FreqTripTStop}s will be inserted at this time.
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
		if (allStops != null)
		{
			for (int i = 0; i < allStops.size(); ++i)
			{
				FreqTripTStop fts = allStops.elementAt(i);
				fts.setFreqTripID(this);
				fts.insert(db);
			}
		}

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
		{ "a_id", "start_locid", "end_locid",
		  "end_odo_trip", "roadtrip_end_aid", "descr", "end_via_id",
		  "typ_timeofday", "flag_weekends", "flag_weekdays", "is_roundtrip", "catid" };
		  */

		String[] fv =
		{
			(a_id != -1 ? Integer.toString(a_id) : null),
			Integer.toString(start_locid), Integer.toString(end_locid),
			Integer.toString(end_odo_trip),
			(roadtrip_end_aid > 0 ? Integer.toString(roadtrip_end_aid) : null),
			descr,
			(end_via_id != 0 ? Integer.toString(end_via_id) : null),
			(typ_timeofday != -1 ? Integer.toString(typ_timeofday) : null),
			(flag_weekends ? "1" : "0"),
			(flag_weekdays ? "1" : "0"),
			(is_roundtrip ? "1" : "0"),
			(catid != 0 ? Integer.toString(catid) : null)
		};

		return fv;
	}

	/**
	 * Get this trip's optional {@link TripCategory} id.
	 * @return the trip category ID, or 0 for none
	 */
	public int getTripCategoryID() {
		return catid;
	}

	/** get the trip-odometer at the end of this trip */
	public int getEnd_odoTrip() {
		return end_odo_trip;
	}

	public int getStart_locID()
	{
		return start_locid;
	}

	public int getEnd_locID()
	{
		return end_locid;
	}

	/** Get the starting area ID, or the only area ID if a local trip */
	public int getStart_aID()
	{
		return a_id;
	}

	/** Get the ending area ID for a roadtrip, or 0 if a local trip */
	public int getEnd_aID_roadtrip()
	{
		return roadtrip_end_aid;
	}

	/** Get the freqtrip's description, or null */
	public String getDescription()
	{
		return descr;
	}

	/** Get the freqtrip's ending via_route, or 0 if not set */
	public int getEnd_ViaRouteID()
	{
		return end_via_id;
	}

	/**
	 * Get the typical time of day (24-hr format), or -1 if not set.
	 * @return Typical time of day; format is hours * 60 + minutes; or -1 if this field is not set.
	 */
	public int getTypicalTimeOfDay()
	{
		return typ_timeofday;
	}

	/** Is this freqtrip typically made on weekends? */
	public boolean isWeekends()
	{
		return flag_weekends;
	}

	/** Is this freqtrip typically made on weekdays? */
	public boolean isWeekdays()
	{
		return flag_weekdays;
	}

	/** Does this trip end at {@link #getStart_locID()} after stopping at {@link #getEnd_locID()}? */
	public boolean isRoundTrip()
	{
		return is_roundtrip;
	}

	/**
	 * A FreqTrip's toString is its description if available,
	 * otherwise the starting location "->" ending location "via" the via (if any).
	 * The database connection should be available to fill these fields.
	 * If not, toString will fall back to just using the IDs.
	 * Cached after first call.
	 */
	public String toString()
	{
		if (toString_descr != null)
			return toString_descr;
		if (descr != null)
		{
			toString_descr = descr;
			return descr;
		}

		StringBuffer sb = new StringBuffer();
		if (dbConn != null)
		{
			if ((start_loc == null) || (end_loc == null))
				readLocations();
			if (start_loc != null)
				sb.append(start_loc.getLocation());
			else
				sb.append(start_locid);
			sb.append(" -> ");
			if (end_loc != null)
				sb.append(end_loc.getLocation());
			else
				sb.append(end_locid);
			if (end_via_id != 0)
			{
				sb.append(" via ");
				ViaRoute vr = null;
				try {
					vr = new ViaRoute(dbConn, end_via_id);
				} catch (Throwable e) {} // RDBKeyNotFoundException
				if (vr != null)
					sb.append(vr.getDescr());
				else
					sb.append(end_via_id);
			}
		} else {
			sb.append("locID ");
			sb.append(start_locid);
			sb.append(" -> ");
			sb.append(end_locid);
			if (end_via_id != 0)
			{
				sb.append(" via ");
				sb.append(end_via_id);
			}
		}

		toString_descr = sb.toString();
		return toString_descr;
	}

	/**
	 * Delete an existing record, <b>Not Currently Allowed</b>.
	 *
	 * @throws NullPointerException if dbConn was null because
	 *     this is a new record, not an existing one
	 * @throws UnsupportedOperationException because this table doesn't
	 *     currently allow deletion.
	 */
	public void delete()
		throws NullPointerException, UnsupportedOperationException
	{
		throw new UnsupportedOperationException();

		/*
		// TODO check if unused in other tables
		dbConn.delete(TABNAME, id);
		deleteCleanup();
		*/
	}

}  // public class FreqTrip
