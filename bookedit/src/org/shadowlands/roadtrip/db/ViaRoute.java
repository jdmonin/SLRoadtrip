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
 * In-memory representation, and database access for, a ViaRoute (<tt>via_route</tt> table).
 * @author jdmonin
 */
public class ViaRoute extends RDBRecord
{
    private static final String TABNAME = "via_route";

    /** db table fields.
     * @see #buildInsertUpdate()
     * @see #initFields(String[])
     */
    private static final String[] FIELDS =
        { "locid_from", "locid_to", "odo_dist", "via_descr" };
    private static final String[] FIELDS_AND_ID =
	    { "locid_from", "locid_to", "odo_dist", "via_descr", "_id" };

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
     * Retrieve all ViaRoutes between 2 location IDs.
     * @param db  db connection
     * @param locID_from  Location to start from
     * @param locID_to  Location to go to
     * @return ViaRoutes between these locations, ordered by description, or null if none
     * @throws IllegalStateException if db not open
     */
    public static ViaRoute[] getAll(RDBAdapter db, final int locID_from, final int locID_to)
        throws IllegalStateException
    {
    	if (db == null)
    		throw new IllegalStateException("db null");
    	final String[] locIDsFromTo = new String[] { Integer.toString(locID_from), Integer.toString(locID_to) };
    	Vector<String[]> sv = db.getRows
    	    (TABNAME, "locid_from=? and locid_to=?", locIDsFromTo, FIELDS_AND_ID, "via_descr", 0);
    	if (sv == null)
    		return null;

    	ViaRoute[] aa = new ViaRoute[sv.size()];
		try
		{
	    	for (int i = 0; i < aa.length; ++i)
	    		aa[i] = new ViaRoute(db, sv.elementAt(i));
		} catch (RDBKeyNotFoundException e) { }  // not thrown, but required
    	return aa;
    }

    /**
     * Retrieve an existing ViaRoute, by id, from the database.
     *
     * @param db  db connection
     * @param id  id field
     * @throws IllegalStateException if db not open
     * @throws RDBKeyNotFoundException if cannot retrieve this ID
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
