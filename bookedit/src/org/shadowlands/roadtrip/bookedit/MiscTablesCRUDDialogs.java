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

package org.shadowlands.roadtrip.bookedit;

import javax.swing.JFrame;

import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.Vehicle;

/**
 * Dialogs to create/read/update/delete various tables.
 * Displayed via {@link MultiInputDialog}.
 *
 * @author jdmonin
 */
public abstract class MiscTablesCRUDDialogs
{
	/**
	 * Dialog to create or edit a person, and update the database.
	 * @param owner  Dialog's owner
	 * @param conn  DB connection
	 * @param p  Existing person, or null if new
	 * @param driverIfNew  For new person, default to "Y" or "N" for driver question
	 * @return  The {@link Person} created/edited, or null if cancelled.
     * @throws IllegalStateException if the db insert/update fails
	 */
	public static Person createEditPersonDialog(JFrame owner, RDBAdapter conn, Person p, final boolean driverIfNew)
	    throws IllegalStateException
	{
		final String[] labels = { "Name", "Driver?" };
		String[] vals = new String[2];
		if (p != null)
		{
			vals[0] = p.getName();
			vals[1] = (p.isDriver() ? "Y" : "N");
		} else {
			vals[0] = null;
			vals[1] = (driverIfNew ? "Y" : "N");
		}

		/**
		 * Show the dialog, wait for user input
		 */
		MultiInputDialog mid = new MultiInputDialog
		    (owner, "Person information", "Information about this person", labels, vals);
		if (! mid.showAndWait())
			return null;  // <--- Cancel button ---

		/**
		 * Update or insert the database
		 */
		vals = mid.getInputs();
		final boolean isDriver = (vals[1] != null) && (vals[1].equalsIgnoreCase("Y"));
		if (p == null)
		{
			p = new Person(vals[0], isDriver, null);
			p.insert(conn);
		} else if (mid.isChanged())
		{
			p.setName(vals[0]);
			p.setIsDriver(isDriver);
			p.commit();
		}

		return p;
	}

	/**
	 * Dialog to create or edit a vehicle, and update the database.
	 * @param owner  Dialog's owner
	 * @param conn  DB connection
	 * @param v  Existing vehicle, or null if new
	 * @param ownerIfNew  For new vehicle, the owner / main driver (for ID#)
	 * @return  The {@link Vehicle} created/edited, or null if cancelled.
     * @throws IllegalStateException if the db insert/update fails
	 */
	public static Vehicle createEditVehicleDialog(JFrame owner, RDBAdapter conn, Vehicle v, final Person ownerIfNew)
	    throws IllegalStateException
	{
		final String[] labels = { "Nickname", "DriverID", "MakeID", "Model", "Year", "Owned from", "Owned to", "VIN", "Original odometer", "Current odometer", "Comment" };
		String[] vals = new String[11];
		if (v != null)
		{
			vals[0] = v.getNickname();
			vals[1] = Integer.toString(v.getDriverID());
			vals[2] = Integer.toString(v.getMakeID());
			vals[3] = v.getModel();
			vals[4] = Integer.toString(v.getYear());
			vals[5] = Integer.toString(v.getDate_from());
			vals[6] = Integer.toString(v.getDate_to());
			vals[7] = v.getVin();
			vals[8] = Integer.toString(v.getOdometerOriginal());
			vals[9] = Integer.toString(v.getOdometerCurrent());
			vals[10] = v.getComment();
		} else {
			for(int i = 0; i < 11; ++i)
				vals[i] = null;
			vals[1] = Integer.toString(ownerIfNew.getID());
		}

		/**
		 * Show the dialog, wait for user input
		 */
		MultiInputDialog mid = new MultiInputDialog
		    (owner, "Vehicle information", "Information about this vehicle", labels, vals);
		if (! mid.showAndWait())
			return null;  // <--- Cancel button ---

		/**
		 * Update or insert the database
		 */
		vals = mid.getInputs();
		if (v == null)
		{
			v = new Vehicle
			    (vals[0], ownerIfNew, Integer.parseInt(vals[2]), vals[3], Integer.parseInt(vals[4]), Integer.parseInt(vals[5]), Integer.parseInt(vals[6]), vals[7], Integer.parseInt(vals[8]), Integer.parseInt(vals[9]), vals[10]);
			v.insert(conn);
		} else if (mid.isChanged())
		{
			v.setNickname(vals[0]);
			// TODO driverid retrieve
			v.commit();
		}

		return v;
	}

}  // public abstract class MiscTablesCRUDDialogs
