/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2014-2015 Jeremy D Monin <jdmonin@nand.net>
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
 * For setting names, see static string fields here or see the schema.
 *<P>
 * Convenience methods: {@link #insertOrUpdate(RDBAdapter, String, Vehicle, int)},
 * {@link #insertOrUpdate(RDBAdapter, String, Vehicle, String)}.
 *<P>
 * Call {@link #changeCurrentVehicle(RDBAdapter, Vehicle, Vehicle)} to check per-vehicle settings
 * and update {@link Settings#getCurrentVehicle(RDBAdapter, boolean)}.
 *<P>
 * If you restore the database from a backup, call {@link Settings#clearSettingsCache()}
 * to remove cached references to the overwritten db's settings objects.
 *<P>
 * Version 0.9.40 made these settings per-vehicle. In older versions they're in {@link Settings}.
 * Static fields here with setting names show their original version ("since 0.8.13" etc),
 * which often is older than 0.9.40.
 *
 * @see RDBSchema#checkSettings(RDBAdapter, int)
 * @see Settings
 * @author jdmonin
 * @since 0.9.40
 */
public class VehSettings extends RDBRecord
{
	/**
	 * int setting for current {@link GeoArea} area ID.
	 * During a roadtrip, this is the trip's starting area ID; when the
	 * roadtrip ends, will be changed to {@link Trip#getRoadtripEndAreaID()}.
	 */
	public static final String CURRENT_AREA = "CURRENT_AREA";

	/**
	 * int setting for current driver ID (in {@link Person} table).
	 * @see #getCurrentDriver(RDBAdapter, Vehicle, boolean)
	 */
	public static final String CURRENT_DRIVER = "CURRENT_DRIVER";

	/**
	 * int setting for current {@link Trip} ID, if any.
	 * @see #getCurrentTrip(RDBAdapter, Vehicle, boolean)
	 */
	public static final String CURRENT_TRIP = "CURRENT_TRIP";

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
	 *<P>
	 * Used during trips to build dropdowns of {@link ViaRoute}s between
	 * {@code PREV_LOCATION} and current TStop's location.
	 * If this setting is missing, no impact beyond ViaRoute setup on current trip.
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

	private static final String TABNAME = "veh_settings";
	private static final String KEYFIELD_S = "sname";
	private static final String KEYFIELD_V = "vid";
	private static final String VALFIELD_STR = "svalue";
	private static final String VALFIELD_INT = "ivalue";
	private static final String WHERE_KEYFIELDS = "sname=? and vid=?";
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
	 * Delete all settings for a {@link Vehicle}.
	 * Called by {@link Vehicle#delete()}.
	 * @param db  connection to use
	 * @param v  Vehicle to write for
	 * @throws IllegalArgumentException if db or v is null
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 */
	public static void deleteAll(final RDBAdapter db, final Vehicle v)
		throws IllegalArgumentException, IllegalStateException
	{
		if ((db == null) || (v == null))
			throw new IllegalArgumentException();

		db.delete(TABNAME, "vid=?", v.getID());
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
			throw new RDBKeyNotFoundException(settname + "," + kv[1]);

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
	 *<P>
	 * Usually it's better to set its int value to 0 and string value to null instead of deleting the setting,
	 * so that the app knows this isn't currently set and doesn't try to find the current setting through other
	 * data (backward-compatible mode, see {@link #changeCurrentVehicle(RDBAdapter, Vehicle, Vehicle)}).
	 *
	 * @throws NullPointerException if dbConn was null because this is a new record, not an existing one
	 * @see #deleteAll(RDBAdapter, Vehicle)
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

	/**
	 * Cached record for {@link #getCurrentArea(RDBAdapter, Vehicle, boolean)}, or {@code null}.
	 * This setting is for the vehicle with ID {@link #currentA_vid}.
	 *<P>
	 * <H5>Caching and the Activity lifecycle:</H5>
	 *
	 * Each cached record from a database includes a {@link RDBAdapter} db reference ({@link RDBRecord#dbConn}
	 * field). On Android this program's Activities call {@link RDBAdapter#close()} to close the db reference
	 * when the activity is paused or destroyed.  The next Activity will create and open a new {@code RDBAdapter}.
	 *<P>
	 * Cached static settings records might still have a {@code dbConn} reference to a previous activity's
	 * RDBAdapter that's been closed already.  If the record then uses this old reference for updates, or other
	 * queries such as {@link Trip#readAllTStops()}, it would reopen the old RDBAdapter and nothing would close
	 * it. At some point later, LogCat will show a sqlite.DatabaseObjectNotClosedException or a
	 * "SQLiteDatabase created and never closed" IllegalStateException.
	 *<P>
	 * So, all of VehSettings' {@code getCurrentX} methods check whether the cached record is owned by the
	 * same database as the caller, but uses a different RDBAdapter reference to access that db.  If so, the
	 * method places the caller's db reference into the cached record's {@link RDBRecord#dbConn dbConn} before
	 * returning it.  After that, any further queries or updates through the cached record would use the
	 * caller's open RDBAdapter.
	 */
	private static GeoArea currentA = null;

	/** vehicle ID for cached {@link #currentA} per-vehicle setting */
	private static int currentA_vid;

	/** cached record for {@link #getCurrentDriver(RDBAdapter, Vehicle, boolean)} */
	private static Person currentD = null;
	private static int currentD_vid;

	/** cached record for {@link #getCurrentTrip(RDBAdapter, Vehicle, boolean)} */
	private static Trip currentT = null;
	private static int currentT_vid;

	/** cached record for {@link #getCurrentFreqTrip(RDBAdapter, Vehicle, boolean)} */
	private static FreqTrip currentFT = null;
	private static int currentFT_vid;

	/** cached record for {@link #getCurrentFreqTripTStops(RDBAdapter, Vehicle, boolean)};
	 *  length never 0, is null in that case. */
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
		currentT = null;
		currentFT = null;
		currentFTS = null;
		currentTS = null;
		prevL = null;
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
		if ((currentA != null) && (currentA_vid == vid) && db.hasSameOwner(currentA.dbConn))
		{
			if (db != currentA.dbConn)
				// cached from earlier activity in Android: see currentA javadoc for more info
				currentA.dbConn = db;

			return currentA;
		}

		VehSettings sCA = null;
		try
		{
			sCA = new VehSettings(db, CURRENT_AREA, v);
			// Sub-try: cleanup in case the setting exists, but the record doesn't
			try {
				int id = sCA.getIntValue();
				if (id != 0)
				{
					currentA = new GeoArea(db, id);
					currentA_vid = vid;
				} else {
					currentA = null;
				}
			} catch (Throwable th) {
				currentA = null;
				if (clearIfBad)
					sCA.delete();
			}
		} catch (Throwable th) {
			return null;  // no setting found for this vehicle; don't use or change currentA
		}

		return currentA;
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
	 * @see #findCurrentDriver(RDBAdapter, Vehicle)
	 */
	public static Person getCurrentDriver(RDBAdapter db, final Vehicle v, final boolean clearIfBad)
		throws IllegalArgumentException, IllegalStateException
	{
		if (db == null)
			throw new IllegalStateException("null db");
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		final int vid = v.getID();
		if ((currentD != null) && (currentD_vid == vid) && db.hasSameOwner(currentD.dbConn))
		{
			if (db != currentD.dbConn)
				// cached from earlier activity in Android: see currentA javadoc for more info
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
				} else {
					currentD = null;
				}
			} catch (Throwable th) {
				currentD = null;
				if (clearIfBad)
					sCD.delete();
			}
		} catch (Throwable th) {
			return null;  // no setting found for this vehicle; don't use or change currentD
		}

		return currentD;
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
	 * Get the Setting for {@link #CURRENT_TRIP} if set.
	 *<P>
	 * The record is cached after the first call, so if it changes,
	 * please call {@link #setCurrentTrip(RDBAdapter, Vehicle, Trip)}.
	 *
	 * @param db  connection to use
	 * @param v  Vehicle to retrieve for
	 * @param clearIfBad  If true, clear the setting to 0 if no record by its ID is found
	 * @return the Person for {@code CURRENT_DRIVER}, or null
	 * @throws IllegalArgumentException if {@code v} is null
	 * @throws IllegalStateException if the db is null or isn't open
	 */
	public static Trip getCurrentTrip(RDBAdapter db, final Vehicle v, final boolean clearIfBad)
		throws IllegalArgumentException, IllegalStateException
	{
		if (db == null)
			throw new IllegalStateException("null db");
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		final int vid = v.getID();
		if ((currentT != null) && (currentT_vid == vid) && db.hasSameOwner(currentT.dbConn))
		{
			if (db != currentT.dbConn)
				// cached from earlier activity in Android: see currentA javadoc for more info
				currentT.dbConn = db;

			return currentT;
		}

		VehSettings sCT = null;
		try
		{
			sCT = new VehSettings(db, CURRENT_TRIP, v);

			// Sub-try: cleanup in case the setting exists, but the record doesn't
			try {
				int id = sCT.getIntValue();
				if (id != 0)
				{
					currentT = new Trip(db, id);
					currentT_vid = vid;
				} else {
					currentT = null;
				}
			} catch (Throwable th) {
				currentT = null;
				if (clearIfBad)
					sCT.delete();
			}
		} catch (Throwable th) {
			return null;  // no setting found for this vehicle; don't use or change currentT
		}

		return currentT;
	}

	/**
	 * Store the Setting for {@link #CURRENT_TRIP}, or clear it to 0.
	 * @param db  connection to use
	 * @param v  Vehicle to set for
	 * @param tr  new trip, or null for none
	 * @throws IllegalStateException if the db isn't open
	 * @throws IllegalArgumentException if {@code v} is null, or if a non-null {@code tr}'s dbconn isn't db;
	 *         if {@code tr}'s dbconn is null, this will be in the exception detail text.
	 * @see #endCurrentTrip(RDBAdapter, Vehicle, Trip, int, int, int, TripCategory, int, boolean)
	 */
	public static void setCurrentTrip(RDBAdapter db, final Vehicle v, Trip tr)
		throws IllegalArgumentException, IllegalStateException
	{
		if (tr != null)
			matchDBOrThrow(db, tr);
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		currentT = tr;
		currentT_vid = v.getID();
		final int id = (tr != null) ? tr.id : 0;
		insertOrUpdate(db, CURRENT_TRIP, v, id);
	}

	/**
	 * Finish this vehicle's current trip in the database.
	 * Set the trip's {@code time_end} and {@code odo_end}.
	 * Set its {@link TripCategory} and passenger count if specified.
	 * Clear CURRENT_TRIP.
	 * Update the Trip and Vehicle odometers.
	 *<P>
	 * If ending a roadtrip, also update CURRENT_AREA and the trip's {@link Trip#getRoadtripEndAreaID()}
	 * from its ending TStop's geoarea.
	 * A roadtrip whose stops are all in its starting geoarea will be converted to a local trip.
	 *<P>
	 * Assumes ending {@link TStop} {@code tsid} is already created or updated in the db.
	 *<P>
	 * <b>Caller must validate</b> that data will be consistent: Odometer and time won't run backwards,
	 * currently stopped (not moving) at a committed TStop at ending location, etc.
	 *<P>
	 * Before v0.9.50, this method was in the {@code android.TripTStopEntry} Activity.
	 *
	 * @param db  connection to use
	 * @param v  Vehicle ending the trip
	 * @param tsid  Trip's ending {@link TStop} ID, not 0
	 * @param odo_total  Total odometer at end of trip, not 0
	 * @param stopTimeSec  Trip ending time (from final tstop), or 0 if not set there
	 * @param tCat  Trip category if any, or null. To help with the GUI, this can also be a TripCategory with
	 *     getID() == -1 to indicate no category; tripcat id 0 will be written to the db in that case.
	 * @param pax  Trip passenger count (optional), 0 if only the driver is in the vehicle, or -1 to omit or clear;
	 *     ignored unless boolean {@link Settings#SHOW_TRIP_PAX} is set.
	 * @throws IllegalArgumentException if {@code v} is null, {@code tsid} is 0, or {@code odo_total} is 0
	 *     or a roadtrip's ending TStop is in GeoArea 0 (none)
	 * @throws IllegalStateException  if no current trip
	 *     ({@link #getCurrentTrip(RDBAdapter, Vehicle, boolean) getCurrentTrip(db, v, false)} is null)
	 * @throws NullPointerException if {@code db} is null
	 */
	public static void endCurrentTrip
		(final RDBAdapter db, final Vehicle v, final int tsid, final int odo_total, final int stopTimeSec,
		 final TripCategory tCat, final int pax)
		throws IllegalArgumentException, IllegalStateException, NullPointerException
	{
		if ((v == null) || (tsid == 0) || (odo_total == 0))
			throw new IllegalArgumentException();

		final Trip currT = VehSettings.getCurrentTrip(db, v, false);
		if (currT == null)
			throw new IllegalStateException
				("No current trip for vehicle " + v.getID() + " " + v.toString());

		// check roadtrip ending geoarea and other areas; convert to local trip if all TStops in starting area
		if (currT.isRoadtrip())
			currT.checkRoadtripTStops(true);
				// throws IllegalArgumentException if ending tstop is in area 0 (none)

		// check for tripcategory
		{
			final int tripCatID = (tCat != null) ? tCat.getID() : -1;
			if (tripCatID > 0)
				currT.setTripCategoryID(tripCatID);
			else
				currT.setTripCategoryID(0);  // tripCat is -1
		}

		// Set and commit other trip fields
		if (stopTimeSec != 0)
			currT.setTime_end(stopTimeSec);
		currT.setOdo_end(odo_total);
		if (Settings.getBoolean(db, Settings.SHOW_TRIP_PAX, false))
			currT.setPassengerCount(pax);

		currT.commit();

		v.setOdometerCurrentAndLastTrip(odo_total, currT, true);
			// also calls currV.commit() for those 2 fields only

		setCurrentTrip(db, v, null);
		if (currT.isFrequent())
			setCurrentFreqTrip(db, v, null);

		// For roadtrip, set current geoarea too
		final int endAreaID = currT.getRoadtripEndAreaID();
		if (endAreaID != 0)
		{
			try {
				setCurrentArea(db, v, new GeoArea(db, endAreaID));
			}
			catch (IllegalStateException e) { }
			catch (IllegalArgumentException e) { }
			catch (RDBKeyNotFoundException e) { }
		}
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
		if ((currentFT != null) && (currentFT_vid == vid) && db.hasSameOwner(currentFT.dbConn))
		{
			if (db != currentFT.dbConn)
				// cached from earlier activity in Android: see currentA javadoc for more info
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
				} else {
					currentFT = null;
				}
			} catch (Throwable th) {
				currentFT = null;
				if (clearIfBad)
					sCT.delete();
			}
		} catch (Throwable th) {
			return null;  // no setting found for this vehicle; don't use or change currentFT
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
	 * @param db  connection to use; must be open, must not be null
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
		if (db == null)
			throw new IllegalStateException("null db");
		if (v == null)
			throw new IllegalArgumentException("null vehicle");

		final int vid = v.getID();
		if ((currentFTS != null) && (currentFTS_vid == vid) && (currentFTS.size() > 0))
		{
			RDBAdapter firstConn = currentFTS.get(0).dbConn;
			if (db.hasSameOwner(firstConn))
			{
				if (db != firstConn)
				{
					// cached from earlier activity in Android: see currentA javadoc for more info
					for (int i = currentFTS.size() - 1; i >= 0; --i)
						currentFTS.get(i).dbConn = db;
				}

				return currentFTS;
			}
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
			return null;  // no setting found for this vehicle; don't use or change currentFTS
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
		if ((currentTS != null) && (currentTS_vid == vid) && db.hasSameOwner(currentTS.dbConn))
		{
			if (db != currentTS.dbConn)
				// cached from earlier activity in Android: see currentA javadoc for more info
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
				} else {
					currentTS = null;
				}
			} catch (Throwable th) {
				currentTS = null;
				if (clearIfBad)
					sCTS.delete();
			}
		} catch (Throwable th) {
			return null;  // no setting found for this vehicle; don't use or change currentTS
		}

		return currentTS;
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
		if ((prevL != null) && (prevL_vid == vid) && db.hasSameOwner(prevL.dbConn))
		{
			if (db != prevL.dbConn)
				// cached from earlier activity in Android: see currentA javadoc for more info
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
				} else {
					prevL = null;
				}
			} catch (Throwable th) {
				prevL = null;
				if (clearIfBad)
					sPL.delete();
			}
		} catch (Throwable th) {
			return null;  // no setting found for this vehicle; don't use or change prevL
		}

		return prevL;
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

	/**
	 * Change the current vehicle to this one, and update setting fields if their new values aren't
	 * directly in the VehSettings table: Current GeoArea, Trip, TStop, etc.
	 * Calls {@link Settings#setCurrentVehicle(RDBAdapter, Vehicle)}.
	 * If new vehicle has current trip, validates current driver setting from trip's driver field.
	 *<P>
	 * Before changing vehicles, updates the old vehicle's current odometer and trip by calling
	 * {@link Vehicle#setOdometerCurrentAndLastTrip(int, Trip, boolean)}, and setting the
	 * current TStop's {@link TStop#TEMPFLAG_CURRENT_TSTOP_AT_CURRV_CHANGE} flag if currently stopped.
	 *<P>
	 * This method first attempts to find per-vehicle settings for the new vehicle's current trip, tstop, etc.
	 * If not found, it uses other data to determine these settings (backward-compatible mode).
	 *<P>
	 * If an old copy of the app was upgraded to v0.9.40 or newer, only the settings for the current vehicle at
	 * that time were copied to VehSettings.  When any other vehicle becomes current for the first time after
	 * the upgrade, this method will use backward-compatible mode to find that vehicle's settings.
	 *
	 * @param db  connection to use
	 * @param oldV  Current (old) vehicle, or null if no settings or info of the old vehicle should be updated
	 * @param newV  New current vehicle
	 * @throws IllegalArgumentException if {@code newV} is null
	 * @throws IllegalStateException if the db isn't open
	 * @see RDBSchema#checkSettings(RDBAdapter, int)
	 * @return true if new vehicle has a current Trip, false otherwise
	 */
	public static boolean changeCurrentVehicle(RDBAdapter db, final Vehicle oldV, final Vehicle newV)
		throws IllegalArgumentException, IllegalStateException
	{
		if (newV == null)
			throw new IllegalArgumentException("null vehicle");

		final Trip oldCurrT;
		if (oldV != null)
			oldCurrT = getCurrentTrip(db, oldV, false);
		else
			oldCurrT = null;

		if (oldCurrT != null)
		{
			// before changing, update old current-vehicle with its current-trip info
			oldV.setOdometerCurrentAndLastTrip
				(oldV.getOdometerCurrent(), oldCurrT, true);

			TStop currTS = getCurrentTStop(db, oldV, false); // CURRENT_TSTOP
			if (currTS != null)
			{
				currTS.setFlagSingle(TStop.TEMPFLAG_CURRENT_TSTOP_AT_CURRV_CHANGE);
				currTS.commit();
			}
		}

		int tripAreaIDCheck = -1;  // area ID to check for new vehicle

		Trip newCurrT = null;
		try
		{
			VehSettings vs = new VehSettings(db, CURRENT_TRIP, newV);
			final int trip_id = vs.ivalue;
			if (trip_id != 0)
			{
				newCurrT = new Trip(db, trip_id);
					// throws RDBKeyNotFoundException if trip_id doesn't exist:
					// inconsistency will be fixed below as if no setting was found
			}
		} catch (RDBKeyNotFoundException e) {
			// no CURRENT_TRIP (0 or otherwise) setting for new vehicle, so
			// look at the vehicle's most recent trip to see if it's still in progress.

			newCurrT = newV.getTripInProgress();
			setCurrentTrip(db, newV, newCurrT);  // save the vsetting, or 0 if null
		}
		final boolean hasCurrentTrip = (newCurrT != null);

		Settings.setCurrentVehicle(db, newV);

		if (hasCurrentTrip)
		{
			// try to find new vehicle's current TStop and previous location, if any,
			// also set tripAreaIDCheck

			tripAreaIDCheck = newCurrT.getAreaID();

			boolean isStopped = false;
			TStop ts = null;  // for current tstop or prev location
			try
			{
				VehSettings vs = new VehSettings(db, CURRENT_TSTOP, newV);
				final int tstop_id = vs.ivalue;
				if (tstop_id != 0)
				{
					ts = new TStop(db, tstop_id);
						// throws RDBKeyNotFoundException if tstop_id doesn't exist:
						// inconsistency will be fixed below as if no setting was found

					isStopped = true;
				}
			} catch (RDBKeyNotFoundException e) {
				// no CURRENT_TSTOP (0 or otherwise) setting for new vehicle, so
				// look at the vehicle's most recent trip to see if it's stopped.

				ts = newCurrT.readLatestTStop();  // tstop may have a different trip id
				if (ts != null)
					isStopped = (newCurrT.getID() == ts.getTripID())
						&& ts.isSingleFlagSet(TStop.TEMPFLAG_CURRENT_TSTOP_AT_CURRV_CHANGE);

				setCurrentTStop(db, newV, (isStopped) ? ts : null);
			}

			Location lo = null;

			try
			{
				VehSettings vs = new VehSettings(db, PREV_LOCATION, newV);
				final int loc_id = vs.ivalue;
				if (loc_id != 0)
				{
					lo = new Location(db, loc_id);
						// throws RDBKeyNotFoundException if loc_id doesn't exist:
						// inconsistency will be fixed below as if no setting was found
				}
			} catch (RDBKeyNotFoundException e) {
				// no PREV_LOCATION (0 or otherwise) setting for new vehicle, so
				// look at the vehicle's most recent tstop.

				if (ts == null)
				{
					// need ts to determine prev location

					ts = newCurrT.readLatestTStop();  // tstop may have a different trip id
					if (ts == null)
						// no TStops yet for this trip
						ts = newCurrT.readStartTStop(true);
				}

				if (ts != null)
				{
					// if not stopped, then ts.getLocationID is the trip's previous location.
					// If stopped, need to find the previous TStop to get the prev location.

					int locID;
					if (! isStopped) {
						locID = ts.getLocationID();
					} else {
						locID = 0;
						TStop tsPrev = TStop.readPreviousTStopWithinTrip(db, newCurrT, ts);
						if (tsPrev != null)
							locID = tsPrev.getLocationID();
					}
					if (locID != 0) {
						try {
							lo = new Location(db, locID);
						} catch (Exception e2) {  /* not found: db closed or inconsistent: TODO */  }
					}
				}

				// PREV_LOCATION was most likely recovered from trip data;
				// if no trip data, will be null: No impact beyond ViaRoute setup
				// on current trip.
				setPreviousLocation(db, newV, lo);
			}

			final int newDID = newCurrT.getDriverID();
			final Person newCurrD = getCurrentDriver(db, newV, false);
			if ((newCurrD == null) || (newCurrD.getID() != newDID))
			{
				// fix current driver from new vehicle's trip
				try {
					VehSettings.setCurrentDriver(db, newV, new Person(db, newDID));
				} catch (Exception e) {  /* not found: db closed or inconsistent: TODO */  }
			}
		} else {
			// no current trip
			// Check CURRENT_TSTOP, PREV_LOCATION, set tripAreaIDCheck and CURRENT_DRIVER

			if (getCurrentTStop(db, newV, false) != null)
				setCurrentTStop(db, newV, null);

			Location prevLoc = null;
			try {
				VehSettings vs = new VehSettings(db, PREV_LOCATION, newV);
				if (vs.ivalue != 0)
					prevLoc = new Location(db, vs.ivalue);
			} catch (RDBKeyNotFoundException e) {}

			final int lastTID = newV.getLastTripID();
			if (lastTID != 0)
			{
				try {
					final Trip lastTrip = new Trip(db, lastTID);
					if (lastTrip.isRoadtrip())
						tripAreaIDCheck = lastTrip.getRoadtripEndAreaID();
					else
						tripAreaIDCheck = lastTrip.getAreaID();

					final int newDID = lastTrip.getDriverID();
					final Person newCurrD = getCurrentDriver(db, newV, false);
					if ((newCurrD == null) || (newCurrD.getID() != newDID))
					{
						// fix current driver from new vehicle's last trip
						try {
							VehSettings.setCurrentDriver(db, newV, new Person(db, newDID));
						} catch (Exception e) {  /* not found: db closed or inconsistent: TODO */  }
					}

					TStop endTS = lastTrip.readLatestTStop();
					if ((endTS != null) && (prevLoc == null))
					{
						prevLoc = endTS.readLocation();
						if (prevLoc != null)
							setPreviousLocation(db, newV, prevLoc);
					}

				} catch (Exception e2) {  /* not found: db closed or inconsistent: TODO */  }
			}
		}

		if (tripAreaIDCheck != -1) {
			// did current area change?
			final int currA = getInt(db, CURRENT_AREA, newV, 0);
			if (currA != tripAreaIDCheck)
			{
				try {
					setCurrentArea(db, newV, new GeoArea(db, tripAreaIDCheck));
				} catch (Exception e) {  /* not found: db closed or inconsistent: TODO */  }
			}
		}

		return hasCurrentTrip;
	}

	/**
	 * Try to find this vehicle's current or most recent driver.
	 * Use {@link #CURRENT_DRIVER} setting if possible; otherwise use "backwards-compatible"
	 * data like {@link #changeCurrentVehicle(RDBAdapter, Vehicle, Vehicle)} does:
	 * {@link #CURRENT_TRIP}, {@link Vehicle#getTripInProgress()}, {@link Vehicle#getLastTripID()}.
	 *<P>
	 * Intended for use by android {@code ChangeDriverOrVehicle}, which may want a vehicle's driver
	 * even if that Vehicle hasn't been used since an upgrade from a pre-0.9.40 database, and so
	 * doesn't have any {@link VehSettings}.
	 *
	 * @param db  connection to use
	 * @param v  Vehicle to find driver for
	 * @return  Driver if found, or null
	 * @throws IllegalArgumentException if {@code v} is null
	 * @throws IllegalStateException if the db is null or isn't open
	 * @since 0.9.40
	 */
	public static Person findCurrentDriver(final RDBAdapter db, final Vehicle v)
	{
		Person currD = getCurrentDriver(db, v, false);  // checks db and v, throws exceptions if needed
		if (currD != null)
			return currD;

		Trip recentT = getCurrentTrip(db, v, false);

		if (recentT == null)
			recentT = v.getTripInProgress();

		if (recentT == null)
		{
			final int lastTID = v.getLastTripID();
			if (lastTID != 0)
			{
				try {
					recentT = new Trip(db, lastTID);
				} catch (Exception e) {}
			}

		}

		if (recentT == null)
			return null;

		try {
			return new Person(db, recentT.getDriverID());
		} catch (Exception e) {
			return null;
		}
	}

}  // public class VehSettings
