/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2012,2017 Jeremy D Monin <jdmonin@nand.net>
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

import java.text.NumberFormat;
import java.text.ParseException;

import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.db.GasBrandGrade;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.RDBSchema;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.TStopGas;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.TextView;

/**
 * Enter a TStop's gas details from {@link TripTStopEntry}.
 *<P>
 * All fields are read-only if called with the
 * {@link TripTStopEntry#EXTRAS_FIELD_VIEW_TSTOP_ID} intent extra.
 */
public class TripTStopGas extends Activity
	implements TextWatcher, OnItemClickListener
{

	private static final String EXTRAS_FIELD_QUANT = "quant",
		EXTRAS_FIELD_PERUNIT = "perunit",
		EXTRAS_FIELD_TOTALCOST = "totalcost",
		EXTRAS_FIELD_ISFILLUP = "isfillup",
		EXTRAS_FIELD_VEH_ID = "vid",
		EXTRAS_FIELD_CALC_FLAGS = "calc";

	/**
	 * Bundle keys related to {@link GasBrandGrade}.
	 * @see #loadFieldsFromBundle(Bundle)
	 * @see #saveBundleFromDBObj(TStopGas, Bundle)
	 * @see #saveDBObjFromBundle(Bundle, TStopGas)
	 */
	public static final String EXTRAS_FIELD_BRANDGRADE_ID = "brandgrade_id",
		EXTRAS_FIELD_BRANDGRADE = "brandgrade",
		EXTRAS_FIELD_BRANDGRADE_CREATED = "brandgrade_created";

	/** needed to populate {@link #brandGrade_at}, {@link #brandGradeObj} */
	private RDBAdapter db = null;

	/**
	 * Read-only mode, for {@link TripTStopEntry#EXTRAS_FIELD_VIEW_TSTOP_ID}.
	 * @since 0.9.51
	 */
	private boolean isReadOnly;

	/** Current vehicle */
	private Vehicle currV;  // TODO use this to set up currency symbol

	/** the pre-existing brand/grade chosen in {@link #brandGrade_at}, if any */
	private GasBrandGrade brandGradeObj = null;

	/** Was {@link #brandGradeObj} created for this TStop? */
	private boolean gbgCreatedHere = false;

	/**
	 * Decimal numbers (no currency sign): quantity, per-unit cost, total cost.
	 * See locale comments at {@link #parseDF}.
	 */
	private EditText quant_et, perunit_et, totalcost_et;

	/**
	 * if true, the contents of the corresponding EditText
	 * are calculated, not entered by the user.
	 */
	private boolean quant_calc, perunit_calc, totalcost_calc;
	private CheckBox isFillup_chk;

	/** {@link GasBrandGrade}s available; updates {@link #brandGradeObj} */
	private AutoCompleteTextView brandGrade_at;

	/**
	 * Decimal parser for {@link #afterTextChanged(Editable)}, in user's locale
	 * because String.format will use that locale.  The locale might have ','
	 * as the decimal separator; Float.parseFloat can't parse that format.
	 * Our layout also uses android:digits="0123456789,." for decimal EditTexts
	 * per comments in http://code.google.com/p/android/issues/detail?id=2626 .
	 */
	private NumberFormat parseDF = NumberFormat.getNumberInstance();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.trip_tstop_gas);

	    quant_et = (EditText) findViewById(R.id.trip_tstopgas_quant);
	    perunit_et = (EditText) findViewById(R.id.trip_tstopgas_perunit);
	    totalcost_et = (EditText) findViewById(R.id.trip_tstopgas_total);
	    isFillup_chk = (CheckBox) findViewById(R.id.trip_tstopgas_fillup_chk);
	    brandGrade_at = (AutoCompleteTextView) findViewById(R.id.trip_tstopgas_brandgrade);

		db = new RDBOpenHelper(this);
	    currV = Settings.getCurrentVehicle(db, false);

	    Bundle b = savedInstanceState;
	    if (b == null)
	    	b = getIntent().getExtras();
	    if (b != null)
	    {
	    	isReadOnly = (b.containsKey(TripTStopEntry.EXTRAS_FIELD_VIEW_TSTOP_ID));

	    	loadFieldsFromBundle(b);
	    	if (currV.getID() != b.getInt(EXTRAS_FIELD_VEH_ID))
	    	{
	    		// shouldn't happen, but just in case
	    		b.putInt(EXTRAS_FIELD_VEH_ID, currV.getID());
	    	}
	    }

	    if (! isReadOnly)
	    {
		quant_et.addTextChangedListener(this);
		perunit_et.addTextChangedListener(this);
		totalcost_et.addTextChangedListener(this);
	    }

		// see onResume for rest of initialization, such as populating brandGrade_at.
	}

	@Override
	public void onPause()
	{
		super.onPause();
		if (db != null)
			db.close();
	}

	/**
	 * If not {@link #isReadOnly}, populate {@link #brandGrade_at} from db.
	 * Otherwise make fields read-only and disable checkboxes/buttons.
	 */
	@Override
	public void onResume()
	{
		super.onResume();

		if (! isReadOnly)
		{
			GasBrandGrade[] gbg = GasBrandGrade.getAll(db);
			if (gbg != null)
			{
				ArrayAdapter<GasBrandGrade> adapter
					= new ArrayAdapter<GasBrandGrade>(this, R.layout.list_item, gbg);
				brandGrade_at.setAdapter(adapter);
				brandGrade_at.setOnItemClickListener(this);
			} else {
				brandGrade_at.setAdapter((ArrayAdapter<GasBrandGrade>) null);
			}
		} else {
			// disable buttons, checkboxes
			final int[] btns = {
				R.id.trip_tstopgas_fillup_chk,
				R.id.trip_tstopgas_btn_enter
			};
			for (final int id : btns)
			{
				TextView tv = (TextView) findViewById(id);
				if (tv != null)
					tv.setEnabled(false);
				tv.setFocusable(false);  // prevent
			}

			// prevent edit of text fields, but don't excessively darken the appearance
			final int[] flds = {
				R.id.trip_tstopgas_quant,
				R.id.trip_tstopgas_perunit,
				R.id.trip_tstopgas_total,
				R.id.trip_tstopgas_brandgrade
			};
			for (final int id : flds)
			{
				TextView tv = (TextView) findViewById(id);
				if (tv != null)
					tv.setFocusable(false);
			}
		}
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		if (db != null)
			db.close();
	}

	/** Show or hide the gas brand/grade dropdown */
	public void onClick_BtnBrandGradeDropdown(View v)
	{
		if (brandGrade_at == null)
			return;

		if (brandGrade_at.isPopupShowing())
			brandGrade_at.dismissDropDown();
		else
			brandGrade_at.showDropDown();
	}

	public void onClick_BtnOK(View v)
	{
		if (isReadOnly)
			return;  // just in case; shouldn't be called if so

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

	/**
	 * Load GUI fields from a bundle.
	 * @see #saveBundleFromFields(Bundle)
	 * @see #saveBundleFromDBObj(TStopGas, Bundle)
	 */
	private void loadFieldsFromBundle(Bundle loadFrom)
	{
		if (loadFrom == null)
			return;

		CharSequence cs = loadFrom.getCharSequence(EXTRAS_FIELD_QUANT);
		if (cs != null)
			quant_et.setText(cs);
		cs = loadFrom.getCharSequence(EXTRAS_FIELD_PERUNIT);
		if (cs != null)
			perunit_et.setText(cs);
		cs = loadFrom.getCharSequence(EXTRAS_FIELD_TOTALCOST);
		if (cs != null)
			totalcost_et.setText(cs);
		isFillup_chk.setChecked(loadFrom.getBoolean(EXTRAS_FIELD_ISFILLUP, false));
		// EXTRAS_FIELD_VEH_ID isn't loaded; use currV instead.
		// That extra is used in saveDBObjFromBundle.
		cs = loadFrom.getCharSequence(EXTRAS_FIELD_BRANDGRADE);
		if (cs != null)
			brandGrade_at.setText(cs);
		brandGradeObj = null;
		gbgCreatedHere = false;
		final int bgid = loadFrom.getInt(EXTRAS_FIELD_BRANDGRADE_ID, 0);
		if (bgid != 0)
		{
			try
			{
				brandGradeObj = new GasBrandGrade(db, bgid);
				gbgCreatedHere = loadFrom.getBoolean(EXTRAS_FIELD_BRANDGRADE_CREATED, false);
			}
			catch (Throwable th) {}
		} else {
		}
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

	/**
	 * Save GUI field contents to a bundle.
	 * @see #loadFieldsFromBundle(Bundle)
	 * @see #saveDBObjFromBundle(Bundle, TStopGas)
	 */
	private void saveBundleFromFields(Bundle saveTo)
	{
		saveTo.putCharSequence(EXTRAS_FIELD_QUANT, quant_et.getText());
		saveTo.putCharSequence(EXTRAS_FIELD_PERUNIT, perunit_et.getText());
		saveTo.putCharSequence(EXTRAS_FIELD_TOTALCOST, totalcost_et.getText());
		saveTo.putBoolean(EXTRAS_FIELD_ISFILLUP, isFillup_chk.isChecked());
		saveTo.putInt(EXTRAS_FIELD_VEH_ID, currV.getID());
		String bgText = brandGrade_at.getText().toString().trim();
		saveTo.putCharSequence(EXTRAS_FIELD_BRANDGRADE, bgText);
		if (brandGradeObj != null)
		{
			if ((bgText.length() == 0)
				 || ! (gbgCreatedHere || bgText.equalsIgnoreCase(brandGradeObj.getName())))
				brandGradeObj = null;  // a new name was typed, or no name was typed
		}
		saveTo.putInt(EXTRAS_FIELD_BRANDGRADE_ID, (brandGradeObj != null) ? brandGradeObj.getID() : 0);
		final boolean[] calc = new boolean[] {
			quant_calc, perunit_calc, totalcost_calc
		};
		saveTo.putBooleanArray(EXTRAS_FIELD_CALC_FLAGS, calc);
		if (! saveTo.containsKey("b_id"))
			saveTo.putLong("b_id", System.currentTimeMillis());
	}

	/**
	 * Save bundle from <tt>tg</tt>'s contents,
	 * for GUI usage by {@link #loadFieldsFromBundle(Bundle)}.
	 *<P>
	 * If <tt>tg.gas_brandgrade</tt> != null, its text will be placed into the
	 * bundle as {@link #EXTRAS_FIELD_BRANDGRADE}.  Otherwise, the bundle will
	 * contain {@link #EXTRAS_FIELD_BRANDGRADE_ID} but not the brand/grade text.
	 */
	public static void saveBundleFromDBObj(TStopGas tg, Bundle saveTo, final boolean gbgCreatedHere)
	{
		saveTo.putCharSequence(EXTRAS_FIELD_QUANT, RDBSchema.formatFixedDec(tg.quant, 3));
		saveTo.putCharSequence(EXTRAS_FIELD_PERUNIT, RDBSchema.formatFixedDec(tg.price_per, 3));
		saveTo.putCharSequence(EXTRAS_FIELD_TOTALCOST, RDBSchema.formatFixedDec(tg.price_total, 2));
		saveTo.putBoolean(EXTRAS_FIELD_ISFILLUP, tg.fillup);
		saveTo.putInt(EXTRAS_FIELD_VEH_ID, tg.vid);
		saveTo.putInt(EXTRAS_FIELD_BRANDGRADE_ID, tg.gas_brandgrade_id);
		if ((tg.gas_brandgrade_id != 0) && (tg.gas_brandgrade != null))
		{
			saveTo.putCharSequence(EXTRAS_FIELD_BRANDGRADE, tg.gas_brandgrade.getName());
			saveTo.putBoolean(EXTRAS_FIELD_BRANDGRADE_CREATED, gbgCreatedHere);
		}
		else if (saveTo.containsKey(EXTRAS_FIELD_BRANDGRADE))
		{
			saveTo.remove(EXTRAS_FIELD_BRANDGRADE);
			saveTo.remove(EXTRAS_FIELD_BRANDGRADE_CREATED);
		}
		if (! saveTo.containsKey("b_id"))
			saveTo.putLong("b_id", System.currentTimeMillis());
	}

	@SuppressWarnings("unused")
	final private static String TAG = "roadtrip.TripTStopGas";

	/**
	 * Save the bundle to the fields of a {@link TStopGas} to write later to the database.
	 *<P>
	 * <b>Note:</b> If a new {@link GasBrandGrade} must be created, please insert it, and
	 * set <tt>saveFrom.</tt>{@link #EXTRAS_FIELD_BRANDGRADE_ID} to its id,
	 * before calling this method.  (You'll need to create the new GasBrandGrade if
	 * {@link #EXTRAS_FIELD_BRANDGRADE_ID} is 0, but {@link #EXTRAS_FIELD_BRANDGRADE}
	 * is not empty.)
	 *
	 * @param saveFrom  bundle as created from {@link #saveBundleFromFields(Bundle)}
	 * @param tg  tstopgas to change based on bundle contents, or null for new
	 * @return created or updated tstopgas, or null if nothing (or all empty strings) in the bundle.
	 *      If a new TStopGas is created, be sure to call {@link TStopGas#setTStop(org.shadowlands.roadtrip.db.TStop)}
	 *      before inserting the TStopGas.
	 */
	public static TStopGas saveDBObjFromBundle(Bundle saveFrom, TStopGas tg)
	{
		// TODO instead of hardcoded # of digits, use currV fields
		final int q = RDBSchema.parseFixedDecOr0(saveFrom.getCharSequence(EXTRAS_FIELD_QUANT), 3),
		          pp = RDBSchema.parseFixedDecOr0(saveFrom.getCharSequence(EXTRAS_FIELD_PERUNIT), 3),
		          pt = RDBSchema.parseFixedDecOr0(saveFrom.getCharSequence(EXTRAS_FIELD_TOTALCOST), 2);
		if ((q == 0) && (pp == 0) && (pt == 0))
		{
			return null;
		}

		if (tg == null)
			tg = new TStopGas(null);
		tg.quant = q;
		tg.price_per = pp;
		tg.price_total = pt;
		tg.fillup = saveFrom.getBoolean(EXTRAS_FIELD_ISFILLUP);
		tg.vid = saveFrom.getInt(EXTRAS_FIELD_VEH_ID);
		tg.gas_brandgrade_id = saveFrom.getInt(EXTRAS_FIELD_BRANDGRADE_ID, 0); 

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
		if (e.length() == 0)
		{
			// a field was cleared; reset flags so we can calc it if we have the other 2
			int has = 0;
			if (quant_et.length() > 0)  ++has;
			if (perunit_et.length() > 0) ++has;
			if (totalcost_et.length() > 0) ++has;
			if (has == 2)
			{
				quant_calc = false;
				perunit_calc = false;
				totalcost_calc = false;
			}
		}

		// Parsing and formatting floats <-> strings: String.format uses the user's locale,
		// which may have ',' as the decimal separator. So, use NumberFormat.parse
		// instead of Float.parseFloat.

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
			final float total;
			try {
				total = parseDF.parse(quant_et.getText().toString()).floatValue()
				      * parseDF.parse(perunit_et.getText().toString()).floatValue();
			} catch (ParseException pe) {
				return;
			}
			totalcost_calc = true;
			afterTextChanged_lastChanged = totalcost_et;
			totalcost_et.setText(String.format("%1$1.2f", total));  // TODO dynamic # of digits from currV
			return;
		}

		if (hasTotal && hasPerU && ! hasQuant)
		{
			if (quant_et.hasFocus() || (quant_et == afterTextChanged_lastChanged))
			{
				afterTextChanged_lastChanged = null;
				return;  // prevent endless callback-loop
			}
			final float quant;
			try {
				quant = parseDF.parse(totalcost_et.getText().toString()).floatValue()
				      / parseDF.parse(perunit_et.getText().toString()).floatValue();
			} catch (ParseException pe) {
				return;
			}
			quant_calc = true;
			afterTextChanged_lastChanged = quant_et;
			quant_et.setText(String.format("%1$1.3f", quant));
			return;
		}

		if (hasQuant && hasTotal && ! hasPerU)
		{
			if (perunit_et.hasFocus() || (perunit_et == afterTextChanged_lastChanged))
			{
				afterTextChanged_lastChanged = null;
				return;  // prevent endless callback-loop
			}
			final float per_u;
			try {
				per_u = parseDF.parse(totalcost_et.getText().toString()).floatValue()
				      / parseDF.parse(quant_et.getText().toString()).floatValue();
			} catch (ParseException pe) {
				return;
			}
			perunit_calc = true;
			afterTextChanged_lastChanged = perunit_et;
			perunit_et.setText(String.format("%1$1.2f", per_u));  // TODO dynamic # of digits from currV
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

	/** For {@link GasBrandGrade} autocomplete, the callback for {@link OnItemClickListener} */
	public void onItemClick(AdapterView<?> parent, View clickedOn, int position, long rowID)
	{
		ListAdapter ga = brandGrade_at.getAdapter();
		if (ga == null)
			return;
		brandGradeObj = (GasBrandGrade) ga.getItem(position);
	}

}
