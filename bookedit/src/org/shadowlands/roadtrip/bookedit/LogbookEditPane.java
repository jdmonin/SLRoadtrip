/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2011,2014-2015,2019 Jeremy D Monin <jdmonin@nand.net>
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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.sql.SQLException;
import java.util.zip.DataFormatException;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

import org.shadowlands.roadtrip.db.AppInfo;
import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.RDBKeyNotFoundException;
import org.shadowlands.roadtrip.db.RDBSchema;
import org.shadowlands.roadtrip.db.RDBVerifier;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.VehSettings;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.jdbc.RDBJDBCAdapter;
import org.shadowlands.roadtrip.model.LogbookTableModel;
import org.shadowlands.roadtrip.model.TableChangeListener;

// TODO dropdowns for driver change, maybe area change
// TODO show current driver, area, etc at top
// TODO consider read-only log if veh inactive

/**
 * Logbook viewer/editor. Shows 1 vehicle's trips at a time,
 * with buttons to view drivers/vehicles and validate the DB.
 *<P>
 * {@link #setupFromMain(String, String, JFrame, boolean, boolean)}
 * takes a database or backup filename, does sanity checks and
 * version upgrade if needed, then creates and shows a {@code LogbookEditPane}.
 */
@SuppressWarnings("serial")
public class LogbookEditPane
	extends JPanel implements ActionListener, ItemListener, WindowListener
{
	/**
	 * Increment in weeks when loading newer/older trips from the database,
	 * or 0 to load all (This may run out of memory).
	 */
	public static final int WEEK_INCREMENT = 2;

	/**
	 * Callback for use by {@link #upgradeDBCopy(File, int, JFrame)} if needed.
	 * @since 0.9.40
	 */
	private static final RDBSchema.UpgradeCopyCaller rdbUpgSingleton = new RDBSchema.UpgradeCopyCaller()
	{
		public RDBAdapter openRDB(final String fullPath)
			throws ClassNotFoundException, SQLException
		{
			return new RDBJDBCAdapter(fullPath);
		}
	};

	private RDBAdapter conn;
	private final boolean isReadOnly;

	/** Vehicle currently being shown in {@link #mdata} */
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

	/**
	 * Combo box of inactive/active vehicles in top panel.
	 * Updated by {@link #updateVehicleInfoRow(Vehicle)}.
	 * Calls {@link #itemStateChanged(ItemEvent)} when vehicle or "Other..." is selected.
	 * Label is {@link #lblVehicles}.
	 * @see #canChangeVehicle
	 * @since 0.9.80
	 */
	private final JComboBox jcbVehicles;

	/**
	 * Can vehicle currently be changed, using {@link #jcbVehicles}?
	 * True unless user is adding a new trip.
	 * @since 0.9.80
	 */
	private boolean canChangeVehicle = true;

	/** Label for {@link #jcbVehicles} */
	private final JLabel lblVehicles;

	private JPanel pbtns;  // below JTable
	private JButton bAddSimple, bAddWithStops, bAddDone, bAddCancel;
	private JButton bTmpValidateDB;
	private final JButton bVehicles, bDrivers;

	/**
	 * Create and show a new scrolling grid, in a new {@link JFrame}, to view or edit this logbook data.
	 * When the JFrame is closed, it will call {@link RDBAdapter#close() conn.close()}.
	 *<P>
	 * Called from {@link #setupFromMain(String, String, JFrame, boolean, boolean)}.
	 *
	 * @param fname  Filename, for display only; all I/O happens via {@code conn}
	 * @param veh   Vehicle to initially show from database
	 * @param conn  An open database connection associated with this data
	 * @param isReadOnly  If true, treat {@code conn} as a read-only database
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
		tb = new JTable(mdata)
		{
			/** Show tooltip with cell's full text if truncated by narrow column width */
			public Component prepareRenderer(final TableCellRenderer tcr, final int vr, final int vc)
			{
				Component co = super.prepareRenderer(tcr, vr, vc);
				if (co instanceof JComponent)
					if (co.getPreferredSize().width > getCellRect(vr, vc, false).width)
						((JComponent) co).setToolTipText((String) getValueAt(vr, vc));
					else
						((JComponent) co).setToolTipText(null);

				return co;
			}			
		};
		setLayout(new BorderLayout());  // stretch JTable on resize
		tb.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);  // don't stretch all cols on manual col-width chg
		// allow select cells, not just entire rows
		tb.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
		tb.setCellSelectionEnabled(true);

		setupTbColumnModel();
		sp = new JScrollPane(tb);
		add(sp, BorderLayout.CENTER);
		lbef.add(this, BorderLayout.CENTER);

		// Vehicle control row and Buttons above JTable
		{
			GridLayout bgl = new GridLayout(0, 1);  // unlimited rows, 1 column, stretch entire width

			JPanel pba = new JPanel(bgl);

			final JPanel vehRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
			lblVehicles = new JLabel(" ");
			jcbVehicles = new JComboBox();
			jcbVehicles.setToolTipText("Display a different vehicle in this logbook.");
			updateVehicleInfoRow(veh, true);  // set lblVehicles & jcbVehicles contents, addItemListener(this)
			jcbVehicles.setEditable(false);
			vehRow.add(lblVehicles);
			vehRow.add(jcbVehicles);
			pba.add(vehRow);

			bLoadPrevious = new JButton("<< Earlier trips");
			bLoadPrevious.setToolTipText("Show trips previous to the ones shown now");
			bLoadPrevious.addActionListener(this);
			pba.add(bLoadPrevious);

			lbef.add(pba, BorderLayout.NORTH);
		}

		// Buttons below JTable
		GridLayout bgl = new GridLayout(3, 3);
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
		bTmpValidateDB = new JButton("Validate DB");
		bTmpValidateDB.setToolTipText("Validate the db data logical structure. The physical structure is already verified when the DB is opened.");
		bTmpValidateDB.addActionListener(this);
		bTmpValidateDB.setVisible(true);
		bVehicles = new JButton("Vehicles...");
		bVehicles.setToolTipText("Show the list of vehicles in the logbook.");
		bVehicles.addActionListener(this);
		bDrivers = new JButton("Drivers...");
		bDrivers.setToolTipText("Show the list of drivers in the logbook.");
		bDrivers.addActionListener(this);

		// TODO temporarily disabling Add buttons until LTM.finishAdd() is tested.
		//if (isReadOnly)
		{
			bAddSimple.setEnabled(false);
			bAddWithStops.setEnabled(false);
		}

		pbtns.add(bAddSimple);
		pbtns.add(bAddWithStops);
		pbtns.add(bTmpValidateDB);
		pbtns.add(bAddDone);
		pbtns.add(bAddCancel);
		pbtns.add(new JLabel());  // end of middle row
		pbtns.add(new JLabel());  // start of bottom row; before v0.9.80, was "Change Vehicle..." button
		pbtns.add(bVehicles);
		pbtns.add(bDrivers);
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
		else if (src == bVehicles)
			new VehicleListDialog(conn, isReadOnly, lbef);
		else if (src == bDrivers)
			new DriverListDialog(conn, isReadOnly, lbef);
		else if (src == bTmpValidateDB)
			actionValidateDB();
	}

	private void actionLoadPrevious()
	{
		// TODO chk mdata.ltm.addMode; btn should be disabled if addMode true, though
		if (! mdata.ltm.addEarlierTrips(conn))
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
		canChangeVehicle = false;
		jcbVehicles.setEnabled(false);
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
		jcbVehicles.setEnabled(true);
		canChangeVehicle = true;
	}

	/**
	 * Read all active/inactive vehicles from database and show the dialog to choose one. 
	 * @param isActive  True to show active vehicles, false to show inactive
	 */
	private void actionChangeVehicle(final boolean isActive)
	{
		Vehicle[] allV = Vehicle.getAll
			(conn, Vehicle.FLAG_WITH_OTHER |
			    ((isActive) ? Vehicle.FLAG_ONLY_ACTIVE : Vehicle.FLAG_ONLY_INACTIVE));
		if (allV.length < 2)
		{
			// TODO allow add new veh
			JOptionPane.showMessageDialog(lbef,
			    "This is the only vehicle in the logbook.",
			    "No other vehicles",
			    JOptionPane.INFORMATION_MESSAGE);
			return;  // <--- Early return: Nothing to change ---
		}

		// show the chooser for vehicles
		new VehicleChooserDialog(allV, isActive, veh.getID());
	}

	/** Validate the DB consistency with {@link RDBVerifier#verify(int)}, and show a passed/failed message box. */
	public void actionValidateDB()
	{
		RDBVerifier verif = new RDBVerifier(conn);
		final int vResult = verif.verify(RDBVerifier.LEVEL_TDATA);
		verif.release();
		String optionPaneMsg;
		int optionPaneLevel;
		if (vResult == 0)
		{
			optionPaneMsg = "Validation complete, no problems found.";
			optionPaneLevel = JOptionPane.INFORMATION_MESSAGE;
		} else {
			final int n = verif.failedItems.size();

			optionPaneMsg = "Validation failed (in level " + vResult + ")"
				+ ((n > 0) ? " for " + n + " item(s)" : "")
				+ ". See console output.";
			optionPaneLevel = JOptionPane.ERROR_MESSAGE;

			// TODO show in GUI as copyable text
			System.err.println("--- db validation failures: ---");
			for (RDBVerifier.FailedItem i : verif.failedItems)
			{
				if (i.failedRelData != null)
					System.err.print("For " + i.failedRelData.getClassAndID() + ": ");
				if (i.data != null)
					System.err.print(i.data.getClassAndID() + ": ");
				System.err.print(i.desc);
				if (i.id != 0)
					System.err.print(" (id " + i.id + ")");
				System.err.println();
			}
			System.err.println("-------------------------------");
		}

		JOptionPane.showMessageDialog(lbef,
			optionPaneMsg,
		    "Validation results",
		    optionPaneLevel);
	}

	/**
	 * Update the Vehicle Info and {@link #jcbVehicles} dropdown for a new current vehicle,
	 * including label for active/inactive vehicle list.
	 * Does not set or use {@link #veh}, only {@code newV}.
	 * @param newV  New vehicle for info row; may be active or inactive. Not null.
	 * @param isInitialSetup  If true, calling for initial setup;
	 *     won't remove Panel as listener before changing contents, but will add afterwards.
	 * @since 0.9.80
	 * @throws NullPointerException if {@code newV} is null
	 */
	private void updateVehicleInfoRow(final Vehicle newV, final boolean isInitialSetup)
		throws NullPointerException
	{
		final boolean isActive = newV.isActive();

		lblVehicles.setText( ((isActive) ? "Active" : "Inactive") + " vehicles: ");

		if (! isInitialSetup)
			jcbVehicles.removeItemListener(this);

		Vehicle[] vlist = Vehicle.getAll
			(conn, Vehicle.FLAG_WITH_OTHER |
			    ((isActive) ? Vehicle.FLAG_ONLY_ACTIVE : Vehicle.FLAG_ONLY_INACTIVE));
		if (vlist == null)
			vlist = new Vehicle[] { Vehicle.OTHER_VEHICLE };

		int selIndex = -1;
		jcbVehicles.removeAllItems();
		for (int i = 0; i < vlist.length; ++i)
		{
			jcbVehicles.addItem(vlist[i]);
			if (newV.getID() == vlist[i].getID())
				selIndex = i;
		}
		if (selIndex != -1)
			jcbVehicles.setSelectedIndex(selIndex);
		else if (vlist[vlist.length - 1] == Vehicle.OTHER_VEHICLE)
			jcbVehicles.setSelectedItem(vlist[vlist.length - 1]);

		jcbVehicles.addItemListener(this);  // only after done making changes
	}

	/**
	 * Handle change of vehicle in top-of-panel dropdown,
	 * including "Other..." item which brings up {@link VehicleChooserDialog}
	 * showing inactive/active vehicles.
	 * @since 0.9.80
	 */
	public void itemStateChanged(ItemEvent e)
	{
		if (! (e.getStateChange() == ItemEvent.SELECTED))
			return;
		Object o = e.getItem();
		if (! (o instanceof Vehicle))
			return;

		if (! canChangeVehicle)
		{
			JOptionPane.showMessageDialog(lbef,
			    "Cannot change vehicles right now: Finish editing the trip first.",
			    "Cannot change now",
			    JOptionPane.INFORMATION_MESSAGE);

			return;
		}

		Vehicle v = (Vehicle) o;
		if (v != Vehicle.OTHER_VEHICLE)
		{
			final int id = v.getID();
			if (id == veh.getID())
				return;
			showVehicleTrips(id);  // callback to LogbookEditPane
		} else {
			// "Other Vehicle...": Show dialog to switch browsing active/inactive vehicles
			actionChangeVehicle(! veh.isActive());
		}
	}

	/**
	 * Show this vehicle's trips. Callback from VehicleChooserDialog or {@link #jcbVehicles} dropdown.
	 * Does nothing if same ID as currently shown vehicle, or if can't change vehicle (during trip editing).
	 */
	public void showVehicleTrips(final int vID)
	{
		if (vID == veh.getID())
			return;  // nothing to do
		if (! canChangeVehicle)
			return;  // not allowed to change right now

		try {
			mdata.fireTableRowsDeleted(1, mdata.getRowCount());
			veh = new Vehicle(conn, vID);
			mdata = new LBSwingTableModel(veh, WEEK_INCREMENT, conn, isReadOnly);
			// setModel and loads the current data from mdata,
			// but loses the column model.
			tb.setModel(mdata);
			setupTbColumnModel();
			bLoadPrevious.setEnabled(true);  // in case disabled because prev vehicle had no earlier trips
			updateVehicleInfoRow(veh, false);
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
	 * Looks for and opens {@code test.sqlite}, or filename on command line, in the current directory.
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

		setupFromMain(fname, fname, f, false, false);  // TODO extract fnshort
	}

	/**
	 * Check the structure and schema of the db file selected by the {@link Main} panel's "Open" or "View Backup"
	 * button, ask to upgrade if needed, then create and show a new {@link LogbookEditPane} with that db.
	 *<P>
	 * Does it need an upgrade to current version?  If so, upgrade a temp copy if backup,
	 * or ask first if not a backup.  Similar logic, with different APIs, is in
	 * {@code org.shadowlands.roadtrip.android.BackupsMain.onItemClick(...)}
	 * and {@code android.BackupsRestore.copyAndUpgradeTempFile()}.
	 *
	 * @param fname  Full path of DB file to open; should not currently be open
	 * @param fnshort  Filename (not full path) to display in window
	 * @param parentf  Parent frame, for error dialogs if needed.
	 *     For responsiveness, assumes that when {@code setupFromMain} is called, {@code parentf} is showing
	 *     {@link Cursor#WAIT_CURSOR}. If this method asks whether to upgrade, it will reset to
	 *     {@link Cursor#DEFAULT_CURSOR} while the asking dialog is showing, then back to
	 *     {@code WAIT_CURSOR} for the upgrade process.
	 * @param isBackup  Is this a backup file (treat as read-only, and copy before upgrade if needed)?
	 * @param isReadOnly  Is this db read-only (no edits allowed)?
	 */
	public static void setupFromMain
		(String fname, final String fnshort, final JFrame parentf, boolean isBackup, boolean isReadOnly)
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
			return;  // <--- Early return: Major problem trying to open it ----
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

				Throwable t = e.getCause();  // print underlying SQLException instead, if any
				if (! (t instanceof SQLException))
					t = e;
				System.err.println
					("DB pragma integrity_check failed: " + t.getClass() + " " + t.getMessage());
				System.err.println();
				for (int max = 3; max > 0; --max)
				{
					Throwable tc = t.getCause();
					if ((tc != null) && ! tc.equals(t))
					{
						t = tc;
						System.err.println("Caused by: " + t.getClass() + " " + t.getMessage());
						System.err.println();
					} else {
						break;
					}
				}
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
					return;  // <--- Early return: Major problem reading this db ---
				} else {
					isReadOnly = true;
				}
			}
		}

		try
		{
			AppInfo aivers = new AppInfo(conn, AppInfo.KEY_DB_CURRENT_SCHEMAVERSION);
			System.out.println("AppInfo: DB schemaversion: " + aivers.getValue());
		} catch (RDBKeyNotFoundException e)
		{
			System.err.println("Not found: " + e.keyString);
			JOptionPane.showMessageDialog(parentf,
			    "An important system record is missing in the database.",
			    "Missing required version record",
			    JOptionPane.ERROR_MESSAGE);
			conn.close();
			return;  // <--- Early return: Version unknown, can't use this db ---
		}

		// Does it need an upgrade?
		final int user_version = ((RDBJDBCAdapter) conn).getSchemaVersion();
		System.out.println("user_version is " + user_version + " (current: " + RDBSchema.DATABASE_VERSION + ")");

		// Quick check for too old or too new
		if (user_version != RDBSchema.DATABASE_VERSION)
		{
			String versMsg = null;
			if (user_version < RDBSchema.DB_VERSION_MIN_UPGRADE)
				versMsg = "It's an early beta version " + user_version + " too old to open.";
			else if (user_version > RDBSchema.DATABASE_VERSION)
				versMsg = "Its schema version " + user_version
					+ " is too new, this program uses version " + RDBSchema.DATABASE_VERSION
					+ ".\nPlease use a newer version of Shadowlands Roadtrip BookEdit.";

			if (versMsg != null)
			{
				conn.close();
				JOptionPane.showMessageDialog(parentf,
					"Cannot use this database:\n" + versMsg,
					"Database version can't be opened",
					JOptionPane.ERROR_MESSAGE);
				return;  // <--- Early return: Too old or too new ---
			}
		}

		if (user_version < RDBSchema.DATABASE_VERSION)
		{
			if (isBackup)
			{
				conn.close();
				File upgCopy = upgradeDBCopy(new File(fname), user_version, parentf);
				if (upgCopy == null)
				{
					return;  // <--- Early return: Error copying or upgrading ---
				}
				fname = upgCopy.getAbsolutePath();

				try {
					conn = new RDBJDBCAdapter(fname);
				} catch (Exception e) {
					JOptionPane.showMessageDialog(parentf,
						"Could not copy and open this database.\nError was: "
							+ e.getClass() + " " + e.getMessage(),
						"Could not open SQLite db",
						JOptionPane.ERROR_MESSAGE);

					return;  // <--- Early return: Error opening copy ---
				}
			} else {
				// ask whether to upgrade temp copy (changes won't be saved) or in place, or cancel

				if (parentf != null)
					// reset cursor from WAIT_CURSOR while asking this,
					// so it doesn't look like parent frame has frozen
					parentf.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

				final String[] upg_exit = { "Upgrade", "Read-only", "Cancel" };
				final int choice = JOptionPane.showOptionDialog(parentf,
					"To use this database, it must be upgraded from older schema version "
						+ user_version + " to the current version "
						+ RDBSchema.DATABASE_VERSION + ".\n"
						+ " Instead of upgrading, would you prefer a read-only view?\n"
						+ " (A temporary copy will be upgraded.)",
					"DB Upgrade Required",
					JOptionPane.DEFAULT_OPTION,
					JOptionPane.QUESTION_MESSAGE,
					null, upg_exit, upg_exit[1]);

				if (parentf != null)
					parentf.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

				switch (choice)
				{
				case 0:  // upgrade in place
					try
					{
						RDBSchema.upgradeToCurrent(conn, user_version, false);
					} catch (Exception e) {
						// TODO capture it somewhere gui-accessible?
						e.printStackTrace();
						try {
							conn.close();
						} catch (Throwable th) {}

						JOptionPane.showMessageDialog(parentf,
						    "An error occurred during the upgrade:\n"
							+ e.toString() + "\n" + e.getMessage()
							+ "\nTo see the detailed stack trace, please run BookEdit from a command line and try again.",
						    "Error during upgrade",
						    JOptionPane.ERROR_MESSAGE);

						return;  // <--- Early return: Error upgrading ---
					}
					break;

				case 1:  // make and open read-only copy to upgrade
					conn.close();
					isBackup = true;
					// if an error occurs, upgradeDBCopy will show a message dialog
					File upgCopy = upgradeDBCopy(new File(fname), user_version, parentf);
					if (upgCopy == null)
					{
						return;  // <--- Early return: Error copying or upgrading ---
					}
					fname = upgCopy.getAbsolutePath();
					try {
						conn = new RDBJDBCAdapter(fname);
					} catch (Exception e) {
						JOptionPane.showMessageDialog(parentf,
							"Could not copy and open this database.\nError was: "
								+ e.getClass() + " " + e.getMessage(),
							"Could not open SQLite db",
							JOptionPane.ERROR_MESSAGE);

						return;  // <--- Early return: Error opening copy ---
					}
					break;

				case 2:  // cancel
					conn.close();
					return;  // <--- Early return: User canceled ---
				}
			}
		}

		//
		// CURRENT_DRIVER
		//
		Person currD = null;
		boolean newDriver = true;

		{
			Vehicle currV = Settings.getCurrentVehicle(conn, false);
			if (currV != null)
			{
				currD = VehSettings.getCurrentDriver(conn, currV, false);
				if (currD != null)
					newDriver = false;
			}
		}

		try
		{
			if (currD == null)
			{
				System.err.println("Could not find CURRENT_DRIVER");
				System.err.println("Continuing without current driver.");					

				String noDriverMsg = null;

				// for backup viewing, just pick any driver
				if (isBackup)
				{
					final Person[] allDrivers = Person.getAll(conn, true);
					if (allDrivers != null)
					{
						currD = allDrivers[allDrivers.length - 1];
						newDriver = false;
					} else {
						noDriverMsg = "Cannot open this backup, its vehicles have no drivers.";
					}
				} else {
					noDriverMsg = "Please enter information about the vehicle's driver.";
				}

				if (noDriverMsg != null)
				    JOptionPane.showMessageDialog
					(parentf, noDriverMsg, "No driver found", JOptionPane.WARNING_MESSAGE);
			}

			// Create driver info if missing. TODO: Eventually make this part of 'new logbook' functionality
			if (newDriver)
				currD = MiscTablesCRUDDialogs.createEditPersonDialog
				    (parentf, conn, isBackup || isReadOnly, currD, true);

			if (currD == null)
			{				
				System.err.println("Cancelled.");
				try {
					conn.close();
				} catch (Throwable th) {}

				return;
			}
		} catch (Throwable t)
		{
			System.err.println("Could not read/create/write the current driver");
			System.err.println("Error was: " + t.getClass() + " " + t.getMessage());
			t.printStackTrace();
			try {
				conn.close();
			} catch (Throwable th) {}

			JOptionPane.showMessageDialog
			    (parentf,
			     "Could not work with vehicle driver data.\nError was: "
				+ t.getClass() + " " + t.getMessage(),
			     null, JOptionPane.ERROR_MESSAGE);

			return;
		}

		//
		// CURRENT_VEHICLE
		//
		Vehicle cveh;
		{
			boolean newVehicle = true;
			cveh = Settings.getCurrentVehicle(conn, false);
			if (cveh != null)
				newVehicle = false;

			try
			{
				if (cveh == null)
				{
					System.err.println("Could not find CURRENT_VEHICLE");
					System.err.println("Continuing without current vehicle.");

					String noVehMsg = null;

					// for backup viewing, just pick any vehicle
					if (isBackup)
					{
						final Vehicle[] allV = Vehicle.getAll(conn, 0);
						if (allV != null)
						{
							cveh = allV[allV.length - 1];
							newVehicle = false;
						} else {
							noVehMsg = "Cannot open this backup, it contains no vehicles.";
						}
					} else {
						noVehMsg = "Please enter information about the vehicle.";
					}

					if (noVehMsg != null)
						JOptionPane.showMessageDialog
						    (parentf, noVehMsg, "No vehicle found", JOptionPane.WARNING_MESSAGE);
				}

				if (newVehicle)
				{
					cveh = MiscTablesCRUDDialogs.createEditVehicleDialog
						(parentf, conn, isBackup || isReadOnly, cveh, currD);
					if (cveh == null)
					{
						System.err.println("Cancelled.");
						try {
							conn.close();
						} catch (Throwable th) {}

						return;
					}

					if (! isReadOnly)
						Settings.setCurrentVehicle(conn, cveh);
				}
			} catch (Throwable t)
			{
				System.err.println("Could not read/create/write the current vehicle");
				System.err.println("Error was: " + t.getClass() + " " + t.getMessage());
				t.printStackTrace();
				try {
					conn.close();
				} catch (Throwable th) {}

				JOptionPane.showMessageDialog
				    (parentf,
				     "Could not work with vehicle data.\nError was: "
					+ t.getClass() + " " + t.getMessage(),
				     null, JOptionPane.ERROR_MESSAGE);

				return;
			}
		}  // CURRENT_VEHICLE

		// Now that we have driver and vehicle, set current driver if missing
		if (newDriver && ! isBackup)
			VehSettings.setCurrentDriver(conn, cveh, currD);

		new LogbookEditPane(fnshort, cveh, conn, isBackup || isReadOnly);

		// When pane closes, that will call conn.close() .
	}

	/**
	 * Make a temporary copy of a db file, then upgrade the copy's schema.
	 * @param sourceBkupFile  DB file to copy and upgrade; should not currently be open
	 * @param sourceSchemaVers  Source schema version, from {@link AppInfo} table
	 *    where {@code aifield =} '{@link AppInfo#KEY_DB_CURRENT_SCHEMAVERSION DB_CURRENT_SCHEMAVERSION}'
	 * @param parentf  Parent frame, for error dialogs if needed
	 * @return Closed File for temporary copy, or null if an error occurred and a message dialog was shown
	 * @since 0.9.40
	 */
	private static File upgradeDBCopy(File sourceBkupFile, final int sourceSchemaVers, final JFrame parentf)
	{
		File destTempFile = null;
		String errMsg = null;
		try
		{
			destTempFile = File.createTempFile("tmpdb-", ".upg");
			try { destTempFile.deleteOnExit(); }
			catch (Exception e) {}

			System.err.println
				("Calling upgradeCopyToCurrent(\"" + destTempFile.getAbsolutePath() + "\", " + sourceSchemaVers + ")");

			RDBSchema.upgradeCopyToCurrent(sourceBkupFile, destTempFile, sourceSchemaVers, rdbUpgSingleton);

			System.err.println("Completed upgrade");
			return destTempFile;

		} catch (DataFormatException e) {
			errMsg = "Error in file validation.";
		} catch (IllegalStateException e) {
			errMsg = "This backup file is an early beta version " + sourceSchemaVers + " too old to restore.";
		} catch (Exception e) {
			// SQLException, IOException
	    		System.err.println("Failed during copy & validation: " + e);
			errMsg = "Error in file copy and validation: " + e;
		}

		JOptionPane.showMessageDialog(parentf,
		    "Cannot open the backup: " + errMsg,
		    "Error upgrading copy of database",
		    JOptionPane.ERROR_MESSAGE);

		return null;
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

	/**
	 * Interactive {@link LogbookTableModel} adapter, with logbook trip data in {@link #ltm}, for our Swing GUI.
	 *<P>
	 * The GUI has column headings, so this adapter clears the {@link LogbookTableModel#render_comments_brackets}
	 * rendering flag.
	 */
	private static class LBSwingTableModel extends AbstractTableModel implements TableChangeListener
	{
		public LogbookTableModel ltm;
		private final boolean isReadOnly;

		static
		{
			LogbookTableModel.render_comments_brackets = false;
		}

		/**
    	 * Create and populate with existing data.
    	 * @param veh  Vehicle
    	 * @param weeks  Week increment when loading data
    	 * @param conn Add existing rows from this connection, via addRowsFromTrips.
    	 */
    	public LBSwingTableModel(Vehicle veh, final int weeks, RDBAdapter conn, final boolean isReadOnly)
    	{
    		this.isReadOnly = isReadOnly;
    		ltm = new LogbookTableModel(veh, weeks, null, conn);
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
		/**
		 * Are the vehicles in the dialog all active or inactive?
		 * @since 0.9.41
		 */
		private final boolean isActive;
		private final int currVID;
		private JComboBox dropdown;

		/**
		 * Create and show the VehicleChooserDialog.
		 * @param vlist  List of active or inactive vehicles, from {@link Vehicle#getAll(RDBAdapter, int)}.
		 *     Should include {@link Vehicle#OTHER_VEHICLE} to change dropdown contents to the other
		 *     (inactive or active) vehicles.
		 * @param isActive  Whether {@code vlist} entries are all active or inactive
		 * @param currV  Currently displayed vehicle, which may or may not be in this list
		 */
		public VehicleChooserDialog(Vehicle[] vlist, final boolean isActive, final int currV)
		{
			this.isActive = isActive;
			this.currVID = currV;
			setLayout(new FlowLayout());
			add(new JLabel((isActive)
				? "Choose an active vehicle to display."
				: "Choose an inactive vehicle to display."));
			dropdown = new JComboBox(vlist);
			dropdown.setEditable(false);
			boolean selAny = false;
			for (int i = vlist.length - 1; i>=0; --i)
			{
				if (currV == vlist[i].getID())
				{
					dropdown.setSelectedItem(vlist[i]);
					selAny = true;
					break;
				}
			}
			if ((! selAny) && (vlist[vlist.length - 1] == Vehicle.OTHER_VEHICLE))
				dropdown.setSelectedItem(vlist[vlist.length - 1]);
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
			if (v != Vehicle.OTHER_VEHICLE)
			{
				final int id = v.getID();
				if (currVID == id)
					return;
				setVisible(false);
				showVehicleTrips(id);  // callback to LogbookEditPane
			} else {
				setVisible(false);
				actionChangeVehicle(! isActive);
			}
			dispose();
		}

		/** handle 'cancel' button click */
		public void actionPerformed(ActionEvent e)
		{
			dispose();
		}

	}  // inner class VehicleChooserDialog


}  // public class LogbookEditPane
