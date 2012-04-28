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
 * Read the Settings db table: Settings which change frequently.
 * For some common settings, see the schema.
 *<P>
 * Convenience methods: {@link #insertOrUpdate(RDBAdapter, String, int)},
 *  {@link #insertOrUpdate(RDBAdapter, String, String)}.
 *<P>
 * If you restore the database from a backup, call {@link #clearSettingsCache()}
 * to remove cached references to the overwritten db's settings objects.
 *
 * @see RDBSchema#checkSettings(RDBAdapter, int, boolean)
 * @see AppInfo
 * @author jdmonin
 */
public class Settings extends RDBRecord
{
	/**
	 * int setting for current {@link GeoArea} area ID.
	 */
	public static final String CURRENT_AREA = "CURRENT_AREA";

	/**
	 * int setting for current driver ID (in {@link Person} table).
	 * @see #getCurrentDriver(RDBAdapter, boolean)
	 */
	public static final String CURRENT_DRIVER = "CURRENT_DRIVER";

	/**
	 * int setting for current {@link Vehicle} ID.
	 * @see #getCurrentVehicle(RDBAdapter, boolean)
	 */
	public static final String CURRENT_VEHICLE = "CURRENT_VEHICLE";

	/**
	 * int setting for current {@link Trip} ID, if any.
	 * @see #getCurrentTrip(RDBAdapter, boolean)
	 */
	public static final String CURRENT_TRIP = "CURRENT_TRIP";

	/**
	 * int setting for current {@link TStop} ID, if any.
	 * @see #getCurrentTStop(RDBAdapter, boolean)
	 */
	public static final String CURRENT_TSTOP = "CURRENT_TSTOP";

	/**
	 * int setting for previous {@link Location} ID, if any.
	 * @since 0.8.13
	 */
	public static final String PREV_LOCATION = "PREV_LOCATION";

	/**
	 * int setting for current {@link FreqTrip} ID, if any.
	 * Should be set only when {@link #CURRENT_TRIP} is set.
	 * @see #getCurrentFreqTrip(RDBAdapter, boolean)
	 * @see #setCurrentFreqTrip(RDBAdapter, FreqTrip)
	 * @since 0.9.00
	 */
	public static final String CURRENT_FREQTRIP = "CURRENT_FREQTRIP";

	/**
	 * string setting for unused TStops in the current {@link FreqTrip}, if any,
	 * as a comma-delimited list of IDs into {@link FreqTripTSTop}.
	 * Should not be set if {@link #CURRENT_TRIP} isn't currently set.
	 * @see #setCurrentFreqTrip(RDBAdapter, FreqTrip)
	 * @see #reduceCurrentFreqTripTStops(RDBAdapter, FreqTripTStop)
	 * @since 0.9.00
	 */
	public static final String CURRENT_FREQTRIP_TSTOPLIST = "CURRENT_FREQTRIP_TSTOPLIST";

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
     */
    public static boolean exists(RDBAdapter db, final String settname)
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
     * @throws IllegalStateException if db not open
     * @see #setBoolean(RDBAdapter, String, boolean)
     */
    public static void insertOrUpdate(RDBAdapter db, final String settname, final int ivalue)
    	throws IllegalStateException
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
     * @throws IllegalStateException if db not open
     * @see #setBoolean(RDBAdapter, String, boolean)
     */
    public static void insertOrUpdate(RDBAdapter db, final String settname, final String svalue)
    	throws IllegalStateException
	{
    	insertOrUpdate(db, settname, 0, svalue);
	}

    private static void insertOrUpdate(RDBAdapter db, final String settname, final int ivalue, String svalue)
		throws IllegalStateException
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
     * @throws IllegalStateException if db not open
     * @see #clearIfExists(RDBAdapter, String, int)
     */
    public static void clearIfExists(RDBAdapter db, final String settname)
    	throws IllegalStateException
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
     * @throws IllegalStateException if db not open
     * @see #clearIfExists(RDBAdapter, String)
     */
    public static void clearIfExists(RDBAdapter db, final String settname, final int ivalue_clear)
    	throws IllegalStateException
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
     * @see #setBoolean(RDBAdapter, String, boolean)
     * @see #getInt(RDBAdapter, String, int)
     * @since 0.9.12
     */
    public static boolean getBoolean(RDBAdapter db, final String settname, final boolean settdefault)
    {
		return (0 != getInt(db, settname, (settdefault ? 1 : 0)));
    }

    /**
     * Set this boolean setting in the database.
     * Booleans are int settings with ivalue 1 or 0.
     * @param db  connection to use
     * @param settname   field to write
     * @param settvalue  value to write
     * @see #getBoolean(RDBAdapter, String, boolean)
     * @see #setInt(RDBAdapter, String, int)
     * @see #insertOrUpdate(RDBAdapter, String, int)
     * @since 0.9.12
     */
    public static void setBoolean(RDBAdapter db, final String settname, final boolean settvalue)
    {
    	setInt(db, settname, settvalue ? 1 : 0);
    }

    /**
     * Get this int setting from the database.
     * @param db  connection to use
     * @param settname   field to read
     * @param settdefault  default value if not found
     * @return  Setting's value, or <tt>settdefault</tt> if not found
     * @see #setInt(RDBAdapter, String, boolean)
     * @see #getBoolean(RDBAdapter, String, int)
     * @since 0.9.12
     */
    public static int getInt(RDBAdapter db, final String settname, final int settdefault)
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
     * @see #getInt(RDBAdapter, String, int)
     * @see #setBoolean(RDBAdapter, String, boolean)
     * @see #insertOrUpdate(RDBAdapter, String, int)
     * @since 0.9.12
     */
    public static void setInt(RDBAdapter db, final String settname, final int ivalue)
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
     * @throws IllegalStateException if db not open
     * @throws RDBKeyNotFoundException if settname not found in database
     * @see #getBoolean(RDBAdapter, String, boolean)
     */
    public Settings(RDBAdapter db, String settname)
        throws IllegalStateException, RDBKeyNotFoundException
    {
    	super(db, -1);
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

	/** cached record for {@link #getCurrentArea(RDBAdapter, boolean)} */
	private static GeoArea currentA = null;

	/** cached record for {@link #getCurrentDriver(RDBAdapter, boolean)} */
	private static Person currentD = null;

	/** cached record for {@link #getCurrentVehicle(RDBAdapter, boolean)} */
	private static Vehicle currentV = null;

	/** cached record for {@link #getCurrentTrip(RDBAdapter, boolean)} */
	private static Trip currentT = null;

	/** cached record for {@link #getCurrentFreqTrip(RDBAdapter, boolean)} */
	private static FreqTrip currentFT = null;

	/** cached record for {@link #getCurrentFreqTripTStops(RDBdapter)}; length enver 0, is null in that case. */
	private static Vector<FreqTripTStop> currentFTS = null;

	/** cached record for {@link #getCurrentTStop(RDBAdapter, boolean)} */
	private static TStop currentTS = null;

	/** cached record for {@link #getPreviousLocation(RDBAdapter, boolean)} */
	private static Location prevL = null;

	/**
	 * Clear cached settings records and associated objects (such
	 * as the Current {@link Vehicle}). Necessary after restoring from a backup.
	 */
	public static void clearSettingsCache()
	{
		currentA = null;
		currentD = null;
		currentV = null;
		currentT = null;
		currentFT = null;
		currentFTS = null;
		currentTS = null;
		prevL = null;
	}

	/**
	 * For use with <tt>setCurrent*</tt>,
	 * validate that db == rec.dbConn.  If all is OK, do nothing,
	 * otherwise throw the exception.
	 * @param db  conn to use
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
	 * Get the Setting for {@link #CURRENT_AREA} if set.
	 *<P>
	 * The record is cached after the first call, so if it changes,
	 * please call {@link #setCurrentArea(RDBAdapter, GeoArea)}.
	 *
	 * @param db  connection to use
	 * @param clearIfBad  If true, clear the setting to 0 if no record by its ID is found
	 * @return the GeoArea for <tt>CURRENT_AREA</tt>, or null
     * @throws IllegalStateException if the db isn't open
	 */
	public static GeoArea getCurrentArea(RDBAdapter db, final boolean clearIfBad)
		throws IllegalStateException
	{
		if (currentA != null)
		{
			if (! currentA.dbConn.hasSameOwner(db))
				currentA.dbConn = db;
			return currentA;
		}

		Settings sCA = null;
		try
		{
			sCA = new Settings(db, Settings.CURRENT_AREA);
			// Sub-try: cleanup in case the setting exists, but the record doesn't
			try {
				int id = sCA.getIntValue();
				if (id != 0)
					currentA = new GeoArea(db, id);
			} catch (Throwable th) {
				if (clearIfBad)
					sCA.delete();
			}
		} catch (Throwable th) {
			return null;
		}
		return currentA;  // will be null if sCA not found
	}

	/**
	 * Store the Setting for {@link #CURRENT_AREA}, or clear it to 0.
	 * @param db  connection to use
	 * @param a  new area, or null for none
     * @throws IllegalStateException if the db isn't open
     * @throws IllegalArgumentException if a non-null <tt>a</tt>'s dbconn isn't db;
     *         if a's dbconn is <tt>null</tt>, this will be in the exception detail text.
	 */
	public static void setCurrentArea(RDBAdapter db, GeoArea a)
		throws IllegalStateException, IllegalArgumentException
	{
		if (a != null)
			matchDBOrThrow(db, a);
		currentA = a;
		final int id = (a != null) ? a.id : 0;
		insertOrUpdate(db, CURRENT_AREA, id);
	}

	/**
	 * Get the Setting for {@link #CURRENT_DRIVER} if set.
	 *<P>
	 * The record is cached after the first call, so if it changes,
	 * please call {@link #setCurrentDriver(RDBAdapter, Person)}.
	 *
	 * @param db  connection to use
	 * @param clearIfBad  If true, clear the setting to 0 if no record by its ID is found
	 * @return the Person for <tt>CURRENT_DRIVER</tt>, or null
     * @throws IllegalStateException if the db isn't open
	 */
	public static Person getCurrentDriver(RDBAdapter db, final boolean clearIfBad)
		throws IllegalStateException
	{
		if (currentD != null)
		{
			if (! currentD.dbConn.hasSameOwner(db))
				currentD.dbConn = db;
			return currentD;
		}

		Settings sCD = null;
		try
		{
			sCD = new Settings(db, Settings.CURRENT_DRIVER);
			// Sub-try: cleanup in case the setting exists, but the record doesn't
			try {
				int id = sCD.getIntValue();
				if (id != 0)
					currentD = new Person(db, id);
			} catch (Throwable th) {
				if (clearIfBad)
					sCD.delete();
			}
		} catch (Throwable th) {
			return null;
		}
		return currentD;  // will be null if sCD not found
	}

	/**
	 * Store the Setting for {@link #CURRENT_DRIVER}, or clear it to 0.
	 * @param db  connection to use
	 * @param dr  new driver, or null for none
     * @throws IllegalStateException if the db isn't open
     * @throws IllegalArgumentException if a non-null <tt>dr</tt>'s dbconn isn't db;
     *         if dr's dbconn is <tt>null</tt>, this will be in the exception detail text.
	 */
	public static void setCurrentDriver(RDBAdapter db, Person dr)
		throws IllegalStateException, IllegalArgumentException
	{
		if (dr != null)
			matchDBOrThrow(db, dr);
		currentD = dr;
		final int id = (dr != null) ? dr.id : 0;
		insertOrUpdate(db, CURRENT_DRIVER, id);
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
     * @throws IllegalStateException if the db isn't open
	 */
	public static Vehicle getCurrentVehicle(RDBAdapter db, final boolean clearIfBad)
		throws IllegalStateException
	{
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
			} catch (Throwable th) {
				if (clearIfBad)
					sCV.delete();
			}
		} catch (Throwable th) {
			return null;
		}
		return currentV;  // will be null if sCV not found
	}

	/**
	 * Store the Setting for {@link #CURRENT_VEHICLE}, or clear it to 0.
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

	/**
	 * Get the Setting for {@link #CURRENT_TRIP} if set.
	 *<P>
	 * The record is cached after the first call, so if it changes,
	 * please call {@link #setCurrentTrip(RDBAdapter, Trip)}.
	 *
	 * @param db  connection to use
	 * @param clearIfBad  If true, clear the setting to 0 if no record by its ID is found
	 * @return the Trip for <tt>CURRENT_TRIP</tt>, or null
     * @throws IllegalStateException if the db isn't open
	 */
	public static Trip getCurrentTrip(RDBAdapter db, final boolean clearIfBad)
		throws IllegalStateException
	{
		if (currentT != null)
		{
			if (! currentT.dbConn.hasSameOwner(db))
				currentT.dbConn = db;
			return currentT;
		}

		Settings sCT = null;
		try
		{
			sCT = new Settings(db, Settings.CURRENT_TRIP);
			// Sub-try: cleanup in case the setting exists, but the record doesn't
			try {
				int id = sCT.getIntValue();
				if (id != 0)
					currentT = new Trip(db, id);
			} catch (Throwable th) {
				if (clearIfBad)
					sCT.delete();
			}
		} catch (Throwable th) {
			return null;
		}
		return currentT;  // will be null if sCT not found
	}

	/**
	 * Store the Setting for {@link #CURRENT_TRIP}, or clear it to 0.
	 * @param db  connection to use
	 * @param tr  new trip, or null for none
     * @throws IllegalStateException if the db isn't open
     * @throws IllegalArgumentException if a non-null <tt>tr</tt>'s dbconn isn't db;
     *         if tr's dbconn is <tt>null</tt>, this will be in the exception detail text.
	 */
	public static void setCurrentTrip(RDBAdapter db, Trip tr)
		throws IllegalStateException, IllegalArgumentException
	{
		if (tr != null)
			matchDBOrThrow(db, tr);
		currentT = tr;
		final int id = (tr != null) ? tr.id : 0;
		insertOrUpdate(db, CURRENT_TRIP, id);
	}

	/**
	 * Get the Setting for {@link #CURRENT_FREQTRIP} if set.
	 *<P>
	 * The record is cached after the first call, so if it changes,
	 * please call {@link #setCurrentFreqTrip(RDBAdapter, Trip)}.
	 *
	 * @param db  connection to use
	 * @param clearIfBad  If true, clear the setting to 0 if no record by its ID is found
	 * @return the FreqTrip for <tt>CURRENT_FREQTRIP</tt>, or null
     * @throws IllegalStateException if the db isn't open
     *
     * @see #getCurrentFreqTripTStops(RDBAdapter, boolean)
	 */
	public static FreqTrip getCurrentFreqTrip(RDBAdapter db, final boolean clearIfBad)
		throws IllegalStateException
	{
		if (currentFT != null)
		{
			if (! currentFT.dbConn.hasSameOwner(db))
				currentFT.dbConn = db;
			return currentFT;
		}

		Settings sCT = null;
		try
		{
			sCT = new Settings(db, Settings.CURRENT_FREQTRIP);
			// Sub-try: cleanup in case the setting exists, but the record doesn't
			try {
				int id = sCT.getIntValue();
				if (id != 0)
					currentFT = new FreqTrip(db, id);
			} catch (Throwable th) {
				if (clearIfBad)
					sCT.delete();
			}
		} catch (Throwable th) {
			return null;
		}
		return currentFT;
	}

	/**
	 * Store the Setting for {@link #CURRENT_FREQTRIP}, or clear it to 0.
	 * Will also set or clear {@link #CURRENT_FREQTRIP_TSTOPLIST}, based on <tt>ft</tt>
	 * == <tt>null</tt> or on {@link FreqTrip#readAllTStops()}.
	 * @param db  connection to use
	 * @param ft  new freqtrip, or null for none
     * @throws IllegalStateException if the db isn't open
     * @throws IllegalArgumentException if a non-null <tt>ft</tt>'s dbconn isn't db;
     *         if ft's dbconn is <tt>null</tt>, this will be in the exception detail text.
     * @see #reduceCurrentFreqTripTStops(RDBAdapter, FreqTripTStop)
	 */
	public static void setCurrentFreqTrip(RDBAdapter db, FreqTrip ft)
		throws IllegalStateException, IllegalArgumentException
	{
		if (ft != null)
			matchDBOrThrow(db, ft);
		currentFT = ft;
		final int id = (ft != null) ? ft.id : 0;
		insertOrUpdate(db, CURRENT_FREQTRIP, id);
		if (ft == null)
		{
			currentFTS = null;
			insertOrUpdate(db, CURRENT_FREQTRIP_TSTOPLIST, null);
		} else {
			Vector<FreqTripTStop> allStops = ft.readAllTStops();
			if (allStops == null)
			{
				currentFTS = null;
				insertOrUpdate(db, CURRENT_FREQTRIP_TSTOPLIST, null);
				return;
			}
			currentFTS = allStops;
			insertOrUpdateCurrentFreqTripTStops(db);
		}
	}

	/**
	 * Write {@link #currentFTS}, as a string of IDs, to {@link #CURRENT_FREQTRIP_TSTOPLIST}.
	 * @param db  connection to use
	 */
	private static void insertOrUpdateCurrentFreqTripTStops(RDBAdapter db)
	{
		if (currentFTS == null)
			return;  // shouldn't happen
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < currentFTS.size(); ++i)
		{
			if (i > 0)
				sb.append(",");
			sb.append(currentFTS.elementAt(i).getID());
		}
		insertOrUpdate(db, CURRENT_FREQTRIP_TSTOPLIST, sb.toString());
	}

	/**
	 * Get the objects listed in the Setting for {@link #CURRENT_FREQTRIP_TSTOPLIST}, if set.
	 * This is set only when {@link #CURRENT_FREQTRIP} is set, and when
	 * that {@link FreqTrip} contains {@link FreqTripTStop}s.
	 *<P>
	 * If you only want to see whether this setting is active or not,
	 * and don't need to read the objects from the database:
	 * Call {@link #getCurrentFreqTrip(RDBAdapter, boolean) getCurrentFreqTrip},
	 * then {@link #Settings(RDBAdapter, String) new Settings(db, CURRENT_FREQTRIP_TSTOPLIST)}
	 * and then {@link #getStrValue()}.
	 *<P>
	 * The record is cached after the first call, so if it changes,
	 * please call {@link #setCurrentFreqTrip(RDBAdapter, Trip)} to update
	 * both the current freqtrip and the tstop list from the database,
	 * or call {@link #reduceCurrentFreqTripTStops(RDBAdapter, FreqTripTStop)}.
	 *
	 * @param db  connection to use
	 * @param clearIfBad  If true, clear the setting to null if no record by its ID is found
	 * @return the FreqTripTStops for <tt>CURRENT_FREQTRIP_TSTOPLIST</tt>, or null;
	 *            will never be 0-length, will return null in that case.
     * @throws IllegalStateException if the db isn't open
     *
     * @see #getCurrentFreqTrip(RDBAdapter, boolean)
	 */
	public static Vector<FreqTripTStop> getCurrentFreqTripTStops(RDBAdapter db, final boolean clearIfBad)
	{
		if (currentFTS != null)
		{
			if ((currentFTS.size() > 0) && ! currentFTS.firstElement().dbConn.hasSameOwner(db))
			{
				for (int i = currentFTS.size() - 1; i >= 0; --i)
					currentFTS.elementAt(i).dbConn = db;
			}
			return currentFTS;
		}

		try
		{
			Settings sFSL = new Settings(db, Settings.CURRENT_FREQTRIP_TSTOPLIST);
			// Sub-try: to cleanup in case the setting exists, but a record doesn't.
			// We'll read each freq stop with an ID in the list.
			try {
				final String slist = sFSL.getStrValue();
				if (slist == null)
					return null;
				final String[] ids = slist.split(",");
				Vector<FreqTripTStop> allStops = new Vector<FreqTripTStop>(ids.length);
				for (int i = 0; i < ids.length; ++i)
					allStops.addElement(new FreqTripTStop(db, Integer.parseInt(ids[i])));
				currentFTS = allStops;
			} catch (Throwable th) {
				if (clearIfBad)
					sFSL.delete();
			}
		} catch (Throwable th) {
			return null;
		}
		return currentFTS;
	}

	/**
	 * If {@link #CURRENT_FREQTRIP_TSTOPLIST} contains the given frequent stop,
	 * remove it from the list, and update the list in the database.
	 *
	 * @param db  connection to use
	 * @param removeStop  Remove this stop's ID from the list
     * @throws IllegalStateException if the db isn't open
	 */
	public static void reduceCurrentFreqTripTStops(RDBAdapter db, final FreqTripTStop removeStop)
		throws IllegalStateException
	{
		if (removeStop == null)
			return;  // shouldn't happen
		if (currentFTS == null)
		{
			getCurrentFreqTripTStops(db, false);
			if (currentFTS == null)
				return;
		}

		final int ftsID = removeStop.getID();
		boolean found = false;
		for (int i = currentFTS.size() - 1; i >= 0; --i)
		{
			FreqTripTStop s = currentFTS.elementAt(i);
			if (ftsID == s.getID())
			{
				found = true;
				currentFTS.removeElementAt(i);			
				break;
			}
		}
		if (found)
		{
			if (currentFTS.isEmpty())
			{
				currentFTS = null;
				insertOrUpdate(db, CURRENT_FREQTRIP_TSTOPLIST, null);
			} else {
				insertOrUpdateCurrentFreqTripTStops(db);
			}
		}
	}

	/**
	 * Get the Setting for {@link #CURRENT_TSTOP} if set.
	 *<P>
	 * The record is cached after the first call, so if it changes,
	 * please call {@link #setCurrentTStop(RDBAdapter, TStop)}.
	 *
	 * @param db  connection to use
	 * @param clearIfBad  If true, clear the setting to 0 if no record by its ID is found
	 * @return the TStop for <tt>CURRENT_TSTOP</tt>, or null
     * @throws IllegalStateException if the db isn't open
	 */
	public static TStop getCurrentTStop(RDBAdapter db, final boolean clearIfBad)
		throws IllegalStateException
	{
		if (currentTS != null)
		{
			if (! currentTS.dbConn.hasSameOwner(db))
				currentTS.dbConn = db;
			return currentTS;
		}

		Settings sCT = null;
		try
		{
			sCT = new Settings(db, Settings.CURRENT_TSTOP);
			// Sub-try: cleanup in case the setting exists, but the record doesn't
			try {
				int id = sCT.getIntValue();
				if (id != 0)
					currentTS = new TStop(db, id);
			} catch (Throwable th) {
				if (clearIfBad)
					sCT.delete();
			}
		} catch (Throwable th) {
			return null;
		}
		return currentTS;  // will be null if sCT not found
	}

	/**
	 * Store the Setting for {@link #CURRENT_TSTOP}, or clear it to 0.
	 * @param db  connection to use
	 * @param ts  new tstop, or null for none
     * @throws IllegalStateException if the db isn't open
     * @throws IllegalArgumentException if a non-null <tt>ts</tt>'s dbconn isn't db;
     *         if ts's dbconn is <tt>null</tt>, this will be in the exception detail text.
	 */
	public static void setCurrentTStop(RDBAdapter db, TStop ts)
		throws IllegalStateException, IllegalArgumentException
	{
		if (ts != null)
			matchDBOrThrow(db, ts);
		currentTS = ts;
		final int id = (ts != null) ? ts.id : 0;
		insertOrUpdate(db, CURRENT_TSTOP, id);
	}

	/**
	 * Get the Setting for {@link #PREV_LOCATION} if set.
	 *<P>
	 * The record is cached after the first call, so if it changes,
	 * please call {@link #setPreviousLocation(RDBAdapter, Location)}.
	 *
	 * @param db  connection to use
	 * @param clearIfBad  If true, clear the setting to 0 if no record by its ID is found
	 * @return the TStop for <tt>PREV_LOCATION</tt>, or null
     * @throws IllegalStateException if the db isn't open
	 */
	public static Location getPreviousLocation(RDBAdapter db, final boolean clearIfBad)
		throws IllegalStateException
	{
		if (prevL != null)
		{
			if (! prevL.dbConn.hasSameOwner(db))
				prevL.dbConn = db;
			return prevL;
		}

		Settings sPL = null;
		try
		{
			sPL = new Settings(db, Settings.PREV_LOCATION);
			// Sub-try: cleanup in case the setting exists, but the record doesn't
			try {
				int id = sPL.getIntValue();
				if (id != 0)
					prevL = new Location(db, id);
			} catch (Throwable th) {
				if (clearIfBad)
					sPL.delete();
			}
		} catch (Throwable th) {
			return null;
		}
		return prevL;  // will be null if sPL not found
	}

	/**
	 * Store the Setting for {@link #PREV_LOCATION}, or clear it to 0.
	 * @param db  connection to use
	 * @param loc new previous Location, or null for none
     * @throws IllegalStateException if the db isn't open
     * @throws IllegalArgumentException if a non-null <tt>loc</tt>'s dbconn isn't db;
     *         if loc's dbconn is <tt>null</tt>, this will be in the exception detail text.
	 */
	public static void setPreviousLocation(RDBAdapter db, Location loc)
		throws IllegalStateException, IllegalArgumentException
	{
		if (loc != null)
			matchDBOrThrow(db, loc);
		prevL = loc;
		final int id = (loc != null) ? loc.id : 0;
		insertOrUpdate(db, PREV_LOCATION, id);
	}

}  // public class Settings
