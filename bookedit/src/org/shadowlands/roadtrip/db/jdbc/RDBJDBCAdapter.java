/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  Copyright (C) 2010-2011 Jeremy D Monin <jdmonin@nand.net>
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

package org.shadowlands.roadtrip.db.jdbc;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.*;
import java.util.Vector;

import org.shadowlands.roadtrip.bookedit.LogbookEditPane;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.RDBSchema;
import org.shadowlands.roadtrip.db.RDBVerifier;

/**
 * SQLite connection via JDBC.
 * @author jdmonin
 *
 */
public class RDBJDBCAdapter implements RDBAdapter
{
	private static boolean didDriverInit = false;

	/** sql-scripts location within jar; includes trailing slash. */
	private static final String SQL_SCRIPTS_DIR = "/org/shadowlands/roadtrip/db/script/";

	private Connection conn;
	private Statement stat;
	private final String dbFilename;

	/**
	 * 
	 * @param sqliteDBFilename  Filename to driver
	 * @throws ClassNotFoundException if org.sqlite.JDBC not found
	 * @throws SQLException if cannot open
	 */
	public RDBJDBCAdapter (final String sqliteDBFilename)
	    throws ClassNotFoundException, SQLException
	{
		if (! didDriverInit)
		{
			// do class initialization:
			Class.forName("org.sqlite.JDBC");
			didDriverInit = true;
		}
		dbFilename = sqliteDBFilename;
	    conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDBFilename);
		stat = conn.createStatement();
	}

	/**
	 * Return the primary key (_id) of the most recently inserted sqlite record.
	 * @throws IllegalStateException if conn has been closed
	 */
	private int getLastInsertID()
        throws IllegalStateException
    {
		if (conn == null)
			throw new IllegalStateException("conn not open");

		ResultSet rs = null;
		try
		{
			rs = stat.executeQuery("SELECT last_insert_rowid();");
		} catch (SQLException e)
		{
			try
			{
				if (rs != null)
					rs.close();
			} catch (SQLException ee) { }
			return -1;
		}
		int retval = -1;
		try
		{
			if (rs.next())
			{
				retval = rs.getInt(1);
				if (retval == 0)
					retval = -1;
			}
		} catch (SQLException e) {}
		try {
			rs.close();
		} catch (SQLException ee) { }

		return retval;
    }

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
	    throws IllegalStateException
	{
		if (conn == null)
			throw new IllegalStateException("conn not open");

		ResultSet rs = null;
		try
		{
			rs = stat.executeQuery("select * from " + tabname + " where _id=" + id + ";");
		} catch (SQLException e)
		{
			try
			{
				if (rs != null)
					rs.close();
			} catch (SQLException ee) { }
			return null;
		}

		return get_extractRowFieldsAndCloseRS(fields, rs);
	}

	/**
	 * Get one row, given a string-type key field name, or all rows except matching.
	 * Returns null if the row isn't found.
	 * @param tabname Table to query
	 * @param kf  Key fieldname; to get all rows except <tt>kf</tt> &lt;&gt; <tt>kv</tt>,
	 *              the <tt>kf</tt> string should contain &lt;&gt; after the fieldname.
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
		if (conn == null)
			throw new IllegalStateException("conn not open");
	
		ResultSet rs = null;
		try
		{
			String sql;
			if (kf.endsWith("<>"))
				sql = "select * from " + tabname + " where " + kf.substring(0, kf.length() - 2) + " <> ? ;";
			else
				sql = "select * from " + tabname + " where " + kf + " = ? ;";
			PreparedStatement prep = conn.prepareStatement(sql);
		    prep.setString(1, kv);
		    rs = prep.executeQuery();
		} catch (SQLException e)
		{
			try
			{
				if (rs != null)
					rs.close();
			} catch (SQLException ee) { }
			return null;
		}
	
		return get_extractRowFieldsAndCloseRS(fields, rs);
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
		if (conn == null)
			throw new IllegalStateException("conn not open");
	
		ResultSet rs = null;
		try
		{
			StringBuffer sb = new StringBuffer("select * from ");
			sb.append(tabname);
			if (kf != null)
			{
				sb.append(" where ");
				if (kf.endsWith("<>"))
				{
					sb.append(kf.substring(0, kf.length() - 2));
					if (kv != null)
						sb.append(" <> ? ");
					else
						sb.append(" is not null ");
				} else {
					sb.append(kf);
					if (kv != null)
						sb.append(" = ? ");
					else
						sb.append(" is null ");
				}
			}
			if (orderby != null)
			{
				sb.append(" order by ");
				sb.append(orderby);
			}
			if (limit != 0)
			{
				sb.append(" limit ");
				sb.append(limit);
			}
			sb.append(';');
			PreparedStatement prep = conn.prepareStatement
				(sb.toString());
			if (kv != null)
				prep.setString(1, kv);
		    rs = prep.executeQuery();
		} catch (SQLException e)
		{
			try
			{
				if (rs != null)
					rs.close();
			} catch (SQLException ee) { }
			return null;
		}
	
		return get_extractRowsFieldsAndCloseRS(fieldnames, rs);
	}

	/**
	 * Get one or more rows matching a SQL Where clause, or all rows in a table.
	 * Returns null if no rows are found.
	 *
	 * @param tabname Table to query
	 * @param where  Where-clause, or null for all rows; may contain <tt>?</tt> which will be
	 *       filled from <tt>whereArgs</tt> contents, as with PreparedStatements.
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
		if (conn == null)
			throw new IllegalStateException("conn not open");
	
		ResultSet rs = null;
		try
		{
			StringBuffer sb = new StringBuffer("select * from ");
			sb.append(tabname);
			if (where != null)
			{
				sb.append(" where ");
				sb.append(where);
			}
			else if (whereArgs != null)
			{
				throw new IllegalArgumentException("null where, non-null whereArgs");
			}
			if (orderby != null)
			{
				sb.append(" order by ");
				sb.append(orderby);
			}
			if (limit != 0)
			{
				sb.append(" limit ");
				sb.append(limit);
			}
			sb.append(';');
			PreparedStatement prep = conn.prepareStatement
				(sb.toString());
			if (whereArgs != null)
			{
				for (int i = 0; i < whereArgs.length; ++i)
					prep.setString(i+1, whereArgs[i]);
			}
		    rs = prep.executeQuery();
		} catch (SQLException e)
		{
			try
			{
				if (rs != null)
					rs.close();
			} catch (SQLException ee) { }
			return null;
		}
	
		return get_extractRowsFieldsAndCloseRS(fieldnames, rs);
	}

	/**
	 * Extract all rows' fields from an open recordset, then close it.
	 * The recordset is allowed to be empty (0 rows).
	 * Whether the extraction succeeds or fails, will call <tt>rs.close()</tt>.
	 * @param fieldnames field names to look for
	 * @param rs recordset to extract all rows, and then close. Empty <tt>rs</tt> (0 rows) is allowed.
	 * @return Extracted field contents, or null.
	 * @see #get_extractRowFieldsAndCloseRS(String[], ResultSet)
	 */
	private static Vector<String[]> get_extractRowsFieldsAndCloseRS
		(final String[] fieldnames, ResultSet rs)
	{
		Vector<String[]> rv = new Vector<String[]>();
	    try
	    {
	    	int L = fieldnames.length;
			while (rs.next())
			{
				String[] res = new String[L];
				for (int i = 0; i < fieldnames.length; ++i)
					res[i] = rs.getString(fieldnames[i]);
				rv.addElement(res);
			}
		} catch (SQLException e) {
			try {
				rs.close();
			} catch (SQLException ee) { }
			return null;
		}
		try {
			rs.close();
		} catch (SQLException ee) { }

		if (rv.isEmpty())
			rv = null;
		return rv;
	}

	/**
	 * Extract one row's fields from an open recordset, then close it.
	 * <tt>rs.next()</tt> will be called first, then each field's <tt>rs.getString</tt>.
	 * Whether the extraction succeeds or fails, will call <tt>rs.close()</tt>.
	 * @param fieldnames field names to look for
	 * @param rs recordset to extract next row, and then close.
	 * @return Extracted field contents, or null.
	 * @see #get_extractRowsFieldsAndCloseRS(String[], ResultSet)
	 */
	private static String[] get_extractRowFieldsAndCloseRS(final String[] fieldnames, ResultSet rs)
	{
		String[] res;
	    try
	    {
			if (rs.next())
			{
				res = new String[fieldnames.length];
				for (int i = 0; i < fieldnames.length; ++i)
					res[i] = rs.getString(fieldnames[i]);
			} else {
				res = null;
			}
		} catch (SQLException e) {
			try {
				rs.close();
			} catch (SQLException ee) { }
			return null;
		}
		try {
			rs.close();
		} catch (SQLException ee) { }
		return res;
	}

	/**
	 * Common query code for all {@link #getRowField(String, String, String, String)}-type methods.
	 * Get one field in one row, given a string-type key field name.
	 *
	 * @param tabname  Table to query
	 * @param kf  Key fieldname
	 * @param kv  Key value
	 * @param fn  Field name to get
	 * @return single-field resultset, or null if not found or if a SQLException occurs
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 */
	private final ResultSet getRowField_rset(final String tabname, final String kf, final String kv, final String fn)
	    throws IllegalStateException
    {
		if (conn == null)
			throw new IllegalStateException("conn not open");

		ResultSet rs = null;
		try
		{
			PreparedStatement prep = conn.prepareStatement
			    ("select " + fn + " from " + tabname + " where " + kf + " = ? ;");
		    prep.setString(1, kv);
		    rs = prep.executeQuery();
		} catch (SQLException e) {
			try
			{
				if (rs != null)
					rs.close();
			} catch (SQLException ee) { }
			return null;
		}
		return rs;
    }

	/**
	 * Common query code for all {@link #getRowField(String, String, String, String[])}-type methods.
	 * Get one field in one row, given a string-type key field name.
	 *
	 * @param tabname  Table to query
	 * @param fn  Field name to get, or aggregate function such as <tt>max(v)</tt>
	 * @param where  Where-clause, or null for all rows; may contain <tt>?</tt> which will be
	 *       filled from <tt>whereArgs</tt> contents, as with PreparedStatements.
	 *       Do not include the "where" keyword.
	 * @param whereArgs  Strings to bind against each <tt>?</tt> in <tt>where</tt>, or null if <tt>where</tt> has none of those
	 * @return the result set, or null if a SQLException occurs.
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @throws IllegalArgumentException  if where is null, but whereArgs is not
	 */
	private final ResultSet getRowField_rset(final String tabname, final String fn, final String where, final String[] whereArgs)
		throws IllegalStateException, IllegalArgumentException
	{
		if (conn == null)
			throw new IllegalStateException("conn not open");
	
		ResultSet rs = null;
		try
		{
			StringBuffer sb = new StringBuffer("select ");
			sb.append(fn);
			sb.append(" from ");
			sb.append(tabname);
			if (where != null)
			{
				sb.append(" where ");
				sb.append(where);
			}
			else if (whereArgs != null)
			{
				throw new IllegalArgumentException("null where, non-null whereArgs");
			}
			sb.append(';');
			PreparedStatement prep = conn.prepareStatement
				(sb.toString());
			if (whereArgs != null)
			{
				for (int i = 0; i < whereArgs.length; ++i)
					prep.setString(i+1, whereArgs[i]);
			}
		    rs = prep.executeQuery();
		} catch (SQLException e)
		{
			try
			{
				if (rs != null)
					rs.close();
			} catch (SQLException ee) { }
			return null;
		}

		return rs;
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
		ResultSet rs = getRowField_rset(tabname, kf, kv, fn);
		if (rs == null)
			return null;
		String retval = null;
		try
		{
			if (rs.next())
				retval = rs.getString(1);
		} catch (SQLException e) { }
		try {
			rs.close();
		} catch (SQLException ee) { }

		return retval;
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
		ResultSet rs = getRowField_rset(tabname, fn, where, whereArgs);
		if (rs == null)
			return null;

		String retval = null;
		try
		{
			if (rs.next())
				retval = rs.getString(1);
			// If fn is aggregate with no matching rows,
			// rs.next() is true but getString returns null.
		} catch (SQLException e) { }
		try {
			rs.close();
		} catch (SQLException ee) { }

		return retval;
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
		ResultSet rs = getRowField_rset(tabname, fn, where, whereArgs);
		if (rs == null)
			return def;

		int retval = def;
		try
		{
			if (rs.next())
				retval = rs.getInt(1);
			// If fn is aggregate with no matching rows,
			// rs.next() is true but getString returns null.
		} catch (SQLException e) { }
		try {
			rs.close();
		} catch (SQLException ee) { }

		return retval;
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
		ResultSet rs = getRowField_rset(tabname, fn, where, whereArgs);
		if (rs == null)
			return def;

		long retval = def;
		try
		{
			if (rs.next())
				retval = rs.getLong(1);
			// If fn is aggregate with no matching rows,
			// rs.next() is true but getString returns null.
		} catch (SQLException e) { }
		try {
			rs.close();
		} catch (SQLException ee) { }

		return retval;
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
		if (conn == null)
			throw new IllegalStateException("conn not open");

		// assert: kf != null.
		StringBuffer sb = new StringBuffer("SELECT COUNT(*) FROM ");
		sb.append(tabname);
		sb.append(" WHERE ");
		sb.append(kf);
		if (bindString && (sv == null))
			sb.append(" IS NULL");
		else
			sb.append(" = ?");
		PreparedStatement sql;
		try {
			sql = conn.prepareStatement(sb.toString());
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
		int retval = 0;
		ResultSet rs = null;
		try {
			if (bindString)
			{
				if (sv != null)
					sql.setString(1, sv);
			} else {
				sql.setLong(1, iv);
			}
		    rs = sql.executeQuery();
			if (rs.next())
				retval = rs.getInt(1);
			rs.close();
		} catch (SQLException e) {
			try
			{
				if (rs != null)
					rs.close();
			} catch (SQLException ee) { }
			throw new IllegalStateException(e);
		}
		return retval;
	}

	/**
	 * Insert a new row into a table.
	 *<BR><b>Reminder:</b> The _id field should be supplied as null here.
	 * @param tabname table name
	 * @param fn  Field names; recommend you include all fields except _id,
	 *          in the same field order as the CREATE TABLE statement.
	 * @param fv   field values, in the same field order as <tt>fn[]</tt>.
	 *          May contain nulls. If <tt>fn[]</tt> contains _id, its <tt>fv</tt> should be null.
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
        throws IllegalStateException, IllegalArgumentException
	{
		if (fn.length != fv.length)
			throw new IllegalArgumentException("length mismatch");

		StringBuffer sb = new StringBuffer("insert into ");
		sb.append(tabname);
		sb.append(" (");
		for (int i = 0; i < fn.length; ++i)
		{
			if (i > 0)
				sb.append(", ");
			sb.append(fn[i]);
		}
		sb.append(") values (");
		for (int i = 0; i < fv.length; ++i)
		{
			if (i > 0)
				sb.append(", ");
			sb.append('?');
		}
		sb.append(");");
		try
		{
			PreparedStatement prep = conn.prepareStatement(sb.toString());
		    for (int i = 0; i < fv.length; ++i)
		    {
		    	String v = fv[i];
		    	if (v != null)
		    		prep.setString(i+1, v);
		    	else
		    		prep.setNull(i+1, java.sql.Types.VARCHAR);  // TODO is this ok if it's int, with SQLite?
		    }
		    prep.executeUpdate();
		    return getLastInsertID();
		} catch (SQLException e) {
			throw new IllegalStateException("error: " + e.getClass() + ":" + e.getMessage());
		}
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

		StringBuffer sb = new StringBuffer("update ");
		sb.append(tabname);
		sb.append(" set ");
		for (int i = 0; i < fv.length; ++i)
		{
			if (i > 0)
				sb.append(", ");
			sb.append(fn[i]);
			sb.append(" = ?");
		}
		sb.append(" where _id = ?;");
		try
		{
			PreparedStatement prep = conn.prepareStatement(sb.toString());
		    for (int i = 0; i < fv.length; ++i)
		    {
		    	String v = fv[i];
		    	if (v != null)
		    		prep.setString(i+1, v);
		    	else
		    		prep.setNull(i+1, java.sql.Types.VARCHAR);  // TODO is this ok if it's int, with SQLite?
		    }
		    prep.setInt(fn.length+1, id);
		    prep.executeUpdate();
		} catch (SQLException e) {
			throw new IllegalStateException("error: " + e.getClass() + ":" + e.getMessage());
		}
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

		StringBuffer sb = new StringBuffer("update ");
		sb.append(tabname);
		sb.append(" set ");
		for (int i = 0; i < fv.length; ++i)
		{
			if (i > 0)
				sb.append(", ");
			sb.append(fn[i]);
			sb.append(" = ?");
		}
		sb.append(" where ");
		sb.append(kf);
		sb.append(" = ?;");
		try
		{
			PreparedStatement prep = conn.prepareStatement(sb.toString());
		    for (int i = 0; i < fv.length; ++i)
		    {
		    	String v = fv[i];
		    	if (v != null)
		    		prep.setString(i+1, v);
		    	else
		    		prep.setNull(i+1, java.sql.Types.VARCHAR);  // TODO is this ok if it's int, with SQLite?
		    }
		    prep.setString(fn.length+1, kv);
		    prep.executeUpdate();
		} catch (SQLException e) {
			throw new IllegalStateException("error: " + e.getClass() + ":" + e.getMessage());
		}
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
		try
		{
			PreparedStatement prep = conn.prepareStatement
			    ("update " + tabname + " set " + fn + " = ? where " + kf + " = ? ;");
			if (fv != null)
				prep.setString(1, fv);
			else
				prep.setNull(1, java.sql.Types.VARCHAR);
			prep.setString(2, kv);
		    prep.executeUpdate();
		} catch (SQLException e) {
			throw new IllegalStateException("error: " + e.getClass() + ":" + e.getMessage());
		}

    }

	/**
	 * Delete a current record, given its id.
	 * @param tabname  Table to delete from
	 * @param id      ID field value, for primary key field "_id"
	 * @throws IllegalStateException if conn has been closed, table not found, etc.
	 * @throws IllegalArgumentException  if record not found
	 */
	public void delete(final String tabname, final int id)
	    throws IllegalStateException, IllegalArgumentException
    {
		if (conn == null)
			throw new IllegalStateException("conn not open");
		try
		{
			PreparedStatement prep = conn.prepareStatement
			    ("delete from " + tabname + " where _id = ? ;");
			prep.setInt(1, id);
			prep.execute();  // TODO does not check for IllegalArgumentException, do we need it?
		} catch (SQLException e) {
			throw new IllegalStateException("error: " + e.getClass() + ":" + e.getMessage());
		}
    }

	/**
	 * Get the full path of this open database's filename.
	 * (For SQLite, this will be the same filename or full path
	 *  that was passed into the constructor.)
	 * @return the filename, including full path
	 * @throws IllegalStateException if db has been closed 
	 */
	public String getFilenameFullPath() throws IllegalStateException
	{
		if (conn == null)
			throw new IllegalStateException("conn not open");
		return dbFilename;
	}

	/**
	 * Close the connection.
	 */
	public void close()
	{
		if (conn == null)
			return;

		stat = null;
		try {
			conn.close();
		} catch (SQLException e) { }
		conn = null;
	}

	/** Do these connections share the same owner? */
	public boolean hasSameOwner(RDBAdapter other)
	{
		if (! (other instanceof RDBJDBCAdapter))
			return false;

		if (dbFilename == null)
			return false;
		else
			return dbFilename.equals( ((RDBJDBCAdapter) other).dbFilename );
	}
		// TODO expl 'owner' in javadoc ; related to db obj lifecycle, etc
		// On the Android side, hasSameOwner was added 20100724.1411 because android activities open/close their db often.
		// On the JDBC side, the conn is kept open.

	/**
	 * Retrieve a SQL create script or upgrade script.
	 * Please close the returned stream as soon as possible.
	 * For use by {@link RDBSchema}.
	 *
	 * @param upgScriptToVersion  0 for the create script,
	 *    otherwise a db version number, to get the script to upgrade
	 *    from the previous version.
	 * @return the sql as a stream
	 * @throws FileNotFoundException if the upgrade script doesn't exist
	 * @throws IOException if a problem occurs locating or opening the script
	 */
	public InputStream getSQLScript(final int upgScriptToVersion)
		throws FileNotFoundException, IOException
	{
		StringBuffer spath = new StringBuffer(SQL_SCRIPTS_DIR);
		// import any class
		if (upgScriptToVersion == 0)
		{
			spath.append(RDBSchema.DB_SCHEMA_CREATE_FILENAME);
		} else {
			spath.append(RDBSchema.DB_SCHEMA_UPGRADE_FILENAME_PREFIX);
			if (upgScriptToVersion < 1000)
				spath.append('0');
			spath.append(upgScriptToVersion);
			spath.append(".sql");
			System.out.println("getSQLScript file: " + spath);
		}
		URL ufile = LogbookEditPane.class.getResource(spath.toString());
		if (ufile != null)
			return ufile.openStream();
		else
			throw new FileNotFoundException("Not found: " + spath.toString());
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
		throws IllegalStateException, SQLException
	{
		if (conn == null)
			throw new IllegalStateException("conn not open");
		System.out.println("execStrucUpdate: " + sql);
		stat.executeUpdate(sql);  // may throw SQLException
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
		if (conn == null)
			throw new IllegalStateException("conn not open");

		ResultSet rs = null;
		try
		{
			rs = stat.executeQuery("pragma integrity_check;");
		} catch (SQLException e)
		{
			try
			{
				if (rs != null)
					rs.close();
			} catch (SQLException ee) { }
			throw new IllegalStateException("SQLException", e);
		}

		String retval = null;
		try
		{
			if (rs.next())
				retval = rs.getString(1);
		} catch (SQLException e) { }
		try {
			rs.close();
		} catch (SQLException ee) { }

		if ((retval != null) && retval.equalsIgnoreCase("ok"))
			return null;  // ok is good news
		else
			return retval;
	}

	//
	// misc
	//

	/**
	 * Get the schema version (sqlite USER_VERSION).
	 * @return the version, or 0 if a SQL error occurs.
	 * @throws IllegalStateException if db has been closed
	 */
	public int getSchemaVersion()
	    throws IllegalStateException 
	{
		if (conn == null)
			throw new IllegalStateException("conn not open");
		ResultSet rs = null;
		int vers = 0;
		try {
			rs = stat.executeQuery("PRAGMA user_version");
			vers = rs.getInt(1);
			rs.close();
		} catch (SQLException e) {
			// should not happen
			if (rs != null)
			{
				try { rs.close(); }
				catch (SQLException ec) {}
			}
		}
		return vers;		
	}

}  // public class RDBJDBCAdapter
