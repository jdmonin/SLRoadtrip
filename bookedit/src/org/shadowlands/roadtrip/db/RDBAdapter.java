/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  Copyright (C) 2010 Jeremy D Monin <jdmonin@nand.net>
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
	    throws IllegalStateException;

	/**
	 * Get one or more rows matching a key field-value pair, or all rows except matching.
	 * Returns null if no rows are found.
	 *
	 * @param tabname Table to query
	 * @param kf  Key fieldname; must not be null.
	 *              To get all rows except <tt>kf</tt> &lt;&gt; <tt>kv</tt>,
	 *              the <tt>kf</tt> string should contain &lt;&gt; after the fieldname.
	 * @param kv  Key value; must not be null
	 * @param fieldnames  Field names to return
	 * @param orderby  Order-by field(s), or null; may contain "desc" for sorting
	 * @return  Corresponding field values to field names, or null if errors or if table not found.
	 *       Field values are returned in the order specified in <tt>fields[]</tt>.
	 *       If any field is null or not in the table, that element is null.
	 * @throws IllegalArgumentException if <tt>kf</tt> or <tt>kv</tt> null
	 * @throws IllegalStateException if conn has been closed
	 * @see #getRows(String, String, String[], String[], String)
	 */
	public Vector<String[]> getRows
	    (final String tabname, final String kf, final String kv, final String[] fieldnames, final String orderby)
	    throws IllegalArgumentException, IllegalStateException;

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
	 * @return  Corresponding field values to field names, or null if errors or if table not found.
	 *       Field values are returned in the order specified in <tt>fields[]</tt>.
	 *       If any field is null or not in the table, that element is null.
	 * @throws IllegalArgumentException if <tt>whereArgs</tt> != null, but <tt>where</tt> == null
	 * @throws IllegalStateException if conn has been closed
	 * @see #getRows(String, String, String, String[], String)
	 */
	public Vector<String[]> getRows
	    (final String tabname, final String where, final String[] whereArgs, final String[] fieldnames, final String orderby)
	    throws IllegalArgumentException, IllegalStateException;

	/**
	 * Get one field in one row, given a string-type key field name.
	 * Returns null if key value not found.
	 * @param tabname  Table to query
	 * @param kf  Key fieldname
	 * @param kv  Key value
	 * @param fn  Field name to get
	 * @return field value, or null if not found
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 */
	public String getRowField(final String tabname, final String kf, final String kv, final String fn)
	    throws IllegalStateException;

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
        throws IllegalStateException, IllegalArgumentException;

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
        throws IllegalStateException, IllegalArgumentException;

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
        throws IllegalStateException, IllegalArgumentException;

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
        throws IllegalStateException;

	/**
	 * Delete a current record, given its id.
	 * @param tabname  Table to delete from
	 * @param id      ID field value, for primary key field "_id"
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @throws IllegalArgumentException  if record not found
	 */
	public void delete(final String tabname, final int id)
	    throws IllegalStateException, IllegalArgumentException;

	/**
	 * Get the full path of this open database's filename.
	 * @return the filename, including full path
	 * @throws IllegalStateException if db has been closed 
	 */
	public String getFilenameFullPath() throws IllegalStateException;

	/**
	 * Close the connection.
	 */
	public abstract void close();

	/** Do these connections share the same owner? */
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

}  // public interface RDBAdapter