/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2011,2019 Jeremy D Monin <jdmonin@nand.net>
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

import java.util.Vector;

/**
 * In-memory representation, and database access for, a Frequent Trip TStop.
 * @author jdmonin
 */
public class FreqTripTStop extends RDBRecord
{
	private static final String TABNAME = "freqtrip_tstop";

	/**
	 * db table fields.
	 * The "descr" field is now used for the "location" of the stop.
	 * @see #buildInsertUpdate()
	 * @see #initFields(String[])
	 */
	private static final String[] FIELDS =
		{ "freqtripid", "locid", "via_id", "odo_trip" };
	private static final String[] FIELDS_AND_ID =
		{ "freqtripid", "locid", "via_id", "odo_trip", "_id" };

	private int freqtripid;  // FK

	/** Location ID.  Never empty/unused. */
	private int locid;

	/** Via ID ({@link ViaRoute}), or 0 for blank/unused. */
	private int via_id;

	/** may be blank (0) */
	private int odo_trip;

	/**
	 * null unless {@link #toString()} was called.
	 * See toString javadoc for contents.
	 */
	private transient String toString_descr;

	/**
	 * Retrieve all stops for a FreqTrip.
	 * @param db  db connection
	 * @param trip  freqtrip to look for, or null for all FreqTripTStops.
	 * @return stops for this FreqTrip, ordered by id, or null if none
	 * @throws IllegalStateException if db not open
	 */
	public static Vector<FreqTripTStop> stopsForTrip(RDBAdapter db, FreqTrip ftrip)
		throws IllegalStateException
	{
		if (db == null)
			throw new IllegalStateException("db null");

		final Vector<String[]> sv;
		if (ftrip != null)
			sv = db.getRows
				(TABNAME, "freqtripid", Integer.toString(ftrip.getID()), FIELDS_AND_ID, "_id", 0);
		else
			sv = db.getRows
				(TABNAME, (String) null, (String[]) null, FIELDS_AND_ID, "_id", 0);
		if (sv == null)
			return null;

		final Vector<FreqTripTStop> vv = new Vector<FreqTripTStop>(sv.size());
		try
		{
			for (int i = 0; i < sv.size(); ++i)
				vv.addElement(new FreqTripTStop(db, sv.elementAt(i)));
		} catch (RDBKeyNotFoundException e) { }

		return vv;
	}

	/**
	 * Retrieve an existing freqtstop, by id, from the database.
	 *
	 * @param db  db connection
	 * @param id  id field
	 * @throws IllegalStateException if db not open
	 * @throws RDBKeyNotFoundException if cannot retrieve this ID
	 */
	public FreqTripTStop(RDBAdapter db, final int id)
		throws IllegalStateException, RDBKeyNotFoundException
	{
		super(db, id);
		String[] rec = db.getRow(TABNAME, id, FIELDS);
		if (rec == null)
			throw new RDBKeyNotFoundException(id);

		initFields(rec);  // null descr shouldn't occur (IllegalArgumentException)
	}

	/**
	 * Existing record: Fill our obj fields from db-record string contents.
	 * @param db  connection
	 * @param rec  field contents, as returned by db.getRows(FIELDS_AND_ID); last element is _id
	 * @throws RDBKeyNotFoundException not thrown, but required due to super call
	 */
	private FreqTripTStop(RDBAdapter db, final String[] rec)
		throws RDBKeyNotFoundException
	{
		super(db, Integer.parseInt(rec[FIELDS.length]));
		initFields(rec);
	}

	/**
	 * Fill our obj fields from db-record string contents.
	 * @param rec  field contents, as returned by db.getRow(FIELDS) or db.getRows(FIELDS_AND_ID)
	 */
	private void initFields(final String[] rec)
		throws IllegalArgumentException
	{
		freqtripid = Integer.parseInt(rec[0]);  // FK
		locid = Integer.parseInt(rec[1]);  // FK
		if (rec[2] != null)
			via_id = Integer.parseInt(rec[2]);
		if (rec[3] != null)
			odo_trip = Integer.parseInt(rec[3]);
	}

	/**
	 * Create a new trip stop, but don't yet write to the database.
	 * When ready to write (after any changes you make to this object),
	 * call {@link #insert(RDBAdapter)}.
	 *
	 * @param ftrip      FreqTrip containing this stop;
	 *              if <tt>ftrip</tt> isn't yet written to the database,
	 *              insert it soon, then call
	 *              {@link #setFreqTripID(FreqTrip)} before calling {@link #insert(RDBAdapter)}
	 *              on this record.
	 * @param locID      Location ID
	 * @param viaID      Via ID, or 0
	 * @param odo_trip   Trip odometer, or 0
	 */
	public FreqTripTStop(FreqTrip ftrip,  final int locID, final int viaID, final int odo_trip)
	{
		super();
		freqtripid = ftrip.getID();
		this.locid = locID;
		this.via_id = viaID;
		this.odo_trip = odo_trip;
	}

	/**
	 * Insert a new record based on the current field values.
	 * Clears dirty field; sets id and dbConn fields.
	 *
	 * @return new record's primary key (_id)
	 * @throws IllegalStateException if the insert fails,
	 *     or if <tt>freqtripid</tt> isn't set before calling
	 */
	public int insert(RDBAdapter db)
		throws IllegalStateException
	{
		if (freqtripid < 1)
			throw new IllegalStateException("freqtripid not set");

		id = db.insert(TABNAME, FIELDS, buildInsertUpdate(), true);
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
		dbConn.update(TABNAME, id, FIELDS, buildInsertUpdate());
		dirty = false;
	}

	/**
	 * Fill the db fields into an array with same
	 * contents/order as {@link #FIELDS}.
	 * @return field contents, ready for db update via insert() or commit()
	 */
	private String[] buildInsertUpdate()
	{
		String[] fv =
		    {
			Integer.toString(freqtripid),
			Integer.toString(locid),
			(via_id != 0 ? Integer.toString(via_id) : null),
			(odo_trip != 0 ? Integer.toString(odo_trip) : null)
		    };
		return fv;
	}

	/** Get our freqtrip ID, or -1 if the trip isn't yet written to the database. */
	public int getFreqTripID() {
		return freqtripid;
	}

	/** Set our freqtrip ID to this trip's id. */
	public void setFreqTripID(FreqTrip ft) {
		freqtripid = ft.id;
		dirty = true;
	}

	/** Get the trip odometer, or 0 if unused */
	public int getOdo_trip() {
		return odo_trip;
	}

	/** Get the location ID. */
	public int getLocationID() {
		return locid;
	}

	/** Get the via ID ({@link ViaRoute}), or 0 if unused */
	public int getViaID() {
		return via_id;
	}

	/**
	 * A FreqTripTStop's toString is
	 * "(" trip-odo ")" location "via" the via (if any).
	 * The database connection should be available to fill these fields.
	 * If not, toString will fall back to just using the IDs.
	 * Cached after first call.
	 */
	public String toString()
	{
		if (toString_descr != null)
			return toString_descr;
		StringBuffer sb = new StringBuffer();
		if (odo_trip != 0)
		{
		    sb.append('(');
		    sb.append((int) (odo_trip / 10));
		    sb.append('.');
		    sb.append(odo_trip % 10);
		    sb.append(") ");
		}
		if (dbConn != null)
		{
			Location L = null;
			try {
				L = new Location(dbConn, locid);
			} catch (Throwable e1) { } // RDBKeyNotFoundException
			if (L != null)
				sb.append(L.getLocation());
			else
				sb.append(locid);
			if (via_id != 0)
			{
				sb.append(" via ");
				ViaRoute vr = null;
				try {
					vr = new ViaRoute(dbConn, via_id);
				} catch (Throwable e) { } // RDBKeyNotFoundException
				if (vr != null)
					sb.append(vr.getDescr());
				else
					sb.append(via_id);
			}
		} else {
			sb.append("locID ");
			sb.append(locid);
			if (via_id != 0)
			{
				sb.append(" via ");
				sb.append(via_id);
			}
		}
		toString_descr = sb.toString();
		return toString_descr;
	}

	/**
	 * Delete an existing record, <b>Not Currently Allowed</b>.
	 *
	 * @throws NullPointerException if dbConn was null because
	 *     this is a new record, not an existing one
	 * @throws UnsupportedOperationException because this table doesn't
	 *     currently allow deletion.
	 */
	public void delete()
	    throws NullPointerException, UnsupportedOperationException
	{
		// TODO check if unused in FreqTrip table
		throw new UnsupportedOperationException();
		/*
		dbConn.delete(TABNAME, id);
		deleteCleanup();
		*/
	}

}  // public class FreqTripTStop
