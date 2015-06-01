/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2015 Jeremy D Monin <jdmonin@nand.net>
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
import javax.swing.JOptionPane;

import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.VehSettings;
import org.shadowlands.roadtrip.db.Vehicle;

/**
 * Create and show a list of {@link Vehicle}s in this logbook.
 * Vehicles are edited from here by
 * {@link MiscTablesCRUDDialogs#createEditVehicleDialog(JFrame, RDBAdapter, boolean, Vehicle, Person)}.
 * @since 0.9.43
 */
@SuppressWarnings("serial")
public class VehicleListDialog extends ItemListDialog<Vehicle>
{
	/**
	 * Create and show a list of vehicles in this logbook.
	 * @param db  An open logbook database
	 * @param isReadOnly  If true, treat db as read-only
	 * @param owner  Parent window
	 */
	public VehicleListDialog(RDBAdapter db, final boolean isReadOnly, final JFrame owner)
	{
		super(db, isReadOnly, owner, null, true, "Vehicle", "Vehicles");
	}

	// see superclass for method javadocs

	public Vehicle[] getAll()
	{
		return Vehicle.getAll(db, 0);
	}

	public Vehicle showAdd()
	{
		Person defaultDriver = null;
		{
			Vehicle currV = Settings.getCurrentVehicle(db, false);
			if (currV == null)
				currV = Vehicle.getMostRecent(db);
			if (currV != null)
				defaultDriver = VehSettings.findCurrentDriver(db, currV);
			if (defaultDriver == null)
				defaultDriver = Person.getMostRecent(db, true);
			if (defaultDriver == null)
			{
				JOptionPane.showMessageDialog
					(owner,
					 "Please add a driver to the database first.",
					 null, JOptionPane.INFORMATION_MESSAGE);
				return null;
			}
		}
		return MiscTablesCRUDDialogs.createEditVehicleDialog(owner, db, isReadOnly, null, defaultDriver);
	}

	public boolean showEdit(Vehicle item)
	{
		item = MiscTablesCRUDDialogs.createEditVehicleDialog(owner, db, isReadOnly, item, null);
		return (item != null);
	}

	public boolean isItemActive(final Vehicle item)
	{
		return item.isActive();
	}

}
