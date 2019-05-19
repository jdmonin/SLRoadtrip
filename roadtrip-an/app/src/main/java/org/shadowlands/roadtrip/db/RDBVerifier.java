/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2011,2013,2015,2019 Jeremy D Monin <jdmonin@nand.net>
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

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;

/**
 * Structural verifier for an open {@link RDBAdapter RDB SQLite database}.
 * See {@link #verify(int)} for details on the available levels of verification.
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
	 * Includes {@link FreqTrip} and their {@link FreqTripTStop}s.
	 */
	public static final int LEVEL_MDATA = 2;

	/**
	 * Validate transactional data (trips, stops, gas, etc)
	 * against master data, after the {@link #LEVEL_MDATA} checks.
	 */
	public static final int LEVEL_TDATA = 3;

	/**
	 * Maximum number of items to allow to fail before refusing to continue the current level of validation.
	 * Default is 100. Sets the max length of {@link #failedItems} at the time validation was ran.
	 * @since 0.9.70
	 */
	public static int MAX_FAILURE_ITEMS = 100;

	/**
	 * If true, store failure detail description text in {@link #failedItems}.
	 * If false, discard; useful for runs on a device where the detail text
	 * won't be shown to the user.
	 * Default is {@code true}.
	 * @since 0.0.62
	 */
	public static boolean FAILURES_HAVE_DESCRIPTIONS = true;

	private RDBAdapter db;

	/**
	 * For iterative calls to {@link #verify(int)}, the level of the
	 * last call if it was successful.
	 */
	private int successfulVerifyLevel = 0;

	/**
	 * Any items which failed validation during {@link #verify(int)}.
	 * Max expected length is {@link #MAX_FAILURE_ITEMS}.
	 * From outside the class, treat as read-only.
	 * @since 0.9.70
	 */
	public final List<FailedItem> failedItems = new ArrayList<FailedItem>();

	///////////////////////////////////////////////////////////
	// List of failed items
	///////////////////////////////////////////////////////////

	/** Add a {@link FailedItem} to {@link #failedItems}. */
	private boolean addFailedItem(int id, String desc)
	{
		return _impl_addFailedItem(id, null, null, desc);
	}

	/** Add a {@link FailedItem} to {@link #failedItems}. */
	private boolean addFailedItem(int id, RDBRecord failedRel, String desc)
	{
		return _impl_addFailedItem(id, null, failedRel, desc);
	}

	/** Add a {@link FailedItem} to {@link #failedItems}. */
	private boolean addFailedItem(RDBRecord data, String desc)
	{
		return _impl_addFailedItem(0, data, null, desc);
	}

	/** Add a {@link FailedItem} to {@link #failedItems}. */
	private boolean addFailedItem(RDBRecord data, RDBRecord failedRel, String desc)
	{
		return _impl_addFailedItem(0, data, failedRel, desc);
	}

	/**
	 * Add a {@link FailedItem} to {@link #failedItems}.
	 * Same params and exceptions as {@link FailedItem#FailedItem(int, RDBRecord, RDBRecord, String)}.
	 */
	private boolean _impl_addFailedItem(int id, RDBRecord data, RDBRecord failedRel, String desc)
		throws IllegalArgumentException
	{
		if (failedItems.size() >= MAX_FAILURE_ITEMS)
			return false;

		failedItems.add(new FailedItem(id, data, failedRel, (FAILURES_HAVE_DESCRIPTIONS) ? desc : null));
		return true;
	}

	///////////////////////////////////////////////////////////
	// Caches for LEVEL_MDATA, LEVEL_TDATA
	///////////////////////////////////////////////////////////

	/**
	 * {@link Vehicle} cache, for {@link #LEVEL_MDATA} verification. Each item is its own key.
	 * TIntObjectHashMap is from <A href="http://trove4j.sourceforge.net/">trove</A> (LGPL).
	 * (<A href="http://trove4j.sourceforge.net/javadocs/">javadocs</a>)
	 */
	private TIntObjectHashMap<Vehicle> vehCache;

	/**
	 * {@link Person} and Driver cache. Each item is its own key.
	 */
	private TIntObjectHashMap<Person> persCache;

	/**
	 * {@link VehicleMake} cache. Each item is its own key.
	 */
	private TIntObjectHashMap<VehicleMake> vehMakeCache;

	/**
	 * {@link GeoArea} cache. Each item is its own key.
	 */
	private TIntObjectHashMap<GeoArea> geoAreaCache;

	/**
	 * {@link Location} cache. Each item is its own key.
	 */
	private TIntObjectHashMap<Location> locCache;

	/**
	 * {@link ViaRoute} cache. Each item is its own key.
	 */
	private TIntObjectHashMap<ViaRoute> viaCache;

	/**
	 * {@link GasBrandGrade} cache. Each item is its own key.
	 */
	private TIntObjectHashMap<GasBrandGrade> gbgCache;

	/**
	 * {@link FreqTrip} cache. Each item is its own key.
	 */
	private TIntObjectHashMap<FreqTrip> ftCache;

	/**
	 * {@link Trip} cache, for {@link #LEVEL_TDATA}. Each item is its own key.
	 */
	private TIntObjectHashMap<Trip> trCache;

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
	 * Verify the database to a given level.
	 *<P>
	 * You can call <tt>verify</tt> multiple times to iteratively
	 * verify at higher levels.  That is, if you've already called
	 * <tt>verify({@link #LEVEL_MDATA})</tt>, then calling <tt>verify({@link #LEVEL_TDATA}}</tt>
	 * won't re-verify the master data.  It's important to not allow
	 * changes to the data between iterative calls.
	 *<P>
	 * If verification fails at {@link #LEVEL_MDATA} or higher, the failed data items
	 * are added to {@link #failedItems} until too many have failed ({@link #MAX_FAILURE_ITEMS}).
	 *
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

		if (successfulVerifyLevel < LEVEL_PHYS)
		{
			if (null != db.execPragmaIntegCheck())
				return LEVEL_PHYS;
			successfulVerifyLevel = LEVEL_PHYS;
		}
		if (level <= LEVEL_PHYS)
			return 0;

		if (successfulVerifyLevel < LEVEL_MDATA)
		{
			if (! verify_mdata())
				return LEVEL_MDATA;
			successfulVerifyLevel = LEVEL_MDATA;
		}
		if (level <= LEVEL_MDATA)
			return 0;

		if (successfulVerifyLevel < LEVEL_TDATA)
		{
			if (! verify_tdata())
				return LEVEL_TDATA;
			successfulVerifyLevel = LEVEL_TDATA;
		}

		return 0;
	}

	///////////////////////////////////////////////////////////
	// Cache methods for LEVEL_MDATA, LEVEL_TDATA
	///////////////////////////////////////////////////////////

	/** Get a vehicle from the db or the cache, or null if <tt>id</tt> not found in the cache or database. */
	private Vehicle getVehicle(final int id)
	{
		Vehicle v = vehCache.get(id);
		if (v == null)
		{
			try
			{
				v = new Vehicle(db, id);
				vehCache.put(id, v);
			}
			catch (Throwable th) {}
		}

		return v;
	}

	/** Get a person/driver from the db or the cache, or null if <tt>id</tt> not found in the cache or database. */
	private Person getPerson(final int id)
	{
		Person p = persCache.get(id);
		if (p == null)
		{
			try
			{
				p = new Person(db, id);
				persCache.put(id, p);
			}
			catch (Throwable th) {}
		}

		return p;
	}

	/** Get a VehicleMake from the db or the cache, or null if <tt>id</tt> not found in the cache or database. */
	private VehicleMake getVehicleMake(final int id)
	{
		VehicleMake vm = vehMakeCache.get(id);
		if (vm == null)
		{
			try
			{
				vm = new VehicleMake(db, id);
				vehMakeCache.put(id, vm);
			}
			catch (Throwable th) {}
		}

		return vm;
	}

	/** Get a Location from the db or the cache, or null if <tt>id</tt> not found in the cache or database. */
	private GeoArea getGeoArea(final int id)
	{
		GeoArea ga = geoAreaCache.get(id);
		if (ga == null)
		{
			try
			{
				ga = new GeoArea(db, id);
				geoAreaCache.put(id, ga);
			}
			catch (Throwable th) {}
		}

		return ga;
	}

	/** Get a Location from the db or the cache, or null if <tt>id</tt> not found in the cache or database. */
	private Location getLocation(final int id)
	{
		Location lo = locCache.get(id);
		if (lo == null)
		{
			try
			{
				lo = new Location(db, id);
				locCache.put(id, lo);
			}
			catch (Throwable th) {}
		}

		return lo;
	}

	/** Get a ViaRoute from the db or the cache, or null if <tt>id</tt> not found in the cache or database. */
	private ViaRoute getViaRoute(final int id)
	{
		ViaRoute via = viaCache.get(id);
		if (via == null)
		{
			try
			{
				via = new ViaRoute(db, id);
				viaCache.put(id, via);
			}
			catch (Throwable th) {}
		}

		return via;
	}

	/** Get a GasBrandGrade from the db or the cache, or null if <tt>id</tt> not found in the cache or database. */
	private GasBrandGrade getGasBrandGrade(final int id)
	{
		GasBrandGrade gbg = gbgCache.get(id);
		if (gbg == null)
		{
			try
			{
				gbg = new GasBrandGrade(db, id);
				gbgCache.put(id, gbg);
			}
			catch (Throwable th) {}
		}

		return gbg;
	}

	/** Get a FreqTrip from the db or the cache, or null if <tt>id</tt> not found in the cache or database. */
	private FreqTrip getFreqTrip(final int id)
	{
		FreqTrip ft = ftCache.get(id);
		if (ft == null)
		{
			try
			{
				ft = new FreqTrip(db, id);
				ftCache.put(id, ft);
			}
			catch (Throwable th) {}
		}

		return ft;
	}

	///////////////////////////////////////////////////////////
	// LEVEL_MDATA methods
	///////////////////////////////////////////////////////////

	/**
	 * Verify to {@link #LEVEL_MDATA}.
	 * Assumes already verified at {@link #LEVEL_PHYS}.
	 * Checks foreign keys of the Vehicle, Location, FreqTrip and FreqTripTStop tables.
	 * @return true if consistent, false if problems found (see {@link #failedItems} for details).
	 * @throws IllegalStateException  if db is closed
	 */
	private boolean verify_mdata()
		throws IllegalStateException
	{
		vehCache = new TIntObjectHashMap<Vehicle>();
		persCache = new TIntObjectHashMap<Person>();
		vehMakeCache = new TIntObjectHashMap<VehicleMake>();
		geoAreaCache = new TIntObjectHashMap<GeoArea>();
		locCache = new TIntObjectHashMap<Location>();
		viaCache = new TIntObjectHashMap<ViaRoute>();
		gbgCache = new TIntObjectHashMap<GasBrandGrade>();
		ftCache = new TIntObjectHashMap<FreqTrip>();

		if (! verify_mdata_vehicle())
			return false;
		if (! verify_mdata_location())
			return false;
		if (! verify_mdata_viaroute())
			return false;
		if (! verify_mdata_freqtrip())
			return false;
		if (! verify_mdata_freqtrip_tstop())
			return false;

		return true;
	}

	/**
	 * Verify the {@link Vehicle}s as part of {@link #verify_mdata()}.
	 * Verify the driverid and makeid.
	 * @return true if OK, false if too many inconsistencies
	 */
	private boolean verify_mdata_vehicle()
	{
		final Vehicle[] all = Vehicle.getAll(db, 0);
		if (all == null)
		{
			addFailedItem(0, "0 vehicles in DB");
			return false;  // there must be vehicles
		}

		for (int i = 0; i < all.length; ++i)
		{
			Vehicle v = all[i];
			vehCache.put(v.id, v);

			int id = v.getMakeID();
			if (null == getVehicleMake(id))
				if (! addFailedItem(id, v, "Can't load VehicleMake"))
					return false;
			id = v.getDriverID();
			if (null == getPerson(id))
				if (! addFailedItem(id, v, "Can't load Person for driver"))
					return false;
		}

		return true;
	}

	/**
	 * Verify the {@link Location}s as part of {@link #verify_mdata()}.
	 * Verify the geoarea and latest_gas_brandgrade_id.
	 * @return true if OK, false if any inconsistencies
	 */
	private boolean verify_mdata_location()
	{
		final Location[] all = Location.getAll(db, -1);
		if (all == null)
			return true;

		for (int i = 0; i < all.length; ++i)
		{
			Location lo = all[i];
			locCache.put(lo.id, lo);

			final int aid = lo.getAreaID();
			if ((aid != 0) && (null == getGeoArea(aid)))
			{
				if (! addFailedItem(aid, lo, "Can't load GeoArea"))
					return false;
			}
			final int gbg = lo.getLatestGasBrandGradeID();
			if ((gbg != 0) && (null == getGasBrandGrade(gbg)))
			{
				if (! addFailedItem(gbg, lo, "Can't load GasBrandGrade"))
					return false;
			}
		}

		return failedItems.isEmpty();
	}

	/**
	 * Verify the {@link ViaRoute}s as part of {@link #verify_mdata()}.
	 * Verify the locid_from and locid_to.
	 * @return true if OK, false if any inconsistencies
	 */
	private boolean verify_mdata_viaroute()
	{
		final ViaRoute[] all = ViaRoute.getAll(db, -1, -1);
		if (all == null)
			return true;

		for (int i = 0; i < all.length; ++i)
		{
			ViaRoute via = all[i];
			viaCache.put(via.id, via);

			int lid = via.getLocID_From();
			if (null == getLocation(lid))
			{
				if (! addFailedItem(lid, via, "Can't load LocID_From"))
					return false;
			}
			lid = via.getLocID_To();
			if (null == getLocation(lid))
			{
				if (! addFailedItem(lid, via, "Can't load LocID_To"))
					return false;
			}
		}

		return failedItems.isEmpty();
	}

	/**
	 * Verify the {@link FreqTrip}s as part of {@link #verify_mdata()}.
	 * Verify the start_locid and end_locid, start_aid and end_aid_roadtrip.
	 * @return true if OK, false if any inconsistencies
	 */
	private boolean verify_mdata_freqtrip()
	{
		final Vector<FreqTrip> all = FreqTrip.getAll(db, false);
		if (all == null)
			return true;

		for (int i = all.size() - 1; i >= 0; --i)
		{
			FreqTrip ft = all.elementAt(i);
			ftCache.put(ft.id, ft);

			int id = ft.getStart_aID();
			if (null == getGeoArea(id))
			{
				if (! addFailedItem(id, ft, "Can't load GeoArea"))
					return false;
			}
			id = ft.getStart_locID();
			if (null == getLocation(id))
			{
				if (! addFailedItem(id, ft, "Can't load Start_Location"))
					return false;
			}
			id = ft.getEnd_locID();
			if (null == getLocation(id))
			{
				if (! addFailedItem(id, ft, "Can't load End_Location"))
					return false;
			}
			id = ft.getEnd_aID_roadtrip();
			if ((id != 0) && (null == getGeoArea(id)))
			{
				if (! addFailedItem(id, ft, "Can't load End_GeoArea"))
					return false;
			}
			id = ft.getEnd_ViaRouteID();
			if ((id != 0) && (null == getViaRoute(id)))
			{
				if (! addFailedItem(id, ft, "Can't load ViaRoute"))
					return false;
			}
		}

		return failedItems.isEmpty();
	}

	/**
	 * Verify the {@link FreqTripTStop}s as part of {@link #verify_mdata()}.
	 * Verify the freqtripid and locid.
	 * @return true if OK, false if any inconsistencies
	 */
	private boolean verify_mdata_freqtrip_tstop()
	{
		final Vector<FreqTripTStop> all = FreqTripTStop.stopsForTrip(db, null);
		if (all == null)
			return true;

		for (int i = all.size() - 1; i >= 0; --i)
		{
			FreqTripTStop fts = all.elementAt(i);

			int id = fts.getFreqTripID();
			if (null == getFreqTrip(id))
			{
				if (! addFailedItem(id, fts, "Can't load FreqTrip"))
					return false;
			}
			id = fts.getLocationID();
			if (null == getLocation(id))
			{
				if (! addFailedItem(id, fts, "Can't load Location"))
					return false;
			}
			id = fts.getViaID();
			if ((id != 0) && (null == getViaRoute(id)))
			{
				if (! addFailedItem(id, fts, "Can't load ViaRoute"))
					return false;
			}
		}

		return failedItems.isEmpty();
	}

	///////////////////////////////////////////////////////////
	// LEVEL_TDATA methods
	///////////////////////////////////////////////////////////

	/**
	 * Verify to {@link #LEVEL_TDATA}.
	 * Assumes already verified at {@link #LEVEL_MDATA}.
	 * @return true if consistent, false if problems found (see {@link #failedItems} for details).
	 * @throws IllegalStateException  if db is closed
	 */
	private boolean verify_tdata()
		throws IllegalStateException
	{
		trCache = new TIntObjectHashMap<Trip>();

		if (! verify_tdata_trip())
			return false;
		if (! verify_tdata_tstop())
			return false;

		return true;
	}

	/**
	 * Verify the {@link Trip}s as part of {@link #verify_tdata()}.
	 * Verify the vid, did, aid, tstopid_start, freqtripid, and roadtrip_end_aid.
	 * @return true if OK, false if any inconsistencies
	 */
	private boolean verify_tdata_trip()
	{
		final List<Trip> all = Trip.tripsForVehicle(db, null, true);
		if (all == null)
			return true;

		for (int i = all.size() - 1; i >= 0; --i)
		{
			final Trip tr = all.get(i);
			trCache.put(tr.id, tr);

			int id = tr.getVehicleID();
			if (null == getVehicle(id))
			{
				if (! addFailedItem(id, tr, "Can't load Vehicle"))
					return false;
			}
			id = tr.getDriverID();
			if (null == getPerson(id))
			{
				if (! addFailedItem(id, tr, "Can't load Person for driver"))
					return false;
			}
			id = tr.getAreaID();
			if (null == getGeoArea(id))
			{
				if (! addFailedItem(id, tr, "Can't load GeoArea"))
					return false;
			}
			id = tr.getFreqTripID();
			if ((id != 0) && (null == getFreqTrip(id)))
			{
				if (! addFailedItem(id, tr, "Can't load FreqTrip"))
					return false;
			}
			id = tr.getRoadtripEndAreaID();
			if ((id != 0) && (null == getGeoArea(id)))
			{
				return false;
			}
			id = tr.getStartTStopID();
			if (id != 0)
			{
				try
				{
					@SuppressWarnings("unused")
					TStop ts = new TStop(db, id);
				} catch (Throwable th) {
					if (! addFailedItem(id, tr, "Can't load Start_TStop"))
						return false;
				}
			}
		}

		return failedItems.isEmpty();
	}

	/**
	 * Verify the {@link TStop}s as part of {@link #verify_tdata()}.
	 * Verify the locid a_id via_id ; flag_sides.
	 * When flag_sides indicates tstop_gas, verify vid and gas_brandgrade_id.
	 * @return true if OK, false if any inconsistencies
	 */
	private boolean verify_tdata_tstop()
	{
		TIntObjectIterator<Trip> iter = trCache.iterator();
		while (iter.hasNext())
		{
			iter.advance();
			Trip tr = iter.value();
			Vector<TStop> vts = tr.readAllTStops();
			if (vts == null)
				continue;

			final int L = vts.size();
			TStop tsPrev = null;
			for (int i = 0; i < L; ++i)
			{
				final TStop ts = vts.elementAt(i);

				int id = ts.getLocationID();
				if (id != 0)
				{
					if (null == getLocation(id))
					{
						if (! addFailedItem(id, ts, "Can't load Location"))
							return false;
					}
				} else {
					String loc = ts.getLocationDescr();
					if ((loc == null) || (loc.length() == 0))
					{
						if (! addFailedItem(ts, "LocationDescr null or empty"))
							return false;
					}
				}
				id = ts.getAreaID();
				if ((id != 0) && (null == getGeoArea(id)))
				{
					if (! addFailedItem(id, ts, "Can't load GeoArea"))
						return false;  // TODO could recover this from ts.locid.a_id
				}
				id = ts.getVia_id();
				if (id != 0)
				{
					ViaRoute via = getViaRoute(id);
					if (null == via)
					{
						if (! addFailedItem(id, ts, "Can't load ViaRoute"))
							return false;
					}
					if (via.getLocID_To() != ts.getLocationID())
					{
						if (! addFailedItem(via, ts, "TStop's Location != Via's LocID_To"))
							return false;
					}
					if (tsPrev != null)
					{
						if ((ts.getTime_stop() != tsPrev.getTime_stop())  // ignore duplicate here
						    && (via.getLocID_From() != tsPrev.getLocationID()))
						{
							if (! addFailedItem(via, tsPrev, "TStop's Location != Via's LocID_From"))
								return false;
						}

						// TODO also validate for first tstop, when tsPrev == null
						//      because previous location is stored in previous trip
					}
				}
				if (ts.isSingleFlagSet(TStop.FLAG_GAS))
				{
					try
					{
						TStopGas tsg = new TStopGas(db, ts.id);
						if (tsg.vid != tr.getVehicleID())
						{
							if (! addFailedItem(tsg, ts, "TStopGas's Vehicle != TStop's Vehicle"))
								return false;
						}
						int gbg = tsg.gas_brandgrade_id;
						if ((gbg != 0) && (null == getGasBrandGrade(gbg)))
						{
							if (! addFailedItem(gbg, tsg, "Can't load GasBrandGrade"))
								return false;
						}
					} catch (Throwable th) {
						if (! addFailedItem(ts.id, ts, "Can't load TStopGas"))
							return false;
					}
				}

				tsPrev = ts;
			}
		}

		return failedItems.isEmpty();
	}

	/**
	 * Details about a data item which failed validation in {@link RDBVerifier#verify(int)}.
	 * Validation failures for overall conditions (like no vehicles in DB) will have {@link #id} == 0
	 * and {@link #data} == {@code null}.
	 * @since 0.9.70
	 */
	public static final class FailedItem
	{
		/**
		 * 0, or the ID of the data record which failed validation;
		 * used only if record couldn't be loaded into {@link #data}.
		 * If used, {@link #failedRelData} might still be != {@code null}.
		 */
		public final int id;

		/**
		 * The record which failed validation, if it could be loaded.
		 * Otherwise {@code null}, and {@link #id} is set instead.
		 * If {@code null}, {@link #failedRelData} might still be != {@code null}.
		 */
		public final RDBRecord data;

		/**
		 * The related record, if any, to the {@link #data} having failed validation.
		 * Otherwise {@code null}.
		 *<H5>Examples:</H5>
		 * If a {@link VehicleMake} can't be loaded, this field holds its {@link Vehicle}.
		 * If a {@link ViaRoute}'s location isn't its referring {@link TStop}'s location,
		 * this field holds the TStop.
		 */
		public final RDBRecord failedRelData;

		/**
		 * Failure description, in English for now (not localized), or "?" if none; never {@code null}.
		 * Always "?" when {@link RDBVerifier#FAILURES_HAVE_DESCRIPTIONS} is {@code false}.
		 */
		public final String desc;

		/**
		 * Construct a FailedItem.
		 * @param id {@link #id}
		 * @param data {@link #data}
		 * @param failedRel {@link #failedRelData}, if any
		 * @param desc  {@link #desc}; if null, "?" will be used
		 * @throws IllegalArgumentException if both {@code data} and {@code id} are set
		 */
		public FailedItem(int id, RDBRecord data, RDBRecord failedRel, String desc)
			throws IllegalArgumentException
		{
			if ((id != 0) && (data != null))
				throw new IllegalArgumentException("both id and data");
			if (desc == null)
				desc = "?";
			this.id = id;
			this.data = data;
			this.failedRelData = failedRel;
			this.desc = desc;
		}
	}

}
