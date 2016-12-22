/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2011,2014-2016 Jeremy D Monin <jdmonin@nand.net>
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
 * In-memory representation, and database access for, a ViaRoute (<tt>via_route</tt> table).
 * @author jdmonin
 */
public class ViaRoute extends RDBRecord
{
    private static final String TABNAME = "via_route";
    private static final String DESCFIELD = "via_descr";
    private static final String DESCFIELD_SORT = "via_descr COLLATE NOCASE";  // syntax may be sqlite-specific
    /** Order by {@code locid_from}, then by {@code via_descr} sorted case-insensitive */
    private static final String DESCFIELD_SORT_AFTER_FROM = "locid_from, via_descr COLLATE NOCASE";

    /** db table fields.
     * @see #buildInsertUpdate()
     * @see #initFields(String[])
     */
    private static final String[] FIELDS =
	{ "locid_from", "locid_to", "odo_dist", DESCFIELD };
    private static final String[] FIELDS_AND_ID =
	{ "locid_from", "locid_to", "odo_dist", DESCFIELD, "_id" };

    /** From,To location IDs. Never empty/unused. */
    private int locid_from, locid_to;  // FK

    /** Trip-odometer distance for this route. 0 if empty/unused. */
    private int odo_dist;

    /** ViaRoute description; never null. */
    private String via_descr;

    /**
     * Retrieve all ViaRoutes between 2 Locations.
     * @param db  db connection
     * @param locFrom  Location to start from
     * @param locTo  Location to go to
     * @return ViaRoutes between these locations, ordered by description, or null if none
     * @throws IllegalStateException if db not open
     * @throws NullPointerException if locFrom or locTo null
     */
    public static ViaRoute[] getAll(RDBAdapter db, Location locFrom, Location locTo)
        throws IllegalStateException, NullPointerException
    {
    	return getAll(db, locFrom.getID(), locTo.getID());
    }

    /**
     * Retrieve all ViaRoutes from a given location ID to another location.
     * This method is not bidirectional; see {@link #getAll(RDBAdapter, int, int, boolean)}.
     * @param db  db connection
     * @param locID_from  Location to start from, or -1 for all ViaRoutes in the database
     * @param locID_to  Location to go to
     * @return ViaRoutes between these locations, ordered by description, or null if none
     * @throws IllegalStateException if db not open
     */
    public static ViaRoute[] getAll(RDBAdapter db, final int locID_from, final int locID_to)
        throws IllegalStateException
    {
    	if (db == null)
    		throw new IllegalStateException("db null");

    	Vector<String[]> sv;
    	if (locID_from != -1)
    	{
    	    final String[] locIDsFromTo = new String[] { Integer.toString(locID_from), Integer.toString(locID_to) };
    	    sv = db.getRows
    	    (TABNAME, "locid_from=? and locid_to=?", locIDsFromTo, FIELDS_AND_ID, DESCFIELD_SORT, 0);
    	} else {
    	    sv = db.getRows
    	    (TABNAME, (String) null, (String[]) null, FIELDS_AND_ID, DESCFIELD_SORT, 0);
    	}

	return toArray(db, sv);
    }

    /**
     * Retrieve all ViaRoutes between 2 location IDs. Optionally bidirectional.
     * @param db  db connection
     * @param locID_A  First location ID
     * @param locID_B  Second location ID
     * @param bidirectional  If true return all ViaRoutes which are either from A to B, or from B to A.
     *     If false return only those from A to B, same as calling {@link #getAll(RDBAdapter, int, int)}.
     * @return ViaRoutes between these locations, ordered by locid_from and then description, or null if none
     * @throws IllegalStateException if db not open
     * @see #getAll(RDBAdapter, int, int)
     * @since 0.9.51
     */
    public static ViaRoute[] getAll
	(RDBAdapter db, final int locID_A, final int locID_B, final boolean bidirectional)
	throws IllegalStateException
    {
	if (! bidirectional)
	    return getAll(db, locID_A, locID_B);

	if (db == null)
		throw new IllegalStateException("db null");

	final String locStr_A = Integer.toString(locID_A), locStr_B = Integer.toString(locID_B);
	final String[] locIDStrs = new String[] { locStr_A, locStr_B, locStr_B, locStr_A };
	Vector<String[]> sv = db.getRows
	    (TABNAME, "(locid_from=? and locid_to=?) or (locid_from=? and locid_to=?)",
	     locIDStrs, FIELDS_AND_ID, DESCFIELD_SORT_AFTER_FROM, 0);

	return toArray(db, sv);
    }

    /**
     * Given ViaRoute db record results from
     * {@link RDBAdapter#getRows(String, String, String, String[], String, int) db.getRows(..)},
     * return an array of objects.
     * @param db  DB adapter to pass to constructor
     * @param sv  String vector results from db.getRows(..), or null
     * @return  Array of ViaRoute objects created with {@link #ViaRoute(RDBAdapter, String[])},
     *     or null if {@code sv == null}
     * @since 0.9.51
     */
    private static ViaRoute[] toArray(final RDBAdapter db, final Vector<String[]> sv)
    {
	if (sv == null)
		return null;

	ViaRoute[] aa = new ViaRoute[sv.size()];
	try
	{
	    for (int i = 0; i < aa.length; ++i)
		aa[i] = new ViaRoute(db, sv.elementAt(i));
	}
	catch (RDBKeyNotFoundException e) { }  // not thrown, but required

	return aa;
    }

    /**
     * Search the table for a ViaRoute between these locations with this description.
     * @param db  db connection
     * @param locID_from  Location to start from
     * @param locID_to  Location to go to
     * @param descr  Description to search for; case-insensitive, not null
     * @return  ViaRoute between these locations with this description, or null if none found.
     *    If somehow the database has multiple matching rows, the one with lowest {@code _id} is returned.
     * @throws IllegalStateException if db not open
     * @since 0.9.40
     */
    public static ViaRoute getByLocsAndDescr(RDBAdapter db, final int locID_from, final int locID_to, final String descr)
	throws IllegalStateException
    {
	try {
		final String[] locIDsAndDescr = new String[]
			{ Integer.toString(locID_from), Integer.toString(locID_to), descr };
		Vector<String[]> vias = db.getRows
			(TABNAME, "locid_from=? and locid_to=? and via_descr COLLATE NOCASE = ?", locIDsAndDescr,
			 FIELDS_AND_ID, "_id", 0);
		if (vias != null)
			return new ViaRoute(db, vias.firstElement());
	}
	catch (IllegalArgumentException e) { }
	catch (RDBKeyNotFoundException e) { }
	// don't catch IllegalStateException, in case db is closed

	return null;
    }

    /**
     * Retrieve an existing ViaRoute, by id, from the database.
     *
     * @param db  db connection
     * @param id  id field
     * @throws IllegalStateException if db not open
     * @throws RDBKeyNotFoundException if cannot retrieve this ID
     * @see #getByLocsAndDescr(RDBAdapter, int, int, String)
     */
    public ViaRoute(RDBAdapter db, final int id)
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
     */
    private ViaRoute(RDBAdapter db, final String[] rec)
    	throws RDBKeyNotFoundException
    {
    	super(db, Integer.parseInt(rec[FIELDS.length]));
    	initFields(rec);
    }

    /**
     * Fill our obj fields from db-record string contents.
     * @param rec  field contents, as returned by db.getRow(FIELDS) or db.getRows(FIELDS_AND_ID)
     * @throws IllegalArgumentException  if via_descr is null, or locid_from or locid_to <= 0
     */
    private void initFields(final String[] rec)
    	throws IllegalArgumentException
    {
		locid_from = Integer.parseInt(rec[0]);  // FK
    	locid_to = Integer.parseInt(rec[1]);  // FK
    	if (rec[2] != null)
    		odo_dist = Integer.parseInt(rec[2]);
    	via_descr = rec[3];
    	if (rec.length == 5)
    		id = Integer.parseInt(rec[4]);
    	if ((locid_from <= 0) || (locid_to <= 0))
    		throw new IllegalArgumentException("locid 0");
    	if (via_descr == null)
    		throw new IllegalArgumentException("null via_descr");
	}

    /**
     * Create a new ViaRoute, but don't yet write to the database.
     * When ready to write (after any changes you make to this object),
     * call {@link #insert(RDBAdapter)}.
     *
     * @param locid_from  Starting location ID
     * @param locid_to   Ending location ID
     * @param odo_dist  Trip-odometer distance if known, or 0
     * @param via_descr  Description; not null
     * @throws IllegalArgumentException  if via_descr is null, or locid_from or locid_to <= 0
     */
    public ViaRoute(final int locid_from, final int locid_to, final int odo_dist, final String via_descr)
    	throws IllegalArgumentException
    {
    	super();
    	if ((locid_from <= 0) || (locid_to <= 0))
    		throw new IllegalArgumentException("locid 0");
    	if (via_descr == null)
    		throw new IllegalArgumentException("null via_descr");
    	this.locid_from = locid_from;
    	this.locid_to = locid_to;
    	this.odo_dist = odo_dist;
    	this.via_descr = via_descr;
    }

	/**
     * Insert a new record based on the current field values.
	 * Clears dirty field; sets id and dbConn fields.
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
		if (! dirty)
			return;
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
		String[] fv =
		    {
			Integer.toString(locid_from),
			Integer.toString(locid_to),
			((odo_dist != 0) ? Integer.toString(odo_dist) : null),
			via_descr
		    };
		return fv;
	}

	/** Get the starting location ID. */
	public int getLocID_From() {
		return locid_from;
	}

	/** Get the ending location ID. */
	public int getLocID_To() {
		return locid_to;
	}

	/** Get the trip-odometer distance, or 0. */
	public int getOdoDist() {
		return odo_dist;
	}

	/**
	 * Set the trip-odometer distance.
	 * @param dist  new distance, or 0
	 * @since 0.9.02
	 */
	public void setOdoDist(final int dist) {
		if (dist == odo_dist)
			return;
		odo_dist = dist;
		dirty = true;
	}

	/** Get the description string. */
	public String getDescr() {
		return via_descr;
	}

	/** Set several fields, for re-use of an existing record. */
	public void set(final int locid_to, final int odo_dist, final String via_descr)
	{
		this.locid_to = locid_to;
		this.odo_dist = odo_dist;
		this.via_descr = via_descr;
		dirty = true;
	}

	/** toString gets the description string. */
	public String toString() {
		return via_descr;
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

}  // public class ViaRoute
