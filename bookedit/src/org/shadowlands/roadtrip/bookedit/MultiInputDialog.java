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

package org.shadowlands.roadtrip.bookedit;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;  // for VK_*
import java.awt.event.KeyListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/**
 * A modal {@link JDialog} to prompt for multiple string inputs,
 * with Continue and Cancel buttons.
 *<P>
 * After construction, call {@link #showAndWait()}, which will block until Continue or Cancel is pressed.
 * If true is returned, call {@link #isChanged()} and {@link #getInputs()} to get the entered data.
 *<P>
 * Closing the dialog is equivalent to hitting the Cancel button.
 *
 * @author jdmonin
 */
@SuppressWarnings("serial")
public class MultiInputDialog
    extends JDialog
    implements ActionListener, KeyListener
{

	/**
	 * Initially set from constructor's passed-in <tt>vals[]</tt>.
	 * Read from input textfields during {@link #clickOK()}. 
	 * Any null elements represent 0-length strings.
	 */
	private String[] inputs;

	/**
	 * False until end of {@link #clickOK()}.
	 */
	private boolean inputsOK;

	/**
	 * False until a field content is changed during {@link #clickOK()}.
	 */
	private boolean anyChanges;

	private JButton ok, cancel;
	private JTextField[] inputTexts;

	/**
	 * Display a modal {@link JDialog} to prompt for multiple string inputs,
	 * with Continue and Cancel buttons.  See the class javadoc for more details.
	 *
	 * @param owner  Frame owning this dialog
	 * @param title  Dialog title
	 * @param prompt  Prompt text
	 * @param fieldnames Field names to display on-screen.
	 * @param vals  Initial values for each field, or null; may contain nulls.
	 *              Note that no reference to <tt>vals</tt> is kept in this object,
	 *              although its string contents may be copied.
	 * @throws HeadlessException  if GraphicsEnvironment.isHeadless()
	 * @throws IllegalArgumentException if fieldnames.length < 1,
	 *        or if vals != null && vals.length != fieldnames.length.
	 */
    public MultiInputDialog
        (JFrame owner, final String title, final String prompt, final String[] fieldnames, final String[] vals)
        throws HeadlessException, IllegalArgumentException
    {
    	super(owner, title, true);  // modal == true
    	setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    	if (fieldnames.length < 1)
    		throw new IllegalArgumentException("fieldnames.length");
    	if ((vals != null) && (vals.length != fieldnames.length))
    		throw new IllegalArgumentException("vals.length");
    	inputs = new String[fieldnames.length];
    	if (vals != null)
    	{
    		for (int i = 0; i < vals.length; ++i)
    			if ((vals[i] != null) && (vals[i].length() > 0))
    				inputs[i] = vals[i];
    	}
    	inputsOK = false;
    	anyChanges = false;
    	createAndPackLayout(prompt, fieldnames, vals);
    }

    /** Did the user hit Continue? (not Cancel or Close Window) */
    public boolean inputsAreOK() { return inputsOK; }

    /** Did the user change the content of any input? */
    public boolean isChanged() { return anyChanges; }

    /**
     * The values entered by the user, if the Continue button was pushed.
     *<P>
     * <b>Note:</b> The user's text is trimmed, and if its length is 0,
     * the array element will be <b>null</b>, not a 0-length string.
     *
     * @return The input values if {@link #inputsAreOK()},
     *     in the same order as field names passed to constructor,
     *     or array of nulls otherwise.
     */
    public String[] getInputs() { return inputs; }

    /**
     * Create the components and do layout.
     * @param prompt Text to display above fields, or null
     * @param fieldnames Field name strings; one field is created per fieldname.
     * @param vals  Initial values per field, or nulls, same as constructor
     */
    private void createAndPackLayout(final String prompt, final String[] fieldnames, final String[] vals)
    {
    	JLabel txt;  // this variable is reused during loops here 

    	GridBagLayout gbl = new GridBagLayout();
    	Container bp = getContentPane();  // button panel (TODO) name
    	bp.setLayout(gbl);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;

    	/**
    	 * Interface setup: Prompt (if any)
    	 */
    	if (prompt != null)
    	{
            gbc.gridwidth = GridBagConstraints.REMAINDER;
    		txt = new JLabel(prompt);
            gbl.setConstraints(txt, gbc);
            bp.add(txt);
        }

        /**
         * Interface setup: Input rows
         */
    	inputTexts = new JTextField[fieldnames.length];
    	for (int i = 0; i < fieldnames.length; ++i)
    	{
    		JTextField tf = new JTextField(20); 
    		if ((vals != null) && (vals[i] != null))
    			tf.setText(vals[i]);
    		tf.addKeyListener(this);     // for ESC/ENTER
    		inputTexts[i] = tf;

    		txt = new JLabel(fieldnames[i], SwingConstants.TRAILING);  // right-aligned
    		txt.setLabelFor(inputTexts[i]);
            gbc.gridwidth = 1;
            gbl.setConstraints(txt, gbc);
    		bp.add(txt);

    		gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbl.setConstraints(inputTexts[i], gbc);
    		bp.add(inputTexts[i]);
    	}

        /**
         * Interface setup: Buttons
         */
    	ok = new JButton("Continue");
        ok.addActionListener(this);
    	ok.setMnemonic(KeyEvent.VK_O);
    	ok.setToolTipText("Accept these values and continue");

    	cancel = new JButton("Cancel");
        cancel.addActionListener(this);
    	cancel.setMnemonic(KeyEvent.VK_ESCAPE);

        gbc.gridwidth = 1;

        gbl.setConstraints(ok, gbc);
        bp.add(ok);

        gbl.setConstraints(cancel, gbc);
        bp.add(cancel);

        /**
         * Final assembly setup
         */
    	getRootPane().setDefaultButton(ok);
        bp.validate();
        pack();
    }

    /**
     * Show the dialog (modal) and wait for the user to type their inputs
     * and hit the "Continue" button or the "Cancel" button.
     * Closing the dialog is treated as Cancel.
     * @return true if "Continue" button was pressed, false otherwise
     */
    @SuppressWarnings("deprecation")  // show()
	public boolean showAndWait()
    {
    	super.show();  // blocks (modal)
    	return inputsOK;
    }

	private void clickCancel()
	{
		inputsOK = false;
		dispose();
	}

	/**
	 * Read data fields, set {@link #isChanged()} and {@link #inputsAreOK()}. Dispose.
	 */
	private void clickOK()
	{
		boolean allEmpty = true;
		for (int i = 0; i < inputTexts.length; ++i)
		{
			String it = inputTexts[i].getText().trim();
			if (it.length() > 0)
			{
				allEmpty = false;
				if (! it.equals(inputs[i]))
				{
					inputs[i] = it;
					anyChanges = true;
				}
			}
			else
			{
				if (inputs[i] != null)
					anyChanges = true;
				inputs[i] = null;
			}
		}
		if (allEmpty)
		{
			inputsOK = false;
			return;  // <---- Ignore the OK button for now ----
		}
		inputsOK = true;
		dispose();
	}

    /**
     * React when a button is clicked.
     */
	public void actionPerformed(ActionEvent e)
	{
		if (e.getSource() == ok)
			clickOK();
		else if (e.getSource() == cancel)
			clickCancel();
	}

    /** Handle Enter or Esc key (KeyListener) */
    public void keyPressed(KeyEvent e)
    {
        if (e.isConsumed())
            return;

        switch (e.getKeyCode())
        {
        case KeyEvent.VK_ENTER:
            clickOK();
            break;

        case KeyEvent.VK_CANCEL:
        case KeyEvent.VK_ESCAPE:
            clickCancel();
            break;
        }  // switch(e)
    }

	/** stub for KeyListener */
	public void keyReleased(KeyEvent e) { }

	/** stub for KeyListener */
	public void keyTyped(KeyEvent e) { }

	/** uncomment for unit testing.

	public static void main(String[] args)
	{
		JFrame f = new JFrame("owner of dialog");
		f.setSize(400, 200);
		f.pack();
		f.setVisible(true);

		final String[] fieldnames = { "first", "second", "third" };
		MultiInputDialog d = new MultiInputDialog
		  (f, "dialog title", "Enter some values.", fieldnames, null);
		boolean isOK = d.showAndWait();
		System.out.println("Dialog returned " + isOK);
		if (isOK)
		{
			String[] ia = d.getInputs(); 
			for (int i = 0; i < ia.length; ++i)
				System.out.println("inputs[" + i + "] = " + ia[i]);
		}

		f.dispose();

	}  // main() for unit testing
	*/

}  // public class MultiInputDialog
