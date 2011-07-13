/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  Copyright (C) 2010-2011 Jeremy D Monin <jdmonin@nand.net>
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
import org.shadowlands.roadtrip.db.Trip.TripListTimeRange;

/**
 * Renders and holds data for display or editing.
 * The last row is filled with the empty string; typing in this row
 * creates a new empty row under it.
 *<P>
 * The data is loaded in "ranges" of several weeks.  You can either
 * retrieve them as a grid of cells, or can retrieve the ranges
 * by calling {@link #getRangeCount()} and {@link #getRange(int)}.
 * Load earlier data by calling {@link #addEarlierTripWeeks(RDBAdapter)}.
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
	public static final String[] COL_HEADINGS
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

	/** the vehicle being displayed */
	private Vehicle veh;

	/**
	 * Increment in weeks when loading newer/older trips into {@link #tData},
	 * or 0 to load all (This may run out of memory).
	 */
	private final int weekIncr;

	/**
	 * Holds each time range of trips; sorted from earliest to latest.
	 */
	private Vector<TripListTimeRange> tData;

	/**
	 * Length of all text rows from {@link #tData}'s tText[] arrays;
	 * does not include {@link #tAddedRows}.
	 */
	private int tDataTextRowCount;

	/**
	 * Rows being newly added (manual typing/edit mode), or null
	 */
	private Vector<String[]> tAddedRows;

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

	/** A {@link #tData}'s tText contents, or null; optimization for {@link #getValueAt(int, int)}. */
	private transient Vector<String[]> getValue_RangeText;

	/** {@link #getValue_RangeText}'s index within {@link #tData}; Optimization for {@link #getValueAt(int, int)} */
	private transient int getValue_RangeIndex;

	/** {@link #getValue_RangeText}'s low and high row numbers, or -1; Optimization for {@link #getValueAt(int, int)} */
	private transient int getValue_RangeRow0, getValue_RangeRowN;

	/**
	 * Create and populate with the most recent trip data.
	 *<P>
	 * You can add earlier trips later by calling
	 * {@link #addEarlierTripWeeks(RDBAdapter)}.
	 * @param veh  Vehicle
	 * @param weeks  Increment in weeks when loading newer/older trips from the database,
	 *          or 0 to load all (This may run out of memory).
	 *          The vehicle's most recent trips are loaded in this constructor.
	 * @param conn Add existing rows from this connection, via addRowsFromTrips.
	 */
	public LogbookTableModel(Vehicle veh, final int weeks, RDBAdapter conn)
	{
		tData = new Vector<TripListTimeRange>();
		tDataTextRowCount = 0;
		tAddedRows = null;
		locCache = new TIntObjectHashMap<Location>();
		viaCache = new TIntObjectHashMap<ViaRoute>();
		gasCache = new TIntObjectHashMap<GasBrandGrade>();

		this.veh = veh;
		this.weekIncr = weeks;
		getValue_RangeRow0 = -1;
		getValue_RangeRowN = -1;

		if (veh != null)
		{
			final int ltime = veh.readLatestTime(null);
			if ((ltime != 0) && (weekIncr != 0))
			{
				addRowsFromDBTrips(ltime, weekIncr, true, false, conn);
				if (! tData.isEmpty())
					tData.lastElement().noneLater = true;  // we know it's the newest trip
			} else {
				addRowsFromDBTrips(conn);
			}
		}
	}

	/**
	 * Add vehicle trips earlier than the current time range,
	 * looking back {@link #getWeekIncrement()} weeks.
	 *<P>
	 * The added trips will be a new {@link TripListTimeRange}
	 * inserted at the start of the range list; keep this
	 * in mind when calling {@link #getRange(int)} afterwards.
	 * (If no trips are found, no range is created.)
	 *
	 * @return true if trips were added, false if none found
	 */
	public boolean addEarlierTripWeeks(RDBAdapter conn)
	{
		if (tData.isEmpty())
			return false;  // No trips at all were previously found for this vehicle.

		final int loadToTime = tData.firstElement().timeStart;
		int nAdded = addRowsFromDBTrips(loadToTime, weekIncr, true, false, conn);
		// TODO ensure previously-oldest trip doesn't appear twice now
		if ((nAdded != 0) && (listener != null))
			listener.fireTableRowsInserted(0, nAdded - 1);

		return (nAdded != 0);
	}

	/**
	 * Add the vehicle's trips in this time range.
	 * Assumes is beginning or ending of previously loaded range.
	 *<P>
	 * If not called from the constructor, you must call
	 * {@link TableChangeListener#fireTableRowsInserted(int, int)}
	 * after calling this method: Use the returned row count,
	 * and remember that {@link #tDataTextRowCount} will already
	 * include the inserted rows.
	 * 
     * @param timeStart  Starting date/time of trip range, in Unix format
     * @param weeks   Retrieve this many weeks past timeStart
     * @param searchBeyondWeeks  If true, and if no trips found within
     *          <tt>weeks</tt>, keep searching until a trip is found
     * @param towardsNewer  If true, retrieve <tt>timeStart</tt> and newer,
     *          and assume adding at the end of {@link #tData};
     *          otherwise retrieve <tt>timeStart</tt> and older,
     *          and add at the start of {@link #tData}.
	 * @param conn Add existing rows from this connection
	 * @return Number of rows of text added to the table
	 */
	private int addRowsFromDBTrips
		(final int timeStart, final int weeks,
    	 final boolean searchBeyondWeeks, final boolean towardsNewer, RDBAdapter conn)
	{
		// TODO check tData for this time range already present
		TripListTimeRange ttr = Trip.tripsForVehicle
			(conn, veh, timeStart, weeks, searchBeyondWeeks, towardsNewer, true);
		if (ttr == null)
		{
			if (searchBeyondWeeks)
				if (towardsNewer)
					tData.lastElement().noneLater = true;
				else
					tData.firstElement().noneEarlier = true;
			return 0;  // <--- nothing found ---
		}
		if (towardsNewer)
			tData.add(ttr);
		else
			tData.insertElementAt(ttr, 0);

		addRowsFromTrips(ttr, conn);
		getValue_RangeRow0 = -1;  // row#s changing, so reset getValue_* vars
		getValue_RangeRowN = -1;
		return ttr.tText.size();
	}

	private void addRowsFromDBTrips(RDBAdapter conn)
	{
		Vector<Trip> td = veh.readAllTrips(true);
		if (td == null)
			return;  // <--- nothing found ---
		TripListTimeRange ttr = new TripListTimeRange
			(td.firstElement().getTime_start(), td.lastElement().getTime_start(), td);
		ttr.noneEarlier = true;
		ttr.noneLater = true;
		tData.add(ttr);

		addRowsFromTrips(ttr, conn);
	}

	/**
	 * Add trip data as text to ttr.tText, looking up from the database as needed.
	 * Updates {@link #tDataTextRowCount} to include the new text rows.
	 * @param ttr  Query and add text of this TripTimeRange's contents
	 * @param conn  Add from this connection
	 */
	private void addRowsFromTrips(TripListTimeRange ttr, RDBAdapter conn)
	{
		Vector<Trip> td = ttr.tr;
		if (td == null)
		{
			return;  // <--- nothing found ---
		}

		DateFormat dfd = DateFormat.getDateInstance(DateFormat.MEDIUM);
		DateFormat dft = DateFormat.getTimeInstance(DateFormat.SHORT);
		final int tRowCount;  // rows in tText before add
		if (ttr.tText == null)
		{
			ttr.tText = new Vector<String[]>();
			tRowCount = 0;
		} else {
			tRowCount = ttr.tText.size();
		}
		Vector<String[]> tText = ttr.tText;

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
				tr = new String[COL_HEADINGS.length];
				tr[0] = dfd.format(tstart);
				tText.addElement(tr);
			}
			prevTripStart = tstart;

			// next row of trip: location/odo, if different from previous trip's location/odo
			String[] firstrow;
			if (! nextTripUsesSameStop)
			{
				firstrow = new String[COL_HEADINGS.length];
				firstrow[2] = "/";
				firstrow[3] = Integer.toString((int) (t.getOdo_start() / 10.0f));
				if (ts_start != null)
					firstrow[6] = getTStopLocDescr(ts_start, conn);
				tText.addElement(firstrow);
			} else {
				firstrow = null;
			}

			// next row of trip: time
			tr = new String[COL_HEADINGS.length];
			tr[1] = dft.format(tstart);
			tText.addElement(tr);

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
						tr = new String[COL_HEADINGS.length];
		    			tr[1] = dft.format(new Date(ttstop * 1000L));
		    			tText.addElement(tr);
					}
	
					// stop info
					tr = new String[COL_HEADINGS.length];
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
	    			tText.addElement(tr);
	
					// start-time (if present)
					if (ttcont != 0)
					{
						tr = new String[COL_HEADINGS.length];
		    			tr[1] = dft.format(new Date(ttcont * 1000L));
		    			tText.addElement(tr);
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
				tr = new String[COL_HEADINGS.length];
				if (nextTripUsesSameStop)
					tr[2] = ">";
				else
					tr[2] = "\\";
				tr[3] = Integer.toString((int) (odo_end / 10.0f));
				tr[6] = t.getComment();
				tText.addElement(tr);
			}
		}

		tDataTextRowCount += (tText.size() - tRowCount);
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

		maxRowBeforeAdd = tDataTextRowCount;
		addMode = true;

		/**
		 * Write the template to the new rows
		 * (TODO) incl highest-date, odo, etc from prev-rows
		 * (TODO) other template: complex
		 */
		if (tAddedRows == null)
			tAddedRows = new Vector<String[]>();
		final String[][] template =
			withStops ? TEMPLATE_ADD_WITHSTOPS : TEMPLATE_ADD_SIMPLE;
		int r;
		for (r = 0; r < template.length; ++r)
		{
			// create a new row
			String[] td = new String[COL_HEADINGS.length];
			for (int i = 0; i < COL_HEADINGS.length; ++i)
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
			tAddedRows.addElement(td);
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

		final int L = tAddedRows.size();
		if (L > 0)
		{
    		if (listener != null)
    			listener.fireTableRowsDeleted(maxRowBeforeAdd+1, maxRowBeforeAdd+L);
		}
		tAddedRows.clear();
		tAddedRows = null;
		addMode = false;
	}

	/** get the week increment when moving to previous/next trips */
	public int getWeekIncrement() { return weekIncr; }

	/**
	 * Get the number of ranges currently loaded from the database.
	 * @see #getRange(int)
	 */
	public int getRangeCount() { return tData.size(); }

	/**
	 * Get a {@link TripListTimeRange} currently loaded from the database.
	 * @param i  index of this range, 0 to {@link #getRangeCount()} - 1
	 * @return the range at index <tt>i</tt>
	 * @throws ArrayIndexOutOfBoundsException  if i &lt; 0 or i >= {@link #getRangeCount()}
	 */
	public TripListTimeRange getRange(final int i)
		throws ArrayIndexOutOfBoundsException
	{
		return tData.elementAt(i);
	}

	/** column count; same as length of {@link #COL_HEADINGS} */
	public int getColumnCount() { return COL_HEADINGS.length; }

	/** occupied row count; during "Add Trip" mode, includes current data entry and blank line at end */
	public int getRowCount()
	{
		int rc = tDataTextRowCount;
		if (addMode)
			++rc;
		if (tAddedRows != null)
			rc += tAddedRows.size();
		return rc;
	}

	public String getColumnName(final int c) { return COL_HEADINGS[c]; }

	public Object getValueAt(final int r, final int c)
	{
		/**
		 * Because the text isn't one long array, but is broken up
		 * among tData's elements, determine the row number.
		 */
		int tr;
		if ((r >= getValue_RangeRow0) && (r < getValue_RangeRowN))
		{
			tr = r - getValue_RangeRow0;

		} else if (r >= tDataTextRowCount)
		{
			if (tAddedRows != null)
			{
				tr = r - tDataTextRowCount;
				if (tr < tAddedRows.size())
					return tAddedRows.elementAt(tr)[c];
				else
					return "";
			} else {
				return "";
			}

		} else if (r == getValue_RangeRowN)
		{
			// just beyond end of previous range
			boolean trFound;
			tr = -1;  // satisfy javac
			if (getValue_RangeIndex == tData.size())
			{
				trFound = false;
			} else {
				++getValue_RangeIndex;
				final TripListTimeRange ttr = tData.elementAt(getValue_RangeIndex);
				final Vector<String[]> tText = ttr.tText;
				if (tText == null)
				{
					trFound = false;
				} else {
					trFound = true;
					final int L = tText.size();   // TODO not 0
					getValue_RangeRow0 = r;
					getValue_RangeRowN = r + L;
					getValue_RangeText = tText;
					tr = 0;  // == r - getValue_RangeRow0
				}
			}
			if (! trFound)
			{
				getValue_RangeRow0 = -1;
				getValue_RangeRowN = -1;
				return "";				
			}

		} else if (r == (getValue_RangeRow0 - 1))
		{
			// just before start of previous range
			boolean trFound;
			tr = -1;  // satisfy javac
			if (getValue_RangeIndex == 0)
			{
				trFound = false;
			} else {
				--getValue_RangeIndex;
				final TripListTimeRange ttr = tData.elementAt(getValue_RangeIndex);
				final Vector<String[]> tText = ttr.tText;
				if (tText == null)
				{
					trFound = false;
				} else {
					trFound = true;
					final int L = tText.size();   // TODO not 0
					getValue_RangeRow0 -= L;
					getValue_RangeRowN = r + 1;
					getValue_RangeText = tText;
					tr = L - 1;  // == r - getValue_RangeRow0
				}
			}
			if (! trFound)
			{
				getValue_RangeRow0 = -1;
				getValue_RangeRowN = -1;
				return "";				
			}

		} else {
			int totalr = 0;        // Total text rows before this range
			tr = r;                // invariant: tr == r - totalr
			boolean trFound = false;
			final int tdSize = tData.size();
			for (int i = 0; i < tdSize; ++i)
			{
				final TripListTimeRange ttr = tData.elementAt(i);
				final Vector<String[]> tText = ttr.tText;
				if (tText == null)
					break;
				final int L = tText.size();
				if (tr < L)
				{
					trFound = true;
					getValue_RangeText = tText;
					getValue_RangeIndex = i;
					getValue_RangeRow0 = totalr;
					getValue_RangeRowN = totalr + L;
					break;
				}
				totalr += L;
				tr -= L;  // invariant: tr == r - totalr
			}
			if (! trFound)
				return "";
		}
		if (tr < getValue_RangeText.size())
			return getValue_RangeText.elementAt(tr)[c];
		else
			return "";
	}

	/**
	 * All added cells (not those loaded from the
	 * database) are editable, except the first 2 columns.
	 * @param r Row, from 0
	 * @param c Column, from 0
	 */
	public boolean isCellEditable(int r, int c)
	{
		return (r >= tDataTextRowCount) && (c >= 2) && (c < COL_HEADINGS.length);
	}

	/**
	 * Update the value in a cell (for AbstractTableModel).
	 * Only the added rows (not those loaded from the
	 * database) are editable. 
	 * @param r Row, from 0
	 * @param c Column, from 0
	 */
	public void setValueAt(Object newValue, final int r, final int c)
	{
		final int addRow = r - tDataTextRowCount;
		if ((addRow < 0) || (c >= COL_HEADINGS.length))
			return;
		boolean rowExists = (tAddedRows != null) && (addRow < tAddedRows.size());
		if (rowExists)
		{
			tAddedRows.elementAt(addRow)[c] = newValue.toString();
		} else {
			// create a new row
			String[] td = new String[COL_HEADINGS.length];
			for (int i = 0; i < COL_HEADINGS.length; ++i)
				if (i != c)
					td[i] = "";
				else
					td[i] = newValue.toString();
			tAddedRows.addElement(td);
		}
		if (listener != null)
		{
			listener.fireTableCellUpdated(r, c);
			if (! rowExists)
				listener.fireTableRowsInserted(r+1, r+1);
		}
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
