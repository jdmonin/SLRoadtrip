/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2011,2017 Jeremy D Monin <jdmonin@nand.net>
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
 * In-memory representation, and database access for,
 * a stop for gas during a Trip's {@link TStop}.
 * The TStop's {@link TStop#getFlags()} will have
 * {@link TStop#FLAG_GAS} set.
 *<P>
 * Several fields here ({@link #quant}, etc) are fixed-point decimal but stored as integers;
 * use {@link #toStringBuilder(Vehicle)} for user-friendly formatting.
 * {@link #quant}, {@link #price_per}, and {@link #price_total} fields' number of decimal digits
 * could in future be different per vehicle; different installations or
 * different vehicles in the same db could use different decimal places or units.
 * In all versions released so far, the number of digits is hardcoded to what's noted
 * in those fields' javadocs.
 *<P>
 *<B>NOTE:</B> tstop_gas._id == associated tstop._id:
 *  The primary key is the same value as the stop's {@link #TStop#id} field.
 *<P>
 * The obsolete field <tt>station</tt> is ignored if present in the database.
 * Schema 0.9.06+ do not contain the station field, using <tt>gas_brandgrade_id</tt> instead.
 *
 * @author jdmonin
 */
public class TStopGas extends RDBRecord
{
	private static final String TABNAME = "tstop_gas";

	/**
	 * db table fields, except for <tt>_id</tt>.
	 * @see #buildInsertUpdate(boolean)
	 * @see #initFields(String[])
	 * @see #FIELDS_AND_ID
	 */
	private static final String[] FIELDS =
		{ "quant", "price_per", "price_total", "fillup", "vid", "gas_brandgrade_id" };

	/**
	 * db table fields, including <tt>_id</tt>.
	 * @see #FIELDS
	 */
	private static final String[] FIELDS_AND_ID =
		{ "quant", "price_per", "price_total", "fillup", "vid", "gas_brandgrade_id", "_id" };

	/** All of our fields, and a few TStop fields, for {@link #recentGasForVehicle(RDBAdapter, Vehicle, int)} */
	private static final String[] FIELDS_AND_ID_AND_TSTOP_SOME =
		{ "g.quant", "g.price_per", "g.price_total", "g.fillup",
		  "g.vid", "g.gas_brandgrade_id", "g._id",
		  "ts." + TStop.FIELD_ODO_TOTAL,
		  "ts." + TStop.FIELD_TIME_STOP,
		  "ts." + TStop.FIELD_TIME_CONTINUE,
		  "ts." + TStop.FIELD_LOCID
		};

	/** Field offset within {@link #FIELDS_AND_ID_AND_TSTOP_SOME} */
	private static final int FIELDNUM_TSTOP_ODO_TOTAL = FIELDS_AND_ID.length,
		FIELDNUM_TSTOP_TIME_STOP = FIELDNUM_TSTOP_ODO_TOTAL + 1,
		FIELDNUM_TSTOP_TIME_CONTINUE = FIELDNUM_TSTOP_TIME_STOP + 1,
		FIELDNUM_TSTOP_LOCID = FIELDNUM_TSTOP_TIME_CONTINUE + 1;

	/** Where-clause to join with {@link TStop} for use in {@link #recentGasForVehicle(RDBAdapter, Vehicle, int) */
	private static final String WHERE_VID_AND_JOIN_TSTOP = "vid = ? and g._id = ts._id";

	/**
	 * The TStop that we're related to.
	 * May be null if this TStopGas was already in the database;
	 * calling {@link #getTStop()} will retrieve it.
	 */
	private TStop ts;

	/**
	 * Fuel quantity added at this stop.
	 * Units: fixed-point decimal (3 places, from {@link Vehicle#fuel_qty_deci})
	 * @see #toStringBuilder(Vehicle)
	 */
	public int quant;

	/**
	 * Price per fuel unit at this stop.
	 * Units: fixed-point decimal (3 places, from {@link Vehicle#fuel_curr_deci})
	 */
	public int price_per;

	/**
	 * Total actual cost paid for fuel at this stop,
	 * calculated by vendor based on {@link #price_per} * {@link #quant}.
	 * Units: fixed-point decimal (2 places, from {@link Vehicle#expense_curr_deci}).
	 * @see #toStringBuilder(Vehicle)
	 */
	public int price_total;

	/** Filled the tank if true, otherwise partial */
	public boolean fillup;

	/** vehicle ID: denormalization for query performance. */
	public int vid;

	/**
	 * FK into {@link GasBrandGrade}, or 0 if unused.
	 * @see #gas_brandgrade
	 */
	public int gas_brandgrade_id;

	/**
	 * Convenience field, not stored in database; used in {@link #toStringBuilder(Vehicle)}.
	 * If not <tt>null</tt>, its ID must == {@link #gas_brandgrade_id}.
	 */
	public transient GasBrandGrade gas_brandgrade;

	/**
	 * Total quantity since last {@link #fillup} gas stop; same units as {@link #quant}.
	 * Convenience field, not stored in database, used in fuel
	 * efficiency calcs between fill-ups.  Calculated in
	 * {@link #recentGasForVehicle(RDBAdapter, Vehicle, int)}.
	 * 0 for non-{@link #fillup} stops or when not calculated.
	 * @see #effic_dist
	 */
	public transient int effic_quant;

	/**
	 * Total distance since last {@link #fillup} gas stop; same units as {@link TStop#getOdo_total()}.
	 * Convenience field, not stored in database, used in fuel
	 * efficiency calcs between fill-ups.  Calculated in
	 * {@link #recentGasForVehicle(RDBAdapter, Vehicle, int)}.
	 * 0 for non-{@link #fillup} stops or when not calculated.
	 * @see #effic_quant
	 */
	public transient int effic_dist;

	/**
	 * Find recent gas stops for this vehicle in the database.
	 *<P>
	 * Note that each returned {@link TStopGas} has all fields from the database,
	 * but its {@link #getTStop()} has only a few fields:
	 * ID, total odometer, stop time, continue time, and location ID.
	 * The trip ID and other fields are not filled by this query,
	 * so do not use that TStop object for other purposes than gas information.
	 *<P>
	 * The {@link #effic_quant} and {@link #effic_dist} fields are calculated
	 * for each {@link #fillup} gas stop after the oldest, by looking at the
	 * distance and quantity since the previous fill-up.
	 *<P>
	 * The TStopGas.{@link #gas_brandgrade} convenience field is not filled here.
	 *
	 * @param db  db connection
	 * @param veh  retrieve for this vehicle
	 * @param limit  maximum number of gas stops to return, or 0 for no limit
	 * @return Gas stops for this vehicle, most recent first, or null if none
	 * @throws IllegalStateException if db is null or not open, or if an unexpected result parse error occurs
	 * @see #efficToStringBuilder(boolean, StringBuilder, Vehicle)
	 */
	public static Vector<TStopGas> recentGasForVehicle
		(RDBAdapter db, final Vehicle veh, final int limit)
		throws IllegalStateException
	{
		if (db == null)
			throw new IllegalStateException("db null");

		// select g.*, ts.odo_total, ts.time_stop, ts.time_cont, ts.locid
		//    from tstop_gas g, tstop ts
		//    where g.vid=2 and g._id=ts._id
		//    order by g._id desc limit 5;

		Vector<String[]> sv = db.getRows
			(TABNAME + " g, " + TStop.TABNAME + " ts",
			  WHERE_VID_AND_JOIN_TSTOP,
			  new String[]{ Integer.toString(veh.getID()) },
			  FIELDS_AND_ID_AND_TSTOP_SOME, "g._id DESC", limit);
		if (sv == null)
			return null;

		final int L = sv.size();
		Vector<TStopGas> tsgv = new Vector<TStopGas>(L);
		try
		{
			for (int i = 0; i < L; ++i)
			{
				TStopGas tsg = new TStopGas(null);
				final String[] s = sv.elementAt(i);
				tsg.initFields(s);
				if (s[FIELDNUM_TSTOP_LOCID] == null)  // workaround for old tstop data
				s[FIELDNUM_TSTOP_LOCID] = "0";    // from before locid was required
				tsg.setTStop(new TStop
					(db, tsg, s[FIELDNUM_TSTOP_ODO_TOTAL], s[FIELDNUM_TSTOP_TIME_STOP],
					 s[FIELDNUM_TSTOP_TIME_CONTINUE], s[FIELDNUM_TSTOP_LOCID]));
				tsgv.addElement(tsg);
			}

			// Now that we have all TSGs and TStops,
			// we can calculate effic_dist and effic_quant.
			// List is reverse chrono: Higher i are earlier.
			for (int i = 0; i < L; ++i)
			{
				TStopGas tsg = tsgv.elementAt(i);
				if (! tsg.fillup)
					continue;

				// Look for previous fillup, calculate effic_dist and effic_quant.
				// Higher i are earlier stops.
				int quant = tsg.quant;
				int odo = 0;  // if still 0 after iprev loop, no fill-ups found
				int iprev;
				for (iprev = i + 1; iprev < L; ++iprev)
				{
					TStopGas prev = tsgv.elementAt(iprev);
					if (! prev.fillup)
					{
						quant += prev.quant;
					} else {
						odo = prev.ts.getOdo_total();
						break;  // found prev fill-up
					}
				}

				if (odo != 0)
				{
					tsg.effic_dist = tsg.ts.getOdo_total() - odo;
					tsg.effic_quant = quant;
				}

				// For next iteration, skip to prev fill-up:
				i = iprev - 1;
			}

		} catch (Throwable t) {
			throw new IllegalStateException("Problem parsing query results", t);
		}

		return tsgv;
	}

	/** Constructor for creating a new record, before field contents are known.
	 *
	 * @param tstop  tstop, or null; if null, you must call {@link #setTStop(TStop)}
	 *          before {@link #insert(RDBAdapter)}.
	 */
	public TStopGas(TStop tstop)
	{
		super();

		ts = tstop;  // will check it again during insert()
		if (tstop != null)
			id = tstop.getID();  // will check it again during insert()
	}

	/**
	 * Retrieve an existing gas stop, by {@link TStop} id, from the database.
	 *
	 * @param db  db connection
	 * @param id  id field; matches a TStop ID
	 * @throws IllegalStateException if db not open
	 * @throws RDBKeyNotFoundException if cannot retrieve this ID
	 */
	public TStopGas(RDBAdapter db, final int id)
		throws IllegalStateException, RDBKeyNotFoundException
	{
		super(db, id);

		String[] rec = db.getRow(TABNAME, id, FIELDS);
		if (rec == null)
			throw new RDBKeyNotFoundException(id);

		initFields(rec);
	}

	/**
	 * Fill our obj fields from db-record string contents.
	 * @param rec  field contents, as returned by
	 *     db.getRow({@link #FIELDS}) or db.getRows({@link #FIELDS_AND_ID})
	 */
	private void initFields(final String[] rec)
	{
		quant = Integer.parseInt(rec[0]);
		price_per = Integer.parseInt(rec[1]);
		price_total = Integer.parseInt(rec[2]);
		fillup = ("1".equals(rec[3]));
		vid = Integer.parseInt(rec[4]);
		gas_brandgrade_id = (rec[5] != null)
			? Integer.parseInt(rec[5])
			: 0 ;
		effic_dist = 0;
		effic_quant = 0;
		if (rec.length >= 7)
			id = Integer.parseInt(rec[6]);
	}

	/**
	 * Create a new gas trip stop, but don't yet write to the database.
	 * When ready to write (after any changes you make to this object),
	 * call {@link #insert(RDBAdapter)}.
	 *<P>
	 * When calling this constructor, the {@link TStop} can also be a new record not yet
	 * written to the database, but you must call {@link TStop#insert(RDBAdapter)}
	 * before calling {@link #insert(RDBAdapter)}} on the new TStopGas,
	 * so that the <tt>_id</tt> field will be set.
	 *
	 * @param tstop    TStop for this gas; must not be null
	 * @param quant    Quantity
	 * @param price_per  Price per unit
	 * @param price_total  Price total
	 * @param fillup   Completely filling up the tank?
	 * @param vehicle_id  Vehicle ID being fueled
	 * @param gasBrandGrade_id  Gas brand/grade (ID from {@link GasBrandGrade}), or 0 if unused
	 */
	public TStopGas(TStop tstop, final int quant, final int price_per,
		final int price_total, final boolean fillup, final int vehicle_id, final int gasBrandGrade_id)
	{
		super();

		ts = tstop;
		if (tstop != null)
			id = tstop.getID();  // will check it again during insert()
		this.quant = quant;
		this.price_per = price_per;
		this.price_total = price_total;
		this.fillup = fillup;
		vid = vehicle_id;
		this.gas_brandgrade_id = gasBrandGrade_id;
	}

	/**
	 * Insert a new record based on field and value.
	 * Clears dirty field; sets id and dbConn fields.
	 *<P>
	 * Before calling this method, make sure that the related TStop
	 * is written to the database.
	 *
	 * @return new record's primary key (_id)
	 * @throws IllegalStateException if the insert fails,
	 *   or if <tt>tstop</tt> isn't set, or if <tt>tstop</tt> isn't inserted already
	 */
	public int insert(RDBAdapter db)
		throws IllegalStateException
	{
		if (ts == null)
			throw new IllegalStateException("tstop not set");

		if (id < 1)
		{
			id = ts.id;
			if (id < 1)
				throw new IllegalStateException("tstop.id not set");
		}
		db.insert(TABNAME, FIELDS_AND_ID, buildInsertUpdate(true), true);
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
		dbConn.update(TABNAME, id, FIELDS, buildInsertUpdate(false));
		dirty = false;
	}

	/**
	 * Fill the db fields into an array with same
	 * contents/order as {@link #FIELDS} or {@link #FIELDS_AND_ID}.
	 * @param withID  For insert, also include the current ID (from {@link #FIELDS_AND_ID}};
	 *                  the ID is the same as our TStop's ID.
	 * @return field contents, ready for db update via insert() or commit() 
	 */
	private String[] buildInsertUpdate(final boolean withID)
	{
		String[] fv =
		    (withID)
			? new String[FIELDS_AND_ID.length]
			: new String[FIELDS.length];
		fv[0] = Integer.toString(quant);
		fv[1] = Integer.toString(price_per);
		fv[2] = Integer.toString(price_total);
		fv[3] = fillup ? "1" : "0";
		fv[4] = Integer.toString(vid);
		fv[5] = (gas_brandgrade_id != 0) ? Integer.toString(gas_brandgrade_id) : null;
		if (withID)
			fv[6] = Integer.toString(id);

		return fv;
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

	/**
	 * Get our related {@link TStop}.
	 * If we don't have a pointer to it yet,
	 * retrieve it from the database using dbConn
	 * based on our <tt>id</tt> field.
	 *
	 * @return the TStop for this TStopGas
	 * @throws IllegalStateException if db not open, or ID not found in TStop
	 */
	public TStop getTStop()
		throws IllegalStateException
	{
		if ((ts == null) && (dbConn != null))
		{
			try {
				ts = new TStop(dbConn, id);
			} catch (RDBKeyNotFoundException e) {
				throw new IllegalStateException("tstop.id not in db");
			}
		}

		return ts;
	}

	/**
	 * Set our related {@link TStop} if it's currently null.
	 * @param tstop new TStop
	 * @throws IllegalStateException if already non-null
	 */
	public void setTStop(TStop tstop)
		throws IllegalStateException
	{
		if (ts != null)
			throw new IllegalStateException("tstop already set");

		ts = tstop;
	}

	/**
	 * Calculate the efficiency and add to this stringbuilder, if data available
	 * and calculated by {@link #recentGasForVehicle(RDBAdapter, Vehicle, int)}.
	 * Format is "##.#" for mpg, or "##.##" for L/100km.
	 *<P>
	 * Before v0.9.61, this method was {@code efficToStringBuffer(..)}.
	 *
	 * @param sb  Use this stringbuilder; if null, a new one is created and returned
	 *     unless {@link #effic_dist} is 0 or {@link #effic_quant} is 0.
	 * @param v  used for number of decimal places, currency symbol
	 * @param fmtPer100  Calculate as L/100km or gal/100mi, not mpg or km/L
	 * @return  the stringbuilder with efficiency number appended,
	 *     or if {@link #effic_dist} or {@link #effic_quant} is 0,
	 *     do nothing and return the unchanged {@code sb} parameter.
	 * @see #toStringBuilder(Vehicle)
	 * @since 0.9.61
	 */
	public StringBuilder efficToStringBuilder(final boolean fmtPer100, StringBuilder sb, Vehicle v)
	{
		if ((effic_dist == 0) || (effic_quant == 0))
			return sb;

		if (sb == null)
			sb = new StringBuilder();
		float dist = effic_dist / 10f;  // Convert from 10ths
		float quant = effic_quant * (float) Math.pow(10, -v.fuel_qty_deci);
		float effic;
		if (fmtPer100)
		{
			effic = (quant * 100f) / dist;
			sb.append(String.format("%.2f", effic));
		} else {
			effic = dist / quant;
			sb.append(String.format("%.1f", effic));
		}

		return sb;
	}

	/** format is: "[partial:] quant @ price-per [totalprice] [gas_brandgrade]".
	 *<P>
	 * If {@link #gas_brandgrade} != <tt>null</tt> and its ID matches {@link #gas_brandgrade_id},
	 * the brand/grade name will be placed into the string buffer.
	 *<P>
	 * Before v0.9.61, this method was {@code toStringBuffer(Vehicle)}.
	 *
	 * @param v  Vehicle taking the Trip containing this TStopGas;
	 *     used for number of decimal places, currency symbol
	 * @see #efficToStringBuilder(boolean, StringBuilder, Vehicle)
	 * @since 0.9.61
	 */
	public StringBuilder toStringBuilder(Vehicle v)
	{
		StringBuilder sb = new StringBuilder();

		if (! fillup)
			sb.append("partial: ");
		sb.append(RDBSchema.formatFixedDec(quant, v.fuel_qty_deci));
		sb.append(" @ ");
		sb.append(RDBSchema.formatFixedDec(price_per, v.fuel_curr_deci));
		sb.append(" [");
		v.formatCurrFixedDeci(sb, price_total);
		sb.append("]");
		if ((gas_brandgrade_id != 0) && (gas_brandgrade != null) && (gas_brandgrade.id == gas_brandgrade_id))
		{
			sb.append(' ');
			sb.append(gas_brandgrade.getName());
		}

		return sb;
	}

}  // public class TStopGas
