/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2011,2013-2015,2017,2019 Jeremy D Monin <jdmonin@nand.net>
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
 * In-memory representation, and database access for,
 * a Location at which vehicles stop.
 * Used for autocomplete and FreqTrip.
 *
 * @author jdmonin
 */
public class Location extends RDBRecord
{
	private static final String TABNAME = "location";

	/** db table fields.
	 * @see #buildInsertUpdate()
	 * @see #initFields(String[])
	 */
	private static final String[] FIELDS =
		{ "a_id", "geo_lat", "geo_lon", "loc_descr", "latest_gas_brandgrade_id" };
	private static final String[] FIELDS_AND_ID =
		{ "a_id", "geo_lat", "geo_lon", "loc_descr", "latest_gas_brandgrade_id", "_id" };

	/**
	 * Field names/where-clause for case-insensitive description search within area.
	 * @since 0.9.60
	 */
	private static final String WHERE_AREAID_AND_DESCR =
		"a_id=? and loc_descr COLLATE NOCASE =?";

	/** May be unused (0); foreign key to {@link GeoArea}. */
	private int area_id;

	/** may be null */
	private String geo_lat, geo_lon;

	/** location ('loc_descr' field); may not be null */
	private String loc_descr;

	/** For gas stop locations, foreign key to {@link GasBrandGrade}; 0 for unused. */
	private int latest_gas_brandgrade_id;

	/**
	 * Get the Locations currently in the database, by area.
	 * @param db  database connection
	 * @param areaID  area ID to filter, or -1 for all locations in all areas;
	 *   use 0 for locations in "no area" on roadtrips
	 * @return an array of Location objects from the database, ordered by description, or null if none
	 */
	public static Location[] getAll(RDBAdapter db, final int areaID)
	{
		Vector<String[]> locs;
		if (areaID != -1)
		{
			final String kv = (areaID > 0) ? Integer.toString(areaID) : null;
			locs = db.getRows(TABNAME, "a_id", kv, FIELDS_AND_ID, "loc_descr COLLATE NOCASE", 0);
		} else {
			locs = db.getRows(TABNAME, (String) null, (String[]) null, FIELDS_AND_ID, "loc_descr COLLATE NOCASE", 0);
		}
		if (locs == null)
			return null;

		Location[] rv = new Location[locs.size()];
		try {
			for (int i = rv.length - 1; i >= 0; --i)
				rv[i] = new Location(db, locs.elementAt(i));

			return rv;
		} catch (RDBKeyNotFoundException e) {
			return null;  // catch is req'd but won't happen; record came from db.
		}
	}

	/**
	 * Search the table for a Location with this description within an area.
	 * @param db  db connection
	 * @param areaID  {@link GeoArea} ID; use 0 for locations in "no area" on roadtrips
	 * @param descr  Description of location; case-insensitive, not null
	 * @return  Location with this description, or null if none found.
	 *    If somehow the database has multiple matching rows, the one with lowest {@code _id} is returned.
	 * @throws IllegalStateException if db not open
	 * @since 0.9.40
	 */
	public static Location getByDescr(RDBAdapter db, final int areaID, final String descr)
		throws IllegalStateException
	{
		try {
			final String[] whereArgs = { Integer.toString(areaID), descr };
			Vector<String[]> locs = db.getRows
				(TABNAME, WHERE_AREAID_AND_DESCR, whereArgs, FIELDS_AND_ID, "_id", 0);
			if (locs != null)
				return new Location(db, locs.firstElement());
		}
		catch (IllegalArgumentException e) { }
		catch (RDBKeyNotFoundException e) { }
		// don't catch IllegalStateException, in case db is closed

		return null;
	}

	/**
	 * Retrieve an existing location, by id, from the database.
	 *
	 * @param db  db connection
	 * @param id  id field
	 * @throws IllegalStateException if db not open
	 * @throws RDBKeyNotFoundException if cannot retrieve this ID
	 * @see #getByDescr(RDBAdapter, int, String)
	 */
	public Location(RDBAdapter db, final int id)
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
	 * @throws IllegalArgumentException if descr is null; descr is rec[3]
	 */
	private Location(RDBAdapter db, final String[] rec)
		throws RDBKeyNotFoundException, IllegalArgumentException
	{
		super(db, Integer.parseInt(rec[FIELDS.length]));
		initFields(rec);
	}

	/**
	 * Fill our obj fields from db-record string contents.
	 * @param rec  field contents, as returned by db.getRow(FIELDS) or db.getRows(FIELDS_AND_ID)
	 * @throws IllegalArgumentException if loc_descr is null; loc_descr is rec[3]
	 */
	private void initFields(final String[] rec)
		throws IllegalArgumentException
	{
		if (rec[0] != null)
			area_id = Integer.parseInt(rec[0]);  // FK
		else
			area_id = 0;
		geo_lat = rec[1];
		geo_lon = rec[2];
		if (rec[3] == null)
			throw new IllegalArgumentException("null loc_descr");
		loc_descr = rec[3];
		if (rec[4] != null)
			latest_gas_brandgrade_id = Integer.parseInt(rec[4]);
		else
			latest_gas_brandgrade_id = 0;

		if (rec.length == 6)
			id = Integer.parseInt(rec[5]);
	}

	/**
	 * Create a new location, but don't yet write to the database.
	 * When ready to write (after any changes you make to this object),
	 * call {@link #insert(RDBAdapter)}.
	 *<P>
	 *
	 * @param area_id    Area id, or 0 for unused
	 * @param geo_lat    Latitude, or null
	 * @param geo_lon    Longitude, or null
	 * @param descr      Description of location; not null
	 * @throws IllegalArgumentException if <tt>descr</tt> is null,
	 *   or if <tt>area_id</tt> is &lt; 0
	 */
	public Location(final int area_id, final String geo_lat, final String geo_lon, final String descr)
		throws IllegalArgumentException
	{
		super();
		if (area_id < 0)
			throw new IllegalArgumentException("area_id");

		this.area_id = area_id;
		this.geo_lat = geo_lat;
		this.geo_lon = geo_lon;
		if (descr == null)
			throw new IllegalArgumentException("null loc_descr");
		this.loc_descr = descr;
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
			(area_id != 0) ? Integer.toString(area_id) : null,
			geo_lat, geo_lon,
			loc_descr,
			(latest_gas_brandgrade_id != 0) ? Integer.toString(latest_gas_brandgrade_id) : null
		    };
		return fv;
	}

	/** Get the area ID, or 0 if empty/unused. Foreign key to {@link GeoArea}. */
	public int getAreaID() {
		return area_id;
	}

	/**
	 * Set or clear the area ID.
	 * @param newArea new area ID, or 0 to clear
	 * @throws IllegalArgumentException if <tt>newArea</tt> &lt; 0
	 */
	public void setAreaID(final int newArea)
		throws IllegalArgumentException
	{
		if (newArea < 0)
			throw new IllegalArgumentException();

		area_id = newArea;
		dirty = true;
	}

	/** Get the location description field. Never null. */
	public String getLocation() {
		return loc_descr;
	}

	/**
	 * Set the location description field.
	 * @param descr  new location description; must not be null.
	 * @throws IllegalArgumentException if descr null
	 */
	public void setLocation(final String descr)
		throws IllegalArgumentException
	{
		if (descr == null)
			throw new IllegalArgumentException();

		loc_descr = descr;
		dirty = true;
	}

	/** For gas stop locations, get the latest {@link GasBrandGrade} ID, or 0 if unused. */
	public int getLatestGasBrandGradeID()
	{
		return latest_gas_brandgrade_id;
	}

	/**
	 * For gas stop locations, set or clear the latest {@link GasBrandGrade} ID.
	 * @param gas_brandgrade_id  New ID, or 0 for none
	 */
	public void setLatestGasBrandGradeID(final int gas_brandgrade_id)
	{
		if (latest_gas_brandgrade_id == gas_brandgrade_id)
			return;

		latest_gas_brandgrade_id = gas_brandgrade_id;
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

	/** A location's toString is its description field. Used for AutoComplete / ComboBox GUI elements. */
	public String toString() { return loc_descr; }

}  // public class Location
