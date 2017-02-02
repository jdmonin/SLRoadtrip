/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010,2012-2015,2017 Jeremy D Monin <jdmonin@nand.net>
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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Vector;
import java.util.zip.DataFormatException;

import org.shadowlands.roadtrip.util.FileUtils;

/**
 * Schema-related utility methods, common to SQLite and Android implementations.
 * Declared static/abstract for occasional use, avoid need to manage lifetime of 1 more object.
 *<P>
 * Also has some utility methods related to db content, such as
 * {@link #parseFixedDecOr0(CharSequence, int)}.
 *<P>
 * <b>If you update the schema:</b> Please remember to:
 *<UL>
 *<LI> Include version in comments for any new fields or changes to new-install default settings
 *<LI> Test the upgrade script with cut and paste in sqlite3
 *<LI> Add the upgrade script, such as {@code upg_v0909.sql}, to android {@code res/raw/}
 *     and bookedit {@code src/org/shadowlands/roadtrip/db/script/}
 *<LI> Save the old schema and the upgrade script to <tt> /doc/hist/ </tt>
 *<LI> Rename the old schema to new schema filename, such as {@code schema_v0909.sql}
 *</UL>
 * Then, please update:
 *<UL>
 *<LI> {@link #DATABASE_VERSION}
 *<LI> {@link #DB_SCHEMA_CREATE_FILENAME}
 *<LI> {@link #upgradeToCurrent(RDBAdapter, int, boolean)}
 *<LI> Under roadtrip-an, {@code RDBOpenHelper}'s class javadoc (schema filename)
 *     and override of {@link RDBAdapter#getSQLScript(int)}
 *     (update case 0, add case for new schema version)
 *</UL>
 * At that point the update can be tested in android.
 * Test upgrading an existing install, doing a fresh install,
 * and restoring a backup made from an older schema.
 * Be sure BookEdit can still view backups from older schemas.
 */
public abstract class RDBSchema
{
	/**
	 * Database version: 1204 would represent version 1.2.04; below 1000 represents pre-1.0 (0.8.09, etc).
	 * Stored in the database {@link AppInfo} table where {@code aifield =}
	 * '{@link AppInfo#KEY_DB_CURRENT_SCHEMAVERSION DB_CURRENT_SCHEMAVERSION}'
	 * and also stored on android as the database schema version.
	 *<P>
	 * See the class javadoc for what to change in the code when you update the schema version.
	 * @see #DB_VERSION_MIN_UPGRADE
	 */
	public static final int DATABASE_VERSION = 961;

	/** Filename of schema create sql script for the current {@link #DATABASE_VERSION}. */
	public static final String DB_SCHEMA_CREATE_FILENAME = "schema_v0961.sql";

	/**
	 * The minimum {@link #DATABASE_VERSION} (901) that can be upgraded by
	 * {@link #upgradeToCurrent(RDBAdapter, int, boolean)}. DB schema v901
	 * is from 2010-11-16, previous versions are very early pre-betas.
	 * @since 0.9.41
	 */
	public static final int DB_VERSION_MIN_UPGRADE = 901;

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

				String sqlower = sqline.toLowerCase(Locale.US);
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
	 * Make a copy of this database file, then upgrade the copy to the current schema version
	 * by calling {@link #upgradeToCurrent(RDBAdapter, int, boolean)}.
	 * @param sourceDB  DB file to copy for upgrade: should not have any open {@link RDBAdapter}.
	 * @param destTempFile  File path to copy to, for example from
	 *    {@link File#createTempFile(String, String, File) File.createTempFile("tmpdb-", ".upg", tempFileDir)}:
	 *    This file should not exist yet, and will be created in this method.
	 * @param sourceSchemaVers  Source schema version, from {@link AppInfo} table
	 *    where {@code aifield =} '{@link AppInfo#KEY_DB_CURRENT_SCHEMAVERSION DB_CURRENT_SCHEMAVERSION}'
	 * @param caller Calling class, which must supply us with an {@link RDBAdapter} to the copied upgraded file
	 * @throws ClassNotFoundException if implementation needs a JDBC driver and driver isn't found
	 * @throws DataFormatException if copy validation at {@link RDBVerifier#LEVEL_PHYS} level fails.
	 *           This exception is "borrowed" from java.util.zip to distinguish from other exceptions
	 *           thrown here, although the method has nothing to do with zip files.
	 * @throws IllegalStateException  if DB file's schema version is earlier than 901, too old to upgrade.
	 *      Schema v0.9.01 was released on 2010-11-16.
	 * @throws IOException  if a problem occurs copying the file, or locating or opening an upgrade script.
	 *           Consider calling this again with a different temporary location.
	 * @throws SQLException  if a syntax or database error occurs during the upgrade
	 * @since 0.9.40
	 */
	public static void upgradeCopyToCurrent
		(final File sourceDB, final File destTempFile, final int sourceSchemaVers, UpgradeCopyCaller caller)
		throws ClassNotFoundException, DataFormatException, IllegalStateException, IOException, SQLException
	{
		FileUtils.copyFile(sourceDB, destTempFile);  // May throw IOException (disk space, etc)

		// Open db copy & validate(LEVEL_PHYS)
		final String destTempAbsPath = destTempFile.getAbsolutePath();
		RDBAdapter bkupDB = caller.openRDB(destTempAbsPath);  // may throw various exceptions
		RDBVerifier v = new RDBVerifier(bkupDB);
		boolean ok = (0 == v.verify(RDBVerifier.LEVEL_PHYS));
		v.release();
		if (! ok)
			throw new DataFormatException("Cannot validate(LEVEL_PHYS) sqlite db: " + destTempAbsPath);

		// upgradeToCurrent
		try {
			upgradeToCurrent(bkupDB, sourceSchemaVers, false);  // may throw various exceptions
		} finally {
			bkupDB.close();
		}
	}

	/**
	 * Perform all needed SQL scripts to upgrade the db schema from an
	 * old version to the current version.
	 *<P>
	 * To check whether the db's {@code oldVersion} is too old,
	 * you can compare it to {@link #DB_VERSION_MIN_UPGRADE}.
	 *
	 * @param  db  an open database
	 * @param  oldVersion  The old schema version
	 * @param  skipSetVersion  Are we running on android under SQLiteOpenHelper?  If so, skip the setVersion pragma.
	 * @see RDBAdapter#getSQLScript(int)
	 * @see #upgradeCopyToCurrent(File, File)
	 * @throws IllegalStateException  if {@code oldVersion} is earlier than 901, too old to upgrade.
	 *      Schema v0.9.01 was released on 2010-11-16, previous versions are very early pre-betas.
	 *      This will also be thrown if {@code oldVersion} is newer than the current schema version.
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

		case 961:
			// Nothing to do, current version already. Don't fall through, don't set anythingDone.
			break;

			/*
			 * older versions that aren't obsolete:
			 * (if minimum changes, please update method javadocs and DB_VERSION_MIN_UPGRADE)
			 */

		case 901:  // 901 -> 905   2010-11-30
			upgradeStep(db, 905);
		case 905:  // 905 -> 906   2010-12-16
			upgradeStep(db, 906);
		case 906:  // 906 -> 908   2012-04-01
			upgradeStep(db, 908);
		case 908:  // 908 -> 909   2012-12-06
			upgradeStep(db, 909);
		case 909:  // 909 -> 940   2014-02-15
			upgradeStep(db, 940);
		case 940:  // 0940 -> 0943   2015-05-26
			upgradeStep(db, 943);
		case 943:  // 0943 -> 0961   2017-02-02
			upgradeStep(db, 961);

		// after all cases, but NOT default case or already-current case
			anythingDone = true;
			break;

		default:
			// Too old; only very early pre-betas affected. v901 is from 2010-11-16.
			// Too new would also be caught here.
			final String tooOldMsg =
				"-- Error, old-version minimum is 901, this version too old to upgrade: " + oldVersion;
			throw new IllegalStateException(tooOldMsg);  // <--- Throw: too old ---
		}

		if (! anythingDone)
			return;

		// Update the schema version number in both places within the db
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
	 * For {@link RDBSchema#checkSettings(RDBAdapter, int)}:
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
	 * For {@link RDBSchema#checkSettings(RDBAdapter, int)}:
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
	 *<P>
	 * If there's a current trip, {@link VehSettings#getCurrentArea(RDBAdapter, Vehicle, boolean)}
	 * is checked against that {@link Trip#getAreaID()} and fixed if different.
	 * If {@link VehSettings#getCurrentDriver(RDBAdapter, Vehicle, boolean)} returns a person
	 * without the {@link Person#isDriver()} flag, this method will set that flag in the db.
	 *
	 * @param db  An open database
	 * @param level  How many settings to request?
	 *          The lowest value is {@link SettingsCheckLevel#SETT_GEOAREA},
	 *          highest value is {@link SettingsCheckLevel#SETT_TSTOP_REQUIRED}.
	 * @return the overall result, any logging messages, and the current settings if requested.
	 *          <P> The overall result is a constant from {@link SettingsCheckLevel}.
	 *          <P> If settings were recovered/fixed, the last message should be logged as a
	 *          warning-level (not an info-level) message.
	 *          <P> If the result is {@link SettingsCheckLevel#SETT_GEOAREA},
	 *          create a GeoArea and set it current, then call this method again.
	 *          (The name of the GeoArea is localized, so it can't be created here.)
	 *          <P> Also returns the current settings as data objects.
	 *          Any settings not requested might be null in the result.
	 *          For example, if {@code level} is less than
	 *          {@link SettingsCheckLevel#SETT_TSTOP_OPTIONAL},
	 *          then {@link SettingsCheckResult#currTS} is probably not retrieved and null.
	 *
	 * @throws IllegalArgumentException  if <tt>level</tt> is out of range 
	 */
	public static SettingsCheckResult checkSettings
		(RDBAdapter db, final int level)
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
				// (Ignore bad vehicle ID here; all trips' vehicle IDs can be checked in
				//  RDBVerifier.verify_tdata_trip)

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

		// Check the area before other settings, because if it doesn't exist,
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
			} else {
				rv.currA = allA[allA.length - 1];
				guessedArea = true;
			}

			VehSettings.setCurrentArea(db, rv.currV, rv.currA);
		}

		// Check currT for recovering other settings, even if level < SETT_TRIP.
		// Does current trip have a different geoarea than currA?

		rv.currT = VehSettings.getCurrentTrip(db, rv.currV, false);
		if ((rv.currT != null) && (rv.currA.getID() != rv.currT.getAreaID()))
		{			
			try
			{
				rv.currA = new GeoArea(db, rv.currT.getAreaID());
				VehSettings.setCurrentArea(db, rv.currV, rv.currA);
				fixedSettings = true;
				guessedArea = false;
			} catch (RDBKeyNotFoundException e) {
				// TODO somehow deal with the inconsistency
				msgv.addElement("The current trip's area is not found; id=" + rv.currT.getAreaID());
			}
		}

		rv.currD = VehSettings.getCurrentDriver(db, rv.currV, false);
		if (rv.currD == null)
		{
			msgv.addElement("recov: no CURRENT_DRIVER");
			if (rv.currT != null)
			{
        			msgv.addElement("recov: has CURRENT_TRIP");
        			try
        			{
        				rv.currD = new Person(db, rv.currT.getDriverID());
        			} catch (RDBKeyNotFoundException e) { }
			} else {
				// No current trip.
				// For missing driver, we'll have to best-guess from table contents.
				// Default to current vehicle's driver, then to most recently added driver.

				try
    				{
					rv.currD = new Person(db, rv.currV.getDriverID());
					fixedGuessed = true;
    				} catch (RDBKeyNotFoundException e) { }

    				if (rv.currD == null)
    				{
    					rv.currD = Person.getMostRecent(db, true);
    					if (rv.currD != null)
    						fixedGuessed = (Person.getAll(db, true).length > 1);
    				}
			}

			if (rv.currD != null)
			{
				VehSettings.setCurrentDriver(db, rv.currV, rv.currD);
	    			msgv.addElement("Recovered settings: Current driver");
				fixedSettings = true;
			} else {
	    			msgv.addElement("Could not recover settings: Current driver: No drivers found");
				rv.result = SettingsCheckLevel.SETT_DRIVER;
				fixedSettings = false;
	    			missingSettings = true;
			}
		}

		if ((rv.currD != null) && ! rv.currD.isDriver())
		{
			rv.currD.setIsDriver(true);
			rv.currD.commit();
			msgv.addElement("recov: Set isDriver flag for CURRENT_DRIVER");
		}

    		// TODO if no trip, check for a current tstop to get trip id

    		if ((level >= SettingsCheckLevel.SETT_TSTOP_OPTIONAL) && (rv.currT != null))
    		{
    			rv.currTS = VehSettings.getCurrentTStop(db, rv.currV, true);
    				// might be null; reported in switch below
    		}

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
    		case SettingsCheckLevel.SETT_GEOAREA:  // handled near top of method, checked here again for completeness
    			if (rv.currA == null)
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

	/**
	 * Callback interface for callers of
	 * {@link RDBSchema#upgradeCopyToCurrent(File, File, int, UpgradeCopyCaller) upgradeCopyToCurrent},
	 * for that method to open the copied db file with the platform-specific {@code RDBAdapter} implementation.
	 *<P>
	 * On Android, be sure that the RDBAdapter won't automatically call {@code upgradeToCurrent}
	 * when this db is opened.
	 * @author jdmonin
	 * @since 0.9.40
	 */
	public interface UpgradeCopyCaller
	{
		/**
		 * Given the full path to a database file, open it with an implementation of RDBAdapter.
		 * @param fullPath  Full path of db file to open
		 * @throws ClassNotFoundException if implementation needs a JDBC driver and driver isn't found
		 * @throws SQLException if cannot open the file
		 */
		public RDBAdapter openRDB(final String fullPath)
			throws ClassNotFoundException, SQLException;
	}

}
