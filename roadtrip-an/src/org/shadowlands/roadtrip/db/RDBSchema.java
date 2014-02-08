/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010,2012-2014 Jeremy D Monin <jdmonin@nand.net>
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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Vector;

/**
 * Schema-related utility methods, common to SQLite and Android implementations.
 * Declared static/abstract for occasional use, avoid need to manage lifetime of 1 more object.
 *<P>
 * Also has some utility methods related to db content, such as
 * {@link #parseFixedDecOr0(CharSequence, int)}.
 *<P>
 * <b>If you update the schema:</b> please update:
 *<UL>
 *<LI> {@link #DATABASE_VERSION}
 *<LI> {@link #DB_SCHEMA_CREATE_FILENAME}
 *<LI> {@link #upgradeToCurrent(RDBAdapter, int, boolean)},
 *<LI> Under roadtrip-an, the <tt>RDBOpenHelper</tt> override of {@link RDBAdapter#getSQLScript(int)}.
 *<LI> Save the old schema and the upgrade script to <tt> /doc/hist/ </tt>
 *</UL>
 */
public abstract class RDBSchema
{
	/**
	 * Database version; 1204 represents version 1.2.04; below 1000 represents pre-1.0 (0.8.09, etc).
	 *<P> See the class javadoc for what to change in the code when you update the schema version.
	 */
	public static final int DATABASE_VERSION = 909;

	/** Filename of schema create sql script for the current {@link #DATABASE_VERSION}. */
	public static final String DB_SCHEMA_CREATE_FILENAME = "schema_v0909.sql";

	/**
	 * Filename prefix of schema upgrade sql script.
	 * To be followed by the target version (4 or more digits) + ".sql".
	 */
	public static final String DB_SCHEMA_UPGRADE_FILENAME_PREFIX = "upg_v";

	/**
	 * Execute this stream's SQL one line at a time.
	 * Ignore some items. (pragma, begin transaction, commit, -- comments)
	 *<P>
	 * The stream will be closed before returning from this method.
	 * @param db an open database
	 * @param sqlStream contents to execute one line at a time
	 * @throws SQLException  if an error occurs; the stream will still be closed.
	 */
	public static void execSQLbyLine(RDBAdapter db, InputStream sqlStream)
		throws SQLException
	{
		SQLException ex = null;

		// TODO some error flag field, on fail
		DataInputStream dsql = new DataInputStream(sqlStream);
		try
		{
			String sqline;
			for (;;)
			{
				sqline = dsql.readLine();
				if (sqline == null)
					break;

				int i = sqline.indexOf("--");
				if (i == 0)
					sqline = "";
				else if (i > 0)
					sqline = sqline.substring(0, i).trim();
				else
					sqline = sqline.trim();

				String sqlower = sqline.toLowerCase();
				if (sqlower.startsWith("pragma"))
					continue;
				//if (sqlower.startsWith("begin transaction"))
				//	continue;
				//if (sqlower.startsWith("commit"))
				//	continue;

				if (sqline.length() > 0)
					db.execStrucUpdate(sqline);
			}
		} catch (IOException ie) {
			ie.printStackTrace();
			// empty catch; not expected.
		} catch (SQLException se) {
			ex = se;
			// deal with it after finally closing the streams.
		} finally {
			try { dsql.close(); } catch (IOException e) {}
			try { sqlStream.close(); } catch (IOException e) {}
		}

		if (ex != null)
		{
			ex.printStackTrace();
			throw ex;
		}
	}

	/**
	 * Perform all needed SQL scripts to upgrade the db schema from an
	 * old version to the current version.
	 * @param  db  an open database
	 * @param  oldVersion  The old schema version
	 * @param  skipSetVersion  Are we running on android under SQLiteOpenHelper?  If so, skip the setVersion pragma.
	 * @see RDBAdapter#getSQLScript(int) 
	 * @throws IllegalStateException  if <tt>oldVersion</tt> is earlier than 901, thus too old to upgrade.
	 *      v901 is from 2010-11-16.
	 * @throws IOException  if a problem occurs locating or opening an upgrade script
	 * @throws SQLException  if a syntax or database error occurs
	 */
	public static void upgradeToCurrent(RDBAdapter db, final int oldVersion, final boolean skipSetVersion)
		throws IllegalStateException, IOException, SQLException
	{
		// Use switch fallthrough to begin schema changes
		// from a starting old version.
		// Android will call db.setVersion for us.
		// REMEMBER: Also update RDBOpenHelper.getSQLScript !
		boolean anythingDone = false;

		switch (oldVersion)
		{
			/* 
			 * obsolete versions, not encountered in the wild:
			 * 
		case 800:  // 800 -> 805
			upgradeStep(db, 805);
		case 805:  // 805 -> 806
			upgradeStep(db, 806);
		case 806:  // 806 -> 807
			upgradeStep(db, 807);
		case 807:  // 807 -> 809
			upgradeStep(db, 809);
		case 809:  // 809 -> 812
			upgradeStep(db, 812);
		case 812:  // 812 -> 813
			upgradeStep(db, 813);
		case 813:  // 813 -> 901
			upgradeStep(db, 901);
			*
			*/

		case 909:
			// Nothing to do, current. Don't set anythingDone.
			break;

		case 901:  // 901 -> 905   2010-11-30
			upgradeStep(db, 905);
		case 905:  // 905 -> 906   2010-12-16
			upgradeStep(db, 906);
		case 906:  // 906 -> 908   2012-04-01
			upgradeStep(db, 908);
		case 908:  // 908 -> 909   2012-12-06
			upgradeStep(db, 909);

		// after all cases, but NOT default case or already-current case
			anythingDone = true;
			break;

		default:
			// Too old; only very early betas affected. v901 is from 2010-11-16.
			final String tooOldMsg =
				"-- Error, old-version minimum is 901, this version too old to upgrade: " + oldVersion;
			throw new IllegalStateException(tooOldMsg);  // <--- Throw: too old ---
		}

		if (! anythingDone)
			return;

		// Need to indicate new db-appvers in the db.
		String dbvers = Integer.toString(DATABASE_VERSION);
		if (! skipSetVersion)
		{
			db.execStrucUpdate("PRAGMA user_version = " + dbvers + " ;");
		}
		if (dbvers.length() < 4)
			dbvers = "0" + dbvers;
		db.execStrucUpdate("UPDATE appinfo SET aivalue = '" + dbvers + "' WHERE aifield = 'DB_CURRENT_SCHEMAVERSION' ;");

		// Upgrade history timestamp (table added in v0908)
		final int now = (int) (System.currentTimeMillis() / 1000L); 
		db.execStrucUpdate("INSERT into app_db_upgrade_hist(db_vers_to, db_vers_from, upg_time) VALUES("
			+ DATABASE_VERSION + ", " + oldVersion + ", " + now + ");");
	}

	private static void upgradeStep(RDBAdapter db, final int toVers)
		throws IOException, SQLException
	{
		InputStream sql = db.getSQLScript(toVers);
		if (sql == null)
			return;
		execSQLbyLine(db, sql);
		// sql.close(); is done in execSQLbyLine.
	}

	/**
	 * Try to parse this CharSequence with {@link Integer#parseInt(String)}.
	 * If it doesn't work, return 0 instead of an error.
	 * @param cs charseq
	 * @param deci number of digits after the decimal
	 * @return the sequence as an integer; parse("5.2", 1) returns 52, parse("5.2", 2) returns 520.
	 */
	public static int parseFixedDecOr0(CharSequence cs, final int deci)
	{
		if (cs == null)
			return 0;
		int ret = 0;
		try {
			final String s = cs.toString();
			int dot = s.indexOf('.');
			if (dot == -1)
				ret = Integer.parseInt(s);
			else if (dot > 0)
				ret = Integer.parseInt(cs.subSequence(0, dot).toString());
			for (int i = deci ; i > 0; --i)
				ret *= 10;  // likely quicker than float convs & Math.pow(10, deci)
			if (dot != -1) 
			{
				int L = s.length();
				if (dot < (L-1))
				{
					int dtotal = 0;
					for (int i = 0; i < deci; ++i)
					{
						++dot;  // now it's char pos of a digit past dot
						dtotal *= 10;
						if (dot < L)
						{
							final char c = cs.charAt(dot);
							if (! Character.isDigit(c))
								return 0;  // <--- Early return: Not a digit ---
							dtotal += Character.digit(c, 10);
						}
					}
					ret += dtotal;
				}
			}
		}
		catch (NumberFormatException e) {
			ret = 0;
		}
		return ret;
	}

	/**
	 * Format a fixed decimal into a proper human-readable string.
	 * @param fixedDec  integer form, such as 12345, as parsed from {@link #parseFixedDecOr0(CharSequence, int)}
	 * @param deci number of places to keep after the decimal
	 * @return "1.2345" or "0.0123"
	 */
	public static String formatFixedDec(final int fixedDec, final int deci)
	{
		if (deci == 0)
			return Integer.toString(fixedDec);
		StringBuffer sb = new StringBuffer(Integer.toString(fixedDec));
		int L = sb.length();
		if (L > deci) {
			sb.insert(L - deci, '.');  // 12345 -> 1.2345
		} else {
			// value must be less than 1.0,
			// because it fits within deci places
			while (L < deci)
			{
				sb.insert(0, '0');  // 12 -> 0012
				++L;
			}
			sb.insert(0, "0.");  // 123 -> 0.123
		}
		return sb.toString();
	}

	/**
	 * For {@link RDBSchema#checkSettings(RDBAdapter, int, boolean)}:
	 * Possible value levels   Request, require, and report the current settings.
	 * For the return value, settings are missing if greater than {@link #SETT_OK}.
	 *<P>
	 * These are constant ints, and not <tt>enum</tt>, so we can easily compare
	 * less-than / greater-than / switch.
	 *<P>
	 * In value order, they are:
	 *<UL>
	 * <LI> {@link #SETT_INTERNAL_ERROR}
	 * <LI> {@link #SETT_RECOV_GUESSED}
	 * <LI> {@link #SETT_RECOV_OK}
	 * <LI> {@link #SETT_OK}
	 * <LI> {@link #SETT_GEOAREA}
	 * <LI> {@link #SETT_DRIVER}
	 * <LI> {@link #SETT_VEHICLE}
	 * <LI> {@link #SETT_TRIP}
	 * <LI> {@link #SETT_TSTOP_OPTIONAL}
	 * <LI> {@link #SETT_TSTOP_REQUIRED} == {@link #SETT_MAX}
	 *</UL>
	 */
	public static abstract class SettingsCheckLevel
	{
		/** An unexpected error occurred during the check; this will probably never be returned */
		public static final int SETT_INTERNAL_ERROR = -3;
		/** An inconsistency was recovered by guessing the correct value from recent activity; please check the current settings. */
		public static final int SETT_RECOV_GUESSED = -2;
		/** An inconsistency was recovered, unambiguously (no guessing was needed for its value) */
		public static final int SETT_RECOV_OK = -1;
		/** All the requested settings are present */
		public static final int SETT_OK = 0;
		/** In call: GeoArea requested. In return: GeoArea missing, please create one. */
		public static final int SETT_GEOAREA = 1;
		/** In call: GeoArea and Driver requested. In return: Driver missing, please create one. */
		public static final int SETT_DRIVER = 2;
		/** In call: GeoArea, Driver, Vehicle requested. In return: Vehicle missing, please create one. */
		public static final int SETT_VEHICLE = 3;
		/** In call: GeoArea, Driver, Vehicle, Trip requested. In return: Trip missing, please create one. */
		public static final int SETT_TRIP = 4;
		/** In call: GeoArea, Driver, Vehicle, Trip, and TStop (if present) requested. Won't be returned, because the TStop is optional. */
		public static final int SETT_TSTOP_OPTIONAL = 5;
		/** In call: GeoArea, Driver, Vehicle, Trip, and TStop (required) requested. In return: TStop missing, please create one. */
		public static final int SETT_TSTOP_REQUIRED = 6;
		/** Maximum permitted value; same as {@link #SETT_TSTOP_REQUIRED}. */
		public static final int SETT_MAX = 6;
	};

	/**
	 * For {@link RDBSchema#checkSettings(RDBAdapter, int, boolean)}:
	 * All possible portions of the return value, reporting the current settings.
	 */
	public static class SettingsCheckResult
	{
		/** Overall result of the check; an integer from {@link SettingsCheckLevel} */
		public int result;
		/** Any messages to log, or null; never 0-length */
		public Vector<String> messages;
		/** Current area, if requested */
		public GeoArea currA;
		/** Current driver, if requested */
		public Person currD;
		/** Current vehicle, if requested */
		public Vehicle currV;
		/** Current trip, if requested */
		public Trip currT;
		/** Current TStop, if any, if requested */
		public TStop currTS;

		/**
		 * Create a new, initially empty SettingsCheckResult.
		 * You will need to fill each field.
		 * <tt>result</tt> is initially {@link SettingsCheckLevel#SETT_INTERNAL_ERROR}.
		 */
		public SettingsCheckResult() { result = SettingsCheckLevel.SETT_INTERNAL_ERROR; }
	}

	/**
	 * Consistency-check, and possibly retrieve, the current settings from the database.
	 *<P>
	 * Most settings are per-vehicle in v0.9.40 and later, so the first check will be for
	 * {@link Settings#getCurrentVehicle(RDBAdapter, boolean)}.  If no vehicles are found in the db,
	 * the returned {@link SettingsCheckResult#result} will be {@link SettingsCheckLevel#SETT_VEHICLE}.
	 *
	 * @param db  An open database
	 * @param level  How many settings to request?
	 *          The lowest value is {@link SettingsCheckLevel#SETT_GEOAREA},
	 *          highest value is {@link SettingsCheckLevel#SETT_TSTOP_REQUIRED}.
	 * @param retrieveToo  Also return the current settings as data objects.
	 *          Any settings not requested will be null In the result.
	 * @return the overall result, any logging messages, and the current settings if requested.
	 *          <P> The overall result is a constant from {@link SettingsCheckLevel}.
	 *          <P> If settings were recovered/fixed, the last message should be logged as a
	 *          warning-level (not an info-level) message.
	 *          <P> If the result is {@link SettingsCheckLevel#SETT_GEOAREA},
	 *          create a GeoArea and set it current, then call this method again.
	 *          (The name of the GeoArea is localized, so it can't be created here.)
	 * @throws IllegalArgumentException  if <tt>level</tt> is out of range 
	 */
	public static SettingsCheckResult checkSettings
		(RDBAdapter db, final int level, final boolean retrieveToo)
		throws IllegalArgumentException 
	{
		if ((level < SettingsCheckLevel.SETT_GEOAREA) || (level > SettingsCheckLevel.SETT_MAX))
			throw new IllegalArgumentException("level out of range: " + level);

		SettingsCheckResult rv = new SettingsCheckResult();
		Vector<String> msgv = new Vector<String>();
		boolean missingSettings = false;  // anything missing?
		boolean fixedSettings = false;  // can we recover the missing setting?
		boolean fixedGuessed = false;   // to recover, did we have to guess? Ignored if ! fixedSettings. Check guessedArea too.
		boolean guessedArea = false;    // did we guess from multiple GeoAreas?  Ignored if ! fixedSettings.

		rv.currV = Settings.getCurrentVehicle(db, true);
		if (rv.currV == null)
		{
			// Most other settings are per-vehicle: Try to recover it
    			msgv.addElement("recov: no CURRENT_VEHICLE");
    			missingSettings = true;

			Trip rTrip = Trip.recentInDB(db);
			if (rTrip != null)
			{
				try {
					rv.currV = new Vehicle(db, rTrip.getVehicleID());
				} catch (RDBKeyNotFoundException e) {}
			}

			if (rv.currV == null)
			{
				// No trips in DB, or most recent has bad vehicle ID. Use most recent vehicle.
				// (If we recover this, all trips' vehicle IDs will be checked below.)

				rv.currV = Vehicle.getMostRecent(db);

				if (rv.currV == null)
				{
    					msgv.addElement("recov: no active vehicles found");
					rv.result = SettingsCheckLevel.SETT_VEHICLE;
					rv.messages = msgv;

					return rv;   // <--- Early return: Other settings need vehicle ---
				}
			}

    			msgv.addElement("recov: fixed CURRENT_VEHICLE");
			fixedSettings = true;
			VehSettings.changeCurrentVehicle(db, null, rv.currV);  // calls Settings.setCurrentVehicle
		}

		// Check the area first, because if it doesn't exist,
		// it might get created with default values and we'll be called again soon.
		rv.currA = VehSettings.getCurrentArea(db, rv.currV, true);
		if (rv.currA == null)
		{
			// Any areas to guess from?
			GeoArea[] allA = GeoArea.getAll(db, -1);
			if (allA == null)
			{
				rv.result = SettingsCheckLevel.SETT_GEOAREA;
				return rv;  // <--- Early return: No geoarea in db ---
			}
			fixedSettings = true;
			if (allA.length == 1)
			{
				rv.currA = allA[0];
			} else
			{
				rv.currA = allA[allA.length - 1];
				guessedArea = true;
			}
		}

	final boolean hasDriver = VehSettings.exists(db, VehSettings.CURRENT_DRIVER, rv.currV);

	if (! hasDriver)
    	{
    		boolean hasTrip = VehSettings.exists(db, VehSettings.CURRENT_TRIP, rv.currV);
    		if (hasTrip)
    		{
				rv.currT = VehSettings.getCurrentTrip(db, rv.currV, false);
				if (rv.currT == null)
				{
					hasTrip = false;
				} else {
        			msgv.addElement("recov: has CURRENT_TRIP");
        			if (! hasDriver)
        			{
        				msgv.addElement("recov: no CURRENT_DRIVER");
        				fixedSettings = false;  // in case was set just above
        				final int did = rv.currT.getDriverID();
        				try
        				{
        					Person dr = new Person(db, did);
        					if (dr.isDriver())
        					{
		        				VehSettings.setCurrentDriver(db, rv.currV, dr);
		        				msgv.addElement("recov: fixed CURRENT_DRIVER");
		        				fixedSettings = true;
        					}
        				}
        				catch (RDBKeyNotFoundException e) { }
        			}
				}
    		}
    		if (! hasTrip)
    		{
    			// No current trip.
    			// For missing driver, we'll have to best-guess from table contents.
    			if (! hasDriver)
    			{
    				msgv.addElement("recov: no CURRENT_DRIVER");
    				rv.currD = null;

    				try
    				{
    					rv.currD = new Person(db, rv.currV.getDriverID());
    					if (rv.currD.isDriver())
    						fixedGuessed = true;
    					else
    						rv.currD = null;
    				} catch (RDBKeyNotFoundException e) { }

    				if (rv.currD == null)
    				{
        				Person[] allDr = Person.getAll(db, true);
        				if (allDr != null)
        				{
        					rv.currD = allDr[allDr.length - 1];  // guess most recent
        					fixedGuessed = (allDr.length > 1);
        				}
    				}
    				if (rv.currD != null)
    				{
    					VehSettings.setCurrentDriver(db, rv.currV, rv.currD);
    					msgv.addElement("recov: fixed CURRENT_DRIVER (guess)");  // TODO maybe not guessed
    					fixedSettings = true;
    				} else {
    					msgv.addElement("recov: no drivers found");
    					rv.result = SettingsCheckLevel.SETT_DRIVER;
    					fixedSettings = false;
    				}
    			}
    		} else {
    			// OK, we have a current trip.  Did we guess currA earlier?
    			if (guessedArea) {
    				try
    				{
    					rv.currA = new GeoArea(db, rv.currT.getAreaID());
    					VehSettings.setCurrentArea(db, rv.currV, rv.currA);
    					guessedArea = false;
    				}
    				catch (RDBKeyNotFoundException e) {
    					// TODO somehow deal with the inconsistency
    					msgv.addElement("The current trip's area is not found; id=" + rv.currT.getAreaID());
    				}
    			}
    		}

    		if (fixedSettings)
    		{
    			msgv.addElement("Recovered settings: Current driver");
    		} else {
    			msgv.addElement("Could not recover settings: Current driver");
    			missingSettings = true;
    		}
    	}

    	// TODO if no trip, check for a tstop

    	/**
    	 * Final check for anything missing.
    	 * Fall-through switch-case, highest to lowest.
    	 */
    	switch(level)
    	{
    	default:
    	case SettingsCheckLevel.SETT_TSTOP_REQUIRED:
    		if (rv.currTS == null)
    		{
    			rv.result = SettingsCheckLevel.SETT_TSTOP_REQUIRED;
    			break;
    		}
    	case SettingsCheckLevel.SETT_TSTOP_OPTIONAL:
    	case SettingsCheckLevel.SETT_TRIP:
    		if (rv.currT == null)
    		{
    			rv.result = SettingsCheckLevel.SETT_TRIP;
    			break;
    		}
    	case SettingsCheckLevel.SETT_VEHICLE:
    		if (rv.currV == null)
    		{
    			rv.result = SettingsCheckLevel.SETT_VEHICLE;
    			break;
    		}
    	case SettingsCheckLevel.SETT_DRIVER:
    		if (rv.currD == null)
    		{
    			rv.result = SettingsCheckLevel.SETT_DRIVER;
    			break;
    		}
    	case SettingsCheckLevel.SETT_GEOAREA:  // handled near top of method,
    		if (rv.currA == null)              // checked here again for completeness.
    		{
    			rv.result = SettingsCheckLevel.SETT_GEOAREA;
    			break;
    		}

		// Fell through all cases, nothing missing.
		// rv.result is set below, after the switch.
			missingSettings = false;

    	}

		if (! missingSettings)
		{
			if (! fixedSettings)
				rv.result = SettingsCheckLevel.SETT_OK;
			else if (! (fixedGuessed || guessedArea))
				rv.result = SettingsCheckLevel.SETT_RECOV_OK;
			else
				rv.result = SettingsCheckLevel.SETT_RECOV_GUESSED;
		} else {
			// what's missing?
			// rv.result should have been set in this method body to what's missing.
			if (rv.result == SettingsCheckLevel.SETT_INTERNAL_ERROR)
				msgv.addElement("Warning: missingSettings true, but rv.result wasn't set");
		}
		if (msgv.size() > 0)
			rv.messages = msgv;

		return rv;
	}
}
