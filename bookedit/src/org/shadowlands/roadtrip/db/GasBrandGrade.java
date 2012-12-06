/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2012 Jeremy D Monin <jdmonin@nand.net>
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
 * a GasBrandGrade, a brand or grade of fuel for the vehicles.
 *
 * @author jdmonin
 * @since 0.9.06
 */
public class GasBrandGrade extends RDBRecord
{
	private static final String TABNAME = "gas_brandgrade";
	private static final String VALFIELD = "name";
	private static final String VALFIELD_SORT = "name COLLATE NOCASE";  // syntax may be sqlite-specific
	private static final String[] FIELDS = { VALFIELD };
	private static final String[] FIELDS_AND_ID = { VALFIELD, "_id" };

	private String name;

    /**
     * Get the GasBrandGrades currently in the database.
     * @param db  database connection
     * @return an array of GasBrandGrade objects from the database, ordered by name, or null if none.
     *   <br><b>NOTE:</b> This array may end with a null value if <tt>exceptID</tt> is used.
     */
    public static GasBrandGrade[] getAll(RDBAdapter db)
    {
		Vector<String[]> gbg = db.getRows(TABNAME, null, (String[]) null, FIELDS_AND_ID, VALFIELD_SORT, 0);
    	if (gbg == null)
    		return null;

    	GasBrandGrade[] rv = new GasBrandGrade[gbg.size()];
		try {
	    	for (int i = rv.length - 1; i >= 0; --i)
				rv[i] = new GasBrandGrade(db, gbg.elementAt(i));
	    	return rv;
		} catch (RDBKeyNotFoundException e) {
			return null;  // catch is req'd but won't happen; record came from db.
		}
    }

    /**
     * Look up a GasBrandGrade from the database.
     * @param db  db connection
     * @param id  id field
     * @throws IllegalStateException if db not open
     * @throws RDBKeyNotFoundException if cannot retrieve this ID
     */
    public GasBrandGrade(RDBAdapter db, final int id)
        throws IllegalStateException, RDBKeyNotFoundException
    {
    	super(db, id);
    	String[] rec = db.getRow(TABNAME, id, FIELDS);
    	if (rec == null)
    		throw new RDBKeyNotFoundException(id);
    	name = rec[0];
    }

    /**
     * Create a new GasBrandGrade (not yet inserted to the database).
     * @param name  name; not null
     * @throws IllegalArgumentException if name is null
     */
    public GasBrandGrade(String name)
    	throws NullPointerException 
    {
    	super();
    	setName(name);
    }

    /**
     * Used for {@link #getAll(RDBAdapter, int)}.
     * @param db  db connection
     * @param fieldsAndID  record fields, in same order as {@link #FIELDS_AND_ID}
     * @throws RDBKeyNotFoundException  not thrown, but required by super
     */
    private GasBrandGrade(RDBAdapter db, String[] fieldsAndID)
    	throws RDBKeyNotFoundException
    {
    	super(db, Integer.parseInt(fieldsAndID[FIELDS.length]));
    	name = fieldsAndID[0];
    }

    public String getName() { return name; }

    /**
     * Change this GasBrandGrade's name.
     * @param newName  new name; not null
     * @throws IllegalArgumentException if newName is null
     */
    public void setName(String newName)
    	throws IllegalArgumentException
    {
    	if (newName == null)
    		throw new IllegalArgumentException("null name");
    	name = newName;
    	dirty = true;
    }

    /**
     * Insert a new record with the current name.
	 * Clears dirty field; sets id and dbConn fields.
     * @param db  db connection
     * @return new record's primary key (_id)
     * @throws IllegalStateException if the insert fails (db closed, etc)
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
     * @throws IllegalStateException if the update fails (db closed, etc)
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

	/** brand/grade name */
	public String toString() {
		return name;
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
		// TODO check if unused in other tables
		throw new UnsupportedOperationException();
		/*
		dbConn.delete(TABNAME, id);
		deleteCleanup();
		*/
	}

}  // public class GasBrandGrade