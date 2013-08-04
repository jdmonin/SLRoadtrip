/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2013 Jeremy D Monin <jdmonin@nand.net>
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

package org.shadowlands.roadtrip.android;

import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.db.GeoArea;
import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.TripCategory;
import org.shadowlands.roadtrip.db.Vehicle;

import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

/**
 * Fill the data in spinners of data-backed object types,
 * such as {@link Vehicle} or {@link Person} (driver).
 *
 * @author jdmonin
 */
public class SpinnerDataFactory
{
	public static Person NEW_DRIVER;  // TODO keep?
	public static Person NEW_VEHICLE; // TODO keep?
	/** Placeholder in spinners for an empty {@link TripCategory}. */
	public static TripCategory EMPTY_TRIPCAT;
	/** Placeholder in spinners for an empty {@link GeoArea}. */
	public static GeoArea GEOAREA_NONE;

	/**
	 * Populate a spinner from the drivers (Persons) in the database.
	 * @param db  connection to use
	 * @param ctx  the calling Activity or Context
	 * @param sp  spinner to fill with the drivers
	 * @param currentID  If not -1, the driver _id to select in the Spinner. 
	 * @return true on success, false if could not populate from database
	 */
	public static boolean setupDriversSpinner(RDBAdapter db, Context ctx, Spinner sp, final int currentID)
	{
		Person[] drivers = populateDriversList(db);
	    if (drivers == null)
	    	return false;

    	ArrayAdapter<Person> daa = new ArrayAdapter<Person>(ctx, android.R.layout.simple_spinner_item, drivers);
    	daa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	sp.setAdapter(daa);

    	if (currentID != -1)
    	{
    		for (int i = drivers.length - 1; i >= 0; --i)
    			if (currentID == drivers[i].getID())
    			{
    				sp.setSelection(i, true);
    				break;
    			}
    	}

    	return true;
	}

	/**
	 * Populate a spinner from the {@link GeoArea}s in the database.
	 * @param db  connection to use
	 * @param ctx  the calling Activity or Context
	 * @param sp  spinner to fill with the areas
	 * @param currentID  If not -1, the area _id to select in the Spinner
	 * @param withNone  If true, include a GeoArea "(none)" with id 0 as the first item 
	 * @return true on success, false if could not populate from database
	 */
	public static boolean setupGeoAreasSpinner(RDBAdapter db, Context ctx, Spinner sp, final int currentID, final boolean withNone)
	{
		GeoArea[] areas = populateGeoAreasList(db, ctx, withNone);
	    if (areas == null)
	    	return false;

    	ArrayAdapter<GeoArea> daa = new ArrayAdapter<GeoArea>(ctx, android.R.layout.simple_spinner_item, areas);
    	daa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	sp.setAdapter(daa);

    	if (currentID != -1)
    	{
    		for (int i = areas.length - 1; i >= 0; --i)
    			if (currentID == areas[i].getID())
    			{
    				sp.setSelection(i, true);
    				break;
    			}
    	}

    	return true;
	}

	/**
	 * Populate a spinner from the Vehicles in the database.
	 * @param db  connection to use
	 * @param activeOnly  If true, don't include inactive vehicles
	 * @param ctx  the calling Activity or Context
	 * @param sp  spinner to fill with the vehicles
	 * @param currentID  If not -1, the driver _id to select in the Spinner. 
	 * @return true on success, false if could not populate from database
	 */
	public static boolean setupVehiclesSpinner
		(final RDBAdapter db, final boolean activeOnly, final Context ctx, final Spinner sp, final int currentID)
	{
		Vehicle[] veh = populateVehiclesList(db, activeOnly);
	    if (veh == null)
	    	return false;

    	ArrayAdapter<Vehicle> daa = new ArrayAdapter<Vehicle>(ctx, android.R.layout.simple_spinner_item, veh);
    	daa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	sp.setAdapter(daa);

    	if (currentID != -1)
    	{
    		for (int i = veh.length - 1; i >= 0; --i)
    			if (currentID == veh[i].getID())
    			{
    				sp.setSelection(i, true);
    				break;
    			}
    	}

    	return true;
	}

	/**
	 * For {@link #setupDriversSpinner(RDBAdapter, Context, Spinner)},
	 * gather the list of drivers (Persons) from the database.
	 *
	 * @param db  connection to use
	 * @return array of drivers (Persons), or null
	 */
	private static Person[] populateDriversList(RDBAdapter db)
	{
		Person[] drivers = null; 

    	try
    	{
    		drivers = Person.getAll(db, true);
    	}
    	catch (SQLiteException e)
    	{}

    	return drivers;
	}

	/**
	 * For {@link #setupGeoAreasSpinner(RDBAdapter, Context, Spinner, int, boolean)},
	 * gather the list of GeoAreas from the database.
	 *
	 * @param db  connection to use
	 * @param ctx  context; ignored unless <tt>withNone</tt>; used for strings for withNone
	 * @param withNone  If true, include "(none)" as first entry; its id is 0
	 * @return array of areas, or null
	 */
	private static GeoArea[] populateGeoAreasList(RDBAdapter db, final Context ctx, final boolean withNone)
	{
		GeoArea[] areas = null; 

    	try
    	{
    		areas = GeoArea.getAll(db, -1);
    	}
    	catch (SQLiteException e)
    	{}

    	if (withNone)
    	{
    		if (GEOAREA_NONE == null)
    		{
    			final String none = ctx.getString(R.string.none__parens);
    			GEOAREA_NONE = new GeoArea(none);  // "(none)"
    			GEOAREA_NONE.setID0();
    		}

    		if (areas == null)
    		{
    			areas = new GeoArea[1];
    		} else {
    			final int L = areas.length;
    			GeoArea[] areas2 = new GeoArea[L + 1];
    			System.arraycopy(areas, 0, areas2, 1, L);
    			areas = areas2;
    		}
    		areas[0] = GEOAREA_NONE;
    	}

    	return areas;
	}

	/**
	 * For {@link #setupVehiclesSpinner(RDBAdapter, Context, Spinner)},
	 * gather the list of vehicles from the database.
	 *
	 * @param db  connection to use
	 * @param activeOnly  If true, don't include inactive vehicles
	 * @return array of vehicles, or null
	 */
	private static Vehicle[] populateVehiclesList(RDBAdapter db, final boolean activeOnly)
	{
		Vehicle[] veh = null; 

    	try
    	{
    		veh = Vehicle.getAll(db, activeOnly);
    	}
    	catch (SQLiteException e)
    	{}

    	return veh;
	}

	/**
	 * Populate a spinner from the {@link TripCategory}s in the database.
	 * @param db  connection to use
	 * @param ctx  the calling Activity or Context
	 * @param sp  spinner to fill with the areas
	 * @param currentID  The area _id to select in the Spinner, or 0 or -1 to select the empty category. 
	 * @return true on success, false if could not populate from database
	 * @since 0.9.08
	 */
	public static boolean setupTripCategoriesSpinner(RDBAdapter db, Context ctx, Spinner sp, int currentID)
	{
		if (EMPTY_TRIPCAT == null)
			EMPTY_TRIPCAT = new TripCategory("", -1);
		if (currentID == 0)
			currentID = -1;

		TripCategory[] cats = null;
		try
		{
			cats = TripCategory.getAll(db, EMPTY_TRIPCAT);
		}
		catch (SQLiteException e)
		{}
		if (cats == null)
			return false;

		ArrayAdapter<TripCategory> daa = new ArrayAdapter<TripCategory>(ctx, android.R.layout.simple_spinner_item, cats);
		daa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		sp.setAdapter(daa);

		for (int i = cats.length - 1; i >= 0; --i)
		{
			if (currentID == cats[i].getID())
			{
				sp.setSelection(i, true);
				break;
			}
		}

		return true;
	}

}
