/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2012,2014 Jeremy D Monin <jdmonin@nand.net>
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

package org.shadowlands.roadtrip.db.android;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Vector;

import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.RDBSchema;
import org.shadowlands.roadtrip.db.RDBVerifier;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

/**
 * A SQLiteOpenHelper for the roadtrip db, with our cross-platform {@link RDBAdapter} interface.
 * Calling any insert/update/query method will call {@link #getWritableDatabase()}.
 * Be sure to call {@link #close()} when you are done.
 *<P>
 * The schema is stored in {@code res/raw/schema_v0940.sql}.
 * This location is hardcoded in {@link #getSQLScript(int)}.
 * If you update the schema, please update {@link #getSQLScript(int)}
 * and {@link RDBSchema#DATABASE_VERSION}; see the {@code DATABASE_VERSION} javadoc
 * for other things you will need to update.
 */
public class RDBOpenHelper
	implements RDBAdapter
{
	/** android log tag */
	public static final String TAG = "Roadtrip.RDBOpenHelper";

	/**
	 * Must be set to <tt>Activity.getApplicationContext().getResources()</tt>.
	 * This app sets it in AndroidStartup.  Also use in BackupsRestore.
	 */
	public static Resources dbSQLRsrcs = null;

	/** the filename will be "roadtrip", without an extension */
	public static final String DATABASE_DEFAULT_DBNAME = "roadtrip";

	private static final String WHERE_ID = "_id=?";

	/** SQLite default db, or null when opening a different file path ({@link #dbPath} not null) */
	private final OpenHelper opener;
	/** File path to db, or null if {@link #opener} not null. */
	private final String dbPath;
	/** Calling {@link #getWritableDatabase()} or {@link #getReadableDatabase()} will set this field. */
	private SQLiteDatabase db;

	/**
	 * Helper 'owner' info, to distinguish the app's default database from another non-default-path db.
	 * See constructors and {@link #hasSameOwner(RDBAdapter)}.
	 *<P>
	 * Before v0.9.40, owner was the {@code Activity}: In 0.9.40 it's ApplicationInfo.packageName
	 * plus (for a non-default db) the full path to the db file.
	 */
	private final String owner;

	/**
	 * Open the default database for this context.
	 *<P>
	 * Creates a SQLiteOpenHelper, so:
	 * If the db doesn't exist, {@link #onCreate(SQLiteDatabase)} will be called.
	 * If the db exists but needs an upgrade, {@link #onUpgrade(SQLiteDatabase, int, int)} will be called.
	 *
	 * @param context context to use
	 */
	public RDBOpenHelper(Context context) {
		owner = context.getApplicationInfo().packageName;
		opener = new OpenHelper(context);  // used by getWritableDatabase(), getReadableDatabase()
		dbPath = null;
		db = null;
	}

	/**
	 * Open a non-default database, which must exist and be the current version already.
	 * The SQLiteOpenHelper can't be used for non-default paths;
	 * {@link #onCreate(SQLiteDatabase)} or {@link #onUpgrade(SQLiteDatabase, int, int)}
	 * won't be called.
	 *
	 * @param context context to use
	 * @param fullPath  Full path and filename to the database
	 */
	public RDBOpenHelper(Context context, final String fullPath) {
		owner = context.getApplicationInfo().packageName + '/' + fullPath;
		opener = null;
		dbPath = fullPath;  // used by getWritableDatabase(), getReadableDatabase()
		db = null;		
	}

	/** Called from the {@link SQLiteOpenHelper}. */
	public void onCreate(SQLiteDatabase dbWritable)
	{
		db = dbWritable;
		if (dbSQLRsrcs != null)
		{
			InputStream dbSchema = getSQLScript(0);
			try {
				RDBSchema.execSQLbyLine(this, dbSchema);
				Log.i(TAG, "onCreate finished, version " + RDBSchema.DATABASE_VERSION);
			} catch (SQLException e) {
				// TODO some error flag field, on fail
				Log.e(TAG, "SQL error in onCreate", e);
				e.printStackTrace();
			}
        	try
        	{
        		dbSchema.close();
        	} catch (IOException e) {}
		} else {
			 Log.e(TAG, "Database onCreate but null dbSQLRsrcs");
		}
	}

	/**
	 * Called from the {@link SQLiteOpenHelper}.
	 * Be sure to set <tt>{@link #dbSQLRsrcs} = getApplicationContext().getResources();</tt> before calling.
	 */
	public void onUpgrade(SQLiteDatabase dbWritable, int oldVersion, int newVersion) {
		Log.i(TAG, "Database onUpgrade called: (" + oldVersion + ", " + newVersion + ")");
		db = dbWritable;
		try
		{
			RDBSchema.upgradeToCurrent(this, oldVersion, true);
			Log.i(TAG, "Database onUpgrade success.");
		} catch (Throwable e) {
			// TODO some error flag field, on fail
			db = null;
			Log.e(TAG, "Database onUpgrade failed: " + e.getMessage(), e);
			throw new RuntimeException("upgrade failed: " + e.getMessage(), e);
		}
	}

	private SQLiteDatabase getReadableDatabase()
	{
		if ((db != null) && db.isOpen())
			return db;

		if (opener != null)
		{
			db = opener.getReadableDatabase();
			return db;
		}
		db = SQLiteDatabase.openDatabase
			(dbPath, null, SQLiteDatabase.OPEN_READONLY);
		return db;
	}

	private SQLiteDatabase getWritableDatabase()
	{
		if ((db != null) && db.isOpen())
		{
			if (! db.isReadOnly())
				return db;
			db.close();
			db = null;
		}

		if (opener != null)
		{
			db = opener.getWritableDatabase();  // TODO chk SQLiteException
			return db;
		}
		db = SQLiteDatabase.openDatabase
			(dbPath, null, SQLiteDatabase.OPEN_READWRITE);
			// TODO chk SQLiteException
		return db;
	}

	//
	// Implement org.shadowlands.roadtrip.db.RDBAdapter:
	//

	/**
	 * Get one row by ID.  Returns null if the row isn't found.
	 * @param tabname Table to query
	 * @param id      ID field value, for primary key field "_id"
	 * @param fields  Field names to return
	 * @return  Corresponding field values to field names, or null if errors or if table not found.
	 *       Field values are returned in the order specified in <tt>fields[]</tt>.
	 *       If any field is null or not in the table, that element is null.
	 * @throws IllegalStateException if conn has been closed
	 */
	public String[] getRow(String tabname, int id, String[] fields)
			throws IllegalStateException {

		if (db == null)
			db = getWritableDatabase();  // TODO chk exceptions

		final String[] whereValue = new String[]{ Integer.toString(id) };
		Cursor dbqc = db.query(tabname, fields, WHERE_ID, whereValue, null, null, null);
		String[] rv;

    	if (dbqc.moveToFirst())
    	{
    		rv = new String[fields.length];
    		for (int i = fields.length - 1; i >= 0; --i)
    			rv[i] = dbqc.isNull(i) ? null : dbqc.getString(i);
    	} else {
    		rv = null;
    	}

    	dbqc.close();
    	return rv;
	}

	/**
	 * Get one row, given a string-type key field name.
	 * Returns null if the row isn't found.
	 * @param tabname Table to query
	 * @param kf  Key fieldname
	 * @param kv  Key value
	 * @param fields  Field names to return
	 * @return  Corresponding field values to field names, or null if errors or if table not found.
	 *       Field values are returned in the order specified in <tt>fields[]</tt>.
	 *       If any field is null or not in the table, that element is null.
	 * @throws IllegalStateException if conn has been closed
	 */
	public String[] getRow(final String tabname, final String kf, final String kv, final String[] fields)
	    throws IllegalStateException
    {
		if (db == null)
			db = getWritableDatabase();  // TODO chk exceptions

		final String tWhere = kf + "=?";
		final String[] tWhereArg = new String[]{ kv };

		Cursor dbqc = db.query(tabname, fields, tWhere, tWhereArg, null, null, null /* ORDERBY */ );
		String[] rv;

    	if (dbqc.moveToFirst())
    	{
    		rv = new String[fields.length];
    		for (int i = fields.length - 1; i >= 0; --i)
    			rv[i] = dbqc.isNull(i) ? null : dbqc.getString(i);
    	} else {
    		rv = null;
    	}

    	dbqc.close();
    	return rv;
    }

	/**
	 * Get one or more rows matching a key field-value pair, or all rows except matching.
	 * Returns null if no rows are found.
	 *<P>
	 * <tt>kf</tt> must not be null; if you want all rows in the table, use
	 * {@link #getRows(String, String, String[], String[], String, int)} instead.
	 *
	 * @param tabname Table to query
	 * @param kf  Key fieldname; must not be null.
	 *              To get all rows except <tt>kv</tt> (<tt>kf</tt> &lt;&gt; <tt>kv</tt>),
	 *              the <tt>kf</tt> string should contain &lt;&gt; after the fieldname.
	 * @param kv  Key value; can be null for "is NULL". For "is not NULL", place &lt;&gt; into kf.
	 * @param fieldnames  Field names to return
	 * @param orderby  Order-by field(s), or null; may contain "desc" for sorting
	 * @param limit  Maximum number of rows to return, or 0 for no limit
	 * @return  Corresponding field values to field names, or null if errors or if table not found.
	 *       Field values are returned in the order specified in <tt>fields[]</tt>.
	 *       If any field is null or not in the table, that element is null.
	 * @throws IllegalArgumentException if <tt>kf</tt> is null
	 * @throws IllegalStateException if conn has been closed
	 * @see #getRows(String, String, String[], String[], String, int)
	 */
	public Vector<String[]> getRows
	    (final String tabname, final String kf, final String kv, final String[] fieldnames, final String orderby, final int limit)
	    throws IllegalArgumentException, IllegalStateException
    {
		if (kf == null)
			throw new IllegalArgumentException("null kf");
		final String where;
		final String[] whereArgs;
		if (kv != null)
		{
			if (kf.endsWith("<>"))
				where = kf.substring(0, kf.length() - 2) + "<>?";
			else 
				where = kf + "=?";
			whereArgs = new String[]{ kv };
		} else {
			if (kf.endsWith("<>"))
				where = kf.substring(0, kf.length() - 2) + " is not null";
			else 
				where = kf + " is null";
			whereArgs = null;
		}

		return getRows(tabname, where, whereArgs, fieldnames, orderby, limit); 
    }

	/**
	 * Get one or more rows matching a SQL Where clause, or all rows in a table.
	 * Returns null if no rows are found.
	 *
	 * @param tabname Table to query
	 * @param where  Where-clause, or null for all rows; may contain <tt>?</tt> which will be
	 *       filled from <tt>whereArgs</tt> contents, as with PreparedStatements.
	 *       Do not include the "where" keyword.
	 * @param whereArgs  Strings to bind against each <tt>?</tt> in <tt>where</tt>, or null if <tt>where</tt> has none of those
	 * @param fieldnames  Field names to return
	 * @param orderby  Order-by field(s) sql clause, or null; may contain "desc" for sorting
	 * @param limit  Maximum number of rows to return, or 0 for no limit
	 * @return  Corresponding field values to field names, or null if errors or if table not found.
	 *       Field values are returned in the order specified in <tt>fields[]</tt>.
	 *       If any field is null or not in the table, that element is null.
	 * @throws IllegalArgumentException if <tt>whereArgs</tt> != null, but <tt>where</tt> == null
	 * @throws IllegalStateException if conn has been closed
	 * @see #getRows(String, String, String, String[], String, int)
	 */
	public Vector<String[]> getRows
	    (final String tabname, final String where, final String[] whereArgs, final String[] fieldnames, final String orderby, final int limit)
	    throws IllegalArgumentException, IllegalStateException
	{
		if ((whereArgs != null) && (where == null))
			throw new IllegalArgumentException("null where, non-null whereArgs");
		if (db == null)
			db = getWritableDatabase();  // TODO chk exceptions

		final String limitStr = (limit == 0) ? null : Integer.toString(limit);
		Cursor dbqc = db.query(tabname, fieldnames, where, whereArgs, null, null, orderby, limitStr);
		Vector<String[]> rv;

    	if (dbqc.moveToFirst())
    	{
    		rv = new Vector<String[]>();

    		final int L = fieldnames.length;
    		do
    		{
	    		String[] fv = new String[L];
	    		for (int i = 0; i < fieldnames.length; ++i)
	    			fv[i] = dbqc.isNull(i) ? null : dbqc.getString(i);
	    		rv.add(fv);
    		} while (dbqc.moveToNext());
    	} else {
    		rv = null;
    	}

    	dbqc.close();
    	return rv;
	
    }

	/**
	 * Get one field in one row, given a string-type key field name.
	 * Returns null if key value not found.
	 *
	 * @param tabname  Table to query
	 * @param kf  Key fieldname
	 * @param kv  Key value
	 * @param fn  Field name to get
	 * @return field value, or null if not found
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @see #getRowIntField(String, String, String, String, int)
	 * @see #getRowLongField(String, String, String, String, int)
	 */
	public String getRowField(final String tabname, final String kf, final String kv, final String fn)
	    throws IllegalStateException
	{
		String[] row = getRow(tabname, kf, kv, new String[]{ fn });
		if (row != null)
			return row[0];
		else
			return null;
	}

	/**
	 * Get one field in one row, given a where-clause.
	 * The where-clause should match at most one row, or <tt>fn</tt> should
	 * contain an aggregate function which evaluates the multiple rows.
	 * Returns null if not found.
	 *
	 * @param tabname  Table to query
	 * @param fn  Field name to get, or aggregate function such as <tt>max(v)</tt>
	 * @param where  Where-clause, or null for all rows; may contain <tt>?</tt> which will be
	 *       filled from <tt>whereArgs</tt> contents, as with PreparedStatements.
	 *       Do not include the "where" keyword.
	 * @param whereArgs  Strings to bind against each <tt>?</tt> in <tt>where</tt>, or null if <tt>where</tt> has none of those
	 * @return field value, or null if not found; the value may be null.
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @throws IllegalArgumentException  if where is null, but whereArgs is not
	 * @since 0.9.06
	 * @see #getRowIntField(String, String, String, String[], int)
	 * @see #getRowLongField(String, String, String, String[], int)
	 */
	public String getRowField(final String tabname, final String fn, final String where, final String[] whereArgs)
	    throws IllegalStateException, IllegalArgumentException
    {
		if (db == null)
			db = getWritableDatabase();  // TODO chk exceptions

		Cursor dbqc = db.query(tabname, new String[]{ fn }, where, whereArgs, null, null, null /* ORDERBY */ );
		String rv;

    	if (dbqc.moveToFirst() && ! dbqc.isNull(0))
		// If fn is aggregate with no matching rows, aggregates except COUNT return null.
		// moveToFirst is true but getString might return null, might throw an exception.

    		rv = dbqc.getString(0);
    	else
    		rv = null;

    	dbqc.close();
    	return rv;
    }

	/**
	 * Get one integer field in one row, given a string-type key field name.
	 * Returns <tt>def</tt> if key value not found.
	 *
	 * @param tabname  Table to query
	 * @param kf  Key fieldname
	 * @param kv  Key value
	 * @param fn  Field name to get
	 * @param def  Value to return if key value not found
	 * @return field value, or <tt>def</tt> if not found
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @see #getRowField(String, String, String, String)
	 * @since 0.9.07
	 */
	public int getRowIntField(final String tabname, final String kf, final String kv, final String fn, final int def)
	    throws IllegalStateException
    {
		final String[] whereArgs = { kv } ;
		return getRowIntField(tabname, fn, kf + " = ?", whereArgs, def);
    }

	/**
	 * Get one integer field in one row, given a where-clause.
	 * The where-clause should match at most one row, or <tt>fn</tt> should
	 * contain an aggregate function which evaluates the multiple rows.
	 * Returns <tt>def</tt> if not found.
	 *
	 * @param tabname  Table to query
	 * @param fn  Field name to get, or aggregate function such as <tt>max(v)</tt>
	 * @param where  Where-clause, or null for all rows; may contain <tt>?</tt> which will be
	 *       filled from <tt>whereArgs</tt> contents, as with PreparedStatements.
	 *       Do not include the "where" keyword.
	 * @param whereArgs  Strings to bind against each <tt>?</tt> in <tt>where</tt>, or null if <tt>where</tt> has none of those
	 * @param def  Value to return if key value not found
	 * @return field value, or <tt>def</tt> if not found.
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @throws IllegalArgumentException  if where is null, but whereArgs is not
	 * @see #getRowField(String, String, String, String[])
	 * @since 0.9.07
	 */
	public int getRowIntField(final String tabname, final String fn, final String where, final String[] whereArgs, final int def)
	    throws IllegalStateException, IllegalArgumentException
    {
		if (db == null)
			db = getWritableDatabase();  // TODO chk exceptions

		Cursor dbqc = db.query(tabname, new String[]{ fn }, where, whereArgs, null, null, null /* ORDERBY */ );
		int rv;

    	if (dbqc.moveToFirst() && ! dbqc.isNull(0))
		// If fn is aggregate with no matching rows, aggregates except COUNT return null.
		// moveToFirst() is true but getInt might return 0, might throw an exception.

    		rv = dbqc.getInt(0);
    	else
    		rv = def;

    	dbqc.close();
    	return rv;
    }

	/**
	 * Get one long-integer field in one row, given a string-type key field name.
	 * Returns <tt>def</tt> if key value not found.
	 *
	 * @param tabname  Table to query
	 * @param kf  Key fieldname
	 * @param kv  Key value
	 * @param fn  Field name to get
	 * @param def  Value to return if key value not found
	 * @return field value, or <tt>def</tt> if not found
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @see #getRowField(String, String, String, String)
	 * @since 0.9.07
	 */
	public long getRowLongField(final String tabname, final String kf, final String kv, final String fn, final long def)
	    throws IllegalStateException
    {
		final String[] whereArgs = { kv } ;
		return getRowLongField(tabname, fn, kf + " = ?", whereArgs, def);
    }

	/**
	 * Get one long-integer field in one row, given a where-clause.
	 * The where-clause should match at most one row, or <tt>fn</tt> should
	 * contain an aggregate function which evaluates the multiple rows.
	 * Returns <tt>def</tt> if not found.
	 *
	 * @param tabname  Table to query
	 * @param fn  Field name to get, or aggregate function such as <tt>max(v)</tt>
	 * @param where  Where-clause, or null for all rows; may contain <tt>?</tt> which will be
	 *       filled from <tt>whereArgs</tt> contents, as with PreparedStatements.
	 *       Do not include the "where" keyword.
	 * @param whereArgs  Strings to bind against each <tt>?</tt> in <tt>where</tt>, or null if <tt>where</tt> has none of those
	 * @param def  Value to return if key value not found
	 * @return field value, or <tt>def</tt> if not found.
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @throws IllegalArgumentException  if where is null, but whereArgs is not
	 * @see #getRowField(String, String, String, String[])
	 * @since 0.9.07
	 */
	public long getRowLongField(final String tabname, final String fn, final String where, final String[] whereArgs, final long def)
	    throws IllegalStateException, IllegalArgumentException
    {
		if (db == null)
			db = getWritableDatabase();  // TODO chk exceptions

		Cursor dbqc = db.query(tabname, new String[]{ fn }, where, whereArgs, null, null, null /* ORDERBY */ );
		long rv;

    	if (dbqc.moveToFirst() && ! dbqc.isNull(0))
		// If fn is aggregate with no matching rows, aggregates except COUNT return null.
		// moveToFirst is true but getLong might throw an exception.

    		rv = dbqc.getLong(0);
    	else
    		rv = def;

    	dbqc.close();
    	return rv;
    }

	/**
	 * Count the rows in this table, optionally matching a key value.
	 * @param tabname  Table to select COUNT(*)
	 * @param kf  Key fieldname, or null to count all rows
	 * @param kv  Key string-value, or null; if <tt>kf</tt> != null, will count rows with null <tt>kf</tt>.
	 * @return row count, or 0
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @since 0.9.00
	 */
	public int getCount(final String tabname, final String kf, final String kv)
		throws IllegalStateException
	{
		return getCount(tabname, kf, true, kv, 0);
	}

	/**
	 * Count the rows in this table, optionally matching a key value.
	 * @param tabname  Table to select COUNT(*)
	 * @param kf  Key fieldname, or null to count all rows
	 * @param kv  Key int-value if <tt>kf</tt> != null
	 * @return row count, or 0
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @since 0.9.00
	 */
	public int getCount(final String tabname, final String kf, final int kv)
		throws IllegalStateException
	{
		return getCount(tabname, kf, false, null, kv);
	}

	private int getCount(final String tabname, final String kf, final boolean bindString, final String sv, final int iv)
		throws IllegalStateException
	{
		if (db == null)
			db = getReadableDatabase();  // TODO chk exceptions
		if (kf == null)
			return (int) DatabaseUtils.queryNumEntries(db, tabname);  // might be optimized

		// assert: kf != null.
		StringBuffer sb = new StringBuffer("SELECT COUNT(*) FROM ");
		sb.append(tabname);
		sb.append(" WHERE ");
		sb.append(kf);
		if (bindString && (sv == null))
			sb.append(" IS NULL");
		else
			sb.append(" = ?");
		SQLiteStatement sql;
		try {
			sql = db.compileStatement(sb.toString());
		} catch (android.database.SQLException e) {
			throw new IllegalStateException(e);
		}
		if (bindString)
		{
			if (sv != null)
				sql.bindString(1, sv);
		} else {
			sql.bindLong(1, iv);
		}
		try {
			return (int) sql.simpleQueryForLong();  // SELECT COUNT(*) FROM ...
		} catch (SQLiteDoneException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Insert a new row into a table.
	 * @param tabname  Table to insert into
	 * @param fn  Field names; recommend you include all fields except _id,
	 *          in the same field order as the CREATE TABLE statement.
	 * @param fv   Field values, in the same field order as <tt>fn[]</tt>.
	 *          May contain nulls.
	 * @param skipID  If true, <tt>fv[]</tt> does not contain the first (_id) field.
	 *          This means that insert and update arrays are the same length.
	 *          So, you can use the same code to build the <tt>fv[]</tt>
	 *          arrays used in {@link #insert(String, String[], boolean)}
	 *          and {@link #update(String, int, String[], String[])}.
	 * @return The new record's row ID (sqlite <tt>last_insert_rowid()</tt>), or -1 on error
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @throws IllegalArgumentException if fn.length != fv.length
	 */
	public int insert(final String tabname, final String[] fn, final String[] fv, final boolean skipID)
        throws IllegalStateException, IllegalArgumentException
    {
		if (fn.length != fv.length)
			throw new IllegalArgumentException("length mismatch");

		if (db == null)
			db = getWritableDatabase();  // TODO chk exceptions

		ContentValues cv = new ContentValues();
		for (int i = 0; i < fn.length; ++i)
		{
			String s = fv[i];
			if (s != null)
				cv.put(fn[i], s);  // on insert, don't mention null fields
		}
		return (int) db.insert(tabname, null, cv);
			// TODO chk retcode/exceptions?
			// TODO long vs int
    }

	/**
	 * Update fields of an existing row in a table, by id.
	 * @param tabname  Table to update
	 * @param id  Primary key "_id" value
	 * @param fn  Field names
	 * @param fv  Field values, in the same field order as <tt>fn[]</tt>.
	 *          May contain nulls.
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @throws IllegalArgumentException if fn.length != fv.length
	 */
	public void update(final String tabname, final int id, final String[] fn, final String[] fv)
        throws IllegalStateException, IllegalArgumentException
    {
		if (fn.length != fv.length)
			throw new IllegalArgumentException("length mismatch");

		if (db == null)
			db = getWritableDatabase();  // TODO chk exceptions

		ContentValues cv = new ContentValues();
		for (int i = 0; i < fn.length; ++i)
			cv.put(fn[i], fv[i]);  // values may be null, if schema allows
		final String[] whereValue = new String[]{ Integer.toString(id) };
		db.update(tabname, cv, WHERE_ID, whereValue);
			// TODO chk retcode/exceptions?
    }

	/**
	 * Update fields of an existing row in a table, by string key field.
	 * @param tabname  Table to update
	 * @param kf  Key fieldname
	 * @param kv  Key value
	 * @param fn  Field names to update
	 * @param fv  Field values, in the same field order as <tt>fn[]</tt>.
	 *          May contain nulls.
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @throws IllegalArgumentException if fn.length != fv.length
	 */
	public void update(final String tabname, final String kf, final String kv, final String[] fn, final String[] fv)
        throws IllegalStateException, IllegalArgumentException
    {
		if (fn.length != fv.length)
			throw new IllegalArgumentException("length mismatch");

		if (db == null)
			db = getWritableDatabase();  // TODO chk exceptions

		ContentValues cv = new ContentValues();
		for (int i = 0; i < fn.length; ++i)
			cv.put(fn[i], fv[i]);
		final String tWhere = kf + "=?";
		final String[] tWhereArg = new String[]{ kv };
		db.update(tabname, cv, tWhere, tWhereArg);
			// TODO chk retcode/exceptions?	
    }

	/**
	 * Update a field in an existing row in a table, given a string-type key field name.
	 * @param tabname  Table to update
	 * @param kf  Key fieldname
	 * @param kv  Key value
	 * @param fn  Field name to update
	 * @param fv  New field value, or null
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 */
	public void updateField(final String tabname, final String kf, final String kv, final String fn, final String fv)
        throws IllegalStateException
    {
		if (db == null)
			db = getWritableDatabase();  // TODO chk exceptions

		ContentValues cv = new ContentValues();
		cv.put(fn, fv);
		final String tWhere = kf + "=?";
		final String[] whereArg = new String[]{ kv };
		db.update(tabname, cv, tWhere, whereArg);
			// TODO chk retcode/exceptions?
    }

	/**
	 * Delete a current record, given its id.
	 * @param tabname  Table to delete from
	 * @param id      ID field value, for primary key field "_id"
	 * @throws IllegalStateException  if conn has been closed, table not found, etc.
	 */
	public void delete(final String tabname, final int id)
	    throws IllegalStateException
    {
		delete(tabname, "_id=?", Integer.toString(id));
    }

	/**
	 * Delete one or more rows matching a simple WHERE clause with an int parameter (such as a foreign key).
	 * @param tabname  Table to delete from
	 * @param where  Where-clause, not null; may contain a {@code ?} which will be
	 *       filled from {@code whereArg} contents, as with PreparedStatements.
	 *       Do not include the "where" keyword.
	 * @param whereArg  Value to bind against the {@code ?} in {@code where}, or 0 if {@code where} has no argument
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @throws IllegalArgumentException  if {@code where} is null
	 * @since 0.9.40
	 */
	public void delete(final String tabname, final String where, final int whereArg)
		throws IllegalStateException, IllegalArgumentException
	{
		delete(tabname, where, Integer.toString(whereArg));
	}

	/**
	 * Delete one or more rows matching a simple WHERE clause with a string parameter.
	 * @param tabname  Table to delete from
	 * @param where  Where-clause, not null; may contain a {@code ?} which will be
	 *       filled from {@code whereArg} contents, as with PreparedStatements.
	 *       Do not include the "where" keyword.
	 * @param whereArg  Value to bind against the {@code ?} in {@code where}, or null if {@code where} has no argument
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @throws IllegalArgumentException  if {@code where} is null
	 * @since 0.9.40
	 */
	public void delete(final String tabname, final String where, final String whereArg)
		throws IllegalStateException, IllegalArgumentException
	{
		if (db == null)
		{
			db = getWritableDatabase();
			if (db == null)
				throw new IllegalStateException("conn not open");
		}
		if (where == null)
			throw new IllegalArgumentException("null: where");

		final String[] whereArr = new String[]{ whereArg };
		db.delete(tabname, where, whereArr);
	}

	/** {@inheritDoc} */
	public final boolean hasSameOwner(RDBAdapter other)
	{
		return (other != null) && (other instanceof RDBOpenHelper)
			&& owner.equals(((RDBOpenHelper) other).owner);
	}

	/**
	 * Get the full path of this open database's filename.
	 * @return the filename, including full path, via {@link SQLiteDatabase#getPath()}.
	 */
	public String getFilenameFullPath() throws IllegalStateException
	{
		boolean dbNotOpen = false;
		if (db == null)
		{
			dbNotOpen = true;
			db = getWritableDatabase();  // TODO chk exceptions
		}
		else if (! db.isOpen())
		{
			dbNotOpen = true;
			db = null;
			db = getWritableDatabase();  // TODO chk exceptions
		}

		final String dbPath = db.getPath();
		if (dbNotOpen)
		{
			db.close();
			db = null;
		}

		return dbPath;
	}

	/**
	 * Close the connection.
	 * If our SQLiteHelper is open, {@link SQLiteOpenHelper#close() close()} it too.
	 * If our underlying db is open, {@link SQLiteDatabase#close() close()} it too.
	 */
	public void close()
	{
		if (opener != null)
			opener.close();
		if (db != null)
		{
			if (db.isOpen())
				db.close();  // TODO API: any exceptions?
			db = null;
		}
	}

	public void finalize()
	{
		if (db != null)
		{
			if (db.isOpen())
				db.close();  // TODO API: any exceptions?
			db = null;
		}
	}

	/**
	 * Retrieve a SQL create script or upgrade script.
	 * Please close the returned stream as soon as possible.
	 * For use by {@link RDBSchema}.
	 *<P>
	 * Be sure to set <tt>{@link #dbSQLRsrcs} = getApplicationContext().getResources();</tt> before calling.
	 *<P>
	 * The schema script and upgrade script filenames are
	 * hardcoded here as android resource references under {@code res/raw/} .
	 *
	 * @param upgScriptToVersion  0 for the create script,
	 *    otherwise a db version number, to get the script to upgrade
	 *    from the previous version.
	 * @return the sql as a stream, or null if the version is unknown or needs no upgrade script
	 *    or if {@link #dbSQLRsrcs} is null
	 * @see RDBSchema#upgradeToCurrent(RDBAdapter, int, boolean)
	 */
	public InputStream getSQLScript(final int upgScriptToVersion)
	{
		if (dbSQLRsrcs == null)
		{
			Log.e(TAG, "getSQLScript: null dbSQLRsrcs");
			return null;  // TODO error msg for user?
		}

		// REMINDER: Please also update RDBSchema.upgradeToCurrent!

		int res = 0;
		switch (upgScriptToVersion)
		{
		case   0: res = R.raw.schema_v0940;  break;  // create, not upgrade

		/* 
		 * obsolete versions, not encountered in the wild:
		 * These upgrade scripts have been moved to /doc/hist.
		 * 
		case 807: res = R.raw.upg_v0807;  break;
		case 809: res = R.raw.upg_v0809;  break;
		case 812: res = R.raw.upg_v0812;  break;
		case 813: res = R.raw.upg_v0813;  break;
		case 901: res = R.raw.upg_v0901;  break;
		 *
		 */

		case 905: res = R.raw.upg_v0905;  break;   // 2010-11-30
		case 906: res = R.raw.upg_v0906;  break;   // 2010-12-16
		case 908: res = R.raw.upg_v0908;  break;   // 2012-04-01
		case 909: res = R.raw.upg_v0909;  break;   // 2012-12-06
		case 940: res = R.raw.upg_v0940;  break;   // 2014-02-15
		}

		if (res == 0)
		{
			Log.w(TAG, "getSQLScript: null for vers " + upgScriptToVersion);
			return null;
		} else {
			Log.i(TAG, "getSQLScript: retrieved vers " + upgScriptToVersion);
		}

		return dbSQLRsrcs.openRawResource(res);
	}

	/**
	 * Execute this SQL update/DDL statement.
	 * For use by the db package, <b>not</b> the application,
	 * for example to update the database structure.
	 *
	 * @param sql  SQL text to execute; it should not be a query and should not produce a ResultSet.
	 *     Only one statement can be sent; do not use ';' to separate multiple within <tt>sql</tt>.
	 * @throws IllegalStateException if db has been closed 
	 * @throws SQLException  If a syntax or database error occurs,
	 *     or (jdbc) if the SQL would produce a ResultSet
	 */
	public void execStrucUpdate(final String sql)
		throws IllegalStateException, java.sql.SQLException
	{
		// First check for un-allowed items
		{
			// TODO how _should_ we do transactions?  Is this sqlexception only during onCreate?
			final String sqlc = sql.toLowerCase(Locale.US).trim();
			if (sqlc.startsWith("begin transaction")
				|| sqlc.startsWith("end transaction")
				|| sqlc.startsWith("commit"))
				return;  // <--- Early return: prevent SQLException 'transaction within a transaction'
		}

		if (db == null)
			db = getWritableDatabase();  // TODO chk exceptions
    	try
        {
    		Log.i(TAG, "execStrucUpdate: " + sql);
			db.execSQL(sql);
		} catch (android.database.SQLException e) {
			throw new java.sql.SQLException(e.getMessage());
		}
	}

	/**
	 * Execute <tt>PRAGMA integrity_check;</tt>
	 * for use by the db package, <b>not</b> the application.
	 * Details at <A href="http://www.sqlite.org/pragma.html#pragma_integrity_check">
	 *    http://www.sqlite.org/pragma.html#pragma_integrity_check</A>.
	 * @throws IllegalStateException if db has been closed, or a database access error occurs;
	 *    {@link Throwable#getCause()} might contain more detail, or might be null.
	 * @return <tt>null</tt>, or the first row of problem text.
	 *    At the SQLite level, a successful check returns one row containing <tt>"ok"</tt>;
	 *    this is returned as <tt>null</tt> instead of a single-element array.
	 * @see RDBVerifier#verify(int)
	 */
	public String execPragmaIntegCheck()
		throws IllegalStateException
	{
		if (db == null)
			db = getWritableDatabase();  // TODO chk exceptions

    	Cursor dbqc = null;
		try
		{
			dbqc = db.rawQuery("pragma integrity_check", null);
		} catch (Throwable e)  // SQLException has been thrown in the wild, but isn't documented
		{
			try
			{
				if (dbqc != null)
					dbqc.close();
			} catch (Throwable ee) { }
			throw new IllegalStateException("SQLException", e);
		}

		String retval = null;
		try
		{
	    	if (dbqc.moveToFirst())
	    		retval = dbqc.getString(0);
		} catch (Throwable e) { }
		try {
	    	dbqc.close();
		} catch (Throwable ee) { }

		if ((retval != null) && retval.equalsIgnoreCase("ok"))
			return null;  // ok is good news
		else
			return retval;
	}

	//
	// misc
	//

	/** for debugging, print this table's columns to Log, using pragma table_info. */
	public void debugLogDescribeTableCols(final String tabname)
	{
		if (db == null)
			db = getWritableDatabase();  // TODO chk exceptions
		try
		{
			Cursor dbqc = db.rawQuery("pragma table_info(" + tabname + ")", null);
			Log.i(TAG, "debugLogDescribeTableCols: -- " + tabname);
			if (dbqc.moveToFirst())
			{
				final int L = dbqc.getColumnCount();
				StringBuffer sb = new StringBuffer();
				{
					final String[] cols = dbqc.getColumnNames();
					for (int i = 0; i < L; ++i)
					{
						if (i > 0)
							sb.append('|');
						sb.append(cols[i]);
					}
					Log.i(TAG, sb.toString());
				}
				do
				{
					sb.delete(0, sb.length());
					for (int i = 0; i < L; ++i)
					{
						if (i > 0)
							sb.append('|');
						sb.append(dbqc.getString(i));
					}
					Log.i(TAG, sb.toString());
				} while (dbqc.moveToNext());
			}
			Log.i(TAG, "debugLogDescribeTableCols: -- end of " + tabname);
			dbqc.close();
		} catch (Throwable e) {
			Log.e(TAG, "debugLogDescribeTableCols: " + e.toString(), e);
		}
	}

	/** Encapsulate SQLiteOpenHelper functions, so we can also open from nonstandard paths. */
	private class OpenHelper extends SQLiteOpenHelper
	{
		public OpenHelper(Context context)
		{
			super(context, DATABASE_DEFAULT_DBNAME, null, RDBSchema.DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase dbWritable)
		{
			RDBOpenHelper.this.onCreate(dbWritable);
		}

		@Override
		public void onUpgrade(SQLiteDatabase dbWritable, int oldVersion, int newVersion)
		{
			RDBOpenHelper.this.onUpgrade(dbWritable, oldVersion, newVersion);
		}

	}  // private class OpenHelper

}
