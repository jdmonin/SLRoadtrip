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
 * In-memory representation, and database access for, a Person (driver or passenger).
 * @author jdmonin
 * See also bookedit.MiscTablesCRUDDialog.createEditPersonDialog.
 */
public class Person extends RDBRecord
{
	private static final String TABNAME = "person";
	private static final String[] FIELDS = { "is_driver", "name", "contact_uri" };
	private static final String[] FIELDS_AND_ID = { "is_driver", "name", "contact_uri", "_id" };

    private boolean is_driver;
    private String name;
    /** contact info; may be null */
    private String contact_uri;

    /**
     * Get the Persons (drivers or all) currently in the database.
     * @param db  database connection
     * @param driversOnly  Only drivers, not all Persons
     * @return an array of Person objects from the database, ordered by name, or null if none
     */
    public static Person[] getAll(RDBAdapter db, final boolean driversOnly)
    {
    	String kf, kv;
    	if (driversOnly)
    	{
    		kf = "is_driver";
    		kv = "1";
    	} else {
    		kf = null;
    		kv = null;
    	}
		Vector<String[]> names = db.getRows(TABNAME, kf, kv, FIELDS_AND_ID, "name COLLATE NOCASE", 0);
    	if (names == null)
    		return null;

    	final int L = names.size();
    	Person[] rv = new Person[L];
    	for (int i = 0; i < L; ++i)
    	{
    		final String[] rec = names.elementAt(i);
    		Person p = new Person(rec[1], ("1".equals(rec[0])), rec[2]);
    		p.isCleanFromDB(db, Integer.parseInt(rec[3]));
    		rv[i] = p;
    	}
    	return rv;
    }

    /**
     * Retrieve an existing person, by id, from the database.
     *
     * @param db  db connection
     * @param id  id field
     * @throws IllegalStateException if db not open
     * @throws RDBKeyNotFoundException if cannot retrieve this ID
     */
    public Person(RDBAdapter db, final int id)
        throws IllegalStateException, RDBKeyNotFoundException
    {
    	super(db, id);
    	String[] rec = db.getRow(TABNAME, id, FIELDS_AND_ID);
    	if (rec == null)
    		throw new RDBKeyNotFoundException(id);
    	is_driver = ("1".equals(rec[0]));
    	name = rec[1];
    	contact_uri = rec[2];
    }

    /**
     * Create a new person, but don't yet write to the database.
     * When ready to write (after any changes you make to this object),
     * call {@link #insert(RDBAdapter)}.
     *
     * @param name  Person's name; cannot be null
     * @param isDriver  Driver, or only a friend or passenger?
     * @param contactURI  Contact info, or null
     * @throws IllegalArgumentException  if name is null
     */
    public Person(String name, boolean isDriver, String contactURI)
    	throws IllegalArgumentException
    {
    	super();
    	if (name == null)
    		throw new IllegalArgumentException("null name");
    	this.name = name;
    	is_driver = isDriver;
    	contact_uri = contactURI;
    }

    public boolean isDriver()
    {
		return is_driver;
	}

	public void setIsDriver(boolean isDriver)
	{
		if (isDriver == is_driver)
			return;
		is_driver = isDriver;
		dirty = true;
	}

	public String getName()
	{
		return name;
	}

	/**
	 * Set the name.  Must be unique (not checked), and not null.
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

	public String getContactURI()
	{
		return contact_uri;
	}

	public void setContactURI(String contactURI)
	{
		contact_uri = contactURI;
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
            { is_driver ? "1" : "0", name, contact_uri };
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
            { is_driver ? "1" : "0", name, contact_uri };
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

}  // public class Person