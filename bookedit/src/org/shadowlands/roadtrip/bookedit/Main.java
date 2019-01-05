/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010,2012,2014-2017,2019 Jeremy D Monin <jdmonin@nand.net>
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

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;

import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.RDBSchema;

/** Main startup for BookEdit. Prompts whether to use a database or a backup file, etc. */
public class Main
{
	/**
	 * Application version code; changes with each point release.
	 * Same format as {@link RDBSchema#DATABASE_VERSION}, but multiple releases may have the same db version.
	 * @see #APP_VERSION_STRING
	 * @since 0.9.07
	 */
	public static final int APP_VERSION_CODE = 962;

	/**
	 * Application version as a human-readable string; changes with each point release.
	 * @see #APP_VERSION_CODE
	 * @since 0.9.07
	 */
	public static final String APP_VERSION_STRING = "0.9.62";

	/**
	 * For GUI adjustments as needed, detect if we're running on Mac OS X.
	 * To identify osx from within java, see technote TN2110:
	 * http://developer.apple.com/technotes/tn2002/tn2110.html
	 * @since 0.9.43
	 */
	public static final boolean isJavaOnOSX =
		System.getProperty("os.name").toLowerCase().contains("os x");

	private String dbFilename = null;
	private StartupChoiceFrame scf;
	private RDBAdapter conn = null;
	private boolean isReadOnly = false;

	/**
	 * If there's a database file on the command line, try to open it.
	 * Otherwise, bring up the startup buttons. 
	 * @param args
	 */
	public static void main(String[] args)
	{
		try
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {}

		// TODO look for a filename
		// TODO allow option for open bkup, etc, with cmdline flags
		Main m = new Main();
		m.initAndShow();
	}

	private void initAndShow()
	{
		scf = new StartupChoiceFrame();
		scf.pack();
		scf.setVisible(true);
	}

	/**
	 * Open this file in a {@link LogbookEditPane}.
	 * @param chooseFile  File chosen by the "New", "Open", or "View Backup" button, or any other File
	 * @param isNew  Should this db be created empty?
	 * @param isBak  Is this a backup file (treat as read-only)?
	 */
	public void openLogbook(File chooseFile, final boolean isNew, final boolean isBak)
	{
		if (chooseFile == null)
			return;

		if (isBak)
		{
			isReadOnly = true;
		} else {
			try {
				isReadOnly = ! chooseFile.canWrite();
			} catch (SecurityException se) {
				isReadOnly = true;
			}
		}

		// TODO handle isNew (create schema, etc)
		dbFilename = chooseFile.getAbsolutePath();

		// Responsiveness: show "busy" mouse cursor while retrieving data
		scf.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

		LogbookEditPane.setupFromMain(dbFilename, chooseFile.getName(), scf, isBak, isReadOnly);

		// At this point the data is visible, or an error message was shown
		scf.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
	}

	/** Gives buttons with choice of new, open, open backup, exit */
	@SuppressWarnings("serial")
	private class StartupChoiceFrame extends JFrame
		implements ActionListener
	{
		private JPanel btns;
		private JButton bNew, bOpen, bOpenBackup, bExit;

		/**
		 * {@link Preferences} key in {@link #userPrefs} for directory of the most recently opened logbook.
		 * @since 0.9.43
		 */
		private final static String PREV_OPENED_DIR = "prevOpenedDir";

		/**
		 * Persistently stored user preferences between runs, such as {@link #PREV_OPENED_DIR}.
		 * Windows stores these under HKCU in the registry, OSX/Unix under the home directory.
		 * @since 0.9.43
		 */
		private Preferences userPrefs;

		/**
		 * Current directory from opening a file with {@link JFileChooser}, or null if no file chosen yet.
		 * Used when calling {@link #chooseFile(boolean, boolean)} again.
		 * Stored between runs at {@link #userPrefs}({@link #PREV_OPENED_DIR})
		 * using {@link #tryStorePrevOpenedDir()}.
		 */
		private File prevFileOpenDir;

		public StartupChoiceFrame()
		{
			super("BookEdit: Choose File or Backup");
			btns = new JPanel();
			btns.setLayout(new BoxLayout(btns, BoxLayout.PAGE_AXIS));
			btns.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));

			btns.add(new JLabel("Welcome to BookEdit. Please choose:"));
			bNew = addBtn("New...", KeyEvent.VK_N);
			bOpen = addBtn("Open...", KeyEvent.VK_O);
			bOpenBackup = addBtn("View Backup...", KeyEvent.VK_V);
			bExit = addBtn("Exit", KeyEvent.VK_X);
			btns.add(new JLabel("Version " + APP_VERSION_STRING
					+ ", database schema version " + RDBSchema.DATABASE_VERSION));

			getContentPane().add(btns);
			getRootPane().setDefaultButton(bOpen);

			userPrefs = Preferences.userNodeForPackage(Main.class);
			tryGetPrevOpenedDir();
		}

		/**
		 * Add this button to the layout.
		 * @param label Button's label
		 * @param vkN  Shortcut mnemonic from {@link KeyEvent}
		 * @return the new button
		 */
		private JButton addBtn(final String label, final int vkN)
		{
			JButton b = new JButton(label);
			b.setMnemonic(vkN);
			btns.add(b);
			b.addActionListener(this);
			Dimension size = b.getPreferredSize();
			size.width = Short.MAX_VALUE;
			b.setMaximumSize(size);
			return b;
		}

		/** Handle button clicks. */
		public void actionPerformed(ActionEvent e)
		{
			// First, sanity check: This dialog shouldn't be visible if
			// there's already a database open.
			if (conn != null)
			{
				return; 
			}

			// React to button click.
			Object src = e.getSource();
			if (src == bOpenBackup)
			{
				openLogbook(chooseFile(true, true), false, true);
			}
			else if (src == bOpen)
			{
				openLogbook(chooseFile(true, false), false, false);
			}
			else if (src == bNew)
			{
				JOptionPane.showMessageDialog
					(this,
					 "Not yet implemented.\nUse the android app to create new logbooks for now.",
					 null, JOptionPane.INFORMATION_MESSAGE);
				// TODO implement it
			}
			else if (src == bExit)
			{
				System.exit(0);
			}
		}

		/**
		 * Show a {@link JFileChooser} to browse and Open or Save a logbook file.
		 * @param notNew  True for Open existing, false for Save new file
		 * @param forBackups  True to open logbook read-only, false to open for editing
		 * @return the chosen file, or null if nothing was chosen
		 */
		private File chooseFile(final boolean notNew, final boolean forBackups)
		{
			// TODO respect forBackups
			// TODO filtering: setFileFilter, addChoosableFileFilter, etc
			final JFileChooser fc = new JFileChooser();
			if (prevFileOpenDir != null)
				fc.setCurrentDirectory(prevFileOpenDir);

			int returnVal;
			if (notNew)
				returnVal = fc.showOpenDialog(this);
			else
				returnVal = fc.showSaveDialog(this);
			if (returnVal != JFileChooser.APPROVE_OPTION)
				return null;

			File file = fc.getSelectedFile();
			System.out.println("file path: " + file.getAbsolutePath());

			prevFileOpenDir = fc.getCurrentDirectory();  // for next time
			tryStorePrevOpenedDir();

			return file;
		}

		/**
		 * Read from persistent {@link #userPrefs} and change 'current' directory field
		 * ({@link #prevFileOpenDir}) to that of the most recently opened logbook if possible.
		 * Catches and ignores exceptions; directory may have moved, etc.
		 * @see #tryStorePrevOpenedDir()
		 */
		private void tryGetPrevOpenedDir()
		{
			prevFileOpenDir = null;

			try
			{
				if (userPrefs == null)
					return;  // unlikely, just in case

				final String dir = userPrefs.get(PREV_OPENED_DIR, null);
				if (dir == null)
					return;

				final File fdir = new File(dir);
				if (fdir.exists() && fdir.isDirectory())
					prevFileOpenDir = fdir;

			} catch (RuntimeException e) {
				// ignore SecurityException, IllegalStateException
			}
		}

		/**
		 * Store the current directory {@link #prevFileOpenDir} to persistent {@link #userPrefs} preferences.
		 * Catches and ignores exceptions, because this field is stored only for convenience.
		 * @see #tryGetPrevOpenedDir()
		 */
		private void tryStorePrevOpenedDir()
		{
			if ((prevFileOpenDir == null) || (userPrefs == null))
				return;

			try
			{
				userPrefs.put(PREV_OPENED_DIR, prevFileOpenDir.getAbsolutePath());
				userPrefs.flush();
			}
			catch (BackingStoreException ex) {}
			catch (SecurityException se) {}
		}

	}  // inner class StartupChoiceFrame

}
