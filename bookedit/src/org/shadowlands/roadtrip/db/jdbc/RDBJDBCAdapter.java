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

/**
 * SQLite connection via JDBC.
 *<P>
 * Not safe for use by multiple threads; protect with synchronization if needed.
 * @author jdmonin
 */
public class RDBJDBCAdapter implements RDBAdapter
{
	/** Was JDBC driver class initialization done already? Checked in constructor. */
	private static boolean didDriverInit = false;

	/** sql-scripts location within JAR; includes trailing slash. */
	private static final String SQL_SCRIPTS_DIR = "/org/shadowlands/roadtrip/db/script/";

	/** Primary key {@code "_id=?"} for where-clauses */
	private static final String WHERE_ID = "_id=?";

	/** Connection to the db, opened in constructor; null if {@link #close()} was called. */
	private Connection conn;

	/**
	 * Reusable statement, for {@link Statement#executeUpdate(String) stat.executeQuery(String)}
	 * or {@link Statement#executeUpdate(String) stat.executeUpdate(String)}.  Some methods
	 * create and use other {@link PreparedStatement}s instead.
	 */
	private Statement stat;

	/** DB file path and filename passed into constructor */
	private final String dbFilename;

	/**
	 * Read the schema version from a closed db file (not the current db).
	 *
	 * @see #getSchemaVersion()
	 * @param dbFilename  Filename or full path to a roadtrip db file
	 * @return Schema version, from table and field
	 *     {@link org.shadowlands.roadtrip.db.AppInfo#KEY_DB_CURRENT_SCHEMAVERSION AppInfo.KEY_DB_CURRENT_SCHEMAVERSION}
	 * @throws ArrayIndexOutOfBoundsException if entry not found in {@code AppInfo} table
	 * @throws ClassNotFoundException if org.sqlite.JDBC not found
	 * @throws NumberFormatException if field contents are malformed
	 * @throws SQLException if cannot open or read the db file
	 * @since 0.9.40
	 */
	public static int readSchemaVersion(final String dbFilename)
		throws ArrayIndexOutOfBoundsException, ClassNotFoundException, NumberFormatException, SQLException
	{
		int schemaVersion = 0;
		Connection db = null;
		ResultSet rs = null;

		try {
			if (! didDriverInit)
			{
				// do class initialization:
				Class.forName("org.sqlite.JDBC");
				didDriverInit = true;
			}

			db = DriverManager.getConnection("jdbc:sqlite:" + dbFilename);
			Statement st = db.createStatement();
			rs = st.executeQuery("SELECT aivalue FROM appinfo WHERE aifield = 'DB_CURRENT_SCHEMAVERSION';");
			if (rs.next())
				schemaVersion = Integer.parseInt(rs.getString(1));  // throws NumberFormatException, SQLException
			else
				throw new ArrayIndexOutOfBoundsException
					("Opened but cannot read appinfo(DB_CURRENT_SCHEMAVERSION)");
		} finally {
			if (rs != null)
			{
				try { rs.close(); }
				catch (Exception e) {}
			}
			if (db != null)
			{
				try { db.close(); }
				catch (Exception e) {}
			}
		}

		return schemaVersion;
	}

	/**
	 * Open a connection to this SQLite database file.
	 * If this is the first time called, loads and initializes the {@code org.sqlite.JDBC} driver class.
	 *
	 * @param sqliteDBFilename  Filename or full path of database
	 * @throws ClassNotFoundException if org.sqlite.JDBC not found
	 * @throws SQLException if cannot open the database
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

	//
	// Implement org.shadowlands.roadtrip.db.RDBAdapter:
	// javadocs inherited from interface
	//

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
				sql = "select * from " + tabname + " where " + kf + " ? ;";  // sql ends with "<> ? ;"
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

	public Vector<String[]> getRows
	    (final String tabname, final String kf, final String kv, final String[] fieldnames,
	     final String orderby, final int limit)
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
					if (kv != null)
					{
						sb.append(kf);
						sb.append(" ? ");  // fieldname + "<> ? "
					} else {
						sb.append(kf.substring(0, kf.length() - 2));
						sb.append(" is not null ");
					}
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

	public Vector<String[]> getRows
	    (final String tabname, final String where, final String[] whereArgs, final String[] fieldnames,
	     final String orderby, final int limit)
	    throws IllegalArgumentException, IllegalStateException
	{
		if ((whereArgs != null) && (where == null))
			throw new IllegalArgumentException("null where, non-null whereArgs");
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

	// TODO write a getRowField_rset for int _id primary keys

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

	public String getRowField(final String tabname, final int id, final String fn)
	    throws IllegalStateException
	{
		return getRowField(tabname, "_id", Integer.toString(id), fn);
	}

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

	public int getRowIntField
	    (final String tabname, final String fn, final String where, final String[] whereArgs, final int def)
	    throws IllegalStateException, IllegalArgumentException
	{
		ResultSet rs = getRowField_rset(tabname, fn, where, whereArgs);
		if (rs == null)
			return def;

		int retval = def;
		try
		{
			if (rs.next())
			{
				// If fn is aggregate with no matching rows, aggregates except COUNT return null.
				// rs.next() is true but getInt returns 0.

				retval = rs.getInt(1);
				if ((def != 0) && rs.wasNull())
					retval = def;
			}
		} catch (SQLException e) { }
		try {
			rs.close();
		} catch (SQLException ee) { }

		return retval;
	}

	public int getRowIntField(final String tabname, final int id, final String fn, final int def)
	    throws IllegalStateException
	{
		final String[] whereArgs = { Integer.toString(id) } ;
		return getRowIntField(tabname, fn, WHERE_ID, whereArgs, def);
	}

	public int getRowIntField(final String tabname, final String kf, final String kv, final String fn, final int def)
	    throws IllegalStateException
	{
		final String[] whereArgs = { kv } ;
		return getRowIntField(tabname, fn, kf + " = ?", whereArgs, def);
	}

	public long getRowLongField
	    (final String tabname, final String fn, final String where, final String[] whereArgs, final long def)
	    throws IllegalStateException, IllegalArgumentException
	{
		ResultSet rs = getRowField_rset(tabname, fn, where, whereArgs);
		if (rs == null)
			return def;

		long retval = def;
		try
		{
			if (rs.next())
			{
				// If fn is aggregate with no matching rows, aggregates except COUNT return null.
				// rs.next() is true but getLong returns 0.

				retval = rs.getLong(1);
				if ((def != 0) && rs.wasNull())
					retval = def;
			}
		} catch (SQLException e) { }
		try {
			rs.close();
		} catch (SQLException ee) { }

		return retval;
	}

	public long getRowLongField(final String tabname, final String kf, final String kv, final String fn, final long def)
	    throws IllegalStateException
	{
		final String[] whereArgs = { kv } ;
		return getRowLongField(tabname, fn, kf + " = ?", whereArgs, def);
	}

	public int getCount(final String tabname, final String kf, final String kv)
		throws IllegalStateException
	{
		return getCount(tabname, kf, true, kv, 0);
	}

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
			rs = sql.executeQuery();  // SELECT COUNT(*) FROM ...
			if (rs.next())
				retval = rs.getInt(1);
			rs.close();
			rs = null;
			sql.close();
		} catch (SQLException e) {
			try
			{
				if (rs != null)
					rs.close();
				sql.close();
			} catch (SQLException ee) { }
			throw new IllegalStateException(e);
		}
		return retval;
	}

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
					prep.setNull(i+1, java.sql.Types.VARCHAR);  // OK even if int, SQLite is lax with column types 
			}
			prep.setInt(fn.length+1, id);
			prep.executeUpdate();
		} catch (SQLException e) {
			throw new IllegalStateException("error: " + e.getClass() + ":" + e.getMessage());
		}
	}

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
					prep.setNull(i+1, java.sql.Types.VARCHAR);  // OK even if int: SQLite is lax with column types
			}
			prep.setString(fn.length+1, kv);
			prep.executeUpdate();
		} catch (SQLException e) {
			throw new IllegalStateException("error: " + e.getClass() + ":" + e.getMessage());
		}
	}

	public void update
	    (final String tabname, final String where, final String[] whereArgs, final String[] fn, final String[] fv)
	    throws IllegalStateException, IllegalArgumentException
	{
		if (fn == null)
			throw new IllegalArgumentException("null fn");
		if (fn.length != fv.length)
			throw new IllegalArgumentException("length mismatch");

		StringBuilder sb = new StringBuilder("update ");
		sb.append(tabname);
		sb.append(" set ");
		for (int i = 0; i < fv.length; ++i)
		{
			if (i > 0)
				sb.append(", ");
			sb.append(fn[i]);
			sb.append(" = ?");
		}
		if (where != null)
		{
			sb.append(" where ");
			sb.append(where);
		}

		try
		{
			PreparedStatement prep = conn.prepareStatement(sb.toString());
			for (int i = 0; i < fv.length; ++i)
			{
				final String v = fv[i];
				if (v != null)
					prep.setString(i+1, v);
				else
					prep.setNull(i+1, java.sql.Types.VARCHAR);
			}
			if (whereArgs != null)
			{
				final int offset = 1 + fv.length;
				for (int i = 0; i < whereArgs.length; ++i)
				{
					final String v = whereArgs[i];
					if (v != null)
						prep.setString(i + offset, v);
					else
						prep.setNull(i + offset, java.sql.Types.VARCHAR);
				}
			}
			prep.executeUpdate();
		} catch (SQLException e) {
			IllegalStateException ise
				= new IllegalStateException("error: " + e.getClass() + ":" + e.getMessage());
			ise.initCause(e);
			throw ise;
		}
	}

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

	public void delete(final String tabname, final int id)
	    throws IllegalStateException
	{
		delete(tabname, WHERE_ID, id);
	}

	public void delete(final String tabname, final String where, final int whereArg)
		throws IllegalStateException, IllegalArgumentException
	{
		if (conn == null)
			throw new IllegalStateException("conn not open");
		if (where == null)
			throw new IllegalArgumentException("null: where");

		try
		{
			PreparedStatement prep = conn.prepareStatement
			    ("delete from " + tabname + " where " + where + " ;");
			if (where.indexOf('?') != -1)
				prep.setInt(1, whereArg);
			prep.execute();  // TODO does not check for IllegalArgumentException, do we need to?
		} catch (SQLException e) {
			throw new IllegalStateException("error: " + e.getClass() + ":" + e.getMessage());
		}
	}

	public void delete(final String tabname, final String where, final String whereArg)
		throws IllegalStateException, IllegalArgumentException
	{
		if (conn == null)
			throw new IllegalStateException("conn not open");
		if (where == null)
			throw new IllegalArgumentException("null: where");

		try
		{
			PreparedStatement prep = conn.prepareStatement
			    ("delete from " + tabname + " where " + where + " ;");
			if (where.indexOf('?') != -1)
				prep.setString(1, whereArg);
			prep.execute();  // TODO does not check for IllegalArgumentException, do we need to?
		} catch (SQLException e) {
			throw new IllegalStateException("error: " + e.getClass() + ":" + e.getMessage());
		}
	}

	/**
	 * {@inheritDoc}
	 * (For SQLite, this will be the same filename or full path
	 *  that was passed into the constructor.)
	 */
	public String getFilenameFullPath() throws IllegalStateException
	{
		if (conn == null)
			throw new IllegalStateException("conn not open");
		return dbFilename;
	}

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

	/**
	 * {@inheritDoc}
	 *<P>
	 * On the Android side, hasSameOwner was added 20100724.1411 because android activities open/close their db often.
	 * On the JDBC side, the connection is kept open: Just compare the db filename (full path).
	 */
	public final boolean hasSameOwner(RDBAdapter other)
	{
		if (! (other instanceof RDBJDBCAdapter))
			return false;

		if (dbFilename == null)
			return false;
		else
			return dbFilename.equals( ((RDBJDBCAdapter) other).dbFilename );
	}

	/**
	 * {@inheritDoc}
	 * @throws FileNotFoundException if the upgrade script doesn't exist
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

	public void execStrucUpdate(final String sql)
		throws IllegalStateException, SQLException
	{
		if (conn == null)
			throw new IllegalStateException("conn not open");
		System.out.println("execStrucUpdate: " + sql);
		stat.executeUpdate(sql);  // may throw SQLException
	}

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
	 * @see #readSchemaVersion(String)
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
