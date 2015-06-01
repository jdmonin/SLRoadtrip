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
		MultiInputDialog.F_TIMESTAMP | MultiInputDialog.F_FLAG_READONLY,
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
		final String[] labels = { "Name", "Driver?", "Added at", "Comment" };
		String[] vals = new String[4];
		if (p != null)
		{
			vals[0] = p.getName();
			vals[1] = (p.isDriver() ? "Y" : "N");
			vals[2] = Integer.toString(p.getDateAdded());
			vals[3] = p.getComment();
		} else {
			vals[0] = null;
			vals[1] = (driverIfNew ? "Y" : "N");
			vals[2] = null;
			vals[3] = null;
		}

		/**
		 * Show the dialog, wait for user input
		 */
		MultiInputDialog mid = new MultiInputDialog
		    (owner, "Person information", "Information about this person",
		     labels, FTYPES_DIA_PERSON, vals, conn, isReadOnly);
		if (! mid.showAndWait())
			return null;  // <--- Cancel button ---

		/**
		 * Update or insert the database
		 */
		vals = mid.getInputs();
		final boolean isDriver = (vals[1] != null) && (vals[1].equalsIgnoreCase("Y"));
		if (p == null)
		{
			p = new Person(vals[0], isDriver, null, vals[3]);
			p.insert(conn);
		} else if (mid.isChanged())
		{
			p.setName(vals[0]);
			p.setIsDriver(isDriver);
			p.setComment(vals[3]);
			p.commit();
		}

		return p;
	}

	/** Field types for {@link #createEditVehicleDialog(JFrame, RDBAdapter, boolean, Vehicle, Person)}. */
	private static final int[] FTYPES_DIA_VEHICLE =
	{
		MultiInputDialog.F_STRING, MultiInputDialog.F_BOOL,
		MultiInputDialog.F_DB_PERSON_DRIVER | MultiInputDialog.F_FLAG_REQUIRED,
		MultiInputDialog.F_DB_VEHICLEMAKE | MultiInputDialog.F_FLAG_REQUIRED,
		MultiInputDialog.F_STRING, MultiInputDialog.F_INT,
		MultiInputDialog.F_TIMESTAMP | MultiInputDialog.F_FLAG_READONLY,
		MultiInputDialog.F_TIMESTAMP, MultiInputDialog.F_TIMESTAMP,
		MultiInputDialog.F_STRING, MultiInputDialog.F_STRING,
		MultiInputDialog.F_ODOMETER | MultiInputDialog.F_FLAG_READONLY,
		MultiInputDialog.F_ODOMETER,
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
			{ "Nickname", "Active?", "Main Driver", "Make", "Model", "Year", "Added at", "Owned from",
			  "Owned to", "VIN", "License plate/tag", "Original odometer", "Current odometer", "Comment" };
		String[] vals = new String[14];
		if (v != null)
		{
			vals[0] = v.getNickname();
			vals[1] = (v.isActive() ? "Y" : "N");
			vals[2] = Integer.toString(v.getDriverID());
			vals[3] = Integer.toString(v.getMakeID());
			vals[4] = v.getModel();
			final int yr = v.getYear();
			if (yr != 0)
				vals[5] = Integer.toString(yr);
			final int da = v.getDate_added();
			if (da != 0)
				vals[6] = Integer.toString(da);
			vals[7] = Integer.toString(v.getDate_from());
			vals[8] = Integer.toString(v.getDate_to());
			vals[9] = v.getVin();
			vals[10] = v.getPlate();
			vals[11] = Integer.toString(v.getOdometerOriginal());
			vals[12] = Integer.toString(v.getOdometerCurrent());
			vals[13] = v.getComment();
		} else {
			vals[1] = "Y";
			vals[2] = Integer.toString(ownerIfNew.getID());
		}

		/**
		 * Show the dialog, wait for user input.
		 * Validate that nickname, year, or model is not blank.
		 */
		MultiInputDialog mid = new MultiInputDialog
		    (owner, "Vehicle information", "Information about this vehicle",
		     labels, FTYPES_DIA_VEHICLE, vals, conn, isReadOnly);
		mid.setValidationListener(new MultiInputDialog.Validator()
		    {
			@Override
			public String validateFields(final String[] vals)
			{
				if ((vals[0] != null) || (vals[4] != null))
					return null;

				int yr = 0;
				try {
					yr = Integer.parseInt(vals[5]);
				} catch (NumberFormatException e) {}

				if (yr == 0)
					return "Enter a nickname, color, year, or model to identify this vehicle.";
				else
					return null;
			}
		    });
		if (! mid.showAndWait())
			return null;  // <--- Cancel button ---

		/**
		 * Update or insert the database
		 */
		vals = mid.getInputs();
		final boolean isActive = (vals[1] != null) && (vals[1].equalsIgnoreCase("Y"));
		Person mainDriv = null;
		if (vals[2] != null)
		{
			try {
				mainDriv = new Person(conn, Integer.parseInt(vals[2]));
			} catch (Exception e) {}
		}
		final int date_from = (vals[7] != null) ? Integer.parseInt(vals[7]) : 0,
		          date_to   = (vals[8] != null) ? Integer.parseInt(vals[8]) : 0,
		          odo_curr = (vals[12] != null) ? Integer.parseInt(vals[12]) : 0;
		if (v == null)
		{
			if (mainDriv == null)
				mainDriv = ownerIfNew;
			final int yr = (vals[5] != null) ? Integer.parseInt(vals[5]) : 0;
			final int odo_orig = (vals[11] != null) ? Integer.parseInt(vals[11]) : 0;
			v = new Vehicle
			    (vals[0], mainDriv, Integer.parseInt(vals[3]), vals[4], yr,
			     date_from, date_to, vals[9], vals[10], odo_orig, odo_curr, vals[13]);
			v.setActive(isActive);
			v.insert(conn);
		} else if (mid.isChanged())
		{
			v.setNickname(vals[0]);
			v.setActive(isActive);
			if (mainDriv != null)
				v.setDriverID(mainDriv);
			if (vals[3] != null)
				v.setMakeID(Integer.parseInt(vals[3]));
			v.setModel(vals[4]);  // may be null
			v.setYear((vals[5] != null) ? Integer.parseInt(vals[5]) : 0);
			v.setDate_from(date_from);
			v.setDate_to(date_to);
			v.setVin(vals[9]);
			v.setPlate(vals[10]);
			v.setOdometerCurrent(odo_curr);
			v.setComment(vals[13]);
			v.commit();
		}

		return v;
	}

}  // public abstract class MiscTablesCRUDDialogs
