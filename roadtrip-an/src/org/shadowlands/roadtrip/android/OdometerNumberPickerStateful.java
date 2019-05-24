/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2019 Jeremy D Monin <jdmonin@nand.net>
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
import android.os.Parcelable;
import android.util.AttributeSet;

import android.widget.NumberPicker;

/**
 * Part of {@link OdometerNumberPicker}. Holds whole-number or tenths-digit portion.
 * Sole purpose is to have a public onSaveInstanceState and onRestoreInstanceState
 * (not protected) for OdometerNumberPicker to call.
 *<P>
 * This is a top-level class, because otherwise the LayoutInflater and
 * class loader doesn't find it properly when setting up the Activity.
 */
public class OdometerNumberPickerStateful
	extends NumberPicker
{

    public OdometerNumberPickerStateful(Context context) {
        super(context);
    }

    public OdometerNumberPickerStateful(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public OdometerNumberPickerStateful(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public OdometerNumberPickerStateful(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        this(context, attrs, defStyleAttr);  // TODO 4-param constructor added to superclass in API 21 (5.0)
    }

    @Override
    public Parcelable onSaveInstanceState() {
        return super.onSaveInstanceState();
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
    }

}
