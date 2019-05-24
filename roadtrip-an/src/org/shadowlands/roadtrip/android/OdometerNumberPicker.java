/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2015,2019 Jeremy D Monin <jdmonin@nand.net>
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

import android.widget.NumberPicker;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
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
 *<P>
 * Optionally, another odometer control can be 'related' to this one (a trip and
 * total odometer pair).  Use {@link #setRelatedUncheckedOdoOnChanges(OdometerNumberPicker, CheckBox)}.
 *
 * @author jdmonin
 */
public class OdometerNumberPicker
	extends LinearLayout implements NumberPicker.OnValueChangeListener
{
	/**
	 * Whole-number maximum value (not including 10ths).
	 */
	public static final int RANGE_MAX_WHOLE = 9999999;

    /** The integer part of the odometer */
    private OdometerNumberPickerWhole mWholeNum;

    /** The tenths part of the odometer */
    private OdometerNumberPickerTenths mTenths;

    /** when the user changes mWholeNum or mTenths, check this box. */
    private CheckBox checkOnChanges;

    /**
     * if false, {@link #mTenths} has been hidden.
     * @see #setTenthsVisibility(boolean)
     */
    private boolean tenthsVisible = true;

    /**
     * if {@link #tenthsVisible} true, and {@link #changeCurrentWhole(int, boolean)} is called,
     * have we already set tenths value to 0?
     */
    private boolean tenthsCleared = false;

    /**
     * If not null, this odometer's value will be updated by another odometer
     * until the user changes the value directly here.
     * That is, this odometer is the other's {@link #relatedOdoOnChanges},
     * and the other's {@link #relatedCheckOnChanges} is null.
     * @see #setRelatedUncheckedOdoOnChanges(OdometerNumberPicker, CheckBox)
     */
    private OdometerNumberPicker controllingRelatedOdoUntilChangedDirectly;

    /**
     * update this other odometer's value on user value changes, if not null.
     * @see #relatedCheckOnChanges
     * @see #controllingRelatedOdoUntilChangedDirectly
     * @see #setRelatedUncheckedOdoOnChanges(OdometerNumberPicker, CheckBox)
     */
    private OdometerNumberPicker relatedOdoOnChanges;

    /** if non-null, and checked, don't update {@link #relatedOdoOnChanges} */
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
        	mWholeNum = (OdometerNumberPickerWhole) findViewById(R.id.org_shadowlands_rtr_odometer_wholenum);
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
        	mWholeNum.setMinValue(0);
	        mWholeNum.setMaxValue(RANGE_MAX_WHOLE);  // expand the range
	        mWholeNum.setWrapSelectorWheel(false);
	        mWholeNum.setValue(0);
	        mWholeNum.setOnValueChangedListener(this);
        }

        try
        {
        	mTenths = (OdometerNumberPickerTenths) findViewById(R.id.org_shadowlands_rtr_odometer_tenths);
        }
        catch (ClassCastException e) { }
        if (mTenths != null)
        {
	        mTenths.setParentOdoPicker(this);
	        mTenths.setValue(0);
	        mTenths.setOnValueChangedListener(this);
        }

        if (!isEnabled()) {
            setEnabled(false);
        }

    }

    /**
     * Get the current value in 10ths of a unit.
     * @return current value, as fixed-decimal 10ths of a unit
     */
    public int getCurrent10d()
    {
    	final int whole = mWholeNum.getValue(),
    	          tenths = mTenths.getValue();  // even if not tenthsVisible
    	return (whole * 10) + tenths;
    }

    /**
     * Set the current value, and optionally make it the minimum.
     *<P>
     * Does not change the related odometer.
     * Does not change our checkbox's value.
     * @param newValue  New value in 10ths of a unit
     * @param setMinimumToo  Also make this the minimum;
     *    note that only the whole-number field gets this minimum,
     *    the 10ths field ignores it.
     *    That is, setting the minimum to 1234 is the same to 1230.
     * @see #changeCurrentTenths(int)
     * @see #changeCurrentWhole(int, boolean)
     * @see #setCurrent10dAndRelated(int)
     */
    public void setCurrent10d(final int newValue, final boolean setMinimumToo)
    {
    	final int whole = (int) (newValue / 10);
    	if (setMinimumToo)
    		mWholeNum.setMinValue(whole);
    	mWholeNum.setValue(whole);
    	final int tenths = (int) (newValue % 10);
    	mTenths.setValue(tenths);
    	if (tenthsCleared && (tenths != 0) && ! tenthsVisible)
    		tenthsCleared = false;
    }

    /**
     * Change the current value and the related odometer.
     * Also sets our checkbox.
     * @param newValue  New value in 10ths of a unit
     * @see #setCurrent10d(int, boolean)
     */
    public void setCurrent10dAndRelated(final int newValue)
    {
		if (checkOnChanges != null)
			checkOnChanges.setChecked(true);

		final int delta = newValue - getCurrent10d();
		setCurrent10d(newValue, false);

		if ((relatedOdoOnChanges == null)
		    || ((relatedCheckOnChanges != null) && relatedCheckOnChanges.isChecked()))
			return;

		relatedOdoOnChanges.setCurrent10d
		  (relatedOdoOnChanges.getCurrent10d() + delta, false);
    }

    /**
     * Change our whole-number part by this amount.
     * If {@link #setTenthsVisibility(boolean) setTenthsVisibility(false)}, clear tenths to 0.
     *<P>
     * Does not change the related odometer.
     * @param wholeChange amount to increase (positive) or decrease (negative).
     * @param onlyIfUnchecked  Only change it if our checkbox isn't currently checked
     * @see #setCurrent10d(int, boolean)
     * @see #setCurrent10dAndRelated(int)
     */
    public void changeCurrentWhole(final int wholeChange, final boolean onlyIfUnchecked)
    {
    	if (onlyIfUnchecked && (checkOnChanges != null) && checkOnChanges.isChecked())
    		return;

    	mWholeNum.setValue(mWholeNum.getValue() + wholeChange);
    	if (! (tenthsVisible || tenthsCleared))
    	{
    		mTenths.setValue(0);
    		tenthsCleared = true;
    	}
    }

    /**
     * Change our tenths part by this amount.
     * If the tenths would wrap around, also change the whole,
     * so that the entire odometer changes by the correct amount.
     *<P>
     * Does not change the related odometer.
     * @param tenthsChange amount to increase (positive) or decrease (negative).
     * @see #setCurrent10d(int, boolean)
     * @see #setCurrent10dAndRelated(int)
     */
    public void changeCurrentTenths(final int tenthsChange)
    {
    	final int oldTenths = mTenths.getValue();
    	final int newTenths = oldTenths + tenthsChange;

    	if ((newTenths >= 0) && (newTenths <= 9))
    	{
    		mTenths.setValue(newTenths);
    	} else {
    		final int newEntire
    		    = (mWholeNum.getValue() * 10)
    		    + oldTenths + tenthsChange;
    		setCurrent10d(newEntire, false);
    	}
    }

    /**
     * If whole-part spinner is less than its maximum, increment.
     * @return  True if could increment, false if was already at max
     * @since 0.9.71 (or other version) TODO
     * @see #decrementWhole()
     * @see #changeCurrentWhole(int, boolean)
     */
    public boolean incrementWhole()
    {
        final int curr = mWholeNum.getValue();
        if (curr >= mWholeNum.getMaxValue())
            return false;

        mWholeNum.setValue(curr + 1);
        return true;
    }

    /**
     * If whole-part spinner is more than its maximum, decrement.
     * @return  True if could decrement, false if was already at min
     * @since 0.9.71 (or other version) TODO
     * @see #incrementWhole()
     * @see #changeCurrentWhole(int, boolean)
     */
    public boolean decrementWhole()
    {
        final int curr = mWholeNum.getValue();
        if (curr <= mWholeNum.getMinValue())
            return false;

        mWholeNum.setValue(curr - 1);
        return true;
    }

    /**
     * Set our related odometer; its value can be changed when this one changes.
     * If <tt>relatedCB</tt> is not null, only change <tt>related</tt>'s value when
     * this checkbox is unchecked.
     * If <tt>relatedCB</tt> is null, change <tt>related</tt> from here only until
     * the user changes it directly there.
     *
     * @param related  The related odometer, or null
     * @param relatedCB  The related odometer's checkbox, or null
     * @see #setCurrent10d(int, boolean)
     */
    public void setRelatedUncheckedOdoOnChanges
        (OdometerNumberPicker related, CheckBox relatedCB)
    {
    	if (relatedOdoOnChanges != related)
    	{
	    	if (relatedOdoOnChanges != null)
	    	{
	    		relatedOdoOnChanges.mTenths.setRelatedOdoPicker(null);
	    		relatedOdoOnChanges.controllingRelatedOdoUntilChangedDirectly = null;
	    	}

	    	relatedOdoOnChanges = related;
	    	relatedOdoOnChanges.mTenths.setRelatedOdoPicker(this);

	    	if (relatedCB == null)
	    		relatedOdoOnChanges.controllingRelatedOdoUntilChangedDirectly = this;
    	}

    	relatedCheckOnChanges = relatedCB;
    }

    /** When the user changes the value, set this checkbox to 'checked'. */
    public void setCheckboxOnChanges(CheckBox cb)
    {
    	checkOnChanges = cb;
    }

    /**
     * Callback from our whole or tenths NumberPicker: the user has changed the value.
     * Potentially check our related checkbox.
     * Potentially adjust related odometer, depending on related odo's checkbox.
     * (If related odo has no checkbox, always adjust it.)
     * @see #setCheckboxOnChanges(CheckBox)
     * @see #setRelatedUncheckedOdoOnChanges(OdometerNumberPicker, CheckBox)
     */
	public void onValueChange(NumberPicker picker, int oldVal, int newVal)
	{
		if (checkOnChanges != null)
			checkOnChanges.setChecked(true);

		if (controllingRelatedOdoUntilChangedDirectly != null)
		{
			// user changed it directly here, so un-link that
			controllingRelatedOdoUntilChangedDirectly.relatedOdoOnChanges = null;
			controllingRelatedOdoUntilChangedDirectly = null;
		}

		if ((relatedOdoOnChanges == null)
		    || ((relatedCheckOnChanges != null) && relatedCheckOnChanges.isChecked()))
			return;

		final int delta = newVal - oldVal;
		if (picker == mWholeNum)
		{
			// avoid big delta jumps during odo text edits (temporarily fewer digits);
			//   see getLowest javadoc for details.  The checks against 0 are because
			//   getLowest() starts high and will already be the new value (1 or more), not 0.

			final int lowest = mWholeNum.getMinValue();
			if (((newVal >= lowest) && ((oldVal == 0) || (oldVal >= lowest)))
			    || ((newVal != 0) && (relatedOdoOnChanges.getCurrent10d() == 0)))
				relatedOdoOnChanges.changeCurrentWhole(delta, false);
		} else {
			relatedOdoOnChanges.changeCurrentTenths(delta);
		}
	}

	/**
	 * Show or hide the tenths portion; will use {@link View#VISIBLE} or {@link View#GONE}.
	 * Hiding the tenths keeps its value, but that value can't be changed by the user.
	 * By default, tenths is visible.
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

	/**
	 * Enable or disable our {@link NumberPicker}s.
	 */
	public void setEnabled(final boolean ena)
	{
		super.setEnabled(ena);
		mWholeNum.setEnabled(ena);
		mTenths.setEnabled(ena);
	}

	/**
	 * Save our state before an Android pause or stop;
	 * to be called from a parent view's <tt>onSaveInstanceState</tt>.
	 * @param outState  bundle to save into; does nothing if null
	 * @param prefix  unique prefix for bundle keys for this odo,
	 *     if parent view has multiple odometers, or null
	 * @see #onRestoreInstanceState(Bundle, String)
	 */
	public void onSaveInstanceState(Bundle outState, String prefix)
	{
		if (outState == null)
			return;
		if (prefix == null)
			prefix = "";

    	Parcelable bWhole = mWholeNum.onSaveInstanceState();
    	outState.putParcelable(prefix+"OW", bWhole);
    	outState.putInt(prefix+"OT", mTenths.getValue());
    	outState.putBoolean(prefix+"OV", tenthsVisible);
	}

	/**
	 * Restore our state after an Android pause or stop;
	 * to be called from a parent view's <tt>onRestoreInstanceState</tt>.
	 * Happens here (and not <tt>onCreate</tt>) to ensure the
	 * initialization is complete before this method is called.
	 * @param inState  bundle to restore from; does nothing if null
	 * @param prefix  unique prefix for bundle keys for this odo,
	 *     if parent view has multiple odometers, or null
	 * @see #onSaveInstanceState(Bundle, String)
	 */
	public void onRestoreInstanceState(Bundle inState, String prefix)
	{
		if (inState == null)
			return;
		if (prefix == null)
			prefix = "";

		Parcelable bWhole = inState.getParcelable(prefix+"OW");
		mWholeNum.onRestoreInstanceState(bWhole);
		mTenths.setValue(inState.getInt(prefix+"OT"));
		tenthsVisible = inState.getBoolean(prefix+"OV", true);
	}

}
