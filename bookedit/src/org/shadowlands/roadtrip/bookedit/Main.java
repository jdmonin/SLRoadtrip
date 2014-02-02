/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010,2012,2014 Jeremy D Monin <jdmonin@nand.net>
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

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

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
	public static final int APP_VERSION_CODE = 940;

	/**
	 * Application version as a human-readable string; changes with each point release.
	 * @see #APP_VERSION_CODE
	 * @since 0.9.07
	 */
	public static final String APP_VERSION_STRING = "0.9.40";

	private String dbFilename = null;
	private StartupChoiceFrame scf;
	private RDBAdapter conn = null;
	private boolean isBackup = false;  // TODO upgrade temp copy when opened
	private boolean isReadOnly = false;

	/**
	 * If there's a database file on the command line, try to open it.
	 * Otherwise, bring up the startup buttons. 
	 * @param args
	 */
	public static void main(String[] args)
	{
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

	/** Open this file in a {@link LogbookEditPane}. */
	public void openLogbook(File chooseFile, final boolean isNew, final boolean isBak)
	{
		if (chooseFile == null)
			return;

		isBackup = isBak;
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
		// STATE here: Does it need upgrade?
		//  If not, set dbFilename and go.
		//  Otherwise, ask and/or copy first.
		final String fpath = chooseFile.getAbsolutePath();
		dbFilename = fpath;
		LogbookEditPane.setupFromMain(fpath, chooseFile.getName(), scf, isReadOnly);
	}

	/** Gives buttons with choice of new, open, open backup, exit */
	@SuppressWarnings("serial")
	private class StartupChoiceFrame extends JFrame
		implements ActionListener
	{
		private JPanel btns;
		private JButton bNew, bOpen, bOpenBackup, bExit;

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
			else if (src == bExit)
			{
				System.exit(0);
			}
			// TODO deal with other buttons
		}

		/** return the chosen file, or null if nothing was chosen */
		private File chooseFile(final boolean notNew, final boolean forBackups)
		{
			// TODO respect forBackups
			// TODO filtering: setFileFilter, addChoosableFileFilter, etc
			final JFileChooser fc = new JFileChooser();
			int returnVal;
			if (notNew)
				returnVal = fc.showOpenDialog(this);
			else
				returnVal = fc.showSaveDialog(this);
			if (returnVal != JFileChooser.APPROVE_OPTION)
				return null;
			File file = fc.getSelectedFile();
			System.out.println("file path: " + file.getAbsolutePath());
			return file;
		}

	}  // inner class StartupChoiceFrame

}
