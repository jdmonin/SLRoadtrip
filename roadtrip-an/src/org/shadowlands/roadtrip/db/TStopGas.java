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

/**
 * In-memory representation, and database access for,
 * a stop for gas during a Trip's {@link TStop}.
 * The TStop's {@link TStop#getFlags()} will have
 * {@link TStop#FLAG_GAS} set.
 *<P>
 * -- NOTE: tstop_gas._id == associated tstop._id :
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

    /** db table fields.
     * @see #buildInsertUpdate(boolean)
     * @see #initFields(String[])
     */
    private static final String[] FIELDS =
    { "quant", "price_per", "price_total", "fillup", "gas_brandgrade_id" };
    private static final String[] FIELDS_AND_ID =
    { "quant", "price_per", "price_total", "fillup", "gas_brandgrade_id", "_id" };

    /**
     * The TStop that we're related to.
     * May be null if this TStopGas was already in the database;
     * calling {@link #getTStop()} will retrieve it.
     */
    private TStop ts;

    public int quant, price_per, price_total;

    public boolean fillup;

    /** 0 if unused */
    public int gas_brandgrade_id;

    /**
     * Convenience field, not stored in database; used in {@link #toStringBuffer(Vehicle)}.
     * If not <tt>null</tt>, its ID must == {@link gas_brandgrade_id}.
     */
    public transient GasBrandGrade gas_brandgrade;

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
     * @param rec  field contents, as returned by db.getRow(FIELDS) or db.getRows(FIELDS_AND_ID)
     */
    private void initFields(final String[] rec)
    {
    	quant = Integer.parseInt(rec[0]);
    	price_per = Integer.parseInt(rec[1]);
    	price_total = Integer.parseInt(rec[2]);
    	fillup = ("1".equals(rec[3]));
    	gas_brandgrade_id = (rec[4] != null)
    		? Integer.parseInt(rec[4])
			: 0 ;
    	if (rec.length == 6)
    		id = Integer.parseInt(rec[5]);
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
     * @param gasBrandGrade_id  Gas brand/grade (ID from {@link GasBrandGrade}), or 0 if unused
     */
    public TStopGas(TStop tstop, final int quant, final int price_per,
    		final int price_total, final boolean fillup, final int gasBrandGrade_id)
    {
    	super();    	
    	ts = tstop;
    	if (tstop != null)
    		id = tstop.getID();  // will check it again during insert()
    	this.quant = quant;
    	this.price_per = price_per;
    	this.price_total = price_total;
    	this.fillup = fillup;
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
		/*
	    private static final String[] FIELDS =
	    { "quant", "price_per", "price_total", "fillup", "station" };
	    */
		String[] fv =
		   (withID)
			? new String[FIELDS_AND_ID.length]
		    : new String[FIELDS.length];
		fv[0] = Integer.toString(quant);
		fv[1] = Integer.toString(price_per);
		fv[2] = Integer.toString(price_total);
		fv[3] = fillup ? "1" : "0";
		fv[4] = (gas_brandgrade_id != 0) ? Integer.toString(gas_brandgrade_id) : null;
		if (withID)
			fv[5] = Integer.toString(id);
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
			} catch (RDBKeyNotFoundException e)
			{
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

	/** format is: "[partial:] quant @ price-per [totalprice] [gas_brandgrade]".
	 *<P>
	 * If {@link #gas_brandgrade} != <tt>null</tt> and its ID matches {@link #gas_brandgrade_id},
	 * the brand/grade name will be placed into the string buffer.
	 *
	 *  @param v  used for number of decimal places, currency symbol
	 */
	public StringBuffer toStringBuffer(Vehicle v)
	{
		StringBuffer sb = new StringBuffer();
		if (! fillup)
			sb.append("partial: ");
		sb.append(RDBSchema.formatFixedDec(quant, v.fuel_qty_deci));
		sb.append(" @ ");
		sb.append(RDBSchema.formatFixedDec(price_per, v.fuel_curr_deci));
		sb.append(" [");
		sb.append(v.expense_curr_sym);
		sb.append(RDBSchema.formatFixedDec(price_total, v.expense_curr_deci));
		sb.append("]");
		if ((gas_brandgrade_id != 0) && (gas_brandgrade != null) && (gas_brandgrade.id == gas_brandgrade_id))
		{
			sb.append(' ');
			sb.append(gas_brandgrade.getName());
		}
		return sb;
	}

}  // public class TStop
