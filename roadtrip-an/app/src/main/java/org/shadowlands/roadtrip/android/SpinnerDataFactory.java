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

package org.shadowlands.roadtrip.android;

import org.shadowlands.roadtrip.db.GeoArea;
import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.RDBRecord;
import org.shadowlands.roadtrip.db.TripCategory;
import org.shadowlands.roadtrip.db.Vehicle;

import android.app.Dialog;
import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

/**
 * Fill the data in spinners of data-backed object types,
 * such as {@link Vehicle} or {@link Person} (driver).
 * Also contains helper methods like {@link #selectRecord(Spinner, int)}
 * and helper class {@link SpinnerItemSelectedListener}
 * for use in dialogs and activities.
 *<P>
 *<H5>I18N:<H5>
 * Before using these call {@link RDBRecord#localizeStatics(String, String)}
 * to localize some values, such as GeoArea "(none)" used by
 * {@link #setupGeoAreasSpinner(RDBAdapter, Context, Spinner, int, boolean, int)}.
 *
 * @author jdmonin
 */
public class SpinnerDataFactory
{
	public static Person NEW_DRIVER;  // TODO keep?
	public static Person NEW_VEHICLE; // TODO keep?

	/** Placeholder in spinners for an empty {@link TripCategory}, with db _id -1. */
	public static TripCategory EMPTY_TRIPCAT;

	/**
	 * Populate a spinner from the drivers (Persons) in the database.
	 * @param db  connection to use
	 * @param ctx  the calling Activity or Context
	 * @param sp  spinner to fill with the drivers
	 * @param currentID  If not -1, the driver _id to select in the Spinner. 
	 * @return true on success, false if could not populate from database
	 * @see #selectRecord(Spinner, int)
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
    				sp.setSelection(i, false);
    				break;
    			}
    	}

    	return true;
	}

	/**
	 * Set the selected record in a spinner previously set up to contain {@link RDBRecord} items
	 * (such as {@link Vehicle}s or {@link GeoArea}s).
	 * If the spinner's items are any other type, the spinner's current selection is not changed.
	 *<P>
	 * Before v0.9.70 this method was {@code selectDriver}.
	 *
	 * @param sp  The spinner to work with. Its {@link Spinner#getItemAtPosition(int)} should always
	 *     return an {@link RDBRecord}. Any other item type is ignored.
	 * @param currentID  Current record ID to select; if no spinner item has this {@link RDBRecord#getID()},
	 *     the spinner's current selection is not changed.
	 * @since 0.9.40
	 */
	public static void selectRecord(final Spinner sp, final int currentID)
	{
		for (int i = sp.getCount() - 1; i >= 0; --i)
		{
			final Object itm = sp.getItemAtPosition(i);
			if ((itm instanceof RDBRecord) && (((RDBRecord) itm).getID() == currentID))
			{
				if (sp.getSelectedItemPosition() != i)
					sp.setSelection(i, false);

				return;
			}
		}
	}

	/**
	 * Populate a spinner from the {@link GeoArea}s in the database.
	 * @param db  connection to use
	 * @param ctx  the calling Activity or Context
	 * @param sp  spinner to fill with the areas
	 * @param currentID  If not -1, the area _id to select in the Spinner
	 * @param withNone  If true, include a GeoArea "(none)" with id 0 as the first item: {@link GeoArea#GEOAREA_NONE}
	 * @param exceptID  If not -1, a geoarea to exclude from the list
	 * @return true on success, false if could not populate from database
	 */
	public static boolean setupGeoAreasSpinner
		(RDBAdapter db, Context ctx, Spinner sp, final int currentID, final boolean withNone, final int exceptID)
	{
		GeoArea[] areas = populateGeoAreasList(db, ctx, withNone, exceptID);
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
	 * @param activeSubsetFlags  0 for all vehicles, or same active/inactive flags
	 *     as {@link Vehicle#getAll(RDBAdapter, int)}
	 * @param ctx  the calling Activity or Context
	 * @param sp  spinner to fill with the vehicles
	 * @param currentID  If not -1, the vehicle _id to select in the Spinner. 
	 * @return true on success, false if could not populate from database
	 */
	public static boolean setupVehiclesSpinner
		(final RDBAdapter db, final int activeSubsetFlags, final Context ctx, final Spinner sp, final int currentID)
	{
	    Vehicle[] veh = populateVehiclesList(db, activeSubsetFlags);
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
	 * For {@link #setupGeoAreasSpinner(RDBAdapter, Context, Spinner, int, boolean, int)},
	 * gather the list of GeoAreas from the database.
	 *
	 * @param db  connection to use
	 * @param ctx  context; ignored unless <tt>withNone</tt>; used for strings for withNone
	 * @param withNone  If true, include "(none)" as first entry; its id is 0
	 * @param exceptID  If not -1, a geoarea to exclude from the list
	 * @return array of areas, or null
	 */
	private static GeoArea[] populateGeoAreasList
		(RDBAdapter db, final Context ctx, final boolean withNone, final int exceptID)
	{
		GeoArea[] areas = null; 

    	try
    	{
    		areas = GeoArea.getAll(db, withNone, exceptID);
    	}
    	catch (SQLiteException e)
    	{}

    	return areas;
	}

	/**
	 * For {@link #setupVehiclesSpinner(RDBAdapter, Context, Spinner)},
	 * gather the list of vehicles from the database.
	 *
	 * @param db  connection to use
	 * @param activeSubsetFlags  0 for all vehicles, or same active/inactive flags
	 *     as {@link Vehicle#getAll(RDBAdapter, int)}
	 * @return array of vehicles, or null
	 */
	private static Vehicle[] populateVehiclesList(RDBAdapter db, final int activeSubsetFlags)
	{
		Vehicle[] veh = null; 

    	try
    	{
    		veh = Vehicle.getAll(db, activeSubsetFlags);
    	}
    	catch (SQLiteException e)
    	{}

    	return veh;
	}

	/**
	 * Populate a spinner from the {@link TripCategory}s in the database.
	 * First item will be the blank (empty) category placeholder {@link #EMPTY_TRIPCAT}.
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

	/**
	 * Spinner {@link AdapterView.OnItemSelectedListener OnItemSelectedListener} with
	 * {@link #dia} field, to allow dismissal of a dynamically built dialog from {@code onItemSelected}.
	 *<P>
	 * Sample usage with {@code AlertDialog.Builder}:
	 * <PRE>
	 *  SpinnerItemSelectedListener spinListener = new SpinnerItemSelectedListener(defaultID)
	 *	{
	 *		public void onItemSelected(AdapterView<?> spinner, View itemv, int pos, long unusedViewID)
	 *		{
	 *			...
	 *			if (someCondition)
	 *			{
	 *				doSomeAction();
	 *				if (dia != null) dia.dismiss();
	 *			}
	 *		}
	 *	};
	 *
	 *  AlertDialog aDia = abuilder.create();
	 *  spinListener.dia = aDia;
	 *  spinner.setOnItemSelectedListener(spinListener);
	 *  return aDia;
	 * </PRE>
	 *
	 * @since 0.9.50
	 * @see {@link TripTStopEntry#onCreateDialog(int)}
	 */
	public static abstract class SpinnerItemSelectedListener implements AdapterView.OnItemSelectedListener
	{
		/** Optional reference to parent dialog for use in listener callbacks, or {@code null}. */
		public Dialog dia;

		/**
		 * item ID passed into the constructor, so {@code onItemSelected} can ignore calls with that ID.
		 * Spinner initialization may call {@code onItemSelected} before the user has made any input.
		 */
		public int itemID_default;

		/**
		 * Create a new {@link SpinnerItemSelectedListener}; see class javadoc.
		 * @param itemID_default  Default item ID, so {@code onItemSelected} can ignore calls with that ID.
		 */
		public SpinnerItemSelectedListener(final int itemID_default)
		{
			super();
			this.itemID_default = itemID_default;
		}

		/**
		 * This stub ignores de-select, instead keeps the currently selected object.
		 * {@code onNothingSelected} can be called when the user scrolls through the spinner and
		 * the selection disappears off screen, but in that case {@code onItemSelected} will be called
		 * whey they touch a list item or the spinner loses focus.
		 */
		public void onNothingSelected(AdapterView<?> parentView) {}

		// let subclass implement onItemSelected
	}

}
