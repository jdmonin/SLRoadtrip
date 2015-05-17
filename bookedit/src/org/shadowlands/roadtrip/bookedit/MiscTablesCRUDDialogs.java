/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010,2012,2014-2015 Jeremy D Monin <jdmonin@nand.net>
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
	/** Field types for {@link #createEditPersonDialog(JFrame, RDBAdapter, boolean, Person, boolean) */
	private static final int[] FTYPES_DIA_PERSON =
	{
		MultiInputDialog.F_STRING | MultiInputDialog.F_FLAG_REQUIRED,
		MultiInputDialog.F_BOOL,
		MultiInputDialog.F_STRING
	};

	/**
	 * Dialog to create or edit a person, and update the database.
	 * @param owner  Dialog's owner
	 * @param conn  DB connection
	 * @param isReadOnly  If true, show the fields as read-only; dialog only has Close button and not OK/Continue
	 * @param p  Existing person, or null if new
	 * @param driverIfNew  For new person, default to "Y" or "N" for driver question
	 * @return  The {@link Person} created/edited, or null if cancelled.
	 * @throws IllegalStateException if the db insert/update fails
	 * @throws NullPointerException if anything other than {@code p} is null
	 */
	public static Person createEditPersonDialog
	    (JFrame owner, RDBAdapter conn, final boolean isReadOnly, Person p, final boolean driverIfNew)
	    throws IllegalStateException, NullPointerException
	{
		final String[] labels = { "Name", "Driver?", "Comment" };
		String[] vals = new String[3];
		if (p != null)
		{
			vals[0] = p.getName();
			vals[1] = (p.isDriver() ? "Y" : "N");
			vals[2] = p.getComment();
		} else {
			vals[0] = null;
			vals[1] = (driverIfNew ? "Y" : "N");
			vals[2] = null;
		}

		/**
		 * Show the dialog, wait for user input
		 */
		MultiInputDialog mid = new MultiInputDialog
		    (owner, "Person information", "Information about this person",
		     labels, FTYPES_DIA_PERSON, vals, isReadOnly);
		if (! mid.showAndWait())
			return null;  // <--- Cancel button ---

		/**
		 * Update or insert the database
		 */
		vals = mid.getInputs();
		final boolean isDriver = (vals[1] != null) && (vals[1].equalsIgnoreCase("Y"));
		if (p == null)
		{
			p = new Person(vals[0], isDriver, null, vals[2]);
			p.insert(conn);
		} else if (mid.isChanged())
		{
			p.setName(vals[0]);
			p.setIsDriver(isDriver);
			p.commit();
		}

		return p;
	}

	/** Field types for {@link #createEditVehicleDialog(JFrame, RDBAdapter, boolean, Vehicle, Person)}. */
	private static final int[] FTYPES_DIA_VEHICLE =
	{
		MultiInputDialog.F_STRING, MultiInputDialog.F_BOOL,
		MultiInputDialog.F_INT | MultiInputDialog.F_FLAG_REQUIRED,  // TODO FK to person(driver)
		MultiInputDialog.F_INT | MultiInputDialog.F_FLAG_REQUIRED,  // TODO FK to vehiclemake
		MultiInputDialog.F_STRING, MultiInputDialog.F_INT | MultiInputDialog.F_FLAG_REQUIRED,
		MultiInputDialog.F_TIMESTAMP, MultiInputDialog.F_TIMESTAMP,
		MultiInputDialog.F_STRING, MultiInputDialog.F_STRING,
		MultiInputDialog.F_ODOMETER, MultiInputDialog.F_ODOMETER,
		MultiInputDialog.F_STRING
	};

	/**
	 * Dialog to create or edit a vehicle, and update the database.
	 * @param owner  Dialog's owner
	 * @param conn  DB connection
	 * @param isReadOnly  If true, show the fields as read-only; dialog has Close button and no OK/Continue button
	 * @param v  Existing vehicle, or null if new
	 * @param ownerIfNew  For new vehicle, the owner / main driver (for ID#)
	 * @return  The {@link Vehicle} created/edited, or null if cancelled.
	 * @throws IllegalStateException if the db insert/update fails
	 * @throws NullPointerException if anything other than {@code v} is null
	 */
	public static Vehicle createEditVehicleDialog
	    (JFrame owner, RDBAdapter conn, final boolean isReadOnly, Vehicle v, final Person ownerIfNew)
	    throws IllegalStateException, NullPointerException
	{
		// keep arrays congruent: labels[], FTYPES_DIA_VEHICLE[], vals[]
		final String[] labels =
			{ "Nickname", "Active?", "DriverID", "MakeID", "Model", "Year", "Owned from", "Owned to",
			  "VIN", "License plate/tag", "Original odometer", "Current odometer", "Comment" };
		String[] vals = new String[13];
		if (v != null)
		{
			vals[0] = v.getNickname();
			vals[1] = (v.isActive() ? "Y" : "N");
			vals[2] = Integer.toString(v.getDriverID());
			vals[3] = Integer.toString(v.getMakeID());
			vals[4] = v.getModel();
			vals[5] = Integer.toString(v.getYear());
			vals[6] = Integer.toString(v.getDate_from());
			vals[7] = Integer.toString(v.getDate_to());
			vals[8] = v.getVin();
			vals[9] = v.getPlate();
			vals[10] = Integer.toString(v.getOdometerOriginal());
			vals[11] = Integer.toString(v.getOdometerCurrent());
			vals[12] = v.getComment();
		} else {
			vals[1] = "Y";
			vals[2] = Integer.toString(ownerIfNew.getID());
		}

		/**
		 * Show the dialog, wait for user input
		 */
		MultiInputDialog mid = new MultiInputDialog
		    (owner, "Vehicle information", "Information about this vehicle",
		     labels, FTYPES_DIA_VEHICLE, vals, isReadOnly);
		if (! mid.showAndWait())
			return null;  // <--- Cancel button ---

		/**
		 * Update or insert the database
		 */
		vals = mid.getInputs();
		final boolean isActive = (vals[1] != null) && (vals[1].equalsIgnoreCase("Y"));
		if (v == null)
		{
			// TODO check ownerIfNew vs driverID in vals[2]
			v = new Vehicle
			    (vals[0], ownerIfNew, Integer.parseInt(vals[3]), vals[4], Integer.parseInt(vals[5]), Integer.parseInt(vals[6]),
			     Integer.parseInt(vals[7]), vals[8], vals[9], Integer.parseInt(vals[10]), Integer.parseInt(vals[11]), vals[12]);
			v.setActive(isActive);
			v.insert(conn);
		} else if (mid.isChanged())
		{
			v.setNickname(vals[0]);
			v.setActive(isActive);
			// TODO driverid retrieve
			// TODO any other permitted field changes
			v.commit();
		}

		return v;
	}

}  // public abstract class MiscTablesCRUDDialogs
