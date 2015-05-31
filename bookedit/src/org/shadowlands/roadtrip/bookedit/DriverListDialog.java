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

import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;

/**
 * Create and show a list of drivers ({@link Person}s) in this logbook. Drivers are edited from here by
 * {@link MiscTablesCRUDDialogs#createEditPersonDialog(JFrame, RDBAdapter, boolean, Person, boolean)}.
 * @since 0.9.43
 */
@SuppressWarnings("serial")
public class DriverListDialog extends ItemListDialog<Person>
{
	/**
	 * Create and show a list of drivers in this logbook.
	 * @param db  An open logbook database
	 * @param isReadOnly  If true, treat db as read-only
	 * @param owner  Parent window
	 */
	public DriverListDialog(RDBAdapter db, final boolean isReadOnly, final JFrame owner)
	{
		super(db, isReadOnly, owner, null, false, "Driver", "Drivers");
	}

	// see superclass for method javadocs

	public Person[] getAll()
	{
		return Person.getAll(db, true);
	}

	public Person showAdd()
	{
		return MiscTablesCRUDDialogs.createEditPersonDialog(owner, db, isReadOnly, null, true);
	}

	public boolean showEdit(Person item)
	{
		item = MiscTablesCRUDDialogs.createEditPersonDialog(owner, db, isReadOnly, item, true);
		return (item != null);
	}

}
