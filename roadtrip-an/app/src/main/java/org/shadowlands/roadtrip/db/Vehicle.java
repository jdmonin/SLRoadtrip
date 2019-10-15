/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2015,2017,2019 Jeremy D Monin <jdmonin@nand.net>
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

import java.util.List;
import java.util.Vector;

/**
 * In-memory representation, and database access for, a Vehicle.
 * To distinctly identify the vehicle at least one of the Year, Model, or Nickname fields
 * should be set; see {@link #toString()}.
 *<P>
 * Some read-only fields are public, instead of having getters.
 * Final is implied, but can't be declared because of strict java syntax checks.
 *<P>
 * To reduce the write frequency, please wait until the end of a trip
 * before you update the vehicle's current odometer and last trip ID,
 * instead of each trip stop: Call {@link #setOdometerCurrentAndLastTrip(int, Trip, boolean)}
 * only when the trip ends.
 *<P>
 * If changing the {@link Settings#CURRENT_VEHICLE} from this vehicle, and there is a
 * trip in progress, call {@link #setOdometerCurrentAndLastTrip(int, Trip, boolean)}
 * to save the current-trip info for the old vehicle, and then call {@link #getTripInProgress()}
 * on the new vehicle.  See also {@link TStop} class comments about the current TStop
 * when changing current vehicle.
 *<P>
 * Currently the {@link #distance_storage}, {@code expense_*} and {@code fuel_*}
 * field values are hardcoded in {@link #insert(RDBAdapter)}.
 * A future version will use localization defaults and/or user dialogs to set these fields
 * before the new vehicle begins its first {@link Trip}.
 *<P>
 * See also bookedit.MiscTablesCRUDDialog.createEditVehicleDialog.
 *
 * @author jdmonin
 */
public class Vehicle extends RDBRecord
{
	private static final String TABNAME = "vehicle";

	private static final String[] FIELDS =
	    { "nickname", "driverid", "makeid", "model", "year", "date_from", "date_to", "vin", "odo_orig", "odo_curr",
	      "last_tripid", "distance_storage", "expense_currency", "expense_curr_sym", "expense_curr_deci", "fuel_curr_deci",
	      "fuel_type", "fuel_qty_unit", "fuel_qty_deci", "comment", "is_active", "date_added", "plate" };
	private static final String[] FIELDS_AND_ID =
	    { "nickname", "driverid", "makeid", "model", "year", "date_from", "date_to", "vin", "odo_orig", "odo_curr",
	      "last_tripid", "distance_storage", "expense_currency", "expense_curr_sym", "expense_curr_deci", "fuel_curr_deci",
	      "fuel_type", "fuel_qty_unit", "fuel_qty_deci", "comment", "is_active", "date_added", "plate", "_id" };
	// If you add or change fields, remember to update initFields and other methods.

	/**
	 * Basic fields only, for {@link #commit()}.  Omits: "distance_storage", "expense_currency", "expense_curr_sym",
	 * "expense_curr_deci", "fuel_curr_deci", "fuel_type", "fuel_qty_unit", "fuel_qty_deci"
	 */
	private static final String[] FIELDS_BASIC =
	    { "nickname", "driverid", "makeid", "model", "year", "date_from", "date_to", "vin", "odo_orig", "odo_curr",
	      "last_tripid", "comment", "is_active", "date_added", "plate" };
	private static final String[] FIELDS_ODO_LASTTRIP =
	    { "odo_curr", "last_tripid" };

	/**
	 * Placeholder for "Other" entry in {@link #getAll(RDBAdapter, int)},
	 * to move from showing Active to Inactive ones.
	 *<P>
	 * <B>I18N:</B> The "Other..." text is kept in the Model field. To localize
	 * this and any other static text, call {@link RDBRecord#localizeStatics(String, String)}.
	 *
	 * @since 0.9.41
	 */
	public static final Vehicle OTHER_VEHICLE = new Vehicle
	    (null, new Person("", true, null, null), -1, "Other...", 0, 0, 0, null, null, 0, 0, null);

	/**
	 * Flag to retrieve only active vehicles in {@link #getAll(RDBAdapter, int)}
	 * @since 0.9.41
	 * @see #FLAG_ONLY_INACTIVE
	 * @see #FLAG_WITH_OTHER
	 */
	public static final int FLAG_ONLY_ACTIVE = 0x1;

	/**
	 * Flag to retrieve only inactive vehicles in {@link #getAll(RDBAdapter, int)}.
	 * @since 0.9.41
	 * @see #FLAG_ONLY_ACTIVE
	 */
	public static final int FLAG_ONLY_INACTIVE = 0x2;

	/**
	 * Flag to add "Other..." ({@link #OTHER_VEHICLE} placeholder) to {@link #getAll(RDBAdapter, int)} results.
	 * @since 0.9.41
	 * @see #FLAG_ONLY_ACTIVE
	 */
	public static final int FLAG_WITH_OTHER = 0x4;

	// Defaults for currency/decimal fields:

	/**
	 * Default value for {@code distance_storage} field: "MI" in DB
	 * @since 0.9.70
	 */
	public static final String DISTANCE_STORAGE_DEFAULT = "MI";

	/**
	 * Default value for {@code distance_storage} field: 'M' in object field
	 * @since 0.9.70
	 */
	public static final char DISTANCE_STORAGE_DEFAULT_CHAR = 'M';

	/**
	 * Default value for {@code expense_currency} field: "USD"
	 * @since 0.9.70
	 */
	public static final String EXPENSE_CURRENCY_DEFAULT = "USD";

	/**
	 * Default value for {@code expense_curr_sym} field: "$"
	 * @since 0.9.70
	 */
	public static final String EXPENSE_CURR_SYM_DEFAULT = "$";

	/**
	 * Default value for {@code expense_curr_deci} field: 2
	 * @since 0.9.70
	 */
	public static final int EXPENSE_CURR_DECI_DEFAULT = 2;

	/**
	 * Default value for {@code fuel_curr_deci} field: 3
	 * @since 0.9.70
	 */
	public static final int FUEL_CURR_DECI_DEFAULT = 3;

	/**
	 * Default value for {@code fuel_type} field: "G"
	 * @since 0.9.70
	 */
	public static final char FUEL_TYPE_DEFAULT = 'G';

	/**
	 * Default value for {@code fuel_qty_unit} field: "ga" in DB
	 * @since 0.9.70
	 */
	public static final String FUEL_QTY_UNIT_DEFAULT = "ga";

	/**
	 * Default value for {@code fuel_qty_unit} field: 'G' in object field
	 * @since 0.9.70
	 */
	public static final char FUEL_QTY_UNIT_DEFAULT_CHAR = 'G';

	/**
	 * Default value for {@code fuel_qty_deci} field: 3
	 * @since 0.9.70
	 */
	public static final int FUEL_QTY_DECI_DEFAULT = 3;

	// Per-record fields

	/** optional nickname or color */
	private String nickname;

	/** usual driver, a {@link Person} ID */
	private int driverid;

	/**
	 * {@link VehicleMake} ID.
	 * @see #makeidName
	 */
	private int makeid;

	private String model;

	/** model year, or 0 if unknown */
	private int year;

	/**
	 * From/to dates of vehicle in use. see sql schema for date fmt.
	 * 0 is empty/unused (null).
	 * @see #date_added
	 */
	private int date_from, date_to;

	/** optional VIN */
	private String vin;

	private int odo_orig, odo_curr;

	/**
	 * id of the vehicle's last completed trip, or 0 for empty/unused.
	 * See {@link #getLastTripID()} for details.
	 * Updated by {@link #setOdometerCurrentAndLastTrip(int, Trip, boolean)}.
	 */
	private int last_tripid;

	/** 'M' for miles, 'K' for km; DB field uses "MI" or "KM" */
	public char distance_storage = DISTANCE_STORAGE_DEFAULT_CHAR;

	/** 'USD', 'CAD', etc */
	public String expense_currency = EXPENSE_CURRENCY_DEFAULT;

	/** '$', etc */
	public String expense_curr_sym = EXPENSE_CURR_SYM_DEFAULT;

	/** decimal digits for expenses, default 2; used in {@link TStopGas} */
	public int expense_curr_deci = EXPENSE_CURR_DECI_DEFAULT;

	/** decimal digits for fuel per-unit cost, default 3; used in {@link TStopGas} */
	public int fuel_curr_deci = FUEL_CURR_DECI_DEFAULT;

	/** 'G' for gas, 'D' for diesel */
	public char fuel_type = FUEL_TYPE_DEFAULT;

	/** 'G' for gallon, 'L' for liter; DB field uses "ga" or "L" */
	public char fuel_qty_unit = FUEL_QTY_UNIT_DEFAULT_CHAR;

	/** decimal digits for fuel quantity, default 3; used in {@link TStopGas} */
	public int fuel_qty_deci = FUEL_QTY_DECI_DEFAULT;

	/** license plate/tag, or null */
	private String plate;

	private String comment;

	private boolean is_active;

	/**
	 * Date this vehicle was added, or 0 if empty/null.
	 * @see #date_from
	 * @since 0.9.43
	 */
	private int date_added;

	/**
	 * Cached name of {@link #makeid}, if needed and available from db, in {@link #toString()}.
	 * @since 0.9.43
	 */
	private transient String makeidName;

	/** null unless {@link #readAllTrips(boolean)} called */
	private transient List<Trip> allTrips;

	/**
	 * Get the Vehicles currently in the database.
	 * @param db  database connection
	 * @param activeSubsetFlags  0 for all vehicles, or flags to include only active or inactive and optionally
	 *     include the {@link #OTHER_VEHICLE} placeholder at the end of the results.
	 *     The active/inactive flags are {@link #FLAG_ONLY_ACTIVE} and {@link #FLAG_ONLY_INACTIVE},
	 *     caller can set {@link #FLAG_WITH_OTHER} with either one.
	 * @return an array of Vehicle objects from the database, ordered by name, or null if none.
	 *     If {@link #FLAG_WITH_OTHER} is set and the database contains vehicles with the other status
	 *     (inactive/active), the {@link #OTHER_VEHICLE} placeholder will be at the end of the array.
	 * @throws IllegalArgumentException if conflicting flags {@link #FLAG_ONLY_ACTIVE} and {@link #FLAG_ONLY_INACTIVE}
	 *     are both set in {@code activeSubsetFlags}
	 * @throws IllegalStateException if db connection has been closed
	 * @see #getMostRecent(RDBAdapter)
	 */
	public static Vehicle[] getAll(final RDBAdapter db, final int activeSubsetFlags)
		throws IllegalArgumentException, IllegalStateException
	{
		if ((FLAG_ONLY_ACTIVE | FLAG_ONLY_INACTIVE) == (activeSubsetFlags & (FLAG_ONLY_ACTIVE | FLAG_ONLY_INACTIVE)))
			throw new IllegalArgumentException();

		final String activesSQL;
		if (0 != (activeSubsetFlags & FLAG_ONLY_ACTIVE))
			activesSQL = "is_active = 1";
		else if (0 != (activeSubsetFlags & FLAG_ONLY_INACTIVE))
			activesSQL = "is_active = 0";
		else
			activesSQL = null;

		Vector<String[]> ves = db.getRows
			(TABNAME, activesSQL, (String[]) null, FIELDS_AND_ID, "nickname COLLATE NOCASE", 0);
		if (ves == null)
			return null;

		// If requested, see if any others exist (inactive/active)
		final boolean hasOthers;
		if ((activesSQL != null) && (0 != (activeSubsetFlags & FLAG_WITH_OTHER)))
		{
			final int otherValue = (0 != (activeSubsetFlags & FLAG_ONLY_ACTIVE)) ? 0 : 1;
			final int count = db.getCount(TABNAME, "is_active", otherValue);
			hasOthers = (count > 0);
		} else {
			hasOthers = false;
		}

		Vehicle[] rv = new Vehicle[ves.size() + ((hasOthers) ? 1 : 0)];
		try {
			for (int i = ves.size() - 1; i >= 0; --i)
				rv[i] = new Vehicle(db, ves.elementAt(i));
			if (hasOthers)
				rv[rv.length - 1] = OTHER_VEHICLE;

			return rv;
		} catch (RDBKeyNotFoundException e) {
			return null;  // catch is req'd but won't happen; record came from db.
		}
	}

	/**
	 * Get the most recently added active vehicle in the database, if any.
	 * @param db  db connection
	 * @return the newest active vehicle by {@code _id}, or null if none in the database
	 * @throws IllegalStateException if db not open
	 * @see #getAll(RDBAdapter, int)
	 * @since 0.9.40
	 */
	public static Vehicle getMostRecent(RDBAdapter db)
		throws IllegalStateException
	{
		if (db == null)
			throw new IllegalStateException("db null");

		final int vID = db.getRowIntField(TABNAME, "MAX(_id)", "is_active=1", (String[]) null, 0);
		if (vID == 0)
			return null;

		try {
			return new Vehicle(db, vID);
		} catch (RDBKeyNotFoundException e) {
			return null;  // required by compiler, but we know the ID exists
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
	 * @param rec  Record's field contents, as returned by db.getRows({@link #FIELDS_AND_ID}); last element is _id
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
	 * {@code _id} is not filled here; the constructor has filled it already.
	 * @param rec  Record's field contents, as returned by db.getRow({@link #FIELDS})
	 *     or db.getRows({@link #FIELDS_AND_ID})
	 * @throws IllegalArgumentException if rec.length is too short
	 */
	private void initFields(final String[] rec)
		throws IllegalArgumentException
	{
		if (rec.length < 23)
			throw new IllegalArgumentException("length < 23: " + rec.length);
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
		fuel_qty_unit = (rec[17].equals("ga") ? 'G' : 'L');
		fuel_qty_deci = Integer.parseInt(rec[18]);
		comment = rec[19];
		is_active = rec[20].equals("1");
		if (rec[21] != null)
			date_added = Integer.parseInt(rec[21]);
		plate = rec[22];
	}

	/**
	 * Create a new vehicle, but don't yet write to the database.
	 * When ready to write (after any changes you make to this object),
	 * call {@link #insert(RDBAdapter)}.
	 *<P>
	 * <tt>last_tripid</tt> will be null, because this new vehicle
	 * hasn't been on any trips yet.
	 * <tt>is_active</tt> will be true.
	 * {@link #getDate_added()}'s field will be set to the current time using {@link System#currentTimeMillis()}.
	 *
	 * @param nickname  Nickname or color, or null; used in {@link #toString()}
	 * @param driver    Vehicle's usual driver or owner
	 * @param makeid    Vehicle make, an ID from {@link VehicleMake} table (unchecked foreign key)
	 * @param model     Model name; used in {@link #toString()}
	 * @param year      Model year, or 0 if unknown
	 * @param datefrom  Used starting at this date, or 0 if field is unused.
	 *     Date format is Unix-time integer, like {@link System#currentTimeMillis()} / 1000.
	 * @param dateto    Used until this date, or 0 if field is unused
	 * @param vin       VIN or null
	 * @param plate     License plate or tag, or null
	 * @param odo_orig  Original odometer, including tenths
	 * @param odo_curr  Current odometer, including tenths
	 * @param comment   Comment or null
	 * @throws IllegalArgumentException  if ! {@link Person#isDriver() driver.isDriver()}
	 */
	public Vehicle
		(String nickname, Person driver, int makeid, String model, int year,
		 int datefrom, int dateto, String vin, String plate, int odo_orig, int odo_curr, String comment)
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
		this.plate = plate;
		this.odo_orig = odo_orig;
		this.odo_curr = odo_curr;
		last_tripid = 0;
		this.comment = comment;
		this.is_active = true;
		date_added = (int) (System.currentTimeMillis() / 1000L);
	}

	/**
	 * Retrieve all Trips for this Vehicle.
	 * Cached after the first read, even if <tt>alsoTStops</tt> is different on the next call.
	 * @param alsoTStops  If true, call {@link Trip#readAllTStops()} for each trip found
	 * @return  ordered list of trips (sorted by time_start), or null if none
	 * @throws IllegalStateException if the db connection is closed
	 * @see Trip#tripsForVehicle(RDBAdapter, Vehicle, int, int, boolean, boolean, boolean)
	 */
	public List<Trip> readAllTrips(final boolean alsoTStops)
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
	 * Assumes no current TStop, because you could use that TStop's time instead.
	 * If the vehicle is currently on a trip, pass that current trip as {@code tr}.
	 * Otherwise will use {@link #getLastTripID()} to check most recent trip.
	 * Calls {@link Trip#readLatestTime()}.
	 * @param tr  The vehicle's current trip, if one is in progress, or null.
	 *          tr's dbConn should be valid (not closed).
	 * @return the time, or 0 if no completed trips for this vehicle
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

	/**
	 * Insert a new Vehicle record with the current field values of this object.
	 * Clears dirty field; sets id and dbConn fields.
	 * @return new record's primary key (_id)
	 * @throws IllegalStateException if the insert fails
	 */
	public int insert(RDBAdapter db)
		throws IllegalStateException
	{
		String dte_f, dte_t, last_tid;
		final String dte_a = (date_added != 0) ? Integer.toString(date_added) : null;
		dte_f = (date_from != 0) ? Integer.toString(date_from) : null;
		dte_t = (date_to != 0) ? Integer.toString(date_to) : null;
		last_tid = (last_tripid != 0) ? Integer.toString(last_tripid) : null;

		String[] fv =
		    { nickname, Integer.toString(driverid), Integer.toString(makeid),
		      model, Integer.toString(year), dte_f, dte_t, vin,
		      Integer.toString(odo_orig), Integer.toString(odo_curr), last_tid,
		      // TODO construc/gui, not hardcoded, for these:  (also getters/setters/commit)
		      //    "distance_storage", "expense_currency", "expense_curr_sym", "expense_curr_deci", "fuel_curr_deci",
		      //    "fuel_type", "fuel_qty_unit", "fuel_qty_deci"
		      DISTANCE_STORAGE_DEFAULT,  // "MI"
		      EXPENSE_CURRENCY_DEFAULT,  // "USD"
		      EXPENSE_CURR_SYM_DEFAULT,  // "$"
		      Integer.toString(EXPENSE_CURR_DECI_DEFAULT),  // "2"
		      Integer.toString(FUEL_CURR_DECI_DEFAULT), // "3"
		      Character.toString(FUEL_TYPE_DEFAULT),    // "G"
		      FUEL_QTY_UNIT_DEFAULT,     // "ga"
		      Integer.toString(FUEL_QTY_DECI_DEFAULT),  // "3"
		      comment, (is_active ? "1" : "0"), dte_a, plate
		    };
		id = db.insert(TABNAME, FIELDS, fv, true);
		dirty = false;
		dbConn = db;

		return id;
	}

	/**
	 * Commit changes to an existing Vehicle record.
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
		final String dte_a = (date_added != 0) ? Integer.toString(date_added) : null;
		dte_f = (date_from != 0) ? Integer.toString(date_from) : null;
		dte_t = (date_to != 0) ? Integer.toString(date_to) :  null;
		l_tripid = (last_tripid != 0) ? Integer.toString(last_tripid) : null;
		String[] fv =
		    {
			nickname, Integer.toString(driverid), Integer.toString(makeid),
			model, Integer.toString(year), dte_f, dte_t, vin,
			Integer.toString(odo_orig), Integer.toString(odo_curr), l_tripid,
			comment, (is_active ? "1" : "0"), dte_a, plate
		    };
		dbConn.update(TABNAME, id, FIELDS_BASIC, fv);
		dirty = false;
	}

	/** @return the optional nickname or color */
	public String getNickname() {
		return nickname;
	}

	/** Set the optional nickname or color */
	public void setNickname(String nickname) {
		this.nickname = nickname;
		dirty = true;
	}

	/** Get the vehicle's usual driver, a {@link Person} ID */
	public int getDriverID() {
		return driverid;
	}

	/**
	 * Set the vehicle's usual driver.
	 * @param driver  New driver; not null
	 * @throws IllegalArgumentException  if ! {@link Person#isDriver() driver.isDriver()}
	 * @throws NullPointerException  if {@code driver} is null
	 */
	public void setDriverID(Person driver)
		throws IllegalArgumentException, NullPointerException
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

	/** @return the model year, or 0 if unknown */
	public int getYear() {
		return year;
	}

	/** Set the model year */
	public void setYear(int year) {
		this.year = year;
		dirty = true;
	}

	/**
	 * Get the date this {@link Vehicle} was added to the database, if known.
	 * @return  The date added, in unix format.  0 if field is empty (null).
	 * @see #getDate_from()
	 * @since 0.9.43
	 */
	public int getDate_added()
	{
		return date_added;
	}

	/**
	 * @return the Date From ("in use since" date), unix format.  0 for empty/unused.
	 * @see #getDate_added()
	 * @see #getDate_to()
	 */
	public int getDate_from() {
		return date_from;
	}

	/**
	 * Set or clear the Date From ("in use since" date).
	 * @param dateFrom  Date in unix format, or 0 for unused
	 */
	public void setDate_from(int dateFrom) {
		date_from = dateFrom;
		dirty = true;
	}

	/**
	 * This field is not yet populated from the android app or bookedit.
	 * @return the Date To ("in use until" date), unix format.  0 for empty/unused.
	 * @see #getDate_from()
	 */
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

	public String getPlate() {
		return plate;
	}

	public void setPlate(String plate) {
		this.plate = plate;
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
	 * When starting a new trip, {@code last_tripid} is used to find the vehicle's previous stopping point.
	 * @return trip ID, or 0 if no trip has been completed.
	 * @see #setOdometerCurrentAndLastTrip(int, Trip, boolean)
	 * @see #getTripInProgress()
	 */
	public int getLastTripID() {
		return last_tripid;
	}

	/**
	 * Does this vehicle (which can't be the current vehicle) have a trip in progress?
	 * Before calling this, check {@link Settings} for the {@code CURRENT_VEHICLE} and {@code CURRENT_TRIP}.
	 * If this is the current vehicle, its {@link #getLastTripID()} won't be the current trip,
	 * so call {@link VehSettings#getCurrentTrip(RDBAdapter, Vehicle, boolean)} instead.
	 *<P>
	 * Checks {@link #getLastTripID()}.
	 * If nonzero, checks that trip's {@link Trip#getOdo_end()} using the vehicle's open db connection.
	 * If trip's {@code odo_end} is 0, that trip is still in progress.
	 *<P>
	 * @return  This non-current vehicle's trip in progress, or {@code null} if none.
	 *          If the db connection is closed, or trip record not found somehow, returns null.
	 * @since 0.9.20
	 */
	public Trip getTripInProgress() {
		if (0 == last_tripid)
			return null;

		Trip tr;
		try {
			tr = new Trip(dbConn, last_tripid);
		} catch (IllegalStateException e) {
			return null;
		} catch (RDBKeyNotFoundException e) {
			return null;
		}

		return (tr.getOdo_end() == 0) ? tr : null;
	}

	/**
	 * At the end of a trip, set the current odometer and last trip ID.
	 * Also used when saving vehicle's current trip just before changing current vehicle.
	 * @param newValue10ths  New current odometer
	 * @param tr  New latest trip; not null
	 * @param commitNow commit these 2 fields ONLY, right now; if false, just set {@link #isDirty()}.
	 * @throws NullPointerException if {@code tr} is null
	 */
	public void setOdometerCurrentAndLastTrip(int newValue10ths, Trip tr, final boolean commitNow)
		throws NullPointerException
	{
		// if no changes, don't update db
		if (commitNow && (! dirty) && (allTrips == null)
		    && (odo_curr == newValue10ths) && (last_tripid == tr.getID()))
			return;

		odo_curr = newValue10ths;
		last_tripid = tr.getID();
		if ((allTrips != null) && ! allTrips.isEmpty())
		{
			if (allTrips.get(allTrips.size() - 1) != tr)
				allTrips.add(tr);
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

	public boolean isActive() {
		return is_active;
	}

	public void setActive(final boolean isActive) {
		if (isActive == is_active)
			return;

		is_active = isActive;
		dirty = true;
	}

	/**
	 * Format a fixed-decimal currency amount, using this Vehicle's currency settings:
	 * {@link #expense_curr_sym}, {@link #expense_curr_deci}.
	 * @param sb  Use this stringbuilder; if null, a new one is created and returned.
	 * @param deciAmt Amount to format, as from {@link TStop#getExpense_total()} or {@link TStopGas#price_total}
	 * @param withSymbol  If true include {@link #expense_curr_sym}, not only the amount, in formatting
	 * @return the stringbuilder with {@link #expense_curr_sym} and formatted currency amount appended
	 * @since 0.9.61
	 */
	public StringBuilder formatCurrFixedDeci(StringBuilder sb, final int deciAmt, final boolean withSymbol)
	{
		if (sb == null)
			sb = new StringBuilder();

		if (withSymbol)
			sb.append(expense_curr_sym);
		sb.append(RDBSchema.formatFixedDec(deciAmt, expense_curr_deci));

		return sb;
	}

	/** format is: "[ nickname - ] year [model or make]" */
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		if ((nickname != null) && (nickname.length() > 0))
		{
			sb.append(nickname);
			sb.append(" -");
		}
		if (year != 0)
		{
			if (sb.length() > 0)
				sb.append(' ');
			sb.append(year);
		}
		if ((model != null) && (model.length() > 0))
		{
			if (sb.length() > 0)
				sb.append(' ');
			sb.append(model);
		} else {
			// no model: try to use makeid
			if ((makeidName == null) && (dbConn != null))
			{
				try {
					VehicleMake mk = new VehicleMake(dbConn, makeid);
					makeidName = mk.getName();
				}
				catch(Exception e) {}
			}
			if (makeidName != null)
			{
				if (sb.length() > 0)
					sb.append(' ');
				sb.append(makeidName);
			}
		}

		if (sb.length() == 0)
			sb.append("(Vehicle, all fields empty)");  // fallback, GUI enforces fields; can skip I18N

		return sb.toString();
	}

	/**
	 * Delete an existing Vehicle record, and also delete related {@link VehSettings} records.
	 * Does not delete trips, TStops, etc.
	 *
	 * @throws NullPointerException if dbConn was null because
	 *     this is a new record, not an existing one
	 */
	public void delete()
		throws NullPointerException
	{
		VehSettings.deleteAll(dbConn, this);  // remove related records before Vehicle
		dbConn.delete(TABNAME, id);
		deleteCleanup();
	}

}  // public class Vehicle
