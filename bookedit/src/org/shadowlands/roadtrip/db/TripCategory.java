/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2012 Jeremy D Monin <jdmonin@nand.net>
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
 * In-memory representation, and database access for, a Trip Category.
 * Used for {@link Trip} and {@link FreqTrip}.
 * @author jdmonin
 */
public class TripCategory extends RDBRecord
{
	private static final String TABNAME = "tripcategory";
	private static final String[] FIELDS = { "cname", "pos" };
	private static final String[] FIELDS_AND_ID = { "cname", "pos", "_id" };
	// If you add fields, check the id position in calls to isCleanFromDB.

    private String name;
    private int pos;

    /**
     * Get the trip categories currently in the database.
     * @param db  database connection
     * @param blank  A placeholder to add the blank (empty) category to the array, or null
     * @return an array of TripCategory objects from the database, ordered by <tt>pos</tt>, or null if none
     */
    public static TripCategory[] getAll(RDBAdapter db, TripCategory blank)
    {
		Vector<String[]> tcs = db.getRows(TABNAME, null, (String[]) null, FIELDS_AND_ID, "pos", 0);
    	if (tcs == null)
    		return null;

    	if (blank != null)
    		tcs.insertElementAt(new String[1], 0);
    	final int L = tcs.size();
    	TripCategory[] rv = new TripCategory[L];
    	for (int i = 0; i < L; ++i)
    	{
    		final String[] rec = tcs.elementAt(i);
    		if (rec.length == 1)
    		{
    			rv[i] = blank;
    		} else {
	    		TripCategory p = new TripCategory(rec[0], Integer.parseInt(rec[1]));
	    		p.isCleanFromDB(db, Integer.parseInt(rec[2]));
	    		rv[i] = p;
    		}
    	}
    	return rv;
    }

    /**
     * Retrieve an existing category, by id, from the database.
     *
     * @param db  db connection
     * @param id  id field
     * @throws IllegalStateException if db not open
     * @throws RDBKeyNotFoundException if cannot retrieve this ID
     */
    public TripCategory(RDBAdapter db, final int id)
        throws IllegalStateException, RDBKeyNotFoundException
    {
    	super(db, id);
    	String[] rec = db.getRow(TABNAME, id, FIELDS);
    	if (rec == null)
    		throw new RDBKeyNotFoundException(id);
    	name = rec[0];
    	pos = Integer.parseInt(rec[1]);
    }

    /**
     * Create a new person, but don't yet write to the database.
     * When ready to write (after any changes you make to this object),
     * call {@link #insert(RDBAdapter)}.
     *
     * @param name  Person's name; cannot be null
     * @param position  Place number for on-screen order (instead of alphabetical listing)
     * @throws IllegalArgumentException  if name is null
     */
    public TripCategory(String name, final int position)
    	throws IllegalArgumentException
    {
    	super();
    	if (name == null)
    		throw new IllegalArgumentException("null name");
    	this.name = name;
    	pos = position;
    }

    public String getName()
	{
		return name;
	}

	/**
	 * Set the name.  Must be unique (not checked here), and not null.
	 * @param name new name
	 * @throws IllegalArgumentException if name is null
	 */
	public void setName(String name)
		throws IllegalArgumentException
	{
		if (name == null)
			throw new IllegalArgumentException("null name");
		if (name.equals(this.name))
			return;
		this.name = name;
		dirty = true;
	}

	/**
	 * Get the position for sorting.
	 * @return  Position number
	 */
	public int getPosition()
	{
		return pos;
	}

	/**
	 * Set the position for sorting.
	 * Must be unique (not checked here).
	 * @param newPos  New position number
	 */
	public void setPosition(final int newPos)
	{
		if (newPos == pos)
			return;
		pos = newPos;
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
    	String[] fv =
            { name, Integer.toString(pos) };
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
    	String[] fv =
    		{ name, Integer.toString(pos) };
		dbConn.update(TABNAME, id, FIELDS, fv);
		dirty = false;
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

	/** Display the name */
	public String toString() { return name; }

}  // public class TripCategory