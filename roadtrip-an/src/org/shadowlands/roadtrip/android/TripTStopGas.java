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
import org.shadowlands.roadtrip.db.RDBSchema;
import org.shadowlands.roadtrip.db.TStopGas;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

public class TripTStopGas extends Activity
	implements TextWatcher
{

	private static final String EXTRAS_FIELD_QUANT = "quant",
		EXTRAS_FIELD_PERUNIT = "perunit",
		EXTRAS_FIELD_TOTALCOST = "totalcost",
		EXTRAS_FIELD_ISFILLUP = "isfillup",
		EXTRAS_FIELD_BRANDGRADE_ID = "brandgrade_id",
		EXTRAS_FIELD_BRANDGRADE = "brandgrade",
		EXTRAS_FIELD_CALC_FLAGS = "calc";

	private EditText quant_et, perunit_et, totalcost_et;
	/**
	 * if true, the contents of the corresponding EditText
	 * are calculated, not entered by the user.
	 */
	private boolean quant_calc, perunit_calc, totalcost_calc;
	private CheckBox isFillup_chk;
	private EditText station_et;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.trip_tstop_gas);

	    quant_et = (EditText) findViewById(R.id.trip_tstopgas_quant);
	    perunit_et = (EditText) findViewById(R.id.trip_tstopgas_perunit);
	    totalcost_et = (EditText) findViewById(R.id.trip_tstopgas_total);
	    isFillup_chk = (CheckBox) findViewById(R.id.trip_tstopgas_fillup_chk);
	    station_et = (EditText) findViewById(R.id.trip_tstopgas_station);

	    Bundle b = savedInstanceState;
	    if (b == null)
	    	b = getIntent().getExtras();
	    if (b != null)
	    	loadFieldsFromBundle(b);

	    quant_et.addTextChangedListener(this);
	    perunit_et.addTextChangedListener(this);
	    totalcost_et.addTextChangedListener(this);
	}

	public void onClick_BtnOK(View v)
	{
		Intent i = getIntent();
		Bundle b = i.getExtras();
		final boolean wasNew = (b == null);
		if (wasNew)
		{
			b = new Bundle();
			b.putLong("b_id", System.currentTimeMillis());
		}
		saveBundleFromFields(b);
		i.putExtras(b);
    	setResult(RESULT_OK, i);
		finish();		
	}

	public void onClick_BtnCancel(View v)
	{
    	setResult(RESULT_CANCELED, getIntent());
		finish();
	}

	private void loadFieldsFromBundle(Bundle loadFrom)
	{
		if (loadFrom == null)
			return;
		quant_et.setText(loadFrom.getCharSequence(EXTRAS_FIELD_QUANT));
		perunit_et.setText(loadFrom.getCharSequence(EXTRAS_FIELD_PERUNIT));
		totalcost_et.setText(loadFrom.getCharSequence(EXTRAS_FIELD_TOTALCOST));
		isFillup_chk.setChecked(loadFrom.getBoolean(EXTRAS_FIELD_ISFILLUP, false));
		station_et.setText(loadFrom.getCharSequence(EXTRAS_FIELD_BRANDGRADE));
		final boolean[] calc = loadFrom.getBooleanArray(EXTRAS_FIELD_CALC_FLAGS);
		if (calc != null)
		{
			quant_calc = calc[0];
			perunit_calc = calc[1];
			totalcost_calc = calc[2];
		} else {
			quant_calc = false;
            perunit_calc = false;
            totalcost_calc = false;
		}
	}

	private void saveBundleFromFields(Bundle saveTo)
	{
		saveTo.putCharSequence(EXTRAS_FIELD_QUANT, quant_et.getText());
		saveTo.putCharSequence(EXTRAS_FIELD_PERUNIT, perunit_et.getText());
		saveTo.putCharSequence(EXTRAS_FIELD_TOTALCOST, totalcost_et.getText());
		saveTo.putBoolean(EXTRAS_FIELD_ISFILLUP, isFillup_chk.isChecked());
		saveTo.putCharSequence(EXTRAS_FIELD_BRANDGRADE_ID, station_et.getText());
		final boolean[] calc = new boolean[] {
			quant_calc, perunit_calc, totalcost_calc
		};
		saveTo.putBooleanArray(EXTRAS_FIELD_CALC_FLAGS, calc);
		if (! saveTo.containsKey("b_id"))
			saveTo.putLong("b_id", System.currentTimeMillis());
	}

	public static void saveBundleFromDBObj(TStopGas tg, Bundle saveTo)
	{
		saveTo.putCharSequence(EXTRAS_FIELD_QUANT, RDBSchema.formatFixedDec(tg.quant, 3));
		saveTo.putCharSequence(EXTRAS_FIELD_PERUNIT, RDBSchema.formatFixedDec(tg.price_per, 3));
		saveTo.putCharSequence(EXTRAS_FIELD_TOTALCOST, RDBSchema.formatFixedDec(tg.price_total, 2));
		saveTo.putBoolean(EXTRAS_FIELD_ISFILLUP, tg.fillup);
		if (tg.gas_brandgrade_id != 0)
		{
			saveTo.putInt(EXTRAS_FIELD_BRANDGRADE_ID, tg.gas_brandgrade_id);
			if (tg.gas_brandgrade != null)
				saveTo.putCharSequence(EXTRAS_FIELD_BRANDGRADE, tg.gas_brandgrade.getName());
		}
		if (! saveTo.containsKey("b_id"))
			saveTo.putLong("b_id", System.currentTimeMillis());
	}

	@SuppressWarnings("unused")
	final private static String TAG = "roadtrip.TripTStopGas";

	/**
	 * Save the bundle to the fields of a {@link TStopGas} to write later to the database.
	 * @param saveFrom
	 * @param tg  tstopgas to change based on bundle contents, or null for new
	 * @return created or updated tstopgas, or null if nothing (or all empty strings) in the bundle.
	 *      If a new TStopGas is created, be sure to call {@link TStopGas#setTStop(org.shadowlands.roadtrip.db.TStop)}
	 *      before inserting it.
	 */
	public static TStopGas saveDBObjFromBundle(Bundle saveFrom, TStopGas tg)
	{
		final int q = RDBSchema.parseFixedDecOr0(saveFrom.getCharSequence(EXTRAS_FIELD_QUANT), 3),
		          pp = RDBSchema.parseFixedDecOr0(saveFrom.getCharSequence(EXTRAS_FIELD_PERUNIT), 3),
		          pt = RDBSchema.parseFixedDecOr0(saveFrom.getCharSequence(EXTRAS_FIELD_TOTALCOST), 2);
		if ((q == 0) && (pp == 0) && (pt == 0))
		{
			// Log.d(TAG, "saveDB: got a 0");
			return null;
		}

		if (tg == null)
			tg = new TStopGas(null);
		tg.quant = q;
		tg.price_per = pp;
		tg.price_total = pt;
		tg.fillup = saveFrom.getBoolean(EXTRAS_FIELD_ISFILLUP);
		CharSequence sta = saveFrom.getCharSequence(EXTRAS_FIELD_BRANDGRADE_ID); 
		// tg.station = ((sta != null) && (sta.length() > 0)) ? sta.toString().trim() : null;

		return tg;
	}

	public void onSaveInstanceState(Bundle saveTo)
	{
		if (! saveTo.containsKey("b_id"))
			saveTo.putLong("b_id", System.currentTimeMillis());
		saveBundleFromFields(saveTo);
	}

	public void onStop()
	{
		super.onStop();
		afterTextChanged_lastChanged = null;  // easier GC if needed
	}

	/** for use by {@link #afterTextChanged(Editable)} to ignore recursive calls */
	private EditText afterTextChanged_lastChanged;

	/** check for empty fields, calculate them if the other 2 are filled */
	public void afterTextChanged(Editable e)
	{
		final boolean hasQuant = (! quant_calc) && (quant_et.length() > 0),
		              hasPerU = (! perunit_calc) && (perunit_et.length() > 0),
		              hasTotal = (! totalcost_calc) && (totalcost_et.length() > 0);

		if (hasQuant && hasPerU && ! hasTotal)
		{
			if (totalcost_et.hasFocus() || (totalcost_et == afterTextChanged_lastChanged))
			{
				afterTextChanged_lastChanged = null;
				return;  // prevent endless callback-loop
			}
			float total = Float.parseFloat(quant_et.getText().toString()) * Float.parseFloat(perunit_et.getText().toString());
			totalcost_calc = true;
			afterTextChanged_lastChanged = totalcost_et;
			totalcost_et.setText(String.format("%1$1.2f", total));  // TODO dynamic # of digits
			return;
		}

		if (hasTotal && hasPerU && ! hasQuant)
		{
			if (quant_et.hasFocus() || (quant_et == afterTextChanged_lastChanged))
			{
				afterTextChanged_lastChanged = null;
				return;  // prevent endless callback-loop
			}
			// TODO calc quant...
			quant_calc = true;
			afterTextChanged_lastChanged = quant_et;
			quant_et.setText("Qcalc'd");
			return;
		}

		if (hasQuant && hasTotal && ! hasPerU)
		{
			if (perunit_et.hasFocus() || (perunit_et == afterTextChanged_lastChanged))
			{
				afterTextChanged_lastChanged = null;
				return;  // prevent endless callback-loop
			}
			// TODO calc perunit...
			perunit_calc = true;
			afterTextChanged_lastChanged = perunit_et;
			perunit_et.setText("Ucalc'd");
			return;
		}
	}

	/** empty required stub for {@link TextWatcher} */ 
	public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) { }

	/** empty required stub for {@link TextWatcher} */ 
	public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}

	/* test-runs parseFixedDecOr0
	public static void main (String[] args)
	{
		System.out.println("parse('5,2', 1) = " + RDBSchema.parseFixedDecOr0("5.2", 1));
		System.out.println("parse('5,2', 2) = " + RDBSchema.parseFixedDecOr0("5.2", 2));
		System.out.println("parse('5'  , 2) = " + RDBSchema.parseFixedDecOr0("5", 2));
		System.out.println("parse('5.123', 1) = " + RDBSchema.parseFixedDecOr0("5.123", 1));
	}
	*/
}
