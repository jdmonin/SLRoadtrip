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

package org.shadowlands.roadtrip.bookedit;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.text.DateFormat;
import java.util.Date;
import java.util.Vector;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;

import org.shadowlands.roadtrip.db.AppInfo;
import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.RDBKeyNotFoundException;
import org.shadowlands.roadtrip.db.RDBSchema;
import org.shadowlands.roadtrip.db.RDBVerifier;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.TStop;
import org.shadowlands.roadtrip.db.Trip;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.jdbc.RDBJDBCAdapter;
import org.shadowlands.roadtrip.model.LogbookTableModel;
import org.shadowlands.roadtrip.model.TableChangeListener;

// TODO: needs buttons to control open/close/etc
// TODO dropdowns for driver change, maybe area change
// TODO show current driver, area, etc at top

@SuppressWarnings("serial")
public class LogbookEditPane extends JPanel implements ActionListener, WindowListener
{
	/** 'Vehicle: ' button text */
	private static final String TEXT_VEHICLE = "Vehicle: ";

	/**
	 * Increment in weeks when loading newer/older trips from the database,
	 * or 0 to load all (This may run out of memory).
	 */
	public static final int WEEK_INCREMENT = 2;

	private RDBAdapter conn;
	private final boolean isReadOnly;
	private Vehicle veh;
	/** The trip data for {@link #tb}; see that field's javadoc. */
	private LBSwingTableModel mdata;
	/** Holds this jpanel */
	private JFrame lbef;
	/**
	 * The current {@link #veh}'s trip data table.
	 * If you change the table model {@link #mdata} to load a different vehicle's trips,
	 * call {@link #setupTbColumnModel()} afterwards.
	 */
	private JTable tb;
	private JScrollPane sp;  // holds tb
	private JButton bLoadPrevious;  // earlier trips
	private JPanel pbtns;  // below JTable
	private JButton bAddSimple, bAddWithStops, bAddDone, bAddCancel, bChgVehicle;
	private JButton bTmpVerifyDB;  // TODO quick test for db verifier; move to a menu or something

	/**
	 * Create and show a new scrolling grid, in a new {@link JFrame}, to view or edit this logbook data.
	 * When the JFrame is closed, it will call {@link RDBAdapter#close() conn.close()}</tt> .
	 * @param fname  Filename, for display only; all I/O happens via <tt>conn</tt>.
	 * @param veh   Vehicle data
	 * @param conn  An open database connection associated with this data
	 * @param isReadOnly  Treat conn as a read-only database
	 */
	public LogbookEditPane(String fname, Vehicle veh, RDBAdapter conn, final boolean isReadOnly)
	{
		this.conn = conn;
		this.veh = veh;
		this.isReadOnly = isReadOnly;
		mdata = new LBSwingTableModel(veh, WEEK_INCREMENT, conn, isReadOnly);
		lbef = new JFrame
		    ( (isReadOnly ? "Quick Viewer - " : "Quick Editor - ") + fname);
		lbef.addWindowListener(this);  // needed for conn.close() when window closes
		tb = new JTable(mdata);
		setLayout(new BorderLayout());  // stretch JTable on resize
		setupTbColumnModel();
		sp = new JScrollPane(tb);
		add(sp, BorderLayout.CENTER);
		lbef.add(this, BorderLayout.CENTER);

		// Buttons above JTable
		{
			GridLayout bgl = new GridLayout(1, 2);
			JPanel pba = new JPanel(bgl);
			bLoadPrevious = new JButton("<< Earlier trips");
			bLoadPrevious.setToolTipText("Show trips previous to the ones shown now");
			bLoadPrevious.addActionListener(this);
			pba.add(bLoadPrevious);
			lbef.add(pba, BorderLayout.NORTH);
		}

		// Buttons below JTable
        GridLayout bgl = new GridLayout(3, 2);
		pbtns = new JPanel(bgl);
		bAddSimple = new JButton("+ Simple Trip");
		bAddSimple.setToolTipText("Add a new trip. If clicked when already adding, ends current trip first.");
		bAddSimple.addActionListener(this);
		bAddWithStops = new JButton("+ With Stops");
		bAddWithStops.setToolTipText("Add a new trip. If clicked when already adding, ends current trip first.");
		bAddWithStops.addActionListener(this);
		bAddDone = new JButton("Done Adding");
		bAddDone.setToolTipText("Complete this new trip.");
		bAddDone.addActionListener(this);
		bAddDone.setVisible(false);
		bAddCancel = new JButton("Cancel Add");
		bAddCancel.setToolTipText("Cancel and clear this new trip.");
		bAddCancel.addActionListener(this);
		bAddCancel.setVisible(false);
		bChgVehicle = new JButton(TEXT_VEHICLE + veh.toString());
		bChgVehicle.setToolTipText("Display a different vehicle in this logbook.");
		bChgVehicle.addActionListener(this);
		bChgVehicle.setVisible(true);
		bTmpVerifyDB = new JButton("Verify DB");
		bTmpVerifyDB.setToolTipText("Validate the db data. The physical structure is already verified when the DB is opened.");
		bTmpVerifyDB.addActionListener(this);
		bTmpVerifyDB.setVisible(true);
		if (isReadOnly)
		{
			bAddSimple.setEnabled(false);
			bAddWithStops.setEnabled(false);
		}
		pbtns.add(bAddSimple);
		pbtns.add(bAddWithStops);
		pbtns.add(bAddDone);
		pbtns.add(bAddCancel);
		pbtns.add(bChgVehicle);
		pbtns.add(bTmpVerifyDB);
		lbef.add(pbtns, BorderLayout.SOUTH);

		lbef.pack();
		lbef.setVisible(true);
	}

	/** Set {@link #tb}'s column model (widths, etc.) */
	private void setupTbColumnModel()
	{
		tb.getColumnModel().getColumn(2).setPreferredWidth(5);  // narrow
		tb.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(new JNumTextField()));
		tb.getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(new JNumTextField()));
	}

	/** Handle button press events */
	public void actionPerformed(ActionEvent e)
	{
		Object src = e.getSource();

		if (src == bLoadPrevious)
			actionLoadPrevious();
		else if (src == bAddSimple)
			actionAddTripBegin(false);
		else if (src == bAddWithStops)
			actionAddTripBegin(true);
		else if (src == bAddDone)
			actionAddTripFinish(true);
		else if (src == bAddCancel)
			actionAddTripFinish(false);
		else if (src == bChgVehicle)
			actionChangeVehicle();
		else if (src == bTmpVerifyDB)
			actionVerifyDB();
	}

	private void actionLoadPrevious()
	{
		// TODO chk mdata.ltm.addMode; btn should be disabled if addMode true, though
		if (! mdata.ltm.addEarlierTripWeeks(conn))
			bLoadPrevious.setEnabled(false);
		else
			tb.scrollRectToVisible(tb.getCellRect(0, 0, true));  // scroll to top
	}

	private void actionAddTripBegin(final boolean withStops)
	{
		if (isReadOnly)
			return;  // just in case
		mdata.ltm.finishAdd();  // in case of previous trip
		mdata.ltm.beginAdd(withStops);
		bChgVehicle.setEnabled(false);
		bChgVehicle.setVisible(false);
		bAddDone.setVisible(true);
		bAddCancel.setVisible(true);
		// wait for AWT to update itself
		// (TODO) how?
		// scroll to bottom of visible
		{
			tb.scrollRectToVisible(tb.getCellRect(mdata.getRowCount(), 0, true));
			// this doesn't deal with the newly-expanded vertical max:
			// JScrollBar vsb = sp.getVerticalScrollBar();
			// vsb.setValue(vsb.getMaximum() - vsb.getVisibleAmount());
		}
	}

	private void actionAddTripFinish(final boolean addNotCancel)
	{
		if (addNotCancel)
			mdata.ltm.finishAdd();
		else
			mdata.ltm.cancelAdd();
		bAddDone.setVisible(false);
		bAddCancel.setVisible(false);
		bChgVehicle.setEnabled(true);
		bChgVehicle.setVisible(true);
	}

	private void actionChangeVehicle()
	{
		Vehicle[] allV = Vehicle.getAll(conn);
		if (allV.length < 2)
		{
			// TODO allow add new veh
			JOptionPane.showMessageDialog(lbef,
			    "This is the only vehicle in the logbook.",
			    "No other vehicles",
			    JOptionPane.INFORMATION_MESSAGE);
			bChgVehicle.setEnabled(false);
			return;  // <--- Early return: Nothing to change ---
		}

		// show the chooser for vehicles
		new VehicleChooserDialog(allV, veh.getID());
	}

	/** Verify the DB consistency with {@link RDBVerifier#verify(int)}, and show a passed/failed message box. */
	public void actionVerifyDB()
	{
		RDBVerifier verif = new RDBVerifier(conn);
		final int vResult = verif.verify(RDBVerifier.LEVEL_TDATA);
		verif.release();
		String optionPaneMsg;
		int optionPaneLevel;
		if (vResult == 0)
		{
			optionPaneMsg = "Verification passed.";
			optionPaneLevel = JOptionPane.INFORMATION_MESSAGE;
		} else {
			optionPaneMsg = "Verification failed (return code " + vResult + ").";
			optionPaneLevel = JOptionPane.ERROR_MESSAGE;
		}

		JOptionPane.showMessageDialog(lbef,
			optionPaneMsg,
		    "Verification results",
		    optionPaneLevel);
	}

	/** Show this vehicle's trips. Callback from VehicleChooserDialog. */
	public void showVehicleTrips(final int vID)
	{
		if (vID == veh.getID())
			return;  // nothing to do
		if (! (bChgVehicle.isEnabled() && bChgVehicle.isVisible()))
			return;  // not allowed to change right now

		try {
			mdata.fireTableRowsDeleted(1, mdata.getRowCount());
			veh = new Vehicle(conn, vID);
			mdata = new LBSwingTableModel(veh, WEEK_INCREMENT, conn, isReadOnly);
			// setModel and loads the current data from mdata,
			// but loses the column model.
			tb.setModel(mdata);
			setupTbColumnModel();
			bChgVehicle.setText(TEXT_VEHICLE + veh.toString());
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RDBKeyNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * This is for development and debugging only, please use <tt>Main.main</tt> instead.
	 * Looks for and opens <tt>test.sqlite</tt> in the current directory.
	 */
	public static void main(String[] args)
	{
		String fname;
		if ((args.length > 0) && (args[0] != null) && (args[0].indexOf('.') != -1))
			fname = args[0];
		else
			fname = "test.sqlite";

		JFrame f = new JFrame("owner of dialog");
		f.setSize(400, 200);
		f.pack();
		f.setVisible(true);

		setupFromMain(fname, fname, f, false);  // TODO extract fnshort
	}

	// TODO javadoc: meant to be called from Main, will prompt user, etc
	public static void setupFromMain(final String fname, final String fnshort, final JFrame parentf, boolean isReadOnly)
	{
		RDBAdapter conn = null;
		try
		{
			conn = new RDBJDBCAdapter(fname);
		} catch (Throwable t)
		{
			System.err.println("Could not open SQLite db " + fname);
			System.err.println("Error was: " + t.getClass() + " " + t.getMessage());
			t.printStackTrace();
			JOptionPane.showMessageDialog(parentf,
			    "Could not open this database.\nError was: " + t.getClass() + " " + t.getMessage(),
			    "Could not open SQLite db",
			    JOptionPane.ERROR_MESSAGE);
			System.exit(8);  // <--- Exit(rc 8) ----
		}

		// Is it physically consistent? (sqlite pragma integrity_check)
		{
			RDBVerifier verif = new RDBVerifier(conn);
			boolean verifOK;
			try
			{
				verifOK = (0 == verif.verify(RDBVerifier.LEVEL_PHYS));
			} catch (IllegalStateException e) {
				verifOK = false;
			}
			verif.release();

			if (! verifOK)
			{
				final String[] upg_exit = { "Continue anyway", "Exit" };
				final int choice = JOptionPane.showOptionDialog(parentf,
					"The physical structure of this database has errors.",
					"DB structure check failed",
					JOptionPane.DEFAULT_OPTION,
					JOptionPane.ERROR_MESSAGE,
					null, upg_exit, upg_exit[1]);
				if (choice == 1)
				{
					conn.close();
					System.exit(8);  // <--- End program: Don't want to use this db ---
				} else {
					isReadOnly = true;
				}
			}
		}

		try
		{
			AppInfo aivers = new AppInfo(conn, "DB_CURRENT_SCHEMAVERSION");
			System.out.println("AppInfo: Current schemaversion: " + aivers.getValue());
		} catch (RDBKeyNotFoundException e)
		{
			System.err.println("Not found: " + e.keyString);
			JOptionPane.showMessageDialog(parentf,
			    "An important record is missing in the database.",
			    "Missing required version record",
			    JOptionPane.ERROR_MESSAGE);
			conn.close();
			System.exit(8);  // <--- End program: Version unknown, can't use this db ---
		}

		// Does it need an upgrade?
		final int user_version = ((RDBJDBCAdapter) conn).getSchemaVersion();
		System.out.println("user_version is " + user_version + " (current: " + RDBSchema.DATABASE_VERSION + ")");
		if (user_version < RDBSchema.DATABASE_VERSION)
		{
			final String[] upg_exit = { "Upgrade", "Exit" };
			final int choice = JOptionPane.showOptionDialog(parentf,
				"To use this database, it must be upgraded from older version "
					+ user_version + " to the current version "
					+ RDBSchema.DATABASE_VERSION + ".",
				"DB Upgrade Required",
				JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null, upg_exit, upg_exit[0]);
			if (choice == 1)
			{
				conn.close();
				System.exit(0);
			}
			System.err.println("-> Chose upgrade");
			try
			{
				RDBSchema.upgradeToCurrent(conn, user_version, false);
			} catch (Throwable e) {
				// TODO capture it somewhere accessible?
				e.printStackTrace();
				JOptionPane.showMessageDialog(parentf,
				    "An error occurred during the upgrade. Please run again from the command line to see the stack trace.\n"
					  + e.toString() + " " + e.getMessage(),
				    "Error during upgrade",
				    JOptionPane.ERROR_MESSAGE);
				System.exit(8);
			}
		}

		//
		// CURRENT_DRIVER
		//
		Settings currentDriverID = null;
		Person cdriv = null;
		boolean newDriver = true;

		try
		{
			currentDriverID = new Settings(conn, "CURRENT_DRIVER");
			System.out.println("Current Driver ID is " + currentDriverID.getIntValue());
			cdriv = new Person(conn, currentDriverID.getIntValue());
			newDriver = false;
		} catch (RDBKeyNotFoundException e)
		{
			System.err.println("Could not find CURRENT_DRIVER");
			System.err.println("Error was: " + e.getClass() + " " + e.getMessage());
			e.printStackTrace();
			System.err.println("Continuing without current driver.");
		}

		try
		{
			if (cdriv == null)
			{
				JOptionPane.showMessageDialog(parentf,
				    "Please enter information about the vehicle's driver.",
				    "No driver found",
				    JOptionPane.WARNING_MESSAGE);
			}
			cdriv = MiscTablesCRUDDialogs.createEditPersonDialog(parentf, conn, cdriv, true);
			if (newDriver)
			{
				if (cdriv == null)
				{
					System.err.println("Cancelled.");
					// TODO EXIT IF CANCEL ?
				} else {
					final int cdid = cdriv.getID();
					if (currentDriverID == null)
					{
						currentDriverID = new Settings("CURRENT_DRIVER", cdid);
						currentDriverID.insert(conn);
					} else {
						currentDriverID.setIntValue(cdid);
						currentDriverID.commit();
					}
				}
			}
		} catch (Throwable t)
		{
			System.err.println("Could not read/create/write the current driver");
			System.err.println("Error was: " + t.getClass() + " " + t.getMessage());
			t.printStackTrace();			
		}

		//
		// CURRENT_VEHICLE
		//
		Vehicle cveh = null;
		if (cdriv != null)
		{
			Settings currentVehicleID = null;
			boolean newVehicle = true;
			try
			{
				currentVehicleID = new Settings(conn, "CURRENT_VEHICLE");
				System.out.println("Current Vehicle ID is " + currentVehicleID.getIntValue());
				cveh = new Vehicle(conn, currentVehicleID.getIntValue());
				newVehicle = false;
			} catch (RDBKeyNotFoundException e)
			{
				System.err.println("Could not find CURRENT_VEHICLE");
				System.err.println("Error was: " + e.getClass() + " " + e.getMessage());
				e.printStackTrace();
				System.err.println("Continuing without current vehicle.");
			}

			try
			{
				if (cveh == null)
				{
					JOptionPane.showMessageDialog(parentf,
					    "Please enter information about the vehicle.",
					    "No vehicle found",
					    JOptionPane.WARNING_MESSAGE);
				}
				cveh = MiscTablesCRUDDialogs.createEditVehicleDialog(parentf, conn, cveh, cdriv);
				if (newVehicle)
				{
					if (cveh == null)
					{
						System.err.println("Cancelled.");
						// TODO EXIT IF CANCEL ?
					} else {
						final int cvid = cveh.getID();
						if (currentVehicleID == null)
						{
							currentVehicleID = new Settings("CURRENT_VEHICLE", cvid);
							currentVehicleID.insert(conn);
						} else {
							currentVehicleID.setIntValue(cvid);
							currentVehicleID.commit();
						}
					}
				}
			} catch (Throwable t)
			{
				System.err.println("Could not read/create/write the current vehicle");
				System.err.println("Error was: " + t.getClass() + " " + t.getMessage());
				t.printStackTrace();			
			}
		}  // CURRENT_VEHICLE

		new LogbookEditPane(fnshort, cveh, conn, isReadOnly);

		// When pane closes, that will call conn.close() .

	}

	//
	// WINDOWLISTENER
	//

	/** Empty required stub for WindowListener. */
	public void windowActivated(WindowEvent e) { }

	/** Empty required stub for WindowListener. */
	public void windowClosed(WindowEvent e) { }

	/** When the window is closing, close the database connection. */
	public void windowClosing(WindowEvent e)
	{
		if (conn != null)
			 conn.close();
	}

	/** Empty required stub for WindowListener. */
	public void windowDeactivated(WindowEvent e) { }

	/** Empty required stub for WindowListener. */
	public void windowDeiconified(WindowEvent e) { }

	/** Empty required stub for WindowListener. */
	public void windowIconified(WindowEvent e) { }

	/** Empty required stub for WindowListener. */
	public void windowOpened(WindowEvent e) { }

	//
	// NESTED CLASSES
	//

	// TODO descr
	private static class LBSwingTableModel extends AbstractTableModel implements TableChangeListener
	{
		public LogbookTableModel ltm;
		private final boolean isReadOnly;

		/**
    	 * Create and populate with existing data.
    	 * @param veh  Vehicle
    	 * @param weeks  Week increment when loading data
    	 * @param conn Add existing rows from this connection, via addRowsFromTrips.
    	 */
    	public LBSwingTableModel(Vehicle veh, final int weeks, RDBAdapter conn, final boolean isReadOnly)
    	{
    		this.isReadOnly = isReadOnly;
    		ltm = new LogbookTableModel(veh, weeks, conn);
    		ltm.setListener(this);
    	}

		public int getRowCount() {
			return ltm.getRowCount();
		}

		public int getColumnCount() {
			return ltm.getColumnCount();
		}

		public Object getValueAt(int rowIndex, int columnIndex) {
			return ltm.getValueAt(rowIndex, columnIndex);
		}

		public String getColumnName(int col) {
			return ltm.getColumnName(col);
		}

    	public boolean isCellEditable(int r, int c) {
    		return (! isReadOnly) && ltm.isCellEditable(r, c);
    	}

	}  // static nested class LBSwingTableModel

	/**
	 * Holds data while editing; model of our logbook data in the JTable.
	 * The last row is filled with the empty string; typing in this row
	 * creates a new empty row under it.
	 */
    private static class LBTableModel extends AbstractTableModel
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
    	 * Are we adding a new trip right now?
    	 * @see #maxRowBeforeAdd
    	 */
    	private boolean addMode;

    	/** During {@link #addMode}, {@link Vector#size() tData.size()} before starting the add. */
    	private int maxRowBeforeAdd;

    	/**
    	 * Create and populate with existing data.
    	 * @param veh  Vehicle
    	 * @param conn Add existing rows from this connection, via addRowsFromTrips.
    	 */
    	public LBTableModel(Vehicle veh, RDBAdapter conn)
    	{
    		tData = new Vector<String[]>();
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

    		for (Trip t : td)
    		{
    			TStop ts_start =  t.readStartTStop(false);  // may be null

    			// first row of trip: odo
    			String[] firstrow = new String[colHeadings.length];
    			firstrow[2] = "/";
    			firstrow[3] = Integer.toString((int) (t.getOdo_start() / 10.0f));
    			if (ts_start != null)
    			{
					firstrow[6] = ts_start.getLocationDescr();  // TODO getlocid ?
    			}
    			tData.addElement(firstrow);

    			// next row of trip: time
    			String[] tr = new String[colHeadings.length];
    			Date tstart = new Date(t.getTime_start() * 1000L);
    			tr[0] = dfd.format(tstart);
    			tr[1] = dft.format(tstart);
    			tData.addElement(tr);

    			final int odo_end = t.getOdo_end();

    			for (TStop ts : t.readAllTStops())
    			{
    				if ((ts_start == null) && (ts.getOdo_trip() == 0))
    				{
    					// this stop is the starting location
    					ts_start = ts;
    					firstrow[6] = ts.getLocationDescr();  // TODO getlocid ?
    					continue;  // <-- doesn't get its own row, only firstrow --
    				}

    				final int ttstop = ts.getTime_stop();
    				final int ttstart = ts.getTime_continue();

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
						if (ttstart != 0)
							tr[2] = ">";  // both stop,start time
						else
							tr[2] = "\\";  // only stop time
					} else if (ttstart != 0)
					{
						tr[2] = "/";  // only start time
					}
					int x = ts.getOdo_total();
					final boolean is_last_stop = (x != 0) && (x == odo_end) && (ttstart == 0);
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
					tr[5] = ts.getVia_route();
					tr[6] = ts.getLocationDescr();
	    			tData.addElement(tr);

    				// start-time (if present)
    				if (ttstart != 0)
    				{
    					tr = new String[colHeadings.length];
    	    			tr[1] = dft.format(new Date(ttstart * 1000L));
    	    			tData.addElement(tr);
    				}
    			}

    			// next-to-last row of trip: time, trip-odo, via, trip-desc
    			tr = new String[colHeadings.length];
    			Date tend = new Date(t.getTime_end() * 1000L);
    			tr[0] = dfd.format(tend);
    			tr[1] = dft.format(tend);
    			tr[6] = t.getComment();
    			tData.addElement(tr);

    			// last row of trip: odo
    			tr = new String[colHeadings.length];
    			tr[2] = "\\";
    			tr[3] = Integer.toString((int) (odo_end / 10.0f));
    			tData.addElement(tr);
    		}
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
    		this.fireTableRowsInserted(maxRowBeforeAdd, maxRowBeforeAdd + r);
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
	    		fireTableRowsDeleted(maxRowBeforeAdd+1, highIdx);
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
    		fireTableCellUpdated(r, c);
    		if (! rowExists)
    			fireTableRowsInserted(r+1, r+1);
    	}

    }  // inner static class LBTableModel

    /** Numeric-only input field, for odometer readings  */
    private static class JNumTextField extends JTextField
    {
    	public void processKeyEvent(KeyEvent e)
    	{
    		final char c = e.getKeyChar();
    		if ((c != '.') && (c != ',')
    			&& (0 == e.getModifiersEx())
    			&& ! Character.isDigit(c))
    		{
    			e.consume();
    		} else {
    			super.processKeyEvent(e);
    		}
    	}
    }  // inner static class JNumTextField

    /**
     * Create and choose a dialog for a vehicle to display.
     * If a different vehicle is chosen, the dialog calls
     * {@link LogbookEditPane#showVehicleTrips(int)}.
     */
	private class VehicleChooserDialog extends JDialog
		implements ItemListener, ActionListener
	{
		private final Vehicle[] veh;
		private final int currV;
		private JComboBox dropdown;

		public VehicleChooserDialog(Vehicle[] vlist, final int currV)
		{
			veh = vlist;
			this.currV = currV;
			setLayout(new FlowLayout());
			add(new JLabel("Choose a vehicle to display."));
			dropdown = new JComboBox(vlist);
			dropdown.setEditable(false);
			for (int i = vlist.length - 1; i>=0; --i)
			{
				if (currV == vlist[i].getID())
				{
					dropdown.setSelectedItem(vlist[i]);
					break;
				}
			}
			dropdown.addItemListener(this);  // not until after setSelectedItem
			add(dropdown);
			JButton bCancel = new JButton("Cancel");
			bCancel.addActionListener(this);
			add(bCancel);
			pack();
			setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			setVisible(true);
		}

		/** handle change of vehicle in chooser dropdown */
		public void itemStateChanged(ItemEvent e)
		{
			if (! (e.getStateChange() == ItemEvent.SELECTED))
				return;
			Object o = e.getItem();
			if (! (o instanceof Vehicle))
				return;
			Vehicle v = (Vehicle) o;
			final int id = v.getID();
			if (currV == id)
				return;
			setVisible(false);
			showVehicleTrips(id);  // callback to LogbookEditPane
			dispose();
		}

		/** handle 'cancel' button click */
		public void actionPerformed(ActionEvent e)
		{
			dispose();
		}

	}  // inner class VehicleChooserDialog


}  // public class LogbookEditPane
