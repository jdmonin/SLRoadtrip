/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2014 Jeremy D Monin <jdmonin@nand.net>
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

/**
 * Read the Settings db table: Settings which change frequently.
 * For some common settings, see static string fields here or see the schema.
 *<P>
 * Convenience methods: {@link #insertOrUpdate(RDBAdapter, String, int)},
 * {@link #insertOrUpdate(RDBAdapter, String, String)}.
 *<P>
 * If you restore the database from a backup, call {@link #clearSettingsCache()}
 * to remove cached references to the overwritten db's settings objects.
 *<P>
 * In version 0.9.40, some settings became per-vehicle {@link VehSettings}. In older versions they're all here.
 *
 * @see RDBSchema#checkSettings(RDBAdapter, int)
 * @see AppInfo
 * @author jdmonin
 */
public class Settings extends RDBRecord
{
	/**
	 * int setting for current {@link Vehicle} ID.
	 * @see #getCurrentVehicle(RDBAdapter, boolean)
	 */
	public static final String CURRENT_VEHICLE = "CURRENT_VEHICLE";

	/**
	 * boolean setting for requiring a Trip Category for each trip.
	 * Default is false.
	 * @since 0.9.12
	 */
	public static final String REQUIRE_TRIPCAT = "REQUIRE_TRIPCAT";

	/**
	 * int setting for showing trip odometers as delta
	 * (difference from the last stop).
	 * 0 = Normal (actual trip-odometer value); 1 = show Delta; 2 = show both Delta and Normal.
	 * Default is 0.
	 * @since 0.9.12
	 */
	public static final String LOGVIEW_ODO_TRIP_DELTA = "LOGVIEW_ODO_TRIP_DELTA";

	/**
	 * boolean setting to hide the Frequent Trip buttons on the main screen.
	 * Default is false.
	 * @since 0.9.12
	 */
	public static final String HIDE_FREQTRIP = "HIDE_FREQTRIP";

	/**
	 * boolean setting to hide the Via Route entry field.
	 * Default is false.
	 * @since 0.9.12
	 */
	public static final String HIDE_VIA = "HIDE_VIA";

	/**
	 * boolean setting to show the optional Trip Passengers entry field.
	 * Default is false.
	 * @since 0.9.20
	 */
	public static final String SHOW_TRIP_PAX = "SHOW_TRIP_PAX";

	private static final String TABNAME = "settings";
	private static final String KEYFIELD = "sname";
	private static final String VALFIELD_STR = "svalue";
	private static final String VALFIELD_INT = "ivalue";
	private static final String[] FIELDS = { KEYFIELD, VALFIELD_STR, VALFIELD_INT };
	private static final String[] VALFIELDS = { VALFIELD_STR, VALFIELD_INT };
	private static final String[] VALFIELDS_AND_ID = { VALFIELD_STR, VALFIELD_INT, "_id" };

	/** Setting name (key) */
    private String sfield;

    /** String value, or null */
    private String svalue;

    /** Int value, ignored unless svalue null */
    private int ivalue;

    /**
     * Check existence of a Setting from the database.
     * @param db  connection to use
     * @param settname field to retrieve
     * @return whether this setting exists in the database
     * @throws NullPointerException if db null
     */
    public static boolean exists(RDBAdapter db, final String settname)
    	throws NullPointerException
    {
		try
		{
			String[] fv = db.getRow(TABNAME, KEYFIELD, settname, VALFIELDS);
			return (fv != null);
		} catch (Exception e) {
			return false;
		}
    }

    /**
     * Create or update a Setting in the database.
     * If the Setting already exists with a string (not int) value,
     * that string value will be cleared to null.
     *
     * @param db  connection to use
     * @param settname field to create or update
     * @param ivalue  int value to set
     * @throws IllegalArgumentException if db null
     * @throws IllegalStateException if db not open
     * @see #setBoolean(RDBAdapter, String, boolean)
     */
    public static void insertOrUpdate(RDBAdapter db, final String settname, final int ivalue)
    	throws IllegalArgumentException, IllegalStateException
	{
    	insertOrUpdate(db, settname, ivalue, null);
	}

    /**
     * Create or update a Setting in the database.
     * If the Setting already exists with an int (not string) value,
     * that int value will be cleared to 0.
     *
     * @param db  connection to use
     * @param settname field to create or update
     * @param svalue  string value to set; "" will be stored as null.
     * @throws IllegalArgumentException if db null
     * @throws IllegalStateException if db not open
     * @see #setBoolean(RDBAdapter, String, boolean)
     */
    public static void insertOrUpdate(RDBAdapter db, final String settname, final String svalue)
    	throws IllegalArgumentException, IllegalStateException
	{
    	insertOrUpdate(db, settname, 0, svalue);
	}

    private static void insertOrUpdate(RDBAdapter db, final String settname, final int ivalue, String svalue)
		throws IllegalArgumentException, IllegalStateException
	{
		Settings s = null;

		if ((svalue != null) && (svalue.length() == 0))
			svalue = null;

		try {
			s = new Settings(db, settname);
		} catch (RDBKeyNotFoundException e) {
			// fall through, create it below
		}
		if (s == null)
		{
			s = new Settings(settname, svalue);
			s.ivalue = ivalue;
			s.insert(db);
		} else {
			s.svalue = svalue;
			s.ivalue = ivalue;
			s.dirty = true;
			s.commit();
		}
	}

    /**
     * Clear this setting's value, if it exists in the database.
     * If not, do nothing (don't create the setting).
     * The string value will become null, and the int value 0.
     *
     * @param db  connection to use
     * @param settname field to create or update
     * @throws IllegalArgumentException if db null
     * @throws IllegalStateException if db not open
     * @see #clearIfExists(RDBAdapter, String, int)
     */
    public static void clearIfExists(RDBAdapter db, final String settname)
    	throws IllegalArgumentException, IllegalStateException
    {
    	clearIfExists(db, settname, 0);
    }

    /**
     * Clear this setting's value, if it exists in the database.
     * If not, do nothing (don't create the setting).
     * The string value will become null, and the int value 0.
     *
     * @param db  connection to use
     * @param settname field to create or update
     * @throws IllegalArgumentException if db null
     * @throws IllegalStateException if db not open
     * @see #clearIfExists(RDBAdapter, String)
     */
    public static void clearIfExists(RDBAdapter db, final String settname, final int ivalue_clear)
    	throws IllegalArgumentException, IllegalStateException
	{
		Settings s = null;

		try {
			s = new Settings(db, settname);
		} catch (RDBKeyNotFoundException e) { }

		if (s == null)
			return;  // nothing to clear

		s.svalue = null;
		s.ivalue = ivalue_clear;
		s.dirty = true;
		s.commit();
	}

    /**
     * Get this boolean setting from the database.
     * Booleans are int settings with ivalue 1 or 0.
     * @param db  connection to use
     * @param settname   field to read
     * @param settdefault  default value if not found
     * @return  Setting's value (true if != 0), or <tt>settdefault</tt> if not found
     * @throws IllegalArgumentException  if db null
     * @throws IllegalStateException if db not open
     * @see #setBoolean(RDBAdapter, String, boolean)
     * @see #getInt(RDBAdapter, String, int)
     * @since 0.9.12
     */
    public static boolean getBoolean(RDBAdapter db, final String settname, final boolean settdefault)
    	throws IllegalArgumentException, IllegalStateException
    {
		return (0 != getInt(db, settname, (settdefault ? 1 : 0)));
    }

    /**
     * Set this boolean setting in the database.
     * Booleans are int settings with ivalue 1 or 0.
     * @param db  connection to use
     * @param settname   field to write
     * @param settvalue  value to write
     * @throws IllegalArgumentException  if db null
     * @throws IllegalStateException if db not open
     * @see #getBoolean(RDBAdapter, String, boolean)
     * @see #setInt(RDBAdapter, String, int)
     * @see #insertOrUpdate(RDBAdapter, String, int)
     * @since 0.9.12
     */
    public static void setBoolean(RDBAdapter db, final String settname, final boolean settvalue)
    	throws IllegalArgumentException, IllegalStateException
    {
    	setInt(db, settname, settvalue ? 1 : 0);
    }

    /**
     * Get this int setting from the database.
     * @param db  connection to use
     * @param settname   field to read
     * @param settdefault  default value if not found
     * @return  Setting's value, or <tt>settdefault</tt> if not found
     * @throws IllegalArgumentException  if db null
     * @throws IllegalStateException if db not open
     * @see #setInt(RDBAdapter, String, boolean)
     * @see #getBoolean(RDBAdapter, String, int)
     * @since 0.9.12
     */
    public static int getInt(RDBAdapter db, final String settname, final int settdefault)
    	throws IllegalArgumentException, IllegalStateException
    {
		Settings s = null;

		try {
			s = new Settings(db, settname);
		} catch (RDBKeyNotFoundException e) { }

		if (s == null)
			return settdefault;

		return s.ivalue;
    }

    /**
     * Set this int setting in the database.
     * @param db  connection to use
     * @param settname   field to write
     * @param ivalue  value to write
     * @throws IllegalArgumentException  if db null
     * @throws IllegalStateException if db not open
     * @see #getInt(RDBAdapter, String, int)
     * @see #setBoolean(RDBAdapter, String, boolean)
     * @see #insertOrUpdate(RDBAdapter, String, int)
     * @since 0.9.12
     */
    public static void setInt(RDBAdapter db, final String settname, final int ivalue)
    	throws IllegalArgumentException, IllegalStateException
    {
		Settings s = null;

		try {
			s = new Settings(db, settname);
		} catch (RDBKeyNotFoundException e) { }

		if (s == null)
		{
			s = new Settings(settname, ivalue);
			s.insert(db);
		}
		else if (ivalue != s.ivalue)
		{
			s.setIntValue(ivalue);
			s.commit();
		}
	}

    /**
     * Look up a Setting from the database.
     * @param db  db connection
     * @param settname field to retrieve
     * @throws IllegalArgumentException  if db null
     * @throws IllegalStateException if db not open
     * @throws RDBKeyNotFoundException if settname not found in database
     * @see #getBoolean(RDBAdapter, String, boolean)
     */
    public Settings(RDBAdapter db, String settname)
        throws IllegalStateException, IllegalArgumentException, RDBKeyNotFoundException
    {
    	super(db, -1);  // checks db != null
    	sfield = settname;
    	String[] fv = db.getRow(TABNAME, KEYFIELD, settname, VALFIELDS_AND_ID);
    	if (fv == null)
    		throw new RDBKeyNotFoundException(settname);

    	try {
			id = Integer.parseInt(fv[2]);
		} catch (NumberFormatException e) {}
    	svalue = fv[0];
    	if (fv[1] != null)
    	{
    		try {
    			ivalue = Integer.parseInt(fv[1]);
    		} catch (NumberFormatException e) {}
    	}
    }

    /**
     * Create a new string-valued Setting (not yet inserted to the database).
     * @param settname field to set; empty strings ("") are stored as null.
     * @param svalue    string value to set
     */
    public Settings(String settname, String svalue)
    {
    	super();
    	if ((svalue != null) && (svalue.length() == 0))
    		svalue = null;
    	sfield = settname;
    	this.svalue = svalue;
    	ivalue = 0;
    }

    /**
     * Create a new int-valued Setting (not yet inserted to the database).
     * @param settname field to set
     * @param ivalue    int value to set
     */
    public Settings(String settname, int ivalue)
    {
    	super();
    	sfield = settname;
    	svalue = null;
    	this.ivalue = ivalue;
    }

    /** name of the field */
    public String getField() { return sfield; }

    /** string value of the field, if set. Empty string values ("") are returned as null. */
    public String getStrValue()
    {
	if ((svalue != null) && (svalue.length() > 0))
	    return svalue;
	else
	    return null;
    }

    /** int value of the field, if set. */
    public int getIntValue() { return ivalue; }

    /**
     * Set the string value, clear the int value (unless <tt>s</tt> is null). Dirty the DB record.
     * @param s New string value, or null; "" is considered null.
     */
    public void setStrValue(String s)
    {
    	if ((s != null) && (s.length() == 0))
    		s = null;
    	svalue = s;
    	if (s != null)
    		ivalue = 0;
    	dirty = true;
	}

    /**
     * Set the int value, clear the string value (unless <tt>i</tt> is 0). Dirty the DB record.
     * @param i New int value, or 0
     */
    public void setIntValue(int i)
    {
    	if (ivalue != i)
    	{
    		ivalue = i;
    		if (i != 0)
    			svalue = null;
    		dirty = true;
    	}
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
    	String iv;
    	if (svalue != null)
    		iv = null;
    	else
    		iv = Integer.toString(ivalue);
    	String[] fv = { sfield, svalue, iv };
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
    	String iv;
    	if (svalue != null)
    		iv = null;
    	else
    		iv = Integer.toString(ivalue);
		String[] fv = { svalue, iv };
		dbConn.update(TABNAME, KEYFIELD, sfield, VALFIELDS, fv);
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

	/**
	 * --- CURRENT_ setting getters/setters ---
	 */

	/** cached record for {@link #getCurrentVehicle(RDBAdapter, boolean)} */
	private static Vehicle currentV = null;

	/**
	 * Clear cached settings records and associated objects (such
	 * as the Current {@link Vehicle}). Necessary after restoring from a backup.
	 * Also calls {@link VehSettings#clearSettingsCache()}.
	 */
	public static void clearSettingsCache()
	{
		currentV = null;
		VehSettings.clearSettingsCache();
	}

	/**
	 * For use with <tt>setCurrent*</tt>,
	 * validate that db == rec.dbConn.  If all is OK, do nothing,
	 * otherwise throw the exception.
	 * @param db  conn to use. If <tt>rec</tt> is new, can be null, otherwise must not be null
	 * @param rec  record to validate dbConn; must not be null
     * @throws IllegalArgumentException if <tt>rec.{@link RDBRecord#dbConn dbConn}</tt>
     *         isn't <tt>db</tt>; if rec's dbconn is <tt>null</tt>, this will be in the exception detail text.
	 */
	private static void matchDBOrThrow(RDBAdapter db, RDBRecord rec)
		throws IllegalArgumentException
	{
		if (db == rec.dbConn)
			return;
		if (rec.dbConn == null)
			throw new IllegalArgumentException("null dbConn in RDBRecord");
		else
			throw new IllegalArgumentException("Wrong dbConn in RDBRecord");
	}

	/**
	 * Get the Setting for {@link #CURRENT_VEHICLE} if set.
	 *<P>
	 * The record is cached after the first call, so if it changes,
	 * please call {@link #setCurrentVehicle(RDBAdapter, Vehicle)}.
	 *
	 * @param db  connection to use
	 * @param clearIfBad  If true, clear the setting to 0 if no record by its ID is found
	 * @return the Vehicle for <tt>CURRENT_VEHICLE</tt>, or null
	 * @throws IllegalStateException if the db is null or isn't open
	 */
	public static Vehicle getCurrentVehicle(RDBAdapter db, final boolean clearIfBad)
		throws IllegalStateException
	{
		if (db == null)
			throw new IllegalStateException("null db");

		if (currentV != null)
		{
			if (! currentV.dbConn.hasSameOwner(db))
				currentV.dbConn = db;
			return currentV;
		}

		Settings sCV = null;
		try
		{
			sCV = new Settings(db, Settings.CURRENT_VEHICLE);
			// Sub-try: cleanup in case the setting exists, but the record doesn't
			try {
				int id = sCV.getIntValue();
				if (id != 0)
					currentV = new Vehicle(db, id);
				else
					currentV = null;
			} catch (Throwable th) {
				currentV = null;
				if (clearIfBad)
					sCV.delete();
			}
		} catch (Throwable th) {
			return null;
		}
		return currentV;  // will be null if sCV not found
	}

	/**
	 * Store the Setting for {@link #CURRENT_VEHICLE}, or clear it to 0.  For DB consistency,
	 * it's better to call {@link VehSettings#changeCurrentVehicle(RDBAdapter, Vehicle, Vehicle)}
	 * instead of directly calling this method.
	 * @param db  connection to use
	 * @param ve  new vehicle, or null for none
     * @throws IllegalStateException if the db isn't open
     * @throws IllegalArgumentException if a non-null <tt>ve</tt>'s dbconn isn't db;
     *         if ve's dbconn is <tt>null</tt>, this will be in the exception detail text.
	 */
	public static void setCurrentVehicle(RDBAdapter db, Vehicle ve)
		throws IllegalStateException, IllegalArgumentException
	{
		if (ve != null)
			matchDBOrThrow(db, ve);

		currentV = ve;
		final int id = (ve != null) ? ve.id : 0;
		insertOrUpdate(db, CURRENT_VEHICLE, id);
	}

}  // public class Settings
