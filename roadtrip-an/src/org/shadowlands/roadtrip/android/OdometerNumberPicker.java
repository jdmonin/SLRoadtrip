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

package org.shadowlands.roadtrip.android;

import org.shadowlands.roadtrip.R;

import com.quietlycoding.android.picker.NumberPicker;
import com.quietlycoding.android.picker.NumberPicker.OnChangedListener;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;

/**
 * Odometer number picker (2 fields: whole number, dot, tenths).
 * The whole-number field goes up to 9.9 million (9999999) miles or kilometers.
 *<P>
 * Optionally, a checkbox can be checked when the user changes the value.
 * Use {@link #setCheckboxOnChanges(CheckBox)}.
 *
 * @author jdmonin
 */
public class OdometerNumberPicker extends LinearLayout implements OnChangedListener
{
	/**
	 * Whole-number maximum value (not including 10ths).
	 */
	public static final int RANGE_MAX_WHOLE = 9999999;

	/** The integer part of the odometer */
	private NumberPicker mWholeNum;

    /** The tenths part of the odometer */
    private OdometerNumberPickerTenths mTenths;

    /** when the user changes mWholeNum or mTenths, check this box. */
    private CheckBox checkOnChanges;

    /** if false, {@link #mTenths} has been hidden */
    private boolean tenthsVisible = true;

    /**
     * if {@link #tenthsVisible} true, and {@link #changeCurrentWhole(int)} is called,
     * have we already set tenths value to 0?
     */
    private boolean tenthsCleared = false;

    /**
     * adj this on user changes, if not null.
     * @see #relatedCheckOnChanges
     */
    private OdometerNumberPicker relatedChangeOnChanges;

    /** if non-null, and checked, don't adj relatedChangeOnChanges */
    private CheckBox relatedCheckOnChanges;

    // TODO javadoc
    public OdometerNumberPicker(Context context) {
        this(context, null, 0);
    }

    // TODO javadoc
    public OdometerNumberPicker(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    // TODO javadoc
    public OdometerNumberPicker(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.odometer_number_picker, this);
        // mHandler = new Handler();
        // InputFilter inputFilter = new NumberPickerInputFilter();
        // mNumberInputFilter = new NumberRangeKeyListener();
        try
        {
        	mWholeNum = (NumberPicker) findViewById(R.id.org_shadowlands_rtr_odometer_wholenum);
        }
        catch (ClassCastException e)
        {
        	// Happens in eclipse layout editor. (2010-08-22)
        	// java.lang.ClassCastException: com.quietlycoding.android.picker.NumberPicker cannot be cast to com.quietlycoding.android.picker.NumberPicker
        	// at org.shadowlands.roadtrip.android.OdometerNumberPicker.<init>(OdometerNumberPicker.java:72)
        	// at org.shadowlands.roadtrip.android.OdometerNumberPicker.<init>(OdometerNumberPicker.java:59)
        }
        if (mWholeNum != null)
        {
	        // May be null in eclipse Layout Editor.
	        mWholeNum.setRange(0, RANGE_MAX_WHOLE);  // expand the range
	        mWholeNum.setCurrent(0);
	        mWholeNum.setOnChangeListener(this);
        }
        try
        {
        	mTenths = (OdometerNumberPickerTenths) findViewById(R.id.org_shadowlands_rtr_odometer_tenths);
        }
        catch (ClassCastException e) { }
        if (mTenths != null)
        {
	        mTenths.setWholePicker(mWholeNum);
	        mTenths.setCurrent(0);
	        mTenths.setOnChangeListener(this);
        }

        if (!isEnabled()) {
            setEnabled(false);
        }

    }

    /**
     * TODO javadoc...
     * @return current value, as fixed-decimal 10ths of a unit
     */
    public int getCurrent10d()
    {
    	int whole = mWholeNum.getCurrent();
    	int tenths = mTenths.getCurrent();  // even if not tenthsVisible
    	return (whole * 10) + tenths;
    }

    /**
     * Set the current value, and optionally make it the minimum.
     * @param newValue  New value in 10ths of a unit
     * @param setMinimumToo  Also make this the minimum;
     *    note that only the whole-number field gets this minimum,
     *    the 10ths field ignores it.
     *    That is, setting the minimum to 1234 is the same to 1230.
     * @see #changeCurrentTenths(int)
     * @see #changeCurrentWhole(int)
     */
    public void setCurrent10d(final int newValue, final boolean setMinimumToo)
    {
    	mTenths.setCurrent((int) (newValue % 10));
    	int whole = (int) (newValue / 10);
    	if (setMinimumToo)
    		mWholeNum.setRange(whole, RANGE_MAX_WHOLE);
    	mWholeNum.setCurrent(whole);
    }

    /**
     * Change our whole-number part by this amount.
     * If {@link #setTenthsVisibility(boolean) setTenthsVisibility(false)}, clear tenths to 0.
     * @param wholeChange amount to increase (positive) or decrease (negative).
     */
    public void changeCurrentWhole(final int wholeChange)
    {
    	mWholeNum.setCurrent(mWholeNum.getCurrent() + wholeChange);
    	if (! (tenthsVisible || tenthsCleared))
    	{
    		mTenths.setCurrent(0);
    		tenthsCleared = true;
    	}
    }

    /**
     * Change our tenths part by this amount.
     * If the tenths would wrap around, also change the whole,
     * so that the entire odometer changes by the correct amount.
     * @param tenthsChange amount to increase (positive) or decrease (negative).
     * @see #setCurrent10d(int, boolean)
     */
    public void changeCurrentTenths(final int tenthsChange)
    {
    	final int oldTenths = mTenths.getCurrent();
    	final int newTenths = oldTenths + tenthsChange;

    	if ((newTenths >= 0) && (newTenths <= 9))
    	{
    		mTenths.setCurrent(newTenths);
    	} else {
    		final int newEntire
    		    = (mWholeNum.getCurrent() * 10)
    		    + oldTenths + tenthsChange;
    		setCurrent10d(newEntire, false);
    	}
    }

    /**
     * Set a related odometer's value; that value can be changed when this one changes.
     * If relatedCB is not null, only change <tt>related</tt> if it's not checked.
     *
     * @param related  The related odometer, or null
     * @param relatedCB  The related odometer's checkbox, or null
     * @see #setCurrent10d(int, boolean)
     */
    public void setRelatedUncheckedOdoOnChanges
        (OdometerNumberPicker related, CheckBox relatedCB)
    {
    	relatedChangeOnChanges = related;
    	relatedCheckOnChanges = relatedCB;
    }

    /** When the user changes the value, check this checkbox. */
    public void setCheckboxOnChanges(CheckBox cb)
    {
    	checkOnChanges = cb;
    }

    /**
     * Callback from our whole or tenths NumberPicker: the user has changed the value.
     * Potentially check our related checkbox.
     * Potentially adjust related odometer, depending on related odo's checkbox.
     * @see #setCheckboxOnChanges(CheckBox)
     * @see #setRelatedUncheckedOdoOnChanges(OdometerNumberPicker, CheckBox)
     */
	public void onChanged(NumberPicker picker, int oldVal, int newVal)
	{
		if (checkOnChanges == null)
			return;
		checkOnChanges.setChecked(true);

		if ((relatedChangeOnChanges == null)
		    || ((relatedCheckOnChanges != null) && relatedCheckOnChanges.isChecked()))
			return;

		final int delta = newVal - oldVal;
		if (picker == mWholeNum)
			relatedChangeOnChanges.changeCurrentWhole(delta);
		else
			relatedChangeOnChanges.changeCurrentTenths(delta);
	}

	/**
	 * Show or hide the tenths portion; will use {@link View#VISIBLE} or {@link View#GONE}.
	 * Hiding the tenths keeps its value, but that value can't be changed by the user.
	 */
	public void setTenthsVisibility(final boolean newVis)
	{
		if (tenthsVisible == newVis)
			return;
		tenthsVisible = newVis;
		tenthsCleared = false;
		final int vis = newVis ? View.VISIBLE : View.GONE;
		mTenths.setVisibility(vis);
		View dot = (View) findViewById(R.id.org_shadowlands_rtr_odometer_dot);
		if (dot != null)
			dot.setVisibility(vis);
	}
}
