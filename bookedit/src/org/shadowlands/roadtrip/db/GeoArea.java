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
 * In-memory representation, and database access for,
 * a GeoArea, a geographic area in which vehicles operate.
 *
 * @author jdmonin
 */
public class GeoArea extends RDBRecord
{
	private static final String TABNAME = "geoarea";
	private static final String VALFIELD = "aname";
	private static final String VALFIELD_SORT = "aname COLLATE NOCASE";  // syntax may be sqlite-specific
	private static final String[] FIELDS = { VALFIELD };
	private static final String[] FIELDS_AND_ID = { VALFIELD, "_id" };

	/**
	 * Placeholder for "(none)" UI entry in {@link #getAll(RDBAdapter, boolean)},
	 * to allow for locations outside of any known geoarea.
	 *<P>
	 * <B>I18N:</B> To localize this and any other static text,
	 * call {@link RDBRecord#localizeStatics(String, String)}.
	 *<P>
	 * Before v0.9.50, this field was in {@code android.SpinnerDataFactory}.
	 */
	public static final GeoArea GEOAREA_NONE;
	static {
		GEOAREA_NONE = new GeoArea("(none)");
		GEOAREA_NONE.id = 0;
	}

	private String aname;

	/**
	 * Get the GeoAreas currently in the database, optionally beginning with the {@link #GEOAREA_NONE} placeholder.
	 * @param db  database connection
	 * @param withAreaNone
	 * @return an array of GeoArea objects from the database, ordered by name, or null if none.
	 *     If {@code withAreaNone} is true, {@link #GEOAREA_NONE} is returned at [0].
	 *     Even if {@code withAreaNone} is true, null is returned if there are no GeoAreas in the db.
	 * @since 0.9.50
	 * @see #getAll(RDBAdapter, int)
	 */
	public static GeoArea[] getAll(RDBAdapter db, final boolean withAreaNone)
	{
		return getAll(db, -1, withAreaNone);
	}

    /**
     * Get the GeoAreas currently in the database.
     * @param db  database connection
     * @param exceptID  Exclude this area; -1 to return all areas
     * @return an array of GeoArea objects from the database, ordered by name, or null if none.
     *   <br><b>NOTE:</b> This array may end with a null value if <tt>exceptID</tt> is used.
     * @see #getAll(RDBAdapter, boolean)
     */
    public static GeoArea[] getAll(RDBAdapter db, final int exceptID)
    {
	return getAll(db, exceptID, false);
    }

    private static GeoArea[] getAll(RDBAdapter db, final int exceptID, final boolean withAreaNone)
    {
    	final Vector<String[]> geos;
    	if (exceptID != -1)
    	{
    		String kf, kv;
    		kf = "_id<>";
    		kv = Integer.toString(exceptID);
    		geos = db.getRows(TABNAME, kf, kv, FIELDS_AND_ID, VALFIELD_SORT, 0);
    	} else {
    		geos = db.getRows(TABNAME, null, (String[]) null, FIELDS_AND_ID, VALFIELD_SORT, 0);
    	}
    	if (geos == null)
    		return null;

    	final int L = geos.size();
    	final int noneIncr = (withAreaNone) ? 1 : 0;
    	GeoArea[] rv = new GeoArea[L + noneIncr];
		try {
	    	for (int i = L - 1; i >= 0; --i)
	    		rv[i + noneIncr] = new GeoArea(db, geos.elementAt(i));
	    	if (withAreaNone)
	    		rv[0] = GEOAREA_NONE;

	    	return rv;
		} catch (RDBKeyNotFoundException e) {
			return null;  // constructor won't throw it; throws decl is required by its super
		}
    }

    /**
     * Look up a GeoArea from the database.
     * @param db  db connection
     * @param id  id field
     * @throws IllegalStateException if db not open
     * @throws RDBKeyNotFoundException if cannot retrieve this ID
     */
    public GeoArea(RDBAdapter db, final int id)
        throws IllegalStateException, RDBKeyNotFoundException
    {
    	super(db, id);
    	String[] rec = db.getRow(TABNAME, id, FIELDS);
    	if (rec == null)
    		throw new RDBKeyNotFoundException(id);
    	aname = rec[0];
    }

    /**
     * Create a new GeoArea (not yet inserted to the database).
     * @param areaname  name; not null
     * @throws IllegalArgumentException if areaname is null
     */
    public GeoArea(String areaname)
    	throws NullPointerException 
    {
    	super();
    	setName(areaname);
    }

    /**
     * Used for {@link #getAll(RDBAdapter, int)}.
     * @param db  db connection
     * @param fieldsAndID  record fields, in same order as {@link #FIELDS_AND_ID}
     * @throws RDBKeyNotFoundException  not thrown, but required by super
     * @throws NumberFormatException  if id field contents isn't an integer
     */
    private GeoArea(RDBAdapter db, String[] fieldsAndID)
    	throws RDBKeyNotFoundException
    {
    	super(db, Integer.parseInt(fieldsAndID[FIELDS.length]));
    	aname = fieldsAndID[0];
    }

    public String getName() { return aname; }

    /**
     * Change this GeoArea's name.
     * @param areaname  new name; not null
     * @throws IllegalArgumentException if areaname is null
     */
    public void setName(String areaname)
    	throws IllegalArgumentException
    {
    	if (areaname == null)
    		throw new IllegalArgumentException("null areaname");
    	aname = areaname;
    	dirty = true;
    }

    /**
     * Insert a new record with the current areaname.
	 * Clears dirty field; sets id and dbConn fields.
     * @param db  db connection
     * @return new record's primary key (_id)
     * @throws IllegalStateException if the insert fails (db closed, etc)
     */
    public int insert(RDBAdapter db)
        throws IllegalStateException
    {
    	String[] fv = { aname };
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
    	String[] fv = { aname };
		dbConn.update(TABNAME, id, FIELDS, fv);
		dirty = false;
	}

	/** area name */
	public String toString() {
		return aname;
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

}  // public class GeoArea