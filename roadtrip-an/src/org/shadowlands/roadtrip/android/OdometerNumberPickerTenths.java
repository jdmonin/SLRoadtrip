/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2012,2019 Jeremy D Monin <jdmonin@nand.net>
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

import android.content.Context;
import android.util.AttributeSet;

import android.widget.NumberPicker;

/**
 * Part of {@link OdometerNumberPicker}.
 * Holds tenths; when increment/decrement past 9/0, increment the whole one.
 * You must point this at the overall picker, by calling {@link #setParentOdoPicker(OdometerNumberPicker)}.
 *<P>
 * This is a top-level class, because otherwise the LayoutInflater and
 * class loader doesn't find it properly when setting up the Activity.
 */
public class OdometerNumberPickerTenths
	extends NumberPicker implements NumberPicker.OnValueChangeListener
{
	private static final int TENTHS_MIN = 0, TENTHS_MAX = 9;

	/**
	 * Within a single {@link OdometerNumberPicker}, the whole-number part that matches this tenths odometer.
	 * Before v0.9.71, {@code matchingWholePicker} was instead used directly.
	 * @since 0.9.71
	 */
	private OdometerNumberPicker parentOdoPicker;

	/** Related {@link OdometerNumberPicker}, for changing its whole-number part. */
	private OdometerNumberPicker relatedOdoPicker;

    public OdometerNumberPickerTenths(Context context) {
        super(context);
    	init();
    }

    public OdometerNumberPickerTenths(Context context, AttributeSet attrs) {
        super(context, attrs);
    	init();
    }

    public OdometerNumberPickerTenths(Context context, AttributeSet attrs, int defStyle) {
    	super(context, attrs, defStyle);
    	init();
    }

    /**
     * Handle common constructor init, after calling super.
     * Unless each NumberPicker constructor calls its own super constructor, falls back to using wrong theme/style.
     * @since 0.9.71
     */
    private void init() {
    	setMinValue(TENTHS_MIN);
    	setMaxValue(TENTHS_MAX);
    	setWrapSelectorWheel(true);
    	setOnValueChangedListener(this);
    }

    public OdometerNumberPickerTenths(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        this(context, attrs, defStyleAttr);  // TODO 4-param constructor added to superclass in API 21 (5.0)
    }

    /**
     * Set this tenths picker's parent (with whole-number part and tenths part) odometer number picker.
     * Within a single {@link OdometerNumberPicker}, set the whole-number part that matches this tenths odometer.
     * When this tenths picker wraps around (0 to 9, or 9 to 0), increment or decrement the whole-number picker.
     * @since 0.9.71
     */
    public void setParentOdoPicker(OdometerNumberPicker parent)
    {
        parentOdoPicker = parent;
    }

    /**
     * Set or clear the reference to a related odometer's whole portion.
     * When this tenths odometer wraps around, and increments or decrements our whole-number picker,
     * also increment or decrement this related odometer's whole-number picker. 
     * Package access for {@link OdometerNumberPicker}'s use.
     * @param relatedOdo  The related odometer picker, or null to clear
     * @since 0.9.07
     */
    void setRelatedOdoPicker(OdometerNumberPicker relatedOdo)
    {
    	relatedOdoPicker = relatedOdo;
    }

    /**
     * If we wrap around the values, don't just go past the end,
     * also increment/decrement the related whole-number picker.
     */
    public void onValueChange(NumberPicker self, final int oldValue, final int newValue)
    {
    	if (parentOdoPicker == null)
    		return;

    	if (newValue == TENTHS_MIN && oldValue == TENTHS_MAX) {

            	if (! parentOdoPicker.incrementWhole())
            		return;  // don't wrap us around
            	else if (relatedOdoPicker != null)
            		relatedOdoPicker.changeCurrentWhole(+1, true);

            } else if (newValue == TENTHS_MAX && oldValue == TENTHS_MIN) {

            	if (! parentOdoPicker.decrementWhole())
            		return;  // don't wrap us around
            	else if (relatedOdoPicker != null)
            		relatedOdoPicker.changeCurrentWhole(-1, true);
    	}
    }

}