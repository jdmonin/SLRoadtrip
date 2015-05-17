/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010,2015 Jeremy D Monin <jdmonin@nand.net>
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
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;  // for VK_*
import java.awt.event.KeyListener;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Date;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import org.shadowlands.roadtrip.util.RTRDateTimeFormatter;

/**
 * A modal {@link JDialog} to prompt for multiple string inputs,
 * with Continue and Cancel buttons.
 *<P>
 * After construction, call {@link #showAndWait()}, which will block until Continue or Cancel is pressed.
 * If true is returned, call {@link #isChanged()} and {@link #getInputs()} to get the entered data.
 *<P>
 * Closing the dialog is equivalent to hitting the Cancel button.
 *<P>
 * In Read-Only mode there is no Continue button, only Close which behaves like Cancel.
 *
 * @author jdmonin
 */
@SuppressWarnings("serial")
public class MultiInputDialog
    extends JDialog
    implements ActionListener, KeyListener
{

	// Dialog input/display field types, for formatted viewing and editing. @since 0.9.43

	/** String field type */
	public static final int F_STRING = 1;

	/** Integer field type */
	public static final int F_INT = 2;

	/** Boolean field type: In string array, "Y" is true (case-insensitive) and any other value or null is false. */
	public static final int F_BOOL = 3;

	/** Odometer field type: Decimal is shifted 1 place to precisely store 10ths within integer field */
	public static final int F_ODOMETER = 4;

	/** Timestamp field type: Same format as db schema;
	 *  UTC seconds since the unix epoch, like {@link System#currentTimeMillis()} / 1000L.
	 */
	public static final int F_TIMESTAMP = 5;

	/** Field type flags mask; all flag bits are within this mask. */
	public static final int F_FLAGS_MASK    = 0xFF000000;

	/** Field flag: Read only */
	public static final int F_FLAG_REQUIRED = 0x80000000;

	/**
	 * Field type for each element of {@link #inputs},
	 * such as {@link #F_STRING} or {@link #F_TIMESTAMP},
	 * including flag bits such as {@link #F_FLAG_REQUIRED}.
	 * @since 0.9.43
	 */
	private final int[] ftypes;

	/**
	 * Field name labels, as passed to constructor.
	 * @since 0.9.43
	 */
	private final String[] fnames;

	/**
	 * Initially set from constructor's passed-in <tt>vals[]</tt>.
	 * Read from input textfields during {@link #clickOK()}.
	 * Any null elements represent 0-length strings.
	 * Each element here is raw, not formatted; field type map is {@link #ftypes}[].
	 */
	private String[] inputs;

	/**
	 * Are this dialog's fields read-only?
	 * If so, {@link #inputTexts}[] elements are null, {@link #ok} is null.
	 * @since 0.9.43
	 */
	private final boolean isReadOnly;

	/**
	 * False until end of {@link #clickOK()}.
	 * Always false if {@link #isReadOnly}.
	 */
	private boolean inputsOK;

	/**
	 * False until a field content is changed during {@link #clickOK()}.
	 * @see #isReadOnly
	 */
	private boolean anyChanges;

	/** OK/Continue button, null if {@link #isReadOnly} */
	private JButton ok;

	/** Cancel button, Close if {@link #isReadOnly} */
	private JButton cancel;

	/** Input textfields; elements are null if {@link #isReadOnly} */
	private JTextField[] inputTexts;

	/**
	 * Our date and time format, for {@link FieldType#F_TIMESTAMP}.
	 * May be null; initialized in constructor.
	 * @since 0.9.43
	 */
	private transient RTRDateTimeFormatter dtf;

	/**
	 * Display a modal {@link JDialog} to prompt for multiple string inputs,
	 * with Continue and Cancel buttons.  See the class javadoc for more details.
	 *
	 * @param owner  Frame owning this dialog
	 * @param title  Dialog title
	 * @param prompt  Prompt text
	 * @param fieldnames Field names to display on-screen.  This array shouldn't be changed after calling;
	 *     a reference to the passed-in array is held, the contents aren't copied.
	 * @param fieldtypes Field type for each field.
	 *     Each element is the field type for an element of {@link #inputs},
	 *     such as {@link #F_STRING} or {@link #F_TIMESTAMP},
	 *     including flag bits such as {@link #F_FLAG_REQUIRED}.
	 *     This array shouldn't be changed after calling; a reference to the passed-in array is held,
	 *     the contents aren't copied.
	 * @param vals  Initial values for each field, or null; may contain nulls.
	 *              Note that no reference to <tt>vals</tt> is kept in this object,
	 *              although its string contents may be copied.
	 * @param isReadOnly  If true, show the fields as read-only; dialog has Close button and no OK/Continue button
	 * @throws HeadlessException  if GraphicsEnvironment.isHeadless()
	 * @throws IllegalArgumentException if fieldnames.length < 1,
	 *        or if vals != null &amp;&amp; vals.length != fieldnames.length,
	 *        or if fieldtypes is null or fieldnames.length != fieldtypes.length.
	 */
    public MultiInputDialog
        (JFrame owner, final String title, final String prompt,
         final String[] fieldnames, final int[] fieldtypes, final String[] vals, final boolean isReadOnly)
        throws HeadlessException, IllegalArgumentException
    {
    	super(owner, title, true);  // modal == true
    	setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    	if (fieldnames.length < 1)
    		throw new IllegalArgumentException("fieldnames.length");
    	if ((vals != null) && (vals.length != fieldnames.length))
    		throw new IllegalArgumentException("vals.length");
    	if ((fieldtypes == null) || (fieldtypes.length != fieldnames.length))
    		throw new IllegalArgumentException("fieldtypes");

    	fnames = fieldnames;
    	ftypes = fieldtypes;
    	inputs = new String[fieldnames.length];
    	if (vals != null)
    	{
    		for (int i = 0; i < vals.length; ++i)
    			if ((vals[i] != null) && (vals[i].length() > 0))
    				inputs[i] = vals[i];
    	}
    	this.isReadOnly = isReadOnly;
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
	if (isReadOnly)
		gbc.insets = new Insets(3, 6, 3, 6);  // padding around JLabels in GBL, since not using JTextFields
	final char decimalSep = new DecimalFormat().getDecimalFormatSymbols().getDecimalSeparator();
		// for odometers; java 1.5, android API 8 don't have DecimalFormatSymbols.getInstance()

    	inputTexts = new JTextField[fieldnames.length];
    	for (int i = 0; i < fieldnames.length; ++i)
    	{
    		String val = (vals != null) ? vals[i] : null;
    		final JComponent valComp;
		if (val != null)
		{
			// This is simple basic transformation for development versions; not user-friendly yet.

			switch (ftypes[i] & ~F_FLAGS_MASK)
			{
			case F_ODOMETER:
				final int L = val.length();
				StringBuilder sb = new StringBuilder();
				if (L < 2)
				{
					sb.append('0');
					sb.append(decimalSep);
					if (L == 1)
						sb.append(val);
					else
						sb.append('0');
				} else {
					sb.append(val.subSequence(0, L-1));
					sb.append(decimalSep);
					sb.append(val.charAt(L-1));
				}
				val = sb.toString();
				break;

			case F_TIMESTAMP:
				if (dtf == null)
					dtf = new RTRDateTimeFormatter();
				final long tstamp = 1000L * Long.parseLong(val);
				if (tstamp == 0)
					val = "";
				else
					val = dtf.formatDateTime(tstamp);
				break;
			}
		}

    		if (! isReadOnly)
    		{
    			JTextField tf = new JTextField(20);
    			if (val != null)
    				tf.setText(val);
    			tf.addKeyListener(this);     // for ESC/ENTER
    			inputTexts[i] = tf;
    			valComp = tf;
    		} else {
    			valComp = (val != null) ? new JLabel(val) : new JLabel();
    		}

    		txt = new JLabel(fieldnames[i], SwingConstants.TRAILING);  // right-aligned
    		txt.setLabelFor(valComp);
            gbc.gridwidth = 1;
            gbl.setConstraints(txt, gbc);
    		bp.add(txt);

    		gbc.gridwidth = GridBagConstraints.REMAINDER;
    		gbl.setConstraints(valComp, gbc);
    		bp.add(valComp);
    	}

        /**
         * Interface setup: Buttons
         */
	if (isReadOnly)
	{
		cancel = new JButton("Close");
		cancel.addActionListener(this);
		cancel.setMnemonic(KeyEvent.VK_ESCAPE);

		gbc.gridx = 1;  // skip left cell
		gbc.anchor = GridBagConstraints.LINE_END;  // right-justify the single button
	} else {
		ok = new JButton("Continue");
		ok.addActionListener(this);
		ok.setMnemonic(KeyEvent.VK_O);
		ok.setToolTipText("Accept these values and continue");

		cancel = new JButton("Cancel");
		cancel.addActionListener(this);
		cancel.setMnemonic(KeyEvent.VK_ESCAPE);
	}
	gbc.gridwidth = 1;

	if (ok != null)
	{
		gbl.setConstraints(ok, gbc);
		bp.add(ok);
	}

        gbl.setConstraints(cancel, gbc);
        bp.add(cancel);
	cancel.registerKeyboardAction
		(this, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        /**
         * Final assembly setup
         */
        getRootPane().setDefaultButton(isReadOnly ? cancel : ok);
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
	 * Read data fields, set {@link #isChanged()} and {@link #inputsAreOK()}. Dispose if no problems.
	 */
	private void clickOK()
	{
		boolean allEmpty = true;

		/** Details if a field has problems; if you set this, set problemFld too. */
		String problemMsg = null;
		/** index within {@link #inputTexts}[] of field with problem, or -1 if none */
		int problemFld = -1;
		final char decimalSep = new DecimalFormat().getDecimalFormatSymbols().getDecimalSeparator();
			// for odometers; java 1.5, android API 8 don't have DecimalFormatSymbols.getInstance()

		for (int i = 0; i < inputTexts.length; ++i)
		{
			String it = inputTexts[i].getText().trim();
			final int L = it.length();
			if (L > 0)
			{
				allEmpty = false;

				// Parse any formatted fields, take others with their current value.
				// This is simple basic transformation for development versions; not user-friendly yet.

				switch (ftypes[i] & ~F_FLAGS_MASK)
				{
				case F_INT:
					try {
						Integer.parseInt(it);
					} catch (NumberFormatException e) {
						problemFld = i;
						problemMsg = "Cannot read this number field.";
					}
					break;

				case F_ODOMETER:
					// expecting a whole number, or at most 1 digit after the decimal.
					// To keep precision use string manipulation, not floats.
					try {
						int p = it.lastIndexOf(decimalSep);
						if (p == -1)
						{
							p = L;
						} else if (p < (L-2)) {
							problemFld = i;
							problemMsg =
							  "Odometer must have at most 1 digit after the decimal.";
						}
						int odo = 0;
						if (p > 0)
							odo = 10 * Integer.parseInt(it.substring(0, p));
						if (p == (L-2))
							odo += Integer.parseInt(it.substring(p+1));
						it = Integer.toString(odo);
					} catch (NumberFormatException e) {
						problemFld = i;
						problemMsg = "Cannot read this odometer field.";
					}
					break;

				case F_TIMESTAMP:
					try {
						if (dtf == null)
							dtf = new RTRDateTimeFormatter();
						Date dt = dtf.parseDateTime(it);
						it = Long.toString(dt.getTime() / 1000L);
					} catch (ParseException e) {
						problemFld = i;
						problemMsg = "Cannot read this date/time field.";
					}
					break;
				}

				// now compare to the original input
				if ((problemMsg == null) && ! it.equals(inputs[i]))
				{
					inputs[i] = it;
					anyChanges = true;
				}
			}
			else
			{
				if (0 != (ftypes[i] & F_FLAG_REQUIRED))
				{
					problemFld = i;
					problemMsg = "This field is required.";
				} else {
					if (inputs[i] != null)
						anyChanges = true;
					inputs[i] = null;
				}
			}
		}

		if (problemMsg != null)
		{
			inputTexts[problemFld].requestFocusInWindow();
			JOptionPane.showMessageDialog
				(getOwner(), fnames[problemFld] + ": " + problemMsg, null, JOptionPane.ERROR_MESSAGE);
			return;  // <---- Problems found ----
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
		if (e.getSource() == cancel)
			clickCancel();
		else if ((ok != null) && (e.getSource() == ok))
			clickOK();
	}

    /** Handle Enter or Esc key (KeyListener) */
    public void keyPressed(KeyEvent e)
    {
        if (e.isConsumed())
            return;

        switch (e.getKeyCode())
        {
        case KeyEvent.VK_ENTER:
            if (! isReadOnly)
        	clickOK();
            e.consume();
            break;

        case KeyEvent.VK_CANCEL:
        case KeyEvent.VK_ESCAPE:
            clickCancel();
            e.consume();
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
