/*
 * Copyright (C) 2008 The Android Open Source Project
 *    Retrieved via http://www.quietlycoding.com/?p=5
 * Portions Copyright (C) 2010,2012-2013 Jeremy D Monin <jdmonin@nand.net>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.quietlycoding.android.picker;

import org.shadowlands.roadtrip.R;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.NumberKeyListener;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnLongClickListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * This class has been pulled from the Android platform source code, its an internal widget that hasn't been
 * made public so its included in the project in this fashion for use with the preferences screen; I have made
 * a few slight modifications to the code here, I simply put a MAX and MIN default in the code but these values
 * can still be set publicly by calling code.
 *<P>
 * This copy was obtained 2010-07-14 at <A href="http://www.quietlycoding.com/?p=5"
 *   >http://www.quietlycoding.com/?p=5</A>.
 *<P>
 * 2010-12-11 jdmonin Update mCurrent if mText is typed into <br>
 * 2012-12-08 jdmonin Javadocs: setOnChangeListener <br>
 * 2013-04-29 jdmonin Add mLowest, javadocs <br>
 *
 * @author Google
 */
public class NumberPicker extends LinearLayout implements OnClickListener,
        OnFocusChangeListener, OnLongClickListener, TextWatcher {

    @SuppressWarnings("unused")
	private static final String TAG = "NumberPicker";
    private static final int DEFAULT_MAX = 200;
    private static final int DEFAULT_MIN = 0;

    /**
     * The OnChangedListener is notified when the user changes the value
     * (clicking or typing), but not if the value is programmatically changed.
     */
    public interface OnChangedListener {
        void onChanged(NumberPicker picker, int oldVal, int newVal);
    }

    public interface Formatter {
        String toString(int value);
    }

    /*
     * Use a custom NumberPicker formatting callback to use two-digit
     * minutes strings like "01".  Keeping a static formatter etc. is the
     * most efficient way to do this; it avoids creating temporary objects
     * on every call to format().
     */
    public static final NumberPicker.Formatter TWO_DIGIT_FORMATTER =
            new NumberPicker.Formatter() {
                final StringBuilder mBuilder = new StringBuilder();
                final java.util.Formatter mFmt = new java.util.Formatter(mBuilder);
                final Object[] mArgs = new Object[1];
                public String toString(int value) {
                    mArgs[0] = value;
                    mBuilder.delete(0, mBuilder.length());
                    mFmt.format("%02d", mArgs);
                    return mFmt.toString();
                }
        };

    private final Handler mHandler;
    private final Runnable mRunnable = new Runnable() {
        public void run() {
            if (mIncrement) {
                changeCurrent(mCurrent + 1);
                mHandler.postDelayed(this, mSpeed);
            } else if (mDecrement) {
                changeCurrent(mCurrent - 1);
                mHandler.postDelayed(this, mSpeed);
            }
        }
    };

    private final EditText mText;
    private final InputFilter mNumberInputFilter;

    private String[] mDisplayedValues;
    protected int mStart;
    protected int mEnd;
    protected int mCurrent;
    protected int mPrevious;
    /**
     * mLowest is the lowest observed {@link #mCurrent} value after increments/decrements;
     * not updated by typing in the text field, because backspacing and entering a new value
     * will temporarily set a very low {@link #mCurrent} value that won't be the final value.
     */
    protected int mLowest;

    private OnChangedListener mListener;
    private Formatter mFormatter;
    private long mSpeed = 300;

    private boolean mIncrement;
    private boolean mDecrement;

    public NumberPicker(Context context) {
        this(context, null);
    }

    public NumberPicker(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NumberPicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.number_picker, this, true);
        mHandler = new Handler();
        InputFilter inputFilter = new NumberPickerInputFilter();
        mNumberInputFilter = new NumberRangeKeyListener();
        try
        {
	        mIncrementButton = (NumberPickerButton) findViewById(R.id.increment);
	        mIncrementButton.setOnClickListener(this);
	        mIncrementButton.setOnLongClickListener(this);
	        mIncrementButton.setNumberPicker(this);
	        mDecrementButton = (NumberPickerButton) findViewById(R.id.decrement);
	        mDecrementButton.setOnClickListener(this);
	        mDecrementButton.setOnLongClickListener(this);        
	        mDecrementButton.setNumberPicker(this);
        }
        catch (ClassCastException e)
        {
        	// This happens in the eclipse layout editor. (jdmonin 2010-08-22)
        	// Caused by: java.lang.ClassCastException: com.quietlycoding.android.picker.NumberPickerButton cannot be cast to com.quietlycoding.android.picker.NumberPickerButton
        	// at com.quietlycoding.android.picker.NumberPicker.<init>(NumberPicker.java:129)
        }

        mText = (EditText) findViewById(R.id.timepicker_input);
        mText.setOnFocusChangeListener(this);
        mText.setFilters(new InputFilter[] {inputFilter});
        mText.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        mText.addTextChangedListener(this);

        if (!isEnabled()) {
            setEnabled(false);
        }

        mStart = DEFAULT_MIN;
        mEnd = DEFAULT_MAX;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (mDecrementButton != null)
        {
	        mIncrementButton.setEnabled(enabled);
	        mDecrementButton.setEnabled(enabled);
        }
        mText.setEnabled(enabled);
    }

    /**
     * Set or clear our change listener.
     * When the user changes the value, call {@link OnChangedListener#onChanged(NumberPicker, int, int)}.
     * Not called if the value is programmatically changed.
     * @param listener  New listener, or <tt>null</tt> to clear
     */
    public void setOnChangeListener(OnChangedListener listener) {
        mListener = listener;
    }

    public void setFormatter(Formatter formatter) {
        mFormatter = formatter;
    }

    /**
     * Set the range of numbers allowed for the number picker. The current
     * value will be automatically set to the start. The {@link #getLowest()} value
     * will be set to the end, so that it can be decreased with decrements.
     *
     * @param start the start of the range (inclusive)
     * @param end the end of the range (inclusive)
     */
    public void setRange(int start, int end) {
        mStart = start;
        mEnd = end;
        mCurrent = start;
        mLowest = end;
        updateView();
    }
 
    /**
     * Set the range of numbers allowed for the number picker. The current
     * value will be automatically set to the start. The {@link #getLowest()} value
     * will be set to the end, so that it can be decreased with decrements.
     * Also provide a mapping for values used to display to the user.
     *
     * @param start the start of the range (inclusive)
     * @param end the end of the range (inclusive)
     * @param displayedValues the values displayed to the user.
     */
    public void setRange(int start, int end, String[] displayedValues) {
        mDisplayedValues = displayedValues;
        mStart = start;
        mEnd = end;
        mCurrent = start;
        mLowest = end;
        updateView();
    }

    public void setCurrent(int current) {
        mCurrent = current;
        updateView();
    }

    /**
     * Increment the number's value, if allowed by range.
     * @return true if incremented, false if already at end of range (highest value).
     */
    public boolean increment() {
    	if (mCurrent >= mEnd)
    		return false;
    	++mCurrent;
        updateView();
    	return true;
    }

    /**
     * Decrement the number's value, if allowed by range.
     * Update {@link #mLowest} but not {@link #mPrevious}.
     * @return true if decremented, false if already at end of range (lowest value).
     */
    public boolean decrement() {
    	if (mCurrent <= mStart)
    		return false;
    	--mCurrent;
    	if (mCurrent < mLowest)
    		mLowest = mCurrent;
        updateView();
    	return true;
    }

    /**
     * The speed (in milliseconds) at which the numbers will scroll
     * when the the +/- buttons are longpressed. Default is 300ms.
     */
    public void setSpeed(long speed) {
        mSpeed = speed;
    }

    public void onClick(View v) {
        validateInput(mText);
        if (!mText.hasFocus()) mText.requestFocus();

        // now perform the increment/decrement
        if (R.id.increment == v.getId()) {
            changeCurrent(mCurrent + 1);
        } else if (R.id.decrement == v.getId()) {
            changeCurrent(mCurrent - 1);
        }
    }

    private String formatNumber(int value) {
        return (mFormatter != null)
                ? mFormatter.toString(value)
                : String.valueOf(value);
    }

    /**
     * Update {@link #mPrevious}, {@link #mCurrent}, and {@link #mLowest}
     * from an increment/decrement button click.
     * Call {@link #notifyChange()} and {@link #updateView()}.
     *<P>
     * Not called when text is typed into {@link #mText}.
     * @param newCurrent
     */
    protected void changeCurrent(int newCurrent) {

        // Wrap around the values if we go past the start or end
        if (newCurrent > mEnd) {
            newCurrent = mStart;
        } else if (newCurrent < mStart) {
            newCurrent = mEnd;
        }
        mPrevious = mCurrent;
        mCurrent = newCurrent;
        if (mCurrent < mLowest)
        	mLowest = mCurrent;

        notifyChange();
        updateView();
    }

    protected void notifyChange() {
        if (mListener != null) {
            mListener.onChanged(this, mPrevious, mCurrent);
        }
    }

    protected void updateView() {

        /* If we don't have displayed values then use the
         * current number else find the correct value in the
         * displayed values for the current number.
         */
        if (mDisplayedValues == null) {
            mText.setText(formatNumber(mCurrent));
        } else {
            mText.setText(mDisplayedValues[mCurrent - mStart]);
        }
        mText.setSelection(mText.getText().length());
    }

    /**
     * Update {@link #mPrevious}, update {@link #mCurrent} from text {@code str}.
     * Call {@link #notifyChange()} if valid, and {@link #updateView()}.
     *<P>
     * Does not call {@link #changeCurrent(int)}.
     * @param str  Contents of {@link #mText} to parse and use in update
     */
    private void validateCurrentView(CharSequence str) {
        int val = getSelectedPos(str.toString());
        if ((val >= mStart) && (val <= mEnd)) {
            if (mCurrent != val) {
                mPrevious = mCurrent;
                mCurrent = val;
                notifyChange();
            }
        }
        updateView();
    }

    public void onFocusChange(View v, boolean hasFocus) {

        /* When focus is lost check that the text field
         * has valid values.
         */
        if (!hasFocus) {
            validateInput(v);
        }
    }

    private void validateInput(View v) {
        String str = String.valueOf(((TextView) v).getText());
        if ("".equals(str)) {

            // Restore to the old value as we don't allow empty values
            updateView();
        } else {

            // Check the new value and ensure it's in range
            validateCurrentView(str);
        }
    }

    /**
     * We start the long click here but rely on the {@link NumberPickerButton}
     * to inform us when the long click has ended.
     */
    public boolean onLongClick(View v) {

        /* The text view may still have focus so clear it's focus which will
         * trigger the on focus changed and any typed values to be pulled.
         */
        mText.clearFocus();

        if (R.id.increment == v.getId()) {
            mIncrement = true;
            mHandler.post(mRunnable);
        } else if (R.id.decrement == v.getId()) {
            mDecrement = true;
            mHandler.post(mRunnable);
        }
        return true;
    }

    public void cancelIncrement() {
        mIncrement = false;
    }

    public void cancelDecrement() {
        mDecrement = false;
    }

    /**
     * Save range, current, displayed values.
     * This is called in Android 2.1 despite not extending View.BaseSavedState.
     */
    public Parcelable onSaveInstanceState()
    {
    	Parcelable sp = super.onSaveInstanceState();
    	Bundle b = new Bundle();
    	b.putParcelable("super", sp);
    	b.putInt("S", mStart);
    	b.putInt("E", mEnd);
    	b.putInt("C", mCurrent);
    	b.putInt("L", mLowest);
    	if (mDisplayedValues != null)
    		b.putStringArray("D", mDisplayedValues);
    	// Log.i(TAG, "onSaveInstanceState put " + b.hashCode());
    	return b;
    }

    /** Save range, current, displayed values */
    public void onRestoreInstanceState(Parcelable p)
    {
    	// Log.i(TAG, "onRestoreInstanceState");
    	if ((p == null) || ! (p instanceof Bundle))
    	{
    		// Log.e(TAG, "onRestoreInstanceState p was " + p.getClass().getCanonicalName());
    		super.onRestoreInstanceState(null);
    		return;
    	}
    	Bundle b = (Bundle) p;
    	// Log.i(TAG, "onRestoreInstanceState got " + p.hashCode());
    	super.onRestoreInstanceState(b.getParcelable("super"));
    	mStart = b.getInt("S", DEFAULT_MIN);
    	mEnd = b.getInt("E", DEFAULT_MAX);
    	mCurrent = b.getInt("C", DEFAULT_MIN);
    	mLowest = b.getInt("L", mEnd);
    	if (b.containsKey("D"))
    		mDisplayedValues = b.getStringArray("D");
    	updateView();
    }
    private static final char[] DIGIT_CHARACTERS = new char[] {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
    };

    private NumberPickerButton mIncrementButton;
    private NumberPickerButton mDecrementButton;

    private class NumberPickerInputFilter implements InputFilter {
        public CharSequence filter(CharSequence source, int start, int end,
                Spanned dest, int dstart, int dend) {
            if (mDisplayedValues == null) {
                return mNumberInputFilter.filter(source, start, end, dest, dstart, dend);
            }
            CharSequence filtered = String.valueOf(source.subSequence(start, end));
            String result = String.valueOf(dest.subSequence(0, dstart))
                    + filtered
                    + dest.subSequence(dend, dest.length());
            String str = String.valueOf(result).toLowerCase();
            for (String val : mDisplayedValues) {
                val = val.toLowerCase();
                if (val.startsWith(str)) {
                    return filtered;
                }
            }
            return "";
        }
    }

    private class NumberRangeKeyListener extends NumberKeyListener {

        // XXX This doesn't allow for range limits when controlled by a
        // soft input method!
        public int getInputType() {
            return InputType.TYPE_CLASS_NUMBER;
        }

        @Override
        protected char[] getAcceptedChars() {
            return DIGIT_CHARACTERS;
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                Spanned dest, int dstart, int dend) {

            CharSequence filtered = super.filter(source, start, end, dest, dstart, dend);
            if (filtered == null) {
                filtered = source.subSequence(start, end);
            }

            String result = String.valueOf(dest.subSequence(0, dstart))
                    + filtered
                    + dest.subSequence(dend, dest.length());

            if ("".equals(result)) {
                return result;
            }
            int val = getSelectedPos(result);

            /* Ensure the user can't type in a value greater
             * than the max allowed. We have to allow less than min
             * as the user might want to delete some numbers
             * and then type a new number.
             */
            if (val > mEnd) {
                return "";
            } else {
                return filtered;
            }
        }
    }

    private int getSelectedPos(String str) {
        if (mDisplayedValues == null) {
            return Integer.parseInt(str);
        } else {
            for (int i = 0; i < mDisplayedValues.length; i++) {

                /* Don't force the user to type in jan when ja will do */
                str = str.toLowerCase();
                if (mDisplayedValues[i].toLowerCase().startsWith(str)) {
                    return mStart + i;
                }
            }

            /* The user might have typed in a number into the month field i.e.
             * 10 instead of OCT so support that too.
             */
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {

                /* Ignore as if it's not a number we don't care */
            }
        }
        return mStart;
    }

    /**
     * @return the current value.
     */
    public int getCurrent() {
        return mCurrent;
    }

    /**
     * Get the lowest non-typed current value.
     * This is the lowest {@link #getCurrent()} value after increments/decrements;
     * not updated by typing in the text field, because backspacing and entering a new value
     * will temporarily set a very low current value that won't be the final value.
     * 
     * @return the lowest non-typed value.
     */
    public int getLowest() {
    	return mLowest;
    }

	/** update current value when typed into */
	public void afterTextChanged(Editable mt)
	{
		// To avoid infinite loops, call validateCurrentView only
		// if the new value is different and valid.
		String nu = mt.toString().trim();
		if (nu.length() > 0)
		{
			int i;
			try {
				i = Integer.parseInt(nu);
				if (i != mCurrent)
					validateCurrentView(nu);  // update mCurrent			
			} catch (NumberFormatException e) { }
		}
	}

	/** empty required stub for {@link TextWatcher} */ 
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

	/** empty required stub for {@link TextWatcher} */ 
	public void onTextChanged(CharSequence s, int start, int before, int count) {}

}
