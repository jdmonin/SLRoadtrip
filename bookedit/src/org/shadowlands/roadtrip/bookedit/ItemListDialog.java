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
import java.awt.Toolkit;
import java.awt.Window;  // for javadoc
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;  // for javadoc
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.shadowlands.roadtrip.db.RDBAdapter;

/**
 * Parent class for the modal Driver and Vehicle list dialogs.
 *<P>
 * Subclasses must implement {@link #getAll()}, {@link #showAdd()}, and {@link #showEdit(Object)}.
 *<P>
 * If a subclass sets the {@link #hasActiveFlag} field when calling the constructor,
 * the subclass should also override {@link #isItemActive(Object)}.
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
	 * @see #ACTCOMP
	 */
	private static final String OBJDATA = "obj";

	/**
	 * Key to associate {@link #jpItemList} entries with their "active" indicator component.
	 * Used only if {@link #hasActiveFlag}.
	 * @see #OBJDATA
	 */
	private static final String ACTCOMP = "actc";

	/**
	 * Does this data type have an "isActive" flag that should be displayed?
	 * @see #isItemActive(Object)
	 */
	protected final boolean hasActiveFlag;

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
	 *<P>
	 * Each item in the list has a {@link JButton}; that's also where {@link #OBJDATA} is connected.
	 * If {@link #hasActiveFlag}, each item also has a {@link JLabel} displaying either
	 * {@link #imgActive} or {@link #imgInactive} to its left, linked from the JButton via
	 * {@link #ACTCOMP}.
	 */
	private final JPanel jpItemList;

	/**
	 * Data items shown in {@link #jpItemList}, or null if none.
	 * Filled by constructor posting a Runnable that calls {@link #getAll()}.
	 * {@code inactiveItems} is null unless {@link #hasActiveFlag} is true.
	 */
	private List<Object> activeItems, inactiveItems;

	/** Button to add an item, or null if {@link #isReadOnly}. */
	private final JButton btnAdd;

	/** Button to close the dialog */
	private final JButton btnClose;

	/** Item isActive indicators, if needed by {@link #hasActiveFlag}. */
	private ImageIcon imgActive, imgInactive;

	/**
	 * Create and show an {@link ItemListDialog} for a specific type of data handled by the subclass.
	 * The list is queried, populated, and made visible in a {@link SwingUtilities#invokeLater(Runnable)}
	 * callback, to avoid throwing db exceptions within the constructor.
	 * @param db  An open logbook database
	 * @param isReadOnly  True if {@code db} should be treated as read-only
	 * @param owner  Parent window
	 * @param title  Window title, or null to use {@code objPlural}
	 * @param hasActiveFlag  True if object type has an isActive flag that should be displayed
	 * @param objName  Object type name, used in "Add Driver" button
	 * @param objPlural  Object type plural name, used for "Drivers in this logbook:" label
	 */
	protected ItemListDialog
		(final RDBAdapter db, final boolean isReadOnly, final JFrame owner,
		 final String title, final boolean hasActiveFlag, final String objName, final String objPlural)
	{
		super(owner, (title != null) ? title : objPlural, true);
		this.db = db;
		this.isReadOnly = isReadOnly;
		this.hasActiveFlag = hasActiveFlag;
		this.owner = owner;

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());

		final JLabel lblTop = new JLabel(objPlural + " in this logbook, click for details:");
		lblTop.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		add(lblTop, BorderLayout.NORTH);

		activeItems = new ArrayList<Object>();
		if (hasActiveFlag)
			inactiveItems = new ArrayList<Object>();

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
			// bind button to OSX Command-A, windows Alt-A
			btnAdd.setMnemonic(KeyEvent.VK_A);  // I18N?
			btnAdd.registerKeyboardAction
				(this, KeyStroke.getKeyStroke
					(KeyEvent.VK_A, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()),
				 JComponent.WHEN_IN_FOCUSED_WINDOW);
			bottomBtns.add(btnAdd);
		}

		btnClose = new JButton("Close");
		btnClose.addActionListener(this);
		btnClose.registerKeyboardAction
			(this, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
		bottomBtns.add(btnClose);

		add(bottomBtns, BorderLayout.SOUTH);
		getRootPane().setDefaultButton(btnClose);

		SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				final Object[] all;
				try
				{
					if (hasActiveFlag)
					{
						URL imageURL = ItemListDialog.class.getResource("img/round-gray16.png");
						if (imageURL != null)
							imgInactive = new ImageIcon(imageURL);
						imageURL = ItemListDialog.class.getResource("img/round-green16.png");
						if (imageURL != null)
							imgActive = new ImageIcon(imageURL);
					}

					all = getAll();  // <-- db query performed here in subclass --
				} catch (Exception ex) {
					JOptionPane.showMessageDialog
						(owner,
						 "Could not load " + objName + " data.\n\nError was: "
						 + ex.getClass() + " " + ex.getMessage(),
						 null, JOptionPane.ERROR_MESSAGE);
					ex.printStackTrace();
					return;
				}

				if (all != null)
				{
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
	 * Add a new item button to the end of the displayed list, and add the item data to {@link #activeItems}
	 * or {@link #inactiveItems}.
	 * Does not call {@link Window#pack() pack()} in case of multiple calls to add several items;
	 * be sure to call {@link Window#pack() pack()} after calling this method.
	 *<P>
	 * Like AWT and Swing, this method is not thread-safe.
	 *
	 * @param itm  Item to add, or null to do nothing.  The item's name in the list
	 *     is its {@link Object#toString() itm.toString()}.
	 */
	protected void addItem(final Object itm)
	{
		if (itm == null)
			return;

		final boolean isActive = (hasActiveFlag) ? isItemActive(itm) : true;
		if (isActive)
			activeItems.add(itm);
		else
			inactiveItems.add(itm);

		JButton jbItm = new JButton(itm.toString());
		jbItm.setBorderPainted(false);  // for a cleaner look
		jbItm.setHorizontalAlignment(SwingConstants.LEADING);
			// align text left like label, not centered like button
		jbItm.putClientProperty(OBJDATA, itm);
		jbItm.addActionListener(ItemListDialog.this);

		final JComponent listAddComp;
		if (! hasActiveFlag)
		{
			listAddComp = jbItm;
		} else {
			// More complex layout: Panel with active/inactive icon, then the button.
			listAddComp = new JPanel(new BorderLayout());
			JLabel jlIcon;
			if (isActive)
			{
				jlIcon = (imgActive != null) ? new JLabel(imgActive) : new JLabel("Active");
				jlIcon.setToolTipText("Active");
			} else {
				jlIcon = (imgInactive != null) ? new JLabel(imgInactive) : new JLabel("Inactive");
				jlIcon.setToolTipText("Inactive");
			}

			jbItm.putClientProperty(ACTCOMP, jlIcon);
			listAddComp.add(jlIcon, BorderLayout.LINE_START);
			listAddComp.add(jbItm, BorderLayout.CENTER);  // stretch JButton to full available width
		}

		jpItemList.add(listAddComp);
	}

	/**
	 * Update the displayed item's name and active indicator.
	 * If {@code activeChanged} and {@link #hasActiveFlag}, checks status to update the inactive/active icon
	 * and updates {@link #activeItems} and {@link #inactiveItems}.
	 *<P>
	 * Like AWT and Swing, this method is not thread-safe.
	 *
	 * @param itm  List item data
	 * @param itmComp  Component showing the item name
	 * @param activeChanged  True if {@code itm}'s isActive flag changed
	 */
	protected void updateItem(final Object itm, final JButton itmComp, final boolean activeChanged)
	{
		itmComp.setText(itm.toString());

		if (! hasActiveFlag)
			return;

		final boolean isActive = isItemActive(itm);
		if (isActive)
		{
			inactiveItems.remove(itm);
			activeItems.add(itm);
		} else {
			activeItems.remove(itm);
			inactiveItems.add(itm);
		}

		JLabel jlIcon = (JLabel) itmComp.getClientProperty(ACTCOMP);
		if (jlIcon == null)
			return;

		jlIcon.setIcon(isActive ? imgActive : imgInactive);  // may be null if loading error
		jlIcon.setToolTipText(isActive ? "Active" : "Inactive");
	}

	/** Handle button press events, including clicks on list items. */
	public void actionPerformed(final ActionEvent e)
	{
		final Object src = e.getSource();

		if (src == null)
			return;
		else if (src == btnClose)
			dispose();

		// assume adding or editing; try/catch in case of problems
		try
		{
			if (src == btnAdd)
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
				if (obj == null)
					return;
				final boolean wasActive = (hasActiveFlag) ? isItemActive(obj) : true;
				if (showEdit(obj))
				{
					final boolean nowActive = (hasActiveFlag) ? isItemActive(obj) : true;
					final boolean activeChanged = (wasActive != nowActive);
					updateItem(obj, (JButton) src, activeChanged);  // TODO re-sort list?
				}
			}
		} catch (Exception ex) {
			JOptionPane.showMessageDialog
				(owner,
				 "An error occurred: "
				 + ex.getClass() + " " + ex.getMessage()
				 + "\n\nFor more techical details, re-run from a command prompt.",
				 null, JOptionPane.ERROR_MESSAGE);
			ex.printStackTrace();
		}
	}

	/**
	 * Query the database to get all objects.
	 * The item names will be displayed using their {@link Object#toString()}.
	 * Item edit requests (clicks) will pass these objects to {@link #showEdit(Object)}.
	 * If {@link #hasActiveFlag} is set, {@link #isItemActive(Object)} will be called for each item returned.
	 *<P>
	 * This returns an array because most of the db table class {@code getAll()}s return arrays.
	 *<P>
	 * If an exception occurs, throw it; {@code ItemListDialog} will catch the exception
	 * and display an error dialog.
	 * @return  All objects of this type currently in the db, or null if none (not an empty list)
	 */
	public abstract Object[] getAll();

	/**
	 * The 'Add' button was clicked; show GUI to add an item.
	 * If {@link #hasActiveFlag} is set, {@link #isItemActive(Object)} will be called on the returned item.
	 * Called from the AWT event thread.
	 * @return The object created, or null if the add was cancelled.
	 *    If db-based, the object should already be committed to the db when returned.
	 */
	public abstract Object showAdd();

	/**
	 * An item name was clicked; show GUI to edit the item.
	 * If {@link #hasActiveFlag} is set, {@link #isItemActive(Object)} will be called on the returned item.
	 * Called from the AWT event thread.
	 * @param item  The object to edit, from {@link #getAll()} or {@link #showAdd()}
	 * @return true if {@code item} was changed and its display entry should be refreshed
	 *    because {@link Object#toString() item.toString()} changed
	 */
	public abstract boolean showEdit(final Object item);

	/**
	 * Get the value of this item's isActive flag.
	 * This default implementation always returns true;
	 * override it in subclasses that use such a flag.
	 * Ignored unless {@link #hasActiveFlag} is true.
	 * @param item  The object to check, from {@link #getAll()} or {@link #showAdd()}
	 * @return  True if the item is flagged as active, false if not.
	 */
	public boolean isItemActive(final Object item)
	{
		return true;
	}

}
