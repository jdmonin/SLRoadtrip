/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2011,2015 Jeremy D Monin <jdmonin@nand.net>
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
 * In-memory representation, and database access for, a VehicleMake (brand of vehicle).
 * @author jdmonin
 * See also bookedit.MiscTablesCRUDDialog.createEditPersonDialog.
 */
public class VehicleMake extends RDBRecord
{
	private static final String TABNAME = "vehiclemake";

	private static final String VALFIELD = "mname";
	private static final String VALFIELD_SORT = "mname COLLATE NOCASE";  // syntax may be sqlite-specific
	private static final String[] FIELDS = { VALFIELD };
	private static final String[] FIELDS_AND_ID = { VALFIELD, "_id" };

    private String name;

    /**
     * Get the Persons currently in the database.
     * @param db  database connection
     * @return a Vector of Person objects from the database, ordered by name
     */
    public static Vector<VehicleMake> getAll(RDBAdapter db)
    {
	Vector<String[]> names = db.getRows(TABNAME, null, (String[]) null, FIELDS_AND_ID, VALFIELD_SORT, 0);
    	if (names == null)
    		return null;

    	Vector<VehicleMake> rv = new Vector<VehicleMake>(names.size());
    	for (String[] rec : names)
    	{
    		VehicleMake p = new VehicleMake(rec[0]);
    		p.isCleanFromDB(db, Integer.parseInt(rec[1]));
    		rv.addElement(p);
    	}
    	return rv;
    }

    /**
     * Retrieve an existing vehiclemake, by id, from the database.
     *
     * @param db  db connection
     * @param id  id field
     * @throws IllegalStateException if db not open
     * @throws RDBKeyNotFoundException if cannot retrieve this ID
     */
    public VehicleMake(RDBAdapter db, final int id)
        throws IllegalStateException, RDBKeyNotFoundException
    {
    	super(db, id);
    	String[] rec = db.getRow(TABNAME, id, FIELDS);
    	if (rec == null)
    		throw new RDBKeyNotFoundException(id);
    	name = rec[0];
    }

    /**
     * Create a new vehiclemake, but don't yet write to the database.
     * When ready to write (after any changes you make to this object),
     * call {@link #insert(RDBAdapter)}.
     *
     * @param name  VehicleMake's name; must be unique, and null is not allowed.
     *            Not checked here.
     */
    public VehicleMake(String name)
    {
    	super();
    	this.name = name;
    }

	public String getName()
	{
		return name;
	}

	/**
	 * Change the name.
	 * @param name new name; null is not allowed, and must be unique. Not checked here.
	 */
	public void setName(String name)
	{
		if (name.equals(this.name))
			return;
		this.name = name;
		dirty = true;
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
    	String[] fv = { name };
    	id = db.insert(TABNAME, FIELDS, fv, true);
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
    	String[] fv = { name };
		dbConn.update(TABNAME, id, FIELDS, fv);
		dirty = false;
	}

	/** Display the name */
	public String toString() { return name; }

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

}  // public class VehicleMake
