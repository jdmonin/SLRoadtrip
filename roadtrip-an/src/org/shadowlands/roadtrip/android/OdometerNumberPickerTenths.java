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

import android.content.Context;
import android.util.AttributeSet;

import com.quietlycoding.android.picker.NumberPicker;

/**
 * Part of {@link OdometerNumberPicker}.
 * Holds tenths; when increment/decrement past 9/0, increment the whole one.
 * You must point this at the whole, by calling {@link #setMatchingWholePicker(NumberPicker)}.
 *<P>
 * This is a top-level class, because otherwise the LayoutInflater and
 * class loader doesn't find it properly when setting up the Activity.
 */
public class OdometerNumberPickerTenths extends NumberPicker
{
	private NumberPicker matchingWholePicker;

    public OdometerNumberPickerTenths(Context context) {
        this(context, null, 0);
    }

    public OdometerNumberPickerTenths(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OdometerNumberPickerTenths(Context context, AttributeSet attrs, int defStyle)
    {
    	super(context, attrs, defStyle);
    	setRange(0, 9);
    }

    public void setWholePicker(NumberPicker whole)
    {
    	matchingWholePicker = whole;
    }

    /**
     * If we wrap around the values, don't just go past the end,
     * also increment/decrement the whole-number picker.
     */
    protected void changeCurrent(int current)
    {
    	if (matchingWholePicker != null) {
            if (current > mEnd) {
            	if (! matchingWholePicker.increment())
            		return;  // don't wrap us around
            } else if (current < mStart) {
            	if (! matchingWholePicker.decrement())
            		return;  // don't wrap us around
            }
    	}
        super.changeCurrent(current);
    }

}