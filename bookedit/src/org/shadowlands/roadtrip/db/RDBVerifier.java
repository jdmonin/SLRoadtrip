/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  Copyright (C) 2011 Jeremy D Monin <jdmonin@nand.net>
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
 * Structural verifier for an open {@link RDBAdapter RDB SQLite database}.
 * See {@link #verify(int)} for the available levels of verification.
 *
 * @author jdmonin
 */
public class RDBVerifier
{
	/**
	 * Physical-level sqlite verification only (database pages, etc),
	 * via SQLite's <tt>pragma integrity_check;</tt> command.
	 */
	public static final int LEVEL_PHYS = 1;

	/**
	 * Validate master data consistency (vehicles, drivers, geoareas,
	 * locations, etc), after the {@link #LEVEL_PHYS} checks.
	 */
	public static final int LEVEL_MDATA = 2;

	/**
	 * Validate transactional data (trips, stops, gas, etc)
	 * against master data, after the {@link #LEVEL_MDATA} checks.
	 */
	public static final int LEVEL_TDATA = 3;

	private RDBAdapter db;

	/**
	 * Create a verifier against this open database.
	 * Next call {@link #verify(int)}.
	 * When done, call {@link #release()} to release the reference to <tt>forDB</tt>.
	 *
	 * @param forDB  A database to verify, already open.
	 */
	public RDBVerifier(RDBAdapter forDB)
	{
		db = forDB;
	}

	/**
	 * Release this object's reference to the database,
	 * this does not close the database.
	 */
	public void release()
	{
		db = null;
	}

	/**
	 * Verify the database to a given level; only {@link #LEVEL_PHYS} is implemented right now.
	 * @param level  Verify to this level:
	 *     <UL>
	 *     <LI> {@link #LEVEL_PHYS}: Physical sqlite structure only (fastest)
	 *     <LI> {@link #LEVEL_MDATA}: Master data consistency
	 *     <LI> {@link #LEVEL_TDATA}: Transaction data consistency (most thorough)
	 *     </UL>
	 * @return  0 if verification passed, or, if problems were found, the <tt>LEVEL_</tt> constant
	 *    at which the problems were found.
	 * @throws IllegalArgumentException  if <tt>level</tt> is not
	 *    {@link #LEVEL_PHYS}, {@link #LEVEL_MDATA} or {@link #LEVEL_TDATA}.
	 * @throws IllegalStateException  if db is closed or {@link #release() released}
	 */
	public int verify(final int level)
		throws IllegalArgumentException, IllegalStateException
	{
		if ((level < LEVEL_PHYS) || (level > LEVEL_TDATA))
			throw new IllegalArgumentException();
		if (db == null)
			throw new IllegalStateException("null db");

		if (null != db.execPragmaIntegCheck())
			return LEVEL_PHYS;
		if (level <= LEVEL_PHYS)
			return 0;

		if (! verify_mdata())
			return LEVEL_MDATA;
		if (level <= LEVEL_MDATA)
			return 0;

		if (! verify_tdata())
			return LEVEL_TDATA;

		return 0;
	}

	/**
	 * Verify to {@link #LEVEL_MDATA}.
	 * Assumes already verified at {@link #LEVEL_PHYS}.
	 * @return true if consistent, false if problems found.
	 * @throws IllegalStateException  if db is closed
	 */
	private boolean verify_mdata()
		throws IllegalStateException
	{
		// TODO not implemented yet
		return true;
	}

	/**
	 * Verify to {@link #LEVEL_TDATA}.
	 * Assumes already verified at {@link #LEVEL_MDATA}.
	 * @return true if consistent, false if problems found.
	 * @throws IllegalStateException  if db is closed
	 */
	private boolean verify_tdata()
		throws IllegalStateException
	{
		// TODO not implemented yet
		return true;
	}

}
