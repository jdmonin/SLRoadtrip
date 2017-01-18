/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2011,2014-2015,2017 Jeremy D Monin <jdmonin@nand.net>
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

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Vector;

/**
 * SQLite connection, abstracts the db API differences.
 * There are subclasses for use by the standalone quick-editor,
 * and the on-device version (RDBJDBCAdapter, RDBOpenHelper).
 *<P>
 * Static Method: Although interfaces can't contain static methods,
 * implementations of this interface will contain a {@code readSchemaVersion}
 * method to read the schema version from any db file.
 *
 * @author jdmonin
 */
public interface RDBAdapter
{
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
	public String[] getRow(final String tabname, final int id, final String[] fields)
	    throws IllegalStateException;

	/**
	 * Get one row, given a string-type key field name, or all rows except matching.
	 * Returns null if the row isn't found.
	 *<P>
	 * {@code kf} must not be null; if you want all rows in the table
	 * or to use aggregate functions over all rows, call
	 * {@link #getRows(String, String, String[], String[], String, int)} instead.
	 *
	 * @param tabname Table to query
	 * @param kf  Key fieldname, not null; to search case-insensitively, use "fieldname COLLATE NOCASE".
	 *              To get all rows except {@code kv} ({@code kf} &lt;&gt; {@code kv}),
	 *              the {@code kf} string should end with &lt;&gt;
	 * @param kv  Key value; must not be null
	 * @param fields  Field names to return
	 * @return  Corresponding field values to field names, or null if errors or if table not found.
	 *       Field values are returned in the order specified in <tt>fields[]</tt>.
	 *       If any field is null or not in the table, that element is null.
	 * @throws IllegalStateException if conn has been closed
	 */
	public String[] getRow(final String tabname, final String kf, final String kv, final String[] fields)
	    throws IllegalStateException;

	/**
	 * Get one or more rows matching a key field-value pair, or all rows except matching.
	 * Returns null if no rows are found.
	 *<P>
	 * {@code kf} must not be null; if you want all rows in the table
	 * or to use aggregate functions over all rows, call
	 * {@link #getRows(String, String, String[], String[], String, int)} instead.
	 *
	 * @param tabname Table to query
	 * @param kf  Key fieldname; must not be null.
	 *              To search case-insensitively, use "fieldname COLLATE NOCASE".
	 *              To get all rows except <tt>kv</tt> (<tt>kf</tt> &lt;&gt; <tt>kv</tt>),
	 *              the <tt>kf</tt> string should end with &lt;&gt;
	 * @param kv  Key value; can be null for "is NULL". For "is not NULL", end {@code kf} with &lt;&gt;
	 * @param fieldnames  Field names to return
	 * @param orderby  Order-by field(s), or null; may contain "desc" and/or "COLLATE NOCASE" for sorting
	 * @param limit  Maximum number of rows to return, or 0 for no limit
	 * @return  Corresponding field values to field names, or null if errors or if table not found.
	 *       Field values are returned in the order specified in <tt>fields[]</tt>.
	 *       If any field is null or not in the table, that element is null.
	 * @throws IllegalArgumentException if <tt>kf</tt> is null
	 * @throws IllegalStateException if conn has been closed
	 * @see #getRows(String, String, String[], String[], String, int)
	 */
	public Vector<String[]> getRows
	    (final String tabname, final String kf, final String kv, final String[] fieldnames,
	     final String orderby, final int limit)
	    throws IllegalArgumentException, IllegalStateException;

	/**
	 * Get one or more rows matching a SQL Where clause, or all rows in a table.
	 * Returns null if no rows are found.
	 * Supports aggregate functions such as <tt>max(fld)</tt>.
	 * (If you want to call a single aggregate function, you can use
	 * {@link #getRowField(String, String, String, String[])},
	 * {@link #getRowIntField(String, String, String, String[], int) getRowIntField(..)},
	 * etc. for a simpler return value.)
	 *
	 * @param tabname Table to query
	 * @param where  Where-clause, or null for all rows; may contain <tt>?</tt> which will be
	 *       filled from <tt>whereArgs</tt> contents, as with PreparedStatements.
	 *       Do not include the "where" keyword.
	 * @param whereArgs  Strings to bind against each <tt>?</tt> in <tt>where</tt>, or null if <tt>where</tt> has none of those
	 * @param fieldnames  Field names to return, or aggregate functions such as <tt>max(fld)</tt>
	 * @param orderby  Order-by field(s) sql clause, or null; may contain "desc" and/or "COLLATE NOCASE" for sorting
	 * @param limit  Maximum number of rows to return, or 0 for no limit
	 * @return  Corresponding field values to field names, or null if errors or if table not found.
	 *       Field values are returned in the order specified in <tt>fields[]</tt>.
	 *       If any field is null or not in the table, that element is null.
	 * @throws IllegalArgumentException if <tt>whereArgs</tt> != null, but <tt>where</tt> == null
	 * @throws IllegalStateException if conn has been closed
	 * @see #getRows(String, String, String, String[], String, int)
	 */
	public Vector<String[]> getRows
	    (final String tabname, final String where, final String[] whereArgs, final String[] fieldnames,
	     final String orderby, final int limit)
	    throws IllegalArgumentException, IllegalStateException;

	/**
	 * Get one field in one row, by row ID.
	 * Returns null if key ID not found.
	 *
	 * @param tabname  Table to query
	 * @param id      ID field value, for primary key field "_id"
	 * @param fn  Field name to get
	 * @return field value, or null if not found
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @see #getRowField(String, String, String, String)
	 * @see #getRowField(String, String, String, String[])
	 * @see #getRowIntField(String, int, String, int)
	 * @since 0.9.60
	 */
	public String getRowField(final String tabname, final int id, final String fn)
	    throws IllegalStateException;

	/**
	 * Get one field in one row, given a string-type key field name.
	 * Returns null if key value not found.
	 *
	 * @param tabname  Table to query
	 * @param kf  Key fieldname; to search case-insensitively, use "fieldname COLLATE NOCASE"
	 * @param kv  Key value
	 * @param fn  Field name to get
	 * @return field value, or null if not found
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @see #getRowIntField(String, String, String, String, int)
	 * @see #getRowLongField(String, String, String, String, int)
	 */
	public String getRowField(final String tabname, final String kf, final String kv, final String fn)
	    throws IllegalStateException;

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
	    throws IllegalStateException, IllegalArgumentException;

	/**
	 * Get one integer field in one row, by row ID.
	 * Returns {@code def} if row not found or field's value is null.
	 * @param tabname Table to query
	 * @param id      ID field value, for primary key field "_id"
	 * @param fn  Field name to get
	 * @param def  Value to return if key value not found
	 * @return field value, or <tt>def</tt> if not found
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @see #getRowIntField(String, String, String, String, int)
	 * @see #getRowIntField(String, String, String, String[], int)
	 * @see #getRow(String, int, String[])
	 * @since 0.9.50
	 */
	public int getRowIntField(final String tabname, final int id, final String fn, final int def)
	    throws IllegalStateException;

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
	 * @see #getRowIntField(String, int, String, int)
	 * @see #getRowIntField(String, String, String, String[], int)
	 * @see #getRowField(String, String, String, String)
	 * @since 0.9.07
	 */
	public int getRowIntField(final String tabname, final String kf, final String kv, final String fn, final int def)
	    throws IllegalStateException;

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
	 * @see #getRowIntField(String, int, String, int)
	 * @see #getRowIntField(String, String, String, String, int)
	 * @see #getRowField(String, String, String, String[])
	 * @since 0.9.07
	 */
	public int getRowIntField(final String tabname, final String fn, final String where, final String[] whereArgs, final int def)
	    throws IllegalStateException, IllegalArgumentException;

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
	    throws IllegalStateException;

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
	public long getRowLongField
	    (final String tabname, final String fn, final String where, final String[] whereArgs, final long def)
	    throws IllegalStateException, IllegalArgumentException;

	/**
	 * Count the rows in this table, optionally matching a key value.
	 * @param tabname  Table to select COUNT(*)
	 * @param kf  Key fieldname, or null to count all rows
	 * @param kv  Key string-value, or null; if null and <tt>kf</tt> != null, will count rows with null <tt>kf</tt>.
	 * @return row count, or 0
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @since 0.9.00
	 */
	public int getCount(final String tabname, final String kf, final String kv)
		throws IllegalStateException;

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
		throws IllegalStateException;

	/**
	 * Insert a new row into a table.
	 *<BR><b>Reminder:</b> The _id field should be supplied as null here.
	 * @param tabname  Table to insert into
	 * @param fn  Field names; recommend you include all fields except _id,
	 *          in the same field order as the CREATE TABLE statement.
	 * @param fv   Field values, in the same field order as {@code fn[]}.
	 *          May contain nulls. If {@code fn[]} contains _id, its {@code fv} should be null.
	 * @param skipID  If true, <tt>fv[]</tt> does not contain the first (_id) field.
	 *          That is, fv[0] is a value field, not the key field.
	 *          This means that insert and update arrays are the same length.
	 *          So, you can use the same code to build the <tt>fv[]</tt>
	 *          arrays used in {@link #insert(String, String[], boolean)}
	 *          and {@link #update(String, int, String[], String[])}.
	 * @return The new record's row ID (sqlite <tt>last_insert_rowid()</tt>), or -1 on error
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @throws IllegalArgumentException if fn.length != fv.length
	 */
	public int insert(final String tabname, final String[] fn, final String[] fv, final boolean skipID)
	    throws IllegalStateException, IllegalArgumentException;

	/**
	 * Update fields of an existing row in a table, by id.
	 * @param tabname  Table to update; not null
	 * @param id  Primary key "_id" value
	 * @param fn  Field names to update; not null
	 * @param fv  Field values, in the same field order as <tt>fn[]</tt>.
	 *          May contain nulls.
	 *          Int field values can be included in this String array,
	 *          because sqlite is lax about column types.
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @throws IllegalArgumentException if fn.length != fv.length
	 */
	public void update(final String tabname, final int id, final String[] fn, final String[] fv)
	    throws IllegalStateException, IllegalArgumentException;

	/**
	 * Update fields of an existing row in a table, by string key field.
	 * @param tabname  Table to update; not null
	 * @param kf  Key fieldname; not null
	 * @param kv  Key value
	 * @param fn  Field names to update; not null
	 * @param fv  Field values, in the same field order as <tt>fn[]</tt>.
	 *          May contain nulls.
	 *          Int field values can be included in this String array,
	 *          because sqlite is lax about column types.
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @throws IllegalArgumentException if fn.length != fv.length
	 */
	public void update(final String tabname, final String kf, final String kv, final String[] fn, final String[] fv)
	    throws IllegalStateException, IllegalArgumentException;

	/**
	 * Update fields of an existing row in a table, using a {@code where} clause.
	 * @param tabname  Table to update; not null
	 * @param where  {@code Where} clause, or null to update all rows; may contain {@code ?} which will be
	 *       filled from {@code whereArgs} contents, as with PreparedStatements.
	 *       Do not include the "where" keyword.
	 * @param whereArgs  Strings to bind against each {@code ?} in {@code where},
	 *       or null if {@code where} has none of those. May contain nulls.
	 *       Int field args can be included in this String array,
	 *       because sqlite is lax about column types.
	 *       Must be same length as the number of {@code ?} placeholders.
	 * @param fn  Field names to update
	 * @param fv  Field values, in the same field order as {@code fn[]}.
	 *       May contain nulls. Int field values can be included in this String array.
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @throws IllegalArgumentException if fn is null or fn.length != fv.length
	 * @since 0.9.50
	 */
	public void update
	    (final String tabname, final String where, final String[] whereArgs, final String[] fn, final String[] fv)
	    throws IllegalStateException, IllegalArgumentException;

	/**
	 * Update a field in an existing row in a table, given a string-type key field name.
	 * @param tabname  Table to update; not null
	 * @param kf  Key fieldname
	 * @param kv  Key value
	 * @param fn  Field name to update
	 * @param fv  New field value, or null
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 */
	public void updateField(final String tabname, final String kf, final String kv, final String fn, final String fv)
	    throws IllegalStateException;

	/**
	 * Delete a current record, given its id.
	 * @param tabname  Table to delete from
	 * @param id      ID field value, for primary key field "_id"
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 */
	public void delete(final String tabname, final int id)
	    throws IllegalStateException;

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
	    throws IllegalStateException, IllegalArgumentException;

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
	    throws IllegalStateException, IllegalArgumentException;

	/**
	 * Get the full path of this open database's filename.
	 * @return the filename, including full path
	 * @throws IllegalStateException if db has been closed 
	 */
	public String getFilenameFullPath() throws IllegalStateException;

	/**
	 * Close the connection.  Remember to close any open recordsets or cursors beforehand.
	 * If already closed, does nothing.  If an exception occurs while closing, the exception is
	 * caught within {@code close()} and not thrown to the caller.
	 */
	public abstract void close();

	/**
	 * Do these db-connections share the same owner (same database)?
	 * Used for data integrity in case multiple databases are open,
	 * to make sure we don't compare keys or commit records into the wrong db.
	 * @param other  Other DB connection to check against
	 * @return  True if same owner (same database), false if not or if {@code other} is null
	 */
	public abstract boolean hasSameOwner(RDBAdapter other);
		// TODO expl 'owner' in javadoc ; related to db obj lifecycle, etc
		// On the Android side, hasSameOwner was added 20100724.1411 because android activities open/close their db often.

	/**
	 * Retrieve a SQL create script or upgrade script.
	 * Please close the returned stream as soon as possible.
	 * For use by {@link RDBSchema}.
	 *
	 * @param upgScriptToVersion  0 for the create script,
	 *    otherwise a db version number, to get the script to upgrade
	 *    from the previous version.
	 * @return the sql as a stream
	 * @throws IOException if a problem occurs locating or opening the script
	 */
	abstract InputStream getSQLScript(final int upgScriptToVersion)
	    throws IOException;

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
	abstract void execStrucUpdate(final String sql)
		throws IllegalStateException, SQLException;

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
	abstract String execPragmaIntegCheck()
		throws IllegalStateException;

}  // public interface RDBAdapter