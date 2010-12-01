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

package org.shadowlands.roadtrip.model;

// for LogbookTableModel on non-AWT (android) platforms.
// based on java swing AbstractTableModel.
public interface TableChangeListener {

	/** The rows in this range (inclusive) have been inserted. */
	public void fireTableRowsInserted(int first, int last);
	
	/** The rows in this range (inclusive) have been deleted. */
	public void fireTableRowsDeleted(int first, int last);

	/** The value at this row,column has been updated. */
	public void fireTableCellUpdated(int row, int col); 
}
