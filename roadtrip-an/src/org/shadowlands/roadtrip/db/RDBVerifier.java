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

import java.util.Vector;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;

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
	 * @return true if consistent, false if problems found.
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
	 * @return true if OK, false if inconsistencies
	 */
	private boolean verify_mdata_vehicle()
	{
		final Vehicle[] all = Vehicle.getAll(db);
		if (all == null)
			return false;  // there must be vehicles
		for (int i = 0; i < all.length; ++i)
		{
			Vehicle v = all[i];
			vehCache.put(v.id, v);

			if (null == getVehicleMake(v.getMakeID()))
				return false;
			if (null == getPerson(v.getDriverID()))
				return false;
		}
		return true;
	}

	/**
	 * Verify the {@link Location}s as part of {@link #verify_mdata()}.
	 * Verify the geoarea and latest_gas_brandgrade_id.
	 * @return true if OK, false if inconsistencies
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
				return false;
			final int gbg = lo.getLatestGasBrandGradeID();
			if ((gbg != 0) && (null == getGasBrandGrade(gbg)))
				return false;
		}
		return true;
	}

	/**
	 * Verify the {@link ViaRoute}s as part of {@link #verify_mdata()}.
	 * Verify the locid_from and locid_to.
	 * @return true if OK, false if inconsistencies
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

			if (null == getLocation(via.getLocID_From()))
				return false;
			if (null == getLocation(via.getLocID_To()))
				return false;
		}
		return true;
	}

	/**
	 * Verify the {@link FreqTrip}s as part of {@link #verify_mdata()}.
	 * Verify the start_locid and end_locid, start_aid and end_aid_roadtrip.
	 * @return true if OK, false if inconsistencies
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

			if (null == getGeoArea(ft.getStart_aID()))
				return false;
			if (null == getLocation(ft.getStart_locID()))
				return false;
			if (null == getLocation(ft.getEnd_locID()))
				return false;
			int id = ft.getEnd_aID_roadtrip();
			if ((id != 0) && (null == getGeoArea(id)))
				return false;
			id = ft.getEnd_ViaRouteID();
			if ((id != 0) && (null == getViaRoute(id)))
				return false;
		}
		return true;
	}

	/**
	 * Verify the {@link FreqTripTStop}s as part of {@link #verify_mdata()}.
	 * Verify the freqtripid and locid.
	 * @return true if OK, false if inconsistencies
	 */
	private boolean verify_mdata_freqtrip_tstop()
	{
		final Vector<FreqTripTStop> all = FreqTripTStop.stopsForTrip(db, null);
		if (all == null)
			return true;
		for (int i = all.size() - 1; i >= 0; --i)
		{
			FreqTripTStop fts = all.elementAt(i);

			if (null == getFreqTrip(fts.getFreqTripID()))
				return false;
			if (null == getLocation(fts.getLocationID()))
				return false;
			int id = fts.getViaID();
			if ((id != 0) && (null == getViaRoute(id)))
				return false;
		}
		return true;
	}

	///////////////////////////////////////////////////////////
	// LEVEL_TDATA methods
	///////////////////////////////////////////////////////////

	/**
	 * Verify to {@link #LEVEL_TDATA}.
	 * Assumes already verified at {@link #LEVEL_MDATA}.
	 * @return true if consistent, false if problems found.
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
	 * @return true if OK, false if inconsistencies
	 */
	private boolean verify_tdata_trip()
	{
		final Vector<Trip> all = Trip.tripsForVehicle(db, null, true);
		if (all == null)
			return true;
		for (int i = all.size() - 1; i >= 0; --i)
		{
			Trip tr = all.elementAt(i);
			trCache.put(tr.id, tr);

			if (null == getVehicle(tr.getVehicleID()))
				return false;
			if (null == getPerson(tr.getDriverID()))
				return false;
			if (null == getGeoArea(tr.getAreaID()))
				return false;
			int id = tr.getFreqTripID();
			if ((id != 0) && (null == getFreqTrip(id)))
				return false;
			id = tr.getRoadtripEndAreaID();
			if ((id != 0) && (null == getGeoArea(id)))
				return false;
			id = tr.getStartTStopID();
			if (id != 0)
			{
				try
				{
					@SuppressWarnings("unused")
					TStop s = new TStop(db, id);
				}
				catch (Throwable th) { return false; }
			}
		}
		return true;
	}

	/**
	 * Verify the {@link TStop}s as part of {@link #verify_tdata()}.
	 * Verify the locid a_id via_id ; flag_sides.
	 * When flag_sides indicates tstop_gas, verify vid and gas_brandgrade_id.
	 * @return true if OK, false if inconsistencies
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
			for (int i = vts.size() - 1; i >= 0; --i)
			{
				TStop ts = vts.elementAt(i);

				int id = ts.getLocationID();
				if (id != 0)
				{
					if (null == getLocation(id))
						return false;
				} else {
					String loc = ts.getLocationDescr();
					if ((loc == null) || (loc.length() == 0))
						return false;
				}
				id = ts.getAreaID();
				if ((id != 0) && (null == getGeoArea(id)))
					return false;
				id = ts.getVia_id();
				if ((id != 0) && (null == getViaRoute(id)))
					return false;
				if (ts.isSingleFlagSet(TStop.FLAG_GAS))
				{
					try
					{
						TStopGas tsg = new TStopGas(db, ts.id);
						if (tsg.vid != tr.getVehicleID())
							return false;
						int gbg = tsg.gas_brandgrade_id;
						if ((gbg != 0) && (null == getGasBrandGrade(gbg)))
							return false;
					}
					catch (Throwable th) { return false; }
				}
			}
		}
		return true;
	}

}
