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

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Window;  // for javadoc
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;  // for javadoc
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.shadowlands.roadtrip.db.RDBAdapter;

/**
 * Parent class for the modal Driver and Vehicle list dialogs.
 *<P>
 * Subclasses must implement {@link #getAll()}, {@link #showAdd()}, and {@link #showEdit(Object)}.
 * @since 0.9.43
 */
@SuppressWarnings("serial")
public abstract class ItemListDialog
	extends JDialog
	implements ActionListener
{
	/**
	 * Key to associate {@link #items} data with entries in {@link #jpItemList}
	 * using {@link JComponent#putClientProperty(Object, Object)}
	 * / {@link JComponent#getClientProperty(Object)}.
	 */
	private static final String OBJDATA = "obj";

	/** Is the data in this dialog read-only, or can items be added, edited, etc? */
	protected final boolean isReadOnly;

	/**
	 * The open database holding these items; may be read-only, see {@link #isReadOnly}.
	 * The {@code ItemListDialog} class doesn't use the db field at all,
	 * it's here just as a convenience to subclasses which use the database.
	 */
	protected final RDBAdapter db;

	/** Owner passed to the constructor */
	protected final JFrame owner;

	/**
	 * GUI elements holding {@link #items}.  Item data is associated with these
	 * using 'client properties', see {@link #OBJDATA} javadoc.
	 */
	private final JPanel jpItemList;

	/**
	 * Data items shown in {@link #jpItemList}, or null if none.
	 * Filled by constructor posting a Runnable that calls {@link #getAll()}.
	 */
	private List<Object> items;

	/** Button to add an item, or null if {@link #isReadOnly}. */
	private final JButton btnAdd;

	/** Button to close the dialog */
	private final JButton btnClose;

	/**
	 * Create and show an {@link ItemListDialog} for a specific type of data handled by the subclass.
	 * The list is queried, populated, and made visible in a {@link SwingUtilities#invokeLater(Runnable)}
	 * callback, to avoid throwing db exceptions within the constructor.
	 * @param db  An open logbook database
	 * @param isReadOnly  True if {@code db} should be treated as read-only
	 * @param owner  Parent window
	 * @param title  Window title, or null to use {@code objPlural}
	 * @param objName  Object type name, used in "Add Driver" button
	 * @param objPlural  Object type plural name, used for "Drivers in this logbook:" label
	 */
	protected ItemListDialog
		(final RDBAdapter db, final boolean isReadOnly, final JFrame owner,
		 final String title, final String objName, final String objPlural)
	{
		super(owner, (title != null) ? title : objPlural, true);
		this.db = db;
		this.isReadOnly = isReadOnly;
		this.owner = owner;

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());

		final JLabel lblTop = new JLabel(objPlural + " in this logbook, click for details:");
		lblTop.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		add(lblTop, BorderLayout.NORTH);

		jpItemList = new JPanel();
		jpItemList.setLayout(new GridLayout(0, 1, 0, 3));
			// use GridLayout to stretch to full width; vertical padding 3
		jpItemList.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		add(new JScrollPane(jpItemList), BorderLayout.CENTER);

		// Buttons under the list
		JPanel bottomBtns = new JPanel();
		if (isReadOnly) {
			btnAdd = null;
		} else {
			btnAdd = new JButton("Add " + objName);
			btnAdd.addActionListener(this);
			bottomBtns.add(btnAdd);
		}
		btnClose = new JButton("Close");
		btnClose.addActionListener(this);
		bottomBtns.add(btnClose);

		add(bottomBtns, BorderLayout.SOUTH);

		SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				final Object[] all;
				try
				{
					all = getAll();
				} catch (Exception ex) {
					JOptionPane.showMessageDialog
						(owner,
						 "Could not load " + objName + " data.\n\nError was: "
						 + ex.getClass() + " " + ex.getMessage(),
						 null, JOptionPane.ERROR_MESSAGE);
					return;
				}

				if (all != null)
				{
					items = new ArrayList<Object>();
					final int L = all.length;
					for (int i = 0; i < L; ++i)
						addItem(all[i]);
				}

				pack();
				setLocationRelativeTo(owner);  // center within owner frame
				setVisible(true);
			}
		});
	}

	/**
	 * Add a new item button to the end of the displayed list, and add the item data to {@link #items}.
	 * Does not call {@link Window#pack() pack()} in case of multiple calls to add several items;
	 * be sure to call {@link Window#pack() pack()} after calling this method.
	 *<P>
	 * If {@link #items} is null, this method creates it.
	 * Like AWT and Swing, this method is not thread-safe.
	 *
	 * @param itm  Item to add, or null to do nothing.  The item's name in the list
	 *     is its {@link Object#toString() itm.toString()}.
	 */
	protected void addItem(final Object itm)
	{
		if (itm == null)
			return;

		if (items == null)
			items = new ArrayList<Object>();
		items.add(itm);

		JButton jbItm = new JButton(itm.toString());
		jbItm.setBorderPainted(false);  // for a cleaner look
		jbItm.setHorizontalAlignment(SwingConstants.LEADING);
			// align text left like label, not centered like button
		jbItm.putClientProperty(OBJDATA, itm);
		jbItm.addActionListener(ItemListDialog.this);

		jpItemList.add(jbItm);
	}

	/** Handle button press events, including clicks on list items. */
	public void actionPerformed(final ActionEvent e)
	{
		final Object src = e.getSource();

		if (src == null)
			return;
		else if (src == btnClose)
			dispose();
		else if (src == btnAdd)
		{
			final Object newObj = showAdd();
			if (newObj != null)
			{
				addItem(newObj);
				// TODO -- re-sort or re-query list?
				pack();
			}
		}
		else if (src instanceof JButton)
		{
			final Object obj = ((JButton) src).getClientProperty(OBJDATA);
			if (obj != null)
				if (showEdit(obj))
					((JButton) src).setText(obj.toString());  // TODO re-sort list?
		}
	}

	/**
	 * Query the database to get all objects.
	 * The item names will be displayed using their {@link Object#toString()}.
	 * Item edit requests (clicks) will pass these objects to {@link #showEdit(Object)}.
	 *<P>
	 * This returns an array because most of the db table class {@code getAll()}s return arrays.
	 *<P>
	 * If an exception occurs, throw it; {@code ItemListDialog} will catch the exception
	 * and display an error dialog.
	 * @return  All objects of this type currently in the db, or null if none (not an empty list)
	 */
	public abstract Object[] getAll();

	/**
	 * The 'Add' button was clicked; show GUI to add an item. Called from the AWT event thread.
	 * @return The object created, or null if the add was cancelled.
	 *    If db-based, the object should already be committed to the db when returned.
	 */
	public abstract Object showAdd();

	/**
	 * An item name was clicked; show GUI to edit the item. Called from the AWT event thread.
	 * @param item  The object to edit, from {@link #getAll()} or {@link #showAdd()}
	 * @return true if {@code item} was changed and its display entry should be refreshed
	 *    because {@link Object#toString() item.toString()} changed
	 */
	public abstract boolean showEdit(final Object item);

}