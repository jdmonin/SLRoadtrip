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

package org.shadowlands.roadtrip.db;

import java.util.Vector;

/**
 * In-memory representation, and database access for, a Vehicle.
 *<P>
 * Some read-only fields are public, instead of having getters.
 * Final is implied, but can't be declared because of strict java syntax checks.
 *<P>
 * To reduce the write frequency, please wait until the end of a trip
 * before you update the vehicle's current odometer and last trip ID,  
 * instead of each trip stop.  Call {@link #setOdometerCurrentAndLastTrip(int, Trip)}.
 *<P>
 * See also bookedit.MiscTablesCRUDDialog.createEditVehicleDialog.
 *
 * @author jdmonin
 */
public class Vehicle extends RDBRecord
{
    private static final String TABNAME = "vehicle";
    private static final String[] FIELDS =
        { "nickname", "driverid", "makeid", "model", "year", "date_from", "date_to", "vin", "odo_orig", "odo_curr", "last_tripid", "distance_storage", "expense_currency", "expense_curr_sym", "expense_curr_deci", "fuel_curr_deci", "fuel_type", "fuel_qty_unit", "fuel_qty_deci", "comment" };
    private static final String[] FIELDS_AND_ID =
    	{ "nickname", "driverid", "makeid", "model", "year", "date_from", "date_to", "vin", "odo_orig", "odo_curr", "last_tripid", "distance_storage", "expense_currency", "expense_curr_sym", "expense_curr_deci", "fuel_curr_deci", "fuel_type", "fuel_qty_unit", "fuel_qty_deci", "comment", "_id" };
    private static final String[] FIELDS_ODO_LASTTRIP =
    	{ "odo_curr", "last_tripid" }; 

    private String nickname;
    private int driverid;
    private int makeid;
    private String model;
    private int year;
    /** see sql schema for date fmt.  0 is assumed empty/unused. */
    private int date_from, date_to;
    private String vin;
    private int odo_orig, odo_curr;
    /** 0 for empty/unused */
    private int last_tripid;
    /** 'M' for miles, 'K' for km */
	public char distance_storage;
	/** 'USD', 'CAD', etc */
	public String expense_currency;
	/** '$', etc */
	public String expense_curr_sym;
	/** decimal digits for expenses */
	public int expense_curr_deci;
	/** decimal digits for fuel per-unit cost */
	public int fuel_curr_deci;
	/** 'G' for gas, 'D' for diesel */
	public char fuel_type;
	/** 'G' for gallon, 'L' for liter */
	public char fuel_qty_unit;
	/** decimal digits for fuel quantity */
	public int fuel_qty_deci;

	private String comment;

    /** null unless {@link #readAllTrips(boolean)} called */
    private transient Vector<Trip> allTrips;

    /**
     * Get the Vehicles currently in the database.
     * @param db  database connection
     * @return an array of Vehicle objects from the database, ordered by name, or null if none
     */
    public static Vehicle[] getAll(RDBAdapter db)
    {
		Vector<String[]> ves = db.getRows(TABNAME, null, (String[]) null, FIELDS_AND_ID, "nickname COLLATE NOCASE", 0);
    	if (ves == null)
    		return null;

    	Vehicle[] rv = new Vehicle[ves.size()];
		try {
	    	for (int i = rv.length - 1; i >= 0; --i)
				rv[i] = new Vehicle(db, ves.elementAt(i));

	    	return rv;
		} catch (RDBKeyNotFoundException e) {
			return null;  // catch is req'd but won't happen; record came from db.
		}
    }

    /**
     * Retrieve an existing vehicle, by id, from the database.
     *
     * @param db  db connection
     * @param id  id field
     * @throws IllegalStateException if db not open
     * @throws RDBKeyNotFoundException if cannot retrieve this ID
     */
    public Vehicle(RDBAdapter db, final int id)
        throws IllegalStateException, RDBKeyNotFoundException
    {
    	super(db, id);
    	String[] rec = db.getRow(TABNAME, id, FIELDS);
    	if (rec == null)
    		throw new RDBKeyNotFoundException(id);

    	initFields(rec);
    }

    /**
     * Existing record: Fill our obj fields from db-record string contents.
     * @param db  connection
     * @param rec, as returned by db.getRows(FIELDS_AND_ID); last element is _id
     * @throws RDBKeyNotFoundException not thrown, but required due to super call
     * @throws IllegalArgumentException if rec.length is too short
     */
    private Vehicle(RDBAdapter db, final String[] rec)
        throws RDBKeyNotFoundException, IllegalArgumentException
    {
    	super(db, Integer.parseInt(rec[FIELDS.length]));
    	initFields(rec);
    }

    /**
     * Fill our obj fields from db-record string contents.
     * <tt>id</tt> is not filled; the constructor has filled it already.
     * @param rec, as returned by db.getRow(FIELDS) or db.getRows(FIELDS_AND_ID)
     * @throws IllegalArgumentException if rec.length is too short
     */
	private void initFields(final String[] rec)
	    throws IllegalArgumentException
	{
		if (rec.length < 20)
			throw new IllegalArgumentException("length < 20: " + rec.length);
		nickname = rec[0];
    	driverid = Integer.parseInt(rec[1]);  // FK
    	makeid = Integer.parseInt(rec[2]);  // FK
    	model = rec[3];
    	year = Integer.parseInt(rec[4]);
    	if (rec[5] != null)
    		date_from = Integer.parseInt(rec[5]);
    	if (rec[6] != null)
    		date_to = Integer.parseInt(rec[6]);
    	vin = rec[7];
    	odo_orig = Integer.parseInt(rec[8]);
    	odo_curr = Integer.parseInt(rec[9]);
    	if (rec[10] != null)
    		last_tripid = Integer.parseInt(rec[10]);
    	else
    		last_tripid = 0;
    	distance_storage = (rec[11].equals("MI") ? 'M' : 'K');
    	expense_currency = rec[12];
    	expense_curr_sym = rec[13];
    	expense_curr_deci = Integer.parseInt(rec[14]);
    	fuel_curr_deci = Integer.parseInt(rec[15]);
    	fuel_type = rec[16].charAt(0);
    	fuel_qty_unit = (rec[17].equals("GA") ? 'G' : 'L');
    	fuel_qty_deci = Integer.parseInt(rec[18]);
    	comment = rec[19];
	}

    /**
     * Create a new vehicle, but don't yet write to the database.
     * When ready to write (after any changes you make to this object),
     * call {@link #insert(RDBAdapter)}.
     *<P>
     * <tt>last_tripid</tt> will be null, because this new vehicle
     * hasn't been on any trips yet.
     *
     * @param nickname
     * @param driver
     * @param makeid
     * @param model
     * @param year
     * @param datefrom
     * @param dateto
     * @param vin
     * @param odo_orig  Original odometer, including tenths
     * @param odo_curr  Current odometer, including tenths
     * @param comment
     * @throws IllegalArgumentException  if ! driver.isDriver()
     */
    public Vehicle
        (String nickname, Person driver, int makeid, String model, int year,
         int datefrom, int dateto, String vin, int odo_orig, int odo_curr, String comment)
        throws IllegalArgumentException
    {
    	super();
    	if (! driver.isDriver())
    		throw new IllegalArgumentException("person.isDriver false: " + driver.getName());

    	this.nickname = nickname;
    	driverid = driver.getID();
    	this.makeid = makeid;  // FK
    	this.model = model;
    	this.year = year;
    	date_from = datefrom;    	
    	date_to = dateto;
    	this.vin = vin;
    	this.odo_orig = odo_orig;
    	this.odo_curr = odo_curr;
    	last_tripid = 0;
    	this.comment = comment;
    }

    /**
     * Retrieve all Trips for this Vehicle.
     * Cached after the first read, even if <tt>alsoTStops</tt> is different on the next call.
     * @param alsoTStops  If true, call {@link Trip#readAllTStops()} for each trip found
     * @return  ordered list of trips (sorted by time_start), or null if none
     * @throws IllegalStateException if the db connection is closed
     * @see Trip#tripsForVehicle(RDBAdapter, Vehicle, int, int, boolean, boolean, boolean)
     */
    public Vector<Trip> readAllTrips(final boolean alsoTStops)
        throws IllegalStateException
    {
    	if (allTrips == null)
    	{
	    	if (dbConn == null)
	    		throw new IllegalStateException("dbConn null");
	    	allTrips = Trip.tripsForVehicle(dbConn, this, alsoTStops);
    	}
    	return allTrips;
    }

    /**
     * Retrieve the most recent time of a trip or tstop for this Vehicle.
     * If the vehicle is currently on a trip, pass that current trip as <tt>tr</tt>.
     * Assumes no current TStop, because you could use that TStop's time instead.
     * @param tr  The vehicle's current trip, if one is in progress, or null.
     *          tr's dbConn should be valid (not closed).
     * @return the time, or 0 if no trips for this vehicle
     * @throws IllegalStateException if the db connection is closed
     */
    public int readLatestTime(Trip tr)
        throws IllegalStateException
    {
    	if (tr == null)
    	{
    		if (last_tripid == 0)
    			return 0;
    		try
    		{
    			tr = new Trip(dbConn, last_tripid);
    		} catch (RDBKeyNotFoundException e) {
    			return 0;
    		}
    	}
    	return tr.readLatestTime();
    }

    public String getNickame()
	{
		return nickname;
	}

	/**
     * Insert a new record based on field and value.
	 * Clears dirty field; sets id and dbConn fields.
     * @return new record's primary key (_id)
     * @throws IllegalStateException if the insert fails
     */
    public int insert(RDBAdapter db)
        throws IllegalStateException
    {
    	String dte_f, dte_t, last_tid;
    	if (date_from != 0)
    		dte_f = Integer.toString(date_from);
    	else
    		dte_f = null;
    	if (date_to != 0)
    		dte_t = Integer.toString(date_to);
    	else
    		dte_t = null;
    	if (last_tripid != 0)
    		last_tid = Integer.toString(last_tripid);
    	else
    		last_tid = null;

    	String[] fv =
            { nickname, Integer.toString(driverid), Integer.toString(makeid),
    		  model, Integer.toString(year), dte_f, dte_t, vin,
    		  Integer.toString(odo_orig), Integer.toString(odo_curr), last_tid,
    		  // TODO construc/gui, not hardcoded, for these:
    		  //    "distance_storage", "expense_currency", "expense_curr_sym", "expense_curr_deci", "fuel_curr_deci", "fuel_type", "fuel_qty_unit", "fuel_qty_deci"
    		  "MI", "USD", "$", "2", "3", "G", "ga", "3",
    		  comment };
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
     * @throws IllegalStateException if the update fails
     * @throws NullPointerException if dbConn was null because
     *     this is a new record, not an existing one
	 */
	public void commit()
        throws IllegalStateException, NullPointerException
	{
    	String dte_f, dte_t, l_tripid;
    	if (date_from != 0)
    		dte_f = Integer.toString(date_from);
    	else
    		dte_f = null;
    	if (date_to != 0)
    		dte_t = Integer.toString(date_to);
    	else
    		dte_t = null;
    	if (last_tripid != 0)
    		l_tripid = Integer.toString(last_tripid);
    	else
    		l_tripid = null;
    	String[] fv =
            { nickname, Integer.toString(driverid), Integer.toString(makeid),
    		  model, Integer.toString(year), dte_f, dte_t, vin,
    		  Integer.toString(odo_orig), Integer.toString(odo_curr), l_tripid, comment };
		dbConn.update(TABNAME, id, FIELDS, fv);
		dirty = false;
	}

	public String getNickname() {
		return nickname;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
		dirty = true;
	}

	public int getDriverID() {
		return driverid;
	}

	public void setDriverID(Person driver)
	    throws IllegalArgumentException
	{
    	if (! driver.isDriver())
    		throw new IllegalArgumentException("person.isDriver false: " + driver.getName());
		driverid = driver.getID();
		dirty = true;
	}

	public int getMakeID() {
		return makeid;
	}

	public void setMakeID(int makeid) {
		this.makeid = makeid;
		dirty = true;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
		dirty = true;
	}

	public int getYear() {
		return year;
	}

	public void setYear(int year) {
		this.year = year;
		dirty = true;
	}

	public int getDate_from() {
		return date_from;
	}

	public void setDate_from(int dateFrom) {
		date_from = dateFrom;
		dirty = true;
	}

	public int getDate_to() {
		return date_to;
	}

	public void setDate_to(int dateTo) {
		date_to = dateTo;
		dirty = true;
	}

	public String getVin() {
		return vin;
	}

	public void setVin(String vin) {
		this.vin = vin;
		dirty = true;
	}

	public int getOdometerOriginal() {
		return odo_orig;
	}

	public int getOdometerCurrent() {
		return odo_curr;
	}

	public void setOdometerCurrent(int newValue10ths) {
		odo_curr = newValue10ths;
		dirty = true;
	}

	/**
	 * Get the id of the vehicle's last completed trip.
	 * @return trip ID, or 0 if no trip has been completed.
	 */
	public int getLastTripID() {
		return last_tripid;
	}

	/**
	 * At the end of a trip, set the current odometer and last trip ID.
	 * @param commitNow commit these 2 fields ONLY, right now; if false, just set {@link #isDirty()}.
	 */
	public void setOdometerCurrentAndLastTrip(int newValue10ths, Trip tr, final boolean commitNow)
	{
		odo_curr = newValue10ths;
		last_tripid = tr.getID();
		if ((allTrips != null) && (allTrips.size() > 0))
		{
			if (allTrips.lastElement() != tr)
				allTrips.addElement(tr);
		}
		if (! commitNow)
		{
			dirty = true;
			return;
		}
		String[] odo_lastTrip = { Integer.toString(newValue10ths), Integer.toString(last_tripid) };
		dbConn.update(TABNAME, id, FIELDS_ODO_LASTTRIP, odo_lastTrip);
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
		dirty = true;
	}

	/** year and model */
	public String toString() {
		return nickname + " - " + Integer.toString(year) + " " + model;
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

}  // public class Vehicle
