/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010,2013 Jeremy D Monin <jdmonin@nand.net>
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
 * Application Info and database metadata.
 * See schema SQL for more keys stored in this table.
 *<P>
 * Some keys in this table track the backup status and previous backup time.
 * See {@link #KEY_DB_BACKUP_THISFILE} for details.
 *
 * @see Settings
 * @author jdmonin
 */
public class AppInfo extends RDBRecord
{
	private static final String TABNAME = "appinfo";
	private static final String KEYFIELD = "aifield";
	private static final String VALFIELD =  "aivalue";
	private static final String[] FIELDS = { KEYFIELD, VALFIELD };  // _id req'd but not used
	private static final String[] VALFIELD_AND_ID = { VALFIELD, "_id" };

	/** filename of previous backup, if any. Filename only, not full path.
	 *  Time of backup is stored in {@link #KEY_DB_BACKUP_PREVTIME}.
	 */
	public static final String KEY_DB_BACKUP_PREVFILE = "DB_BACKUP_PREVFILE";

	/** time of {@link #KEY_DB_BACKUP_PREVFILE}, in unix format */
	public static final String KEY_DB_BACKUP_PREVTIME = "DB_BACKUP_PREVTIME";

	/**
	 * Optional: Directory path on device of currently-being-backed-up backup. Do not include trailing slash.
	 * Useful when the user wants to use a non-default backup folder for ongoing backups.
	 * Value is "" if using the default backup location {@code DBBackup.getDBBackupPath(Context)}.
	 *<P>
	 * This table entry is optional; see also the required key {@link #KEY_DB_BACKUP_THISFILE}.
	 * @since 0.9.20
	 */
	public static final String KEY_DB_BACKUP_THISDIR = "DB_BACKUP_THISDIR";

	/**
	 * filename of currently-being-backed-up backup, if any. Filename only, not full path.
	 * Written just before closing db for backup copy; if backup fails, clear it afterwards
	 * (copy its value back from {@link #DB_BACKUP_PREVFILE}).
	 * Time of backup is stored in {@link #KEY_DB_BACKUP_THISTIME}.
	 * Directory path is optionally stored in {@link #KEY_DB_BACKUP_THISDIR}.
	 */
	public static final String KEY_DB_BACKUP_THISFILE = "DB_BACKUP_THISFILE";

	/** time of {@link #KEY_DB_BACKUP_THISFILE}, in unix format */
	public static final String KEY_DB_BACKUP_THISTIME = "DB_BACKUP_THISTIME";

	private String aifield, aivalue;

    /**
     * Look up an AppInfo from the database.
     * @param db  db connection
     * @param keyname key value to retrieve. See schema SQL or <tt>KEY_*</tt> constant fields.
     * @throws IllegalStateException if db not open
     * @throws RDBKeyNotFoundException if fieldname not found
     */
    public AppInfo(RDBAdapter db, String keyname)
        throws IllegalStateException, RDBKeyNotFoundException
    {
    	super(db, -1);
    	aifield = keyname;
    	String[] fv = db.getRow(TABNAME, KEYFIELD, keyname, VALFIELD_AND_ID);
    	if (fv == null)
    		throw new RDBKeyNotFoundException(keyname);
    	try {
			id = Integer.parseInt(fv[1]);
		} catch (NumberFormatException e) {}
    	aivalue = fv[0];
    }

    /**
     * Create a new AppInfo (not yet inserted to the database).
     * @param fieldname field to set
     * @param fvalue    value to set; null is not allowed in the schema
     * @throws IllegalArgumentException if fvalue is null
     */
    public AppInfo(String fieldname, final String fvalue)
    	throws NullPointerException 
    {
    	super();
    	if (fvalue == null)
    		throw new IllegalArgumentException("null fvalue");
    	aifield = fieldname;
    	aivalue = fvalue;
    }

    public String getField() { return aifield; }
    public String getValue() { return aivalue; }

    /**
     * Change this AppInfo's value.
     * @param fvalue    value to set; null is not allowed in the schema
     * @throws IllegalArgumentException if fvalue is null
     */
    public void setValue(String fvalue)
    	throws IllegalArgumentException
    {
    	if (fvalue == null)
    		throw new IllegalArgumentException("null fvalue");
    	aivalue = fvalue;
    	dirty = true;
    }

    /**
     * Insert a new record based on field and value.
	 * Clears dirty field; sets id and dbConn fields.
     * @param db  db connection
     * @return new record's primary key (_id)
     * @throws IllegalStateException if the insert fails (db closed, etc)
     */
    public int insert(RDBAdapter db)
        throws IllegalStateException
    {
    	String[] fv = { aifield, aivalue };
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
		dbConn.updateField(TABNAME, KEYFIELD, aifield, VALFIELD, aivalue);
		dirty = false;
	}

	/**
	 * Insert or update a record with this field/value combination.
	 * Calls commit or insert.
     * @param db  db connection
     * @param fname  field to set
     * @param fvalue value to set; null is not allowed in the schema
	 * @return the created or updated AppInfo
     * @throws IllegalArgumentException if fvalue is null
	 * @throws IllegalStateException if the db isn't open
	 */
	public static AppInfo insertOrUpdate
		(RDBAdapter db, final String fname, final String fvalue)
    	throws IllegalArgumentException, IllegalStateException
	{
		if (fvalue == null)
    		throw new IllegalArgumentException("null fvalue");

		AppInfo ai = null;
		try {
			ai = new AppInfo(db, fname);
			ai.setValue(fvalue);
			ai.commit();
		} catch (RDBKeyNotFoundException e)
		{
			ai = new AppInfo(fname, fvalue);
			ai.insert(db);
		}

		return ai;
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

}  // public class AppInfo