/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2014 Jeremy D Monin <jdmonin@nand.net>
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

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Read the VehSettings db table: Per-vehicle settings which change frequently.
 * For setting names, see static fields here or see the schema.
 *<P>
 * Convenience methods: {@link #insertOrUpdate(RDBAdapter, String, Vehicle, int)},
 *  {@link #insertOrUpdate(RDBAdapter, String, Vehicle, String)}.
 *<P>
 * If you restore the database from a backup, call {@link Settings#clearSettingsCache()}
 * to remove cached references to the overwritten db's settings objects.
 *<P>
 * Version 0.9.40 made these settings per-vehicle. In older versions they're in {@link Settings}.
 * Static fields here with setting names show their original version ("since 0.8.13" etc),
 * which often is older than 0.9.40.
 *
 * @see RDBSchema#checkSettings(RDBAdapter, int, boolean)
 * @see Settings
 * @author jdmonin
 * @since 0.9.40
 */
public class VehSettings extends RDBRecord
{
	/**
	 * int setting for current {@link GeoArea} area ID.
	 */
	public static final String CURRENT_AREA = "CURRENT_AREA";

	/**
	 * int setting for current driver ID (in {@link Person} table).
	 * @see #getCurrentDriver(RDBAdapter, Vehicle, boolean)
	 */
	public static final String CURRENT_DRIVER = "CURRENT_DRIVER";

	/**
	 * int setting for current {@link TStop} ID, if any.
	 * @see #getCurrentTStop(RDBAdapter, Vehicle, boolean)
	 */
	public static final String CURRENT_TSTOP = "CURRENT_TSTOP";

	/**
	 * int setting for previous {@link Location} ID, if any.
	 * When stopped at a {@link TStop}, the previous stop's location ID;
	 * at the trip's first stop, should be the trip start location.
	 * May be 0 between trips, or the previous trip's ending location.
	 * Used during trips to build dropdowns of {@link ViaRoute}s between
	 * {@code PREV_LOCATION} and current TStop's location.
	 * @since 0.8.13
	 */
	public static final String PREV_LOCATION = "PREV_LOCATION";

	/**
	 * int setting for current {@link FreqTrip} ID, if any.
	 * Should be set only when {@link #CURRENT_TRIP} is set.
	 * @see #getCurrentFreqTrip(RDBAdapter, Vehicle, boolean)
	 * @see #setCurrentFreqTrip(RDBAdapter, Vehicle, FreqTrip)
	 * @since 0.9.00
	 */
	public static final String CURRENT_FREQTRIP = "CURRENT_FREQTRIP";

	/**
	 * string setting for unused TStops in the current {@link FreqTrip}, if any,
	 * as a comma-delimited list of IDs into {@link FreqTripTSTop}.
	 * Should not be set if {@link #CURRENT_TRIP} isn't currently set.
	 * @see #setCurrentFreqTrip(RDBAdapter, Vehicle, FreqTrip)
	 * @see #reduceCurrentFreqTripTStops(RDBAdapter, Vehicle, FreqTripTStop)
	 * @since 0.9.00
	 */
	public static final String CURRENT_FREQTRIP_TSTOPLIST = "CURRENT_FREQTRIP_TSTOPLIST";

	private static final String TABNAME = "vehsettings";
	private static final String KEYFIELD_S = "sname";
	private static final String KEYFIELD_V = "vid";
	private static final String VALFIELD_STR = "svalue";
	private static final String VALFIELD_INT = "ivalue";
	private static final String WHERE_KEYFIELDS = "svalue=? and ivalue=?";
	private static final String[] FIELDS = { KEYFIELD_S, KEYFIELD_V, VALFIELD_STR, VALFIELD_INT };
	private static final String[] VALFIELDS = { VALFIELD_STR, VALFIELD_INT };
	private static final String[] VALFIELDS_AND_ID = { VALFIELD_STR, VALFIELD_INT, "_id" };

	/** Setting name (key) */
	private final String sfield;

	/** Vehicle ID (key) */
	private final int vid;

	/** String value, or null */
	private String svalue;

	/** Int value, ignored unless svalue null */
	private int ivalue;

	/**
	 * Check existence of a VehSetting from the database.
	 * @param db  connection to use
	 * @param settname field to retrieve
	 * @param v  Vehicle to retrieve for
	 * @return whether this setting exists in the database
	 * @throws NullPointerException  if {@code db} null
	 * @throws IllegalArgumentException  if {@code settname} null or {@code v} null
	 */
	public static boolean exists(RDBAdapter db, final String settname, final Vehicle v)
		throws IllegalArgumentException, NullPointerException
	{
		if (settname == null)
			throw new IllegalArgumentException("null settname");
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		try
		{
			final String[] kv = { settname, Integer.toString(v.getID()) };
			List<String[]> rv = db.getRows(TABNAME, WHERE_KEYFIELDS, kv, VALFIELDS, null, 0);
			return (rv != null);
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Create or update a VehSetting in the database.
	 * If the setting already exists with a string (not int) value,
	 * that string value will be cleared to null.
	 *
	 * @param db  connection to use
	 * @param settname field to create or update
	 * @param v  Vehicle to create or update for
	 * @param ivalue  int value to set
	 * @throws IllegalArgumentException  if {@code db} null, {@code settname} null, or {@code v} null
	 * @throws IllegalStateException if db not open
	 * @see #setBoolean(RDBAdapter, String, Vehicle, boolean)
	 */
	public static void insertOrUpdate(RDBAdapter db, final String settname, final Vehicle v, final int ivalue)
		throws IllegalArgumentException, IllegalStateException
	{
		insertOrUpdate(db, settname, v, ivalue, null);
	}

	/**
	 * Create or update a VehSetting in the database.
	 * If the Setting already exists with an int (not string) value,
	 * that int value will be cleared to 0.
	 *
	 * @param db  connection to use
	 * @param settname field to create or update
	 * @param v  Vehicle to create or update for
	 * @param svalue  string value to set, or null; "" will be stored as null
	 * @throws IllegalArgumentException  if {@code db} null, {@code settname} null, or {@code v} null
	 * @throws IllegalStateException if db not open
	 * @see #setBoolean(RDBAdapter, String, Vehicle, boolean)
	 */
	public static void insertOrUpdate(RDBAdapter db, final String settname, final Vehicle v, final String svalue)
		throws IllegalArgumentException, IllegalStateException
	{
		insertOrUpdate(db, settname, v, 0, svalue);
	}

	/**
	 * Create or update a VehSetting in the database.
	 * Implements public {@link #insertOrUpdate(RDBAdapter, String, Vehicle, int)}
	 * and {@link #insertOrUpdate(RDBAdapter, String, Vehicle, String)}, see those
	 * javadocs for details on parameters and exceptions.
	 */
	private static void insertOrUpdate
		(RDBAdapter db, final String settname, final Vehicle v, final int ivalue, String svalue)
		throws IllegalArgumentException, IllegalStateException
	{
		VehSettings s = null;

		if ((svalue != null) && (svalue.length() == 0))
			svalue = null;

		try {
			s = new VehSettings(db, settname, v);  // checks for null db, settname, v
		} catch (RDBKeyNotFoundException e) {
			// fall through, create it below
		}
		if (s == null)
		{
			s = new VehSettings(settname, v, svalue);
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
	 * @param settname field to clear
	 * @param v  Vehicle to clear for
	 * @throws IllegalArgumentException  if {@code db} null, {@code settname} null, or {@code v} null
	 * @throws IllegalStateException if db not open
	 */
	public static void clearIfExists(RDBAdapter db, final String settname, final Vehicle v)
		throws IllegalArgumentException, IllegalStateException
	{
		VehSettings s = null;

		try {
			s = new VehSettings(db, settname, v);
		} catch (RDBKeyNotFoundException e) { }

		if (s == null)
			return;  // nothing to clear

		s.svalue = null;
		s.ivalue = 0;
		s.dirty = true;
		s.commit();
	}

	/**
	 * Get this boolean setting from the database.
	 * Booleans are int settings with ivalue 1 or 0.
	 * @param db  connection to use
	 * @param settname   field to read
	 * @param v  Vehicle to read for
	 * @param settdefault  default value if not found
	 * @return  Setting's value (true if != 0), or {@code settdefault} if not found
	 * @throws IllegalArgumentException  if {@code db} null or {@code v} null
	 * @throws IllegalStateException if db not open
	 * @see #setBoolean(RDBAdapter, String, Vehicle, boolean)
	 * @see #getInt(RDBAdapter, String, Vehicle, int)
	 */
	public static boolean getBoolean
		(RDBAdapter db, final String settname, final Vehicle v, final boolean settdefault)
		throws IllegalArgumentException, IllegalStateException
	{
		return (0 != getInt(db, settname, v, (settdefault ? 1 : 0)));
	}

	/**
	 * Set this boolean setting in the database.
	 * Booleans are int settings with ivalue 1 or 0.
	 * @param db  connection to use
	 * @param settname   field to write
	 * @param v  Vehicle to write for
	 * @param settvalue  value to write
	 * @throws IllegalArgumentException  if {@code db} null or {@code v} null
	 * @throws IllegalStateException if db not open
	 * @see #getBoolean(RDBAdapter, String, Vehicle, boolean)
	 * @see #setInt(RDBAdapter, String, Vehicle, int)
	 * @see #insertOrUpdate(RDBAdapter, String, Vehicle, int)
	 */
	public static void setBoolean(RDBAdapter db, final String settname, final Vehicle v, final boolean settvalue)
		throws IllegalArgumentException, IllegalStateException
	{
		setInt(db, settname, v, settvalue ? 1 : 0);
	}

	/**
	 * Get this int setting from the database.
	 * @param db  connection to use
	 * @param settname   field to read
	 * @param v  Vehicle to read for
	 * @param settdefault  default value if not found
	 * @return  Setting's value, or {@code settdefault} if not found
	 * @throws IllegalArgumentException  if {@code db} null or {@code v} null
	 * @throws IllegalStateException if db not open
	 * @see #setInt(RDBAdapter, String, Vehicle, int)
	 * @see #getBoolean(RDBAdapter, String, Vehicle, boolean)
	 */
	public static int getInt(RDBAdapter db, final String settname, final Vehicle v, final int settdefault)
		throws IllegalArgumentException, IllegalStateException
	{
		VehSettings s = null;

		try {
			s = new VehSettings(db, settname, v);
		} catch (RDBKeyNotFoundException e) { }

		if (s == null)
			return settdefault;

		return s.ivalue;
	}

	/**
	 * Set this int setting in the database.
	 * @param db  connection to use
	 * @param settname   field to write
	 * @param v  Vehicle to write for
	 * @param ivalue  value to write
	 * @throws IllegalArgumentException  if {@code db} null or {@code v} null
	 * @throws IllegalStateException if db not open
	 * @see #getInt(RDBAdapter, String, Vehicle, int)
	 * @see #setBoolean(RDBAdapter, String, Vehicle, boolean)
	 * @see #insertOrUpdate(RDBAdapter, String, Vehicle, int)
	 */
	public static void setInt(RDBAdapter db, final String settname, final Vehicle v, final int ivalue)
		throws IllegalArgumentException, IllegalStateException
	{
		VehSettings s = null;

		try {
			s = new VehSettings(db, settname, v);
		} catch (RDBKeyNotFoundException e) { }

		if (s == null)
		{
			s = new VehSettings(settname, v, ivalue);
			s.insert(db);
		}
		else if (ivalue != s.ivalue)
		{
			s.setIntValue(ivalue);
			s.commit();
		}
	}

	/**
	 * Look up a VehSetting from the database.
	 * @param db  db connection
	 * @param settname field to retrieve
	 * @param v  Vehicle to retrieve for; only v.getID() is used, the vehicle object isn't retained
	 * @throws IllegalArgumentException  if {@code db} null, {@code settname} null, or {@code v} null
	 * @throws IllegalStateException if db not open
	 * @throws RDBKeyNotFoundException if settname not found in database
	 * @see #getBoolean(RDBAdapter, String, Vehicle, boolean)
	 */
	public VehSettings(RDBAdapter db, String settname, final Vehicle v)
		throws IllegalArgumentException, IllegalStateException, RDBKeyNotFoundException
	{
		super(db, -1);  // checks db != null
		if (settname == null)
			throw new IllegalArgumentException("null settname");
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		sfield = settname;
		vid = v.getID();
		final String[] kv = { settname, Integer.toString(vid) };
		final List<String[]> rv = db.getRows(TABNAME, WHERE_KEYFIELDS, kv, VALFIELDS_AND_ID, null, 0);
		if (rv == null)
			throw new RDBKeyNotFoundException(settname);

		// keys are together unique, so there won't be more than 1 row in fv
		final String[] fv = rv.get(0);
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
	 * Create a new string-valued VehSetting (not yet inserted to the database).
	 * @param settname field to set
	 * @param v  Vehicle to set for; only v.getID() is used, the vehicle object isn't retained
	 * @param svalue    string value to set; empty strings ("") are stored as null.
	 * @throws IllegalArgumentException  if {@code settname} null or {@code v} null
	 */
	public VehSettings(final String settname, final Vehicle v, String svalue)
		throws IllegalArgumentException
	{
		super();
		if (settname == null)
			throw new IllegalArgumentException("null settname");
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		if ((svalue != null) && (svalue.length() == 0))
			svalue = null;
		sfield = settname;
		vid = v.getID();
		this.svalue = svalue;
		ivalue = 0;
	}

	/**
	 * Create a new int-valued Setting (not yet inserted to the database).
	 * @param settname field to set
	 * @param v  Vehicle to set for; only v.getID() is used, the vehicle object isn't retained
	 * @param ivalue    int value to set
	 * @throws IllegalArgumentException  if {@code settname} null or {@code v} null
	 */
	public VehSettings(final String settname, final Vehicle v, int ivalue)
	{
		super();
		if (settname == null)
			throw new IllegalArgumentException("null settname");
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		sfield = settname;
		vid = v.getID();
		svalue = null;
		this.ivalue = ivalue;
	}

	/** name of the field, such as {@link #CURRENT_TSTOP} */
	public String getField() { return sfield; }

	/** vehicle ID */
	public int getVehicleID() { return vid; }

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
	 * Set the string value; clear the int value unless {@code s} is null. Dirty the DB record.
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
	 * Set the int value; clear the string value unless {@code i} is 0. Dirty the DB record.
	 * @param i New int value, or 0
	 */
	public void setIntValue(int i)
	{
		if (ivalue == i)
			return;

		ivalue = i;
		if (i != 0)
			svalue = null;

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
		String iv;
		if (svalue != null)
			iv = null;
		else
			iv = Integer.toString(ivalue);
		String[] fv = { sfield, Integer.toString(vid), svalue, iv };
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

		// can update by id: the 2 key fields are final
		String[] fv = { svalue, iv };
		dbConn.update(TABNAME, id, VALFIELDS, fv);
		dirty = false;
	}

	/**
	 * Delete an existing record.
	 *
	 * @throws NullPointerException if dbConn was null because this is a new record, not an existing one
	 */
	public void delete()
		throws NullPointerException
	{
		dbConn.delete(TABNAME, id);
		deleteCleanup();
	}

	/**
	 * --- CURRENT_ setting getters/setters ---
	 *     and cached per-vehicle settings
	 */

	/** cached record for {@link #getCurrentArea(RDBAdapter, Vehicle, boolean)} */
	private static GeoArea currentA = null;
	/** vehicle ID for cached {@link #currentA} per-vehicle setting */
	private static int currentA_vid;

	/** cached record for {@link #getCurrentDriver(RDBAdapter, Vehicle, boolean)} */
	private static Person currentD = null;
	private static int currentD_vid;

	/** cached record for {@link #getCurrentFreqTrip(RDBAdapter, Vehicle, boolean)} */
	private static FreqTrip currentFT = null;
	private static int currentFT_vid;

	/** cached record for {@link #getCurrentFreqTripTStops(RDBAdapter, Vehicle, boolean)}; length never 0, is null in that case. */
	private static List<FreqTripTStop> currentFTS = null;
	private static int currentFTS_vid;

	/** cached record for {@link #getCurrentTStop(RDBAdapter, Vehicle, boolean)} */
	private static TStop currentTS = null;
	private static int currentTS_vid;

	/** cached record for {@link #getPreviousLocation(RDBAdapter, Vehicle, boolean)} */
	private static Location prevL = null;
	private static int prevL_vid;

	/**
	 * Clear cached settings records and associated objects (such
	 * as the Current {@link GeoArea}). Necessary after restoring from a backup.
	 * Called from {@link Settings#clearSettingsCache()}.
	 */
	public static void clearSettingsCache()
	{
		currentA = null;
		currentD = null;
		currentFT = null;
		currentFTS = null;
		currentTS = null;
		prevL = null;
	}

	/**
	 * For use with {@code setCurrent*}, validate that db == rec.dbConn.
	 * If all is OK, do nothing, otherwise throw the exception.
	 * @param db  conn to use. If {@code rec} is new, can be null, otherwise must not be null
	 * @param rec  record to validate dbConn; must not be null
	 * @throws IllegalArgumentException if <tt>rec.{@link RDBRecord#dbConn dbConn}</tt>
	 *         isn't {@code db}; if rec's dbconn is null, this will be in the exception detail text.
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
	 * Get the setting for {@link #CURRENT_AREA} if set.
	 *<P>
	 * The record is cached after the first call, so if it changes,
	 * please call {@link #setCurrentArea(RDBAdapter, Vehicle, GeoArea)}.
	 *
	 * @param db  connection to use
	 * @param v   Vehicle to get setting for
	 * @param clearIfBad  If true, clear the setting to 0 if no record by its ID is found
	 * @return the GeoArea for {@code CURRENT_AREA}, or null
	 * @throws IllegalArgumentException if {@code v} is null
	 * @throws IllegalStateException if the db is null or isn't open
	 */
	public static GeoArea getCurrentArea(RDBAdapter db, final Vehicle v, final boolean clearIfBad)
		throws IllegalArgumentException, IllegalStateException
	{
		if (db == null)
			throw new IllegalStateException("null db");
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		final int vid = v.getID();
		if ((currentA != null) && (currentA_vid == vid))
		{
			if (! currentA.dbConn.hasSameOwner(db))
				currentA.dbConn = db;
			return currentA;
		}

		VehSettings sCA = null;
		try
		{
			sCA = new VehSettings(db, Settings.CURRENT_AREA, v);
			// Sub-try: cleanup in case the setting exists, but the record doesn't
			try {
				int id = sCA.getIntValue();
				if (id != 0)
				{
					currentA = new GeoArea(db, id);
					currentA_vid = vid;
				}
			} catch (Throwable th) {
				currentA = null;
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
	 * @param v  Vehicle to set for
	 * @param a  new area, or null for none
	 * @throws IllegalStateException if the db isn't open
	 * @throws IllegalArgumentException if {@code v} is null, or if a non-null {@code a}'s dbconn isn't db;
	 *         if {@code a}'s dbconn is null, this will be in the exception detail text.
	 */
	public static void setCurrentArea(RDBAdapter db, final Vehicle v, GeoArea a)
		throws IllegalArgumentException, IllegalStateException 
	{
		if (a != null)
			matchDBOrThrow(db, a);
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		currentA = a;
		currentA_vid = v.getID();
		final int id = (a != null) ? a.id : 0;
		insertOrUpdate(db, CURRENT_AREA, v, id);
	}

	/**
	 * Get the Setting for {@link #CURRENT_DRIVER} if set.
	 *<P>
	 * The record is cached after the first call, so if it changes,
	 * please call {@link #setCurrentDriver(RDBAdapter, Vehicle, Person)}.
	 *
	 * @param db  connection to use
	 * @param v  Vehicle to retrieve for
	 * @param clearIfBad  If true, clear the setting to 0 if no record by its ID is found
	 * @return the Person for {@code CURRENT_DRIVER}, or null
	 * @throws IllegalArgumentException if {@code v} is null
	 * @throws IllegalStateException if the db is null or isn't open
	 */
	public static Person getCurrentDriver(RDBAdapter db, final Vehicle v, final boolean clearIfBad)
		throws IllegalArgumentException, IllegalStateException
	{
		if (db == null)
			throw new IllegalStateException("null db");
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		final int vid = v.getID();
		if ((currentD != null) && (currentD_vid == vid))
		{
			if (! currentD.dbConn.hasSameOwner(db))
				currentD.dbConn = db;
			return currentD;
		}

		VehSettings sCD = null;
		try
		{
			sCD = new VehSettings(db, CURRENT_DRIVER, v);
			// Sub-try: cleanup in case the setting exists, but the record doesn't
			try {
				int id = sCD.getIntValue();
				if (id != 0)
				{
					currentD = new Person(db, id);
					currentD_vid = vid;
				}
			} catch (Throwable th) {
				currentD = null;
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
	 * @param v  Vehicle to set for
	 * @param dr  new driver, or null for none
	 * @throws IllegalStateException if the db isn't open
	 * @throws IllegalArgumentException if {@code v} is null, or if a non-null {@code dr}'s dbconn isn't db;
	 *         if {@code dr}'s dbconn is null, this will be in the exception detail text.
	 */
	public static void setCurrentDriver(RDBAdapter db, final Vehicle v, Person dr)
		throws IllegalArgumentException, IllegalStateException 
	{
		if (dr != null)
			matchDBOrThrow(db, dr);
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		currentD = dr;
		currentD_vid = v.getID();
		final int id = (dr != null) ? dr.id : 0;
		insertOrUpdate(db, CURRENT_DRIVER, v, id);
	}

	/**
	 * Get the Setting for {@link #CURRENT_FREQTRIP} if set.
	 *<P>
	 * The record is cached after the first call, so if it changes,
	 * please call {@link #setCurrentFreqTrip(RDBAdapter, Vehicle, FreqTrip)}.
	 *
	 * @param db  connection to use
	 * @param v  Vehicle to retrieve for
	 * @param clearIfBad  If true, clear the setting to 0 if no record by its ID is found
	 * @return the FreqTrip for {@code CURRENT_FREQTRIP}, or null
	 * @throws IllegalArgumentException if {@code v} is null
	 * @throws IllegalStateException if the db is null or isn't open
	 *
	 * @see #getCurrentFreqTripTStops(RDBAdapter, Vehicle, boolean)
	 */
	public static FreqTrip getCurrentFreqTrip(RDBAdapter db, final Vehicle v, final boolean clearIfBad)
		throws IllegalArgumentException, IllegalStateException
	{
		if (db == null)
			throw new IllegalStateException("null db");
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		final int vid = v.getID();
		if ((currentFT != null) && (currentFT_vid == vid))
		{
			if (! currentFT.dbConn.hasSameOwner(db))
				currentFT.dbConn = db;
			return currentFT;
		}

		VehSettings sCT = null;
		try
		{
			sCT = new VehSettings(db, CURRENT_FREQTRIP, v);
			// Sub-try: cleanup in case the setting exists, but the record doesn't
			try {
				int id = sCT.getIntValue();
				if (id != 0)
				{
					currentFT = new FreqTrip(db, id);
					currentFT_vid = vid;
				}
			} catch (Throwable th) {
				currentFT = null;
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
	 * Will also set or clear {@link #CURRENT_FREQTRIP_TSTOPLIST}, based on {@code ft} == null
	 * or on {@link FreqTrip#readAllTStops()}.
	 * @param db  connection to use
	 * @param v  Vehicle to set for
	 * @param ft  new freqtrip, or null for none
	 * @throws IllegalStateException if the db isn't open
	 * @throws IllegalArgumentException if {@code v} is null, or if a non-null {@code ft}'s dbconn isn't db;
	 *         if {@code ft}'s dbconn is null, this will be in the exception detail text.
	 * @see #reduceCurrentFreqTripTStops(RDBAdapter, Vehicle, FreqTripTStop)
	 */
	public static void setCurrentFreqTrip(RDBAdapter db, final Vehicle v, FreqTrip ft)
		throws IllegalArgumentException, IllegalStateException
	{
		if (ft != null)
			matchDBOrThrow(db, ft);
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		currentFT = ft;
		currentFT_vid = v.getID();
		final int id = (ft != null) ? ft.id : 0;
		insertOrUpdate(db, CURRENT_FREQTRIP, v, id);
		if (ft == null)
		{
			currentFTS = null;
			insertOrUpdate(db, CURRENT_FREQTRIP_TSTOPLIST, v, null);
		} else {
			Vector<FreqTripTStop> allStops = ft.readAllTStops();
			if (allStops == null)
			{
				currentFTS = null;
				insertOrUpdate(db, CURRENT_FREQTRIP_TSTOPLIST, v, null);
				return;
			}
			insertOrUpdateCurrentFreqTripTStops(db, v, allStops);  // sets currentFTS, currentFTS_vid
		}
	}

	/**
	 * Write {@link #currentFTS}, as a string of IDs, to {@link #CURRENT_FREQTRIP_TSTOPLIST}.
	 * @param db  connection to use
	 * @param v  Vehicle to update stop list for: not null
	 * @param currFTS  Frequent stops from {@link #currentFTS} or a local variable, or null or empty to clear setting
	 * @throws IllegalArgumentException if {@code db} is null or {@code v} is null
	 * @throws IllegalStateException if db not open
	 */
	private static void insertOrUpdateCurrentFreqTripTStops
		(RDBAdapter db, final Vehicle v, final List<FreqTripTStop> currFTS)
		throws IllegalArgumentException, IllegalStateException
	{
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		if ((currFTS == null) || currFTS.isEmpty())
		{
			insertOrUpdate(db, CURRENT_FREQTRIP_TSTOPLIST, v, null);
			currentFTS = null;
			return;
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < currFTS.size(); ++i)
		{
			if (i > 0)
				sb.append(",");
			sb.append(currFTS.get(i).getID());
		}
		currentFTS = currFTS;
		currentFTS_vid = v.getID();
		insertOrUpdate(db, CURRENT_FREQTRIP_TSTOPLIST, v, sb.toString());
	}

	/**
	 * Get the objects listed in the Setting for {@link #CURRENT_FREQTRIP_TSTOPLIST}, if set.
	 * This is set only when {@link #CURRENT_FREQTRIP} is set, and when
	 * that {@link FreqTrip} contains {@link FreqTripTStop}s.
	 *<P>
	 * If you only want to see whether this setting is active or not,
	 * and don't need to read the objects from the database:
	 * Call {@link #getCurrentFreqTrip(RDBAdapter, Vehicle, boolean) getCurrentFreqTrip},
	 * then {@link #VehSettings(RDBAdapter, String, Vehicle) new VehSettings(db, v, CURRENT_FREQTRIP_TSTOPLIST)}
	 * and then {@link #getStrValue()}.
	 *<P>
	 * The record is cached after the first call, so if it changes,
	 * please call {@link #setCurrentFreqTrip(RDBAdapter, Vehicle, FreqTrip)} to update
	 * both the current freqtrip and the tstop list from the database,
	 * or call {@link #reduceCurrentFreqTripTStops(RDBAdapter, Vehicle, FreqTripTStop)}.
	 *
	 * @param db  connection to use; must be open, must not be null, or null will be returned
	 * @param v  Vehicle to get stop list for
	 * @param clearIfBad  If true, clear the setting to null if no record by its ID is found
	 * @return the FreqTripTStops for {@code CURRENT_FREQTRIP_TSTOPLIST}, or null;
	 *            will never be 0-length, will return null in that case.
	 * @throws IllegalArgumentException if {@code db} is null or {@code v} is null
	 * @throws IllegalStateException if db not open
	 *
	 * @see #getCurrentFreqTrip(RDBAdapter, Vehicle, boolean)
	 */
	public static List<FreqTripTStop> getCurrentFreqTripTStops
		(RDBAdapter db, final Vehicle v, final boolean clearIfBad)
		throws IllegalArgumentException, IllegalStateException
	{
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		final int vid = v.getID();
		if ((currentFTS != null) && (currentFTS_vid == vid))
		{
			if ((currentFTS.size() > 0) && ! currentFTS.get(0).dbConn.hasSameOwner(db))
			{
				for (int i = currentFTS.size() - 1; i >= 0; --i)
					currentFTS.get(i).dbConn = db;
			}
			return currentFTS;
		}

		try
		{
			VehSettings sFSL = new VehSettings(db, CURRENT_FREQTRIP_TSTOPLIST, v);
			// Sub-try: to cleanup in case the setting exists, but a record doesn't.
			// We'll read each freq stop with an ID in the list.
			try {
				final String slist = sFSL.getStrValue();
				if (slist == null)
				{
					currentFTS = null;
					return null;
				}

				final String[] ids = slist.split(",");
				ArrayList<FreqTripTStop> allStops = new ArrayList<FreqTripTStop>(ids.length);
				for (int i = 0; i < ids.length; ++i)
					allStops.add(new FreqTripTStop(db, Integer.parseInt(ids[i])));
				currentFTS = allStops;
				currentFTS_vid = vid;
			} catch (Throwable th) {
				currentFTS = null;
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
	 * @param v  Vehicle to update stop list for
	 * @param removeStop  Remove this stop's ID from the list
	 * @throws IllegalArgumentException if {@code db} is null or {@code v} is null
	 * @throws IllegalStateException if the db isn't open
	 */
	public static void reduceCurrentFreqTripTStops(RDBAdapter db, final Vehicle v, final FreqTripTStop removeStop)
		throws IllegalArgumentException, IllegalStateException
	{
		if (removeStop == null)
			return;  // nothing to do

		List<FreqTripTStop> currFTS = getCurrentFreqTripTStops(db, v, false);
		if (currFTS == null)
			return;  // list aready empty

		final int ftsID = removeStop.getID();
		boolean found = false;
		for (int i = currFTS.size() - 1; i >= 0; --i)
		{
			FreqTripTStop s = currFTS.get(i);
			if (s.getID() == ftsID)
			{
				found = true;
				currFTS.remove(i);
				break;
			}
		}

		if (found)
			insertOrUpdateCurrentFreqTripTStops(db, v, currFTS);
	}

	/**
	 * Get the Setting for {@link #CURRENT_TSTOP} if set.
	 *<P>
	 * The record is cached after the first call, so if it changes,
	 * please call {@link #setCurrentTStop(RDBAdapter, Vehicle, TStop)}.
	 *
	 * @param db  connection to use
	 * @param v  Vehicle to retrieve for
	 * @param clearIfBad  If true, clear the setting to 0 if no record by its ID is found
	 * @return the TStop for {@code CURRENT_TSTOP}, or null
	 * @throws IllegalArgumentException if {@code v} is null
	 * @throws IllegalStateException if the db is null or isn't open
	 */
	public static TStop getCurrentTStop(RDBAdapter db, final Vehicle v, final boolean clearIfBad)
		throws IllegalArgumentException, IllegalStateException
	{
		if (db == null)
			throw new IllegalStateException("null db");
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		final int vid = v.getID();
		if ((currentTS != null) && (currentTS_vid == vid))
		{
			if (! currentTS.dbConn.hasSameOwner(db))
				currentTS.dbConn = db;
			return currentTS;
		}

		VehSettings sCTS = null;
		try
		{
			sCTS = new VehSettings(db, CURRENT_TSTOP, v);
			// Sub-try: cleanup in case the setting exists, but the record doesn't
			try {
				int id = sCTS.getIntValue();
				if (id != 0)
				{
					currentTS = new TStop(db, id);
					currentTS_vid = vid;
				}
			} catch (Throwable th) {
				currentTS = null;
				if (clearIfBad)
					sCTS.delete();
			}
		} catch (Throwable th) {
			return null;
		}
		return currentTS;  // will be null if sCT not found
	}

	/**
	 * Store the Setting for {@link #CURRENT_TSTOP}, or clear it to 0.
	 * @param db  connection to use
	 * @param v  Vehicle to set for
	 * @param ts  new tstop, or null for none
	 * @throws IllegalStateException if the db isn't open
	 * @throws IllegalArgumentException if {@code v} is null, or if a non-null {@code ts}'s dbconn isn't db;
	 *         if {@code ts}'s dbconn is null, this will be in the exception detail text.
	 */
	public static void setCurrentTStop(RDBAdapter db, final Vehicle v, TStop ts)
		throws IllegalArgumentException, IllegalStateException
	{
		if (ts != null)
			matchDBOrThrow(db, ts);
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		currentTS = ts;
		currentTS_vid = v.getID();
		final int id = (ts != null) ? ts.id : 0;
		insertOrUpdate(db, CURRENT_TSTOP, v, id);
	}

	/**
	 * Get the Setting for {@link #PREV_LOCATION} if set.
	 *<P>
	 * The record is cached after the first call, so if it changes,
	 * please call {@link #setPreviousLocation(RDBAdapter, Vehicle, Location)}.
	 *
	 * @param db  connection to use
	 * @param v  Vehicle to retrieve for
	 * @param clearIfBad  If true, clear the setting to 0 if no record by its ID is found
	 * @return the TStop for {@code PREV_LOCATION}, or null
	 * @throws IllegalArgumentException if {@code v} is null
	 * @throws IllegalStateException if the db is null or isn't open
	 */
	public static Location getPreviousLocation(RDBAdapter db, final Vehicle v, final boolean clearIfBad)
		throws IllegalArgumentException, IllegalStateException
	{
		if (db == null)
			throw new IllegalStateException("null db");
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		final int vid = v.getID();
		if ((prevL != null) && (prevL_vid == vid))
		{
			if (! prevL.dbConn.hasSameOwner(db))
				prevL.dbConn = db;
			return prevL;
		}

		VehSettings sPL = null;
		try
		{
			sPL = new VehSettings(db, PREV_LOCATION, v);
			// Sub-try: cleanup in case the setting exists, but the record doesn't
			try {
				int id = sPL.getIntValue();
				if (id != 0)
				{
					prevL = new Location(db, id);
					prevL_vid = vid;
				}
			} catch (Throwable th) {
				prevL = null;
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
	 * @param v  Vehicle to set for
	 * @param loc new previous Location, or null for none
	 * @throws IllegalStateException if the db isn't open
	 * @throws IllegalArgumentException if {@code v} is null, or if a non-null {@code loc}'s dbconn isn't db;
	 *         if {@code loc}'s dbconn is null, this will be in the exception detail text.
	 */
	public static void setPreviousLocation(RDBAdapter db, final Vehicle v, Location loc)
		throws IllegalArgumentException, IllegalStateException 
	{
		if (loc != null)
			matchDBOrThrow(db, loc);
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		prevL = loc;
		prevL_vid = v.getID();
		final int id = (loc != null) ? loc.id : 0;
		insertOrUpdate(db, PREV_LOCATION, v, id);
	}

}  // public class VehSettings
