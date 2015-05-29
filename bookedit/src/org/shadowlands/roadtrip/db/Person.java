/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2012,2014-2015 Jeremy D Monin <jdmonin@nand.net>
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
	private static final String[] FIELDS =
		{ "is_driver", "name", "contact_uri", "is_active", "date_added", "comment" };
	private static final String[] FIELDS_AND_ID =
		{ "is_driver", "name", "contact_uri", "is_active", "date_added", "comment", "_id" };
	// If you add or change fields, remember to update initFields and other methods.

    private boolean is_driver;
    private boolean is_active;
    private String name;

    /**
     * Date this person was added, or 0 if empty/null.
     * @since 0.9.43
     */
    private int date_added;

    /** contact info; may be null */
    private String contact_uri;
    /** comment; may be null */
    private String comment;

    /**
     * Get the Persons (drivers or all) currently in the database.
     * @param db  database connection
     * @param driversOnly  Only drivers, not all Persons
     * @return an array of Person objects from the database, ordered by name, or null if none
     * @see #getMostRecent(RDBAdapter, boolean)
     * @throws IllegalStateException if db not open
     */
    public static Person[] getAll(RDBAdapter db, final boolean driversOnly)
	throws IllegalStateException
    {
	final Vector<String[]> names;

	if (driversOnly)
    	{
    		String kf = "is_driver", kv = "1";
    		names = db.getRows(TABNAME, kf, kv, FIELDS_AND_ID, "name COLLATE NOCASE", 0);
    	} else {
    		names = db.getRows(TABNAME, null, (String[]) null, FIELDS_AND_ID, "name COLLATE NOCASE", 0);
    	}
    	if (names == null)
    		return null;

    	final int L = names.size();
    	Person[] rv = new Person[L];
	try
	{
		for (int i = 0; i < L; ++i)
			rv[i] = new Person(db, names.elementAt(i));
	} catch (RDBKeyNotFoundException e) {
		return null;  // catch is req'd but won't happen; record came from db.
	}

	return rv;
    }

	/**
	 * Get the most recently entered Person (or driver) in the database.
	 * @param db  database connection
	 * @param driversOnly  Only drivers, not all Persons
	 * @return The most recent person or driver, by {@code _id}, or null if none
	 * @throws IllegalStateException if db not open
	 * @since 0.9.40
	 * @see #getAll(RDBAdapter, boolean)
	 */
	public static Person getMostRecent(RDBAdapter db, final boolean driversOnly)
		throws IllegalStateException
	{
		final String whereClause = (driversOnly) ? "is_driver=1 and is_active=1" : "is_active=1";
		final int pID = db.getRowIntField
			(TABNAME, "MAX(_id)", whereClause, (String[]) null, 0);
		if (pID == 0)
			return null;

		try {
			return new Person(db, pID);
		} catch (RDBKeyNotFoundException e) {
			return null;  // required by compiler, but we know the ID exists
		}
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

	initFields(rec);
    }

    /**
     * Create a new person, but don't yet write to the database.
     * When ready to write (after any changes you make to this object),
     * call {@link #insert(RDBAdapter)}.
     *<P>
     * {@link #getDateAdded()}'s field will be set to the current time using {@link System#currentTimeMillis()}.
     *
     * @param name  Person's name; cannot be null
     * @param isDriver  Driver, or only a friend or passenger?
     * @param contactURI  Contact info, or null
     * @param commment  Comment, or null
     * @throws IllegalArgumentException  if name is null
     */
    public Person(String name, boolean isDriver, String contactURI, String comment)
    {
    	this(name, isDriver, true, contactURI, comment);
    }

    /**
     * Create a new person, but don't yet write to the database.
     * When ready to write (after any changes you make to this object),
     * call {@link #insert(RDBAdapter)}.
     *<P>
     * {@link #getDateAdded()}'s field will be set to the current time using {@link System#currentTimeMillis()}.
     *
     * @param name  Person's name; cannot be null
     * @param isDriver  Driver, or only a friend or passenger?
     * @param isActive  Actively used?
     * @param contactURI  Contact info, or null
     * @param commment  Comment, or null
     * @throws IllegalArgumentException  if name is null
     */
    public Person(String name, boolean isDriver, boolean isActive, String contactURI, String comment)
    	throws IllegalArgumentException
    {
    	super();
    	if (name == null)
    		throw new IllegalArgumentException("null name");
    	this.name = name;
    	is_driver = isDriver;
    	is_active = isActive;
    	contact_uri = contactURI;
    	this.comment = comment;
	date_added = (int) (System.currentTimeMillis() / 1000L);
    }

	/**
	 * Existing record: Fill our obj fields from db-record string contents.
	 * @param db  connection
	 * @param rec, as returned by db.getRows({@link #FIELDS_AND_ID}); last element is _id
	 * @throws RDBKeyNotFoundException not thrown, but required due to super call
	 * @throws IllegalArgumentException if rec.length is too short
	 * @since 0.9.43
	 */
	private Person(RDBAdapter db, final String[] rec)
		throws RDBKeyNotFoundException, IllegalArgumentException
	{
		super(db, Integer.parseInt(rec[FIELDS.length]));
		initFields(rec);
	}

	/**
	 * Fill our obj fields from db-record string contents.
	 * {@code _id} is not filled here; the constructor has filled it already.
	 * @param rec, as returned by db.getRow({@link #FIELDS}) or db.getRows({@link #FIELDS_AND_ID})
	 * @throws IllegalArgumentException if rec.length is too short
	 * @since 0.9.43
	 */
	private void initFields(final String[] rec)
	{
		if (rec.length < 6)
			throw new IllegalArgumentException("length < 6: " + rec.length);
		is_driver = ("1".equals(rec[0]));
		name = rec[1];
		contact_uri = rec[2];
		is_active = ("1".equals(rec[3]));
		if (rec[4] != null)
			date_added = Integer.parseInt(rec[4]);
		comment = rec[5];
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

    public boolean isActive()
    {
		return is_active;
	}

    public void setActive(boolean isActive)
	{
		if (isActive == is_active)
			return;
		is_active = isActive;
		dirty = true;
	}

	/**
	 * Get the date this {@link Person} was added to the database, if known.
	 * @return  The date added, in unix format, or 0 if field is empty (null).
	 * @since 0.9.43
	 */
	public int getDateAdded()
	{
		return date_added;
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

	public String getComment()
	{
		return comment;
	}

	public void setComment(String comment)
	{
		this.comment = comment;
		dirty = true;
	}

	/**
     * Insert a new record with the current field values of this object.
	 * Clears dirty field; sets id and dbConn fields.
     * @return new record's primary key (_id)
     * @throws IllegalStateException if the insert fails
     */
    public int insert(RDBAdapter db)
        throws IllegalStateException
    {
	final String dateAdded_str = (date_added != 0) ? Integer.toString(date_added) : null;
    	String[] fv =
            { is_driver ? "1" : "0", name, contact_uri, is_active ? "1" : "0", dateAdded_str, comment };
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
	final String dateAdded_str = (date_added != 0) ? Integer.toString(date_added) : null;
    	String[] fv =
            { is_driver ? "1" : "0", name, contact_uri, is_active ? "1" : "0", dateAdded_str, comment };
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