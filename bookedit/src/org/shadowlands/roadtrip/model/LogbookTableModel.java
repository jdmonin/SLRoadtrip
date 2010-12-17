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

import gnu.trove.TIntObjectHashMap;

import java.text.DateFormat;
import java.util.Date;
import java.util.Vector;

import org.shadowlands.roadtrip.db.GasBrandGrade;
import org.shadowlands.roadtrip.db.Location;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.TStop;
import org.shadowlands.roadtrip.db.TStopGas;
import org.shadowlands.roadtrip.db.Trip;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.ViaRoute;

/**
 * Renders and holds data for display or editing.
 * The last row is filled with the empty string; typing in this row
 * creates a new empty row under it.
 *<P>
 * Assumes that data won't change elsewhere while displayed; for example,
 * cached ViaRoute object contents.
 *
 * @author jdmonin
 */
public class LogbookTableModel // extends javax.swing.table.AbstractTableModel
{
	/**
	 * The length of this array determines the number of columns.
	 */
	private static final String[] colHeadings
	    = { "Date", "Time", "", "Odometer", "Trip-O", "Via", "Notes" };

	private static final String[][] TEMPLATE_ADD_SIMPLE
	   = { { null, null, "/", "Start-odo", null, null, "Start at" },
		   { "Date", "Start-Time", },
		   { null , "End-Time", null, null, "Trip-odo", "via", "End at" },
		   { null, null, "\\", "End-odo" } };

	private static final String[][] TEMPLATE_ADD_WITHSTOPS
   = { { null, null, "/", "Start-odo", null, null, "Start at" },
	   { "Date", "Start-Time", },
	   { null, "Stop-time" },
	   { null, null, ">", "Stop-odo", "Stop-trip", "Route to stop", "Stop at" },
	   { null, "Resume-time" },
	   { null , "End-Time", null, null, "Trip-odo", "via", "End at" },
	   { null, null, "\\", "End-odo" } };

	/**
	 * Holds each data row, not incl the 1 empty-string row at the end.
	 * Each row's length is <tt>{@link #colHeadings}.length</tt>.
	 */
	private Vector<String[]> tData;

	/**
	 * {@link Location} cache. Each item is its own key.
	 * TIntObjectHashMap is from <A href="http://trove4j.sourceforge.net/">trove</A> (LGPL).
	 * (<A href="http://trove4j.sourceforge.net/javadocs/">javadocs</a>)
	 */
	private TIntObjectHashMap<Location> locCache;

	/**
	 * {@link ViaRoute} cache. Each item is its own key.
	 */
	private TIntObjectHashMap<ViaRoute> viaCache;

	/**
	 * {@link ViaRoute} cache. Each item is its own key.
	 */
	private TIntObjectHashMap<GasBrandGrade> gasCache;

	/**
	 * Are we adding a new trip right now?
	 * @see #maxRowBeforeAdd
	 */
	private boolean addMode;

	/** During {@link #addMode}, {@link Vector#size() tData.size()} before starting the add. */
	private int maxRowBeforeAdd;

	/** if non-null, listener for our changes. */
	private TableChangeListener listener;

	/**
	 * Create and populate with existing data.
	 * @param veh  Vehicle
	 * @param conn Add existing rows from this connection, via addRowsFromTrips.
	 */
	public LogbookTableModel(Vehicle veh, RDBAdapter conn)
	{
		tData = new Vector<String[]>();
		locCache = new TIntObjectHashMap<Location>();
		viaCache = new TIntObjectHashMap<ViaRoute>();
		gasCache = new TIntObjectHashMap<GasBrandGrade>();

		if (veh != null)
		{
			addRowsFromTrips(veh, conn);
		} else {
    		for (int i = 0; i < 3; ++i)
    		{
    			String[] td = new String[colHeadings.length];
    			for (int j = 0; j < colHeadings.length; ++j)
    				td[j] = new String("x" + i + j);
    			tData.addElement(td);
    		}
		}
	}

	private void addRowsFromTrips(Vehicle veh, RDBAdapter conn)
	{
		DateFormat dfd = DateFormat.getDateInstance(DateFormat.MEDIUM);
		DateFormat dft = DateFormat.getTimeInstance(DateFormat.SHORT);

		Vector<Trip> td = veh.readAllTrips(true);

		if (td == null)
		{
			return;  // <--- nothing found ---
		}

		Date prevTripStart = null;  // time of trip start

		// Does next trip continue from the same tstop and odometer?
		boolean nextTripUsesSameStop = false;  // Updated at bottom of loop.

		final int L = td.size();
		for (int i = 0; i < L; ++i)  // towards end of trip, must look at next trip
		{
			Trip t = td.elementAt(i);
			TStop ts_start = t.readStartTStop(false);  // may be null
			String[] tr;

			// first row of trip: date, if different from prev date
			Date tstart = new Date(t.getTime_start() * 1000L);
			if ((prevTripStart == null)
			    || (prevTripStart.getDate() != tstart.getDate())
			    || (prevTripStart.getMonth() != tstart.getMonth()))
			{
				tr = new String[colHeadings.length];
				tr[0] = dfd.format(tstart);
				tData.addElement(tr);
			}
			prevTripStart = tstart;

			// next row of trip: location/odo, if different from previous trip's location/odo
			String[] firstrow;
			if (! nextTripUsesSameStop)
			{
				firstrow = new String[colHeadings.length];
				firstrow[2] = "/";
				firstrow[3] = Integer.toString((int) (t.getOdo_start() / 10.0f));
				if (ts_start != null)
					firstrow[6] = getTStopLocDescr(ts_start, conn);
				tData.addElement(firstrow);
			} else {
				firstrow = null;
			}

			// next row of trip: time
			tr = new String[colHeadings.length];
			tr[1] = dft.format(tstart);
			tData.addElement(tr);

			final int odo_end = t.getOdo_end();

			// All well-formed trips have 1 or more TStops.
			Vector<TStop> stops = t.readAllTStops();  // TODO current trip vs this?		
			final TStop lastStop = (stops != null) ? stops.lastElement() : null;
			if (stops != null)
			{
				for (TStop ts : stops)
				{
					if ((ts_start == null) && (ts.getOdo_trip() == 0) && (firstrow != null))
					{
						// this stop is the starting location
						ts_start = ts;
						firstrow[6] = getTStopLocDescr(ts, conn);
						continue;  // <-- doesn't get its own row, only firstrow --
					}
	
					final int ttstop = ts.getTime_stop();
					final int ttcont = ts.getTime_continue();
	
					// stop-time (if present)
					if (ttstop != 0)
					{
						tr = new String[colHeadings.length];
		    			tr[1] = dft.format(new Date(ttstop * 1000L));
		    			tData.addElement(tr);
					}
	
					// stop info
					tr = new String[colHeadings.length];
					if (ttstop != 0)
					{
						if (ttcont != 0)
							tr[2] = ">";  // both stop,start time
						else
							tr[2] = "\\";  // only stop time
					} else if (ttcont != 0)
					{
						tr[2] = "/";  // only start time
					}
					int x = ts.getOdo_total();
					final boolean is_last_stop = (x != 0) && (x == odo_end) && (ttcont == 0);
					if ((x != 0) && ! is_last_stop)
						tr[3] = Integer.toString((int) (x / 10.0f));
					x = ts.getOdo_trip();
					if (x != 0)
					{
						if (! is_last_stop)
							tr[4] = String.format("(%.1f)", x / 10.0f);
						else
							tr[4] = String.format("%.1f", x / 10.0f);
					}
					final int viaID = ts.getVia_id();
					ViaRoute vr = null;
					if (viaID > 0)
					{
						vr = viaCache.get(viaID);
						if (vr == null)
							try
							{
								vr = new ViaRoute(conn, viaID);
								viaCache.put(viaID, vr);
							} catch (Throwable e) { }  // RDBKeyNotFoundException
					}
					if (vr == null)
						tr[5] = ts.getVia_route();
					else
						tr[5] = vr.getDescr();
					if (tr[5] != null)
						tr[5] = "via " + tr[5];

					StringBuffer desc = new StringBuffer(getTStopLocDescr(ts, conn));

					// Look for a gas tstop
					if (ts.isSingleFlagSet(TStop.FLAG_GAS))
					{
						try
						{
							TStopGas tsg = new TStopGas(conn, ts.getID());
							final int gradeID = tsg.gas_brandgrade_id;
							if (gradeID != 0)
							{
								GasBrandGrade grade = gasCache.get(gradeID);
								if (grade == null)
								{
									try
									{
										grade = new GasBrandGrade(conn, gradeID);
										gasCache.put(gradeID, grade);
									}
									catch (Throwable th) {}
								}
								if (grade != null)
									tsg.gas_brandgrade = grade;  // for toStringBuffer's use
							}
							StringBuffer gsb = new StringBuffer("* Gas: ");
							gsb.append(tsg.toStringBuffer(veh));
							if (gradeID != 0)
								tsg.gas_brandgrade = null;  // clear the reference
							if (desc.length() > 0)
								gsb.append(' ');
							desc.insert(0, gsb);
						}
						catch (Throwable th) {}
					}

					// If it's the very last stop, Arrow to indicate that
					if ((ts == lastStop) && (desc.length() > 0)
						&& (odo_end != 0))
						desc.insert(0, "-> ");  // Very last stop: "-> location"

					// Done with this row
					tr[6] = desc.toString();
	    			tData.addElement(tr);
	
					// start-time (if present)
					if (ttcont != 0)
					{
						tr = new String[colHeadings.length];
		    			tr[1] = dft.format(new Date(ttcont * 1000L));
		    			tData.addElement(tr);
					}
				}
			}  // each TStop

			// last rows, only if completed trip
			if (odo_end != 0)
			{
				// does next trip continue from the same tstop and odometer?
				if ((i < (L-1)) && (lastStop != null))
				{
					Trip nt = td.elementAt(i+1);
					nextTripUsesSameStop =
						(t.getOdo_end() == nt.getOdo_start())
						 && nt.isStartTStopFromPrevTrip();
				} else {
					nextTripUsesSameStop = false;
				}

				// last row of trip: odo, comment/desc
				tr = new String[colHeadings.length];
				if (nextTripUsesSameStop)
					tr[2] = ">";
				else
					tr[2] = "\\";
				tr[3] = Integer.toString((int) (odo_end / 10.0f));
				tr[6] = t.getComment();
				tData.addElement(tr);
			}
		}
	}

	/**
	 * Read this TStop's location description from text or from its associated Location.
	 * Attempts to read or fill {@link #locCache}.
	 * @param conn  db connection to use
	 * @param ts  TStop to look at
	 * @return Location text, or null
	 */
	private String getTStopLocDescr(TStop ts, RDBAdapter conn)
	{
		String locDescr = ts.getLocationDescr();
		if (locDescr == null)
		{
			final int locID = ts.getLocationID();
			if (locID != 0)
			{
				Location lo = locCache.get(locID);
				if (lo == null)
				{
					try
					{
						lo = new Location(conn, locID);
						locCache.put(locID, lo);
					} catch (Throwable e) { }  // RDBKeyNotFoundException
				}
				if (lo != null)
					locDescr = lo.getLocation();
			}
		}
		return locDescr;
	}

	/**
	 * Set up the grid to add a new simple trip or a trip with stops.
	 * @param withStops  Does this trip include stops, or is it a simple trip?
	 * @throws IllegalStateException if already in add mode
	 * @see #finishAdd()
	 * @see #cancelAdd()
	 */
	public void beginAdd(final boolean withStops)
	    throws IllegalStateException
	{
		if (addMode)
			throw new IllegalStateException("Already adding");

		maxRowBeforeAdd = tData.size();
		addMode = true;

		/**
		 * Write the template to the new rows
		 * (TODO) incl highest-date, odo, etc from prev-rows
		 * (TODO) other template: complex
		 */
		final String[][] template =
			withStops ? TEMPLATE_ADD_WITHSTOPS : TEMPLATE_ADD_SIMPLE;
		int r;
		for (r = 0; r < template.length; ++r)
		{
			// create a new row
			String[] td = new String[colHeadings.length];
			for (int i = 0; i < colHeadings.length; ++i)
			{
				String content;
				if (i < template[r].length)
					content = template[r][i];  // may be null
				else
					content = null;
				if (content == null)
					td[i] = "";
				else
					td[i] = content;
			}
			tData.addElement(td);
		}
		if (listener != null)
			listener.fireTableRowsInserted(maxRowBeforeAdd, maxRowBeforeAdd + r);
	}

	/**
	 * Interpret and add data entered by the user since {@link #beginAdd(boolean)}.
	 * If <tt>beginAdd</tt> wasn't previously called, do nothing.
	 */
	public void finishAdd()  {}
	  // TODO interpret data from maxRowBeforeAdd, save to db, un-set mode

	/**
	 * Cancel and clear the data entered by the user since {@link #beginAdd(boolean)}.
	 * If <tt>beginAdd</tt> wasn't previously called, do nothing.
	 */
	public void cancelAdd()
	{
		if (! addMode)
			return;

		final int highIdx = tData.size();
		if (highIdx != maxRowBeforeAdd)
		{
    		for (int i = highIdx - maxRowBeforeAdd; i > 0; --i)
    			tData.remove(maxRowBeforeAdd);
    		if (listener != null)
    			listener.fireTableRowsDeleted(maxRowBeforeAdd+1, highIdx);
		}
		addMode = false;
	}

	/** column count; same as length of {@link #colHeadings} */
	public int getColumnCount() { return colHeadings.length; }

	/** occupied row count; includes current data entry during "Add Trip" mode */
	public int getRowCount() { return 1 + tData.size(); }

	public String getColumnName(final int c) { return colHeadings[c]; }

	public Object getValueAt(int r, int c)
	{
		if (r < tData.size())
			return tData.elementAt(r)[c];
		else
			return "";
	}

	/** all cells are editable, except 1st row, 1st 2 cols */
	public boolean isCellEditable(int r, int c)
	{
		return (r > 0) || (c < 2);
	}

	/**
	 * Update the value in a cell (for AbstractTableModel).
	 * @param r Row, from 0
	 * @param c Column, from 0
	 */
	public void setValueAt(Object newValue, int r, int c)
	{
		boolean rowExists = (r < tData.size());
		if (rowExists)
		{
			tData.elementAt(r)[c] = newValue.toString();
		} else {
			// create a new row
			String[] td = new String[colHeadings.length];
			for (int i = 0; i < colHeadings.length; ++i)
				if (i != c)
					td[i] = "";
				else
					td[i] = newValue.toString();
			tData.addElement(td);
		}
		if (listener != null)
		{
			listener.fireTableCellUpdated(r, c);
			if (! rowExists)
				listener.fireTableRowsInserted(r+1, r+1);
		}
	}

	/**
	 * Append \n and then tab-delimited (\t) contents of this row. 
	 * @return true if appended, false if <tt>r</tt> is past {@link #getRowCount()}.
	 */
	public boolean appendRowAsTabbedString(final int r, StringBuffer sb)
	{
		if (r >= tData.size())
			return false;

		sb.append('\n');
		final String[] rstr = tData.elementAt(r);
		if (rstr[0] != null)
			sb.append(rstr[0]);
		for (int c = 1; c < rstr.length; ++c)
		{
			sb.append('\t');
			if (rstr[c] != null)
				sb.append(rstr[c]);
		}
		return true;
	}

	/**
	 * Set (or clear) our listener.
	 * @param tcl  The only listener (does not allow multiple at once), or null to clear.
	 */
	public void setListener(TableChangeListener tcl)
	{
		listener = tcl;
	}

}  // public class LogbookTableModel
