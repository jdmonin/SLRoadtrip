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

package org.shadowlands.roadtrip.db;

/**
 * Signals that a key wasn't found; used by {@link RDBRecord} subclass constructors.
 * @author jdmonin
 */
@SuppressWarnings("serial")
public class RDBKeyNotFoundException extends Exception
{
	public String keyString;
	public int keyID;

	public RDBKeyNotFoundException(final String keyvalue)
	{
		super(keyvalue);
		keyString = keyvalue;
	}

	public RDBKeyNotFoundException(final int id)
	{
		super(Integer.toString(id));
		keyID = id;
	}

}  // RDBKeyNotFoundException
