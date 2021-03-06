/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010-2012,2014-2015,2017,2019-2020 Jeremy D Monin <jdmonin@nand.net>
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

import java.io.DataInputStream;
import java.io.InputStream;

import org.shadowlands.roadtrip.AndroidStartup;
import org.shadowlands.roadtrip.R;
import org.shadowlands.roadtrip.android.util.Misc;
import org.shadowlands.roadtrip.db.FreqTrip;
import org.shadowlands.roadtrip.db.GeoArea;
import org.shadowlands.roadtrip.db.Person;
import org.shadowlands.roadtrip.db.RDBAdapter;
import org.shadowlands.roadtrip.db.Settings;
import org.shadowlands.roadtrip.db.TStop;
import org.shadowlands.roadtrip.db.Trip;
import org.shadowlands.roadtrip.db.TripCategory;
import org.shadowlands.roadtrip.db.VehSettings;
import org.shadowlands.roadtrip.db.Vehicle;
import org.shadowlands.roadtrip.db.android.RDBOpenHelper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Main activity/screen of the application.
 * Go here after CURRENT_DRIVER and CURRENT_VEHICLE are set.
 * If these aren't found, go back to AndroidStartup activity.
 *<P>
 * Also contains the About box dialog.
 *
 * @author jdmonin
 */
public class Main extends Activity
{
	private RDBAdapter db = null;

	/** Current vehicle; updated in {@link #updateDriverVehTripTextAndButtons()} */
	Vehicle currV = null;

	/**
	 * Holds 'Trip in Progress' status text; updated in {@link #updateDriverVehTripTextAndButtons()}.
	 * For current roadtrip, show source and destination GeoAreas.
	 * For categorized trip, show the category.
	 */
	private TextView tvCurrentSet;

	private Button btnBeginTrip, btnBeginFreq, changeDriverOrVeh, endTrip, stopContinue;

	/** Called when the activity is first created.
	 * See {@link #onResume()} for remainder of init work,
	 * which includes checking the current driver/vehicle/trip
	 * and hiding/showing buttons as appropriate.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		tvCurrentSet = (TextView) findViewById(R.id.main_text_current);
		db = new RDBOpenHelper(this);

		btnBeginTrip = (Button) findViewById(R.id.main_btn_begin_trip);
		btnBeginFreq = (Button) findViewById(R.id.main_btn_begin_freqtrip);
		endTrip      = (Button) findViewById(R.id.main_btn_end_trip);
		stopContinue = (Button) findViewById(R.id.main_btn_stop_continue);
		changeDriverOrVeh = (Button) findViewById(R.id.main_btn_change_driver_vehicle);

		// Allow long-press on Frequent-trip button (TODO) better interface for this
		registerForContextMenu(btnBeginFreq);

		if (Settings.getBoolean(db, Settings.HIDE_FREQTRIP, false))
			btnBeginFreq.setVisibility(View.GONE);

		// see onResume for rest of initialization.
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.main_menu, menu);
		return true;
	}

	/**
	 * If current trip, enable and update wording of "undo"/"cancel" menu item.
	 * The trip conditions tested here are also checked in {@link #onUndoCancelItemSelected()}
	 * to determine which action to take; see that method's javadoc for details.
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		super.onPrepareOptionsMenu(menu);
		MenuItem item = menu.findItem(R.id.menu_main_undocancel);
		if (item != null)
		{
			if (currV == null)
				currV = Settings.getCurrentVehicle(db, false);  // can happen after screen rotation

			final Trip currT = VehSettings.getCurrentTrip(db, currV, false);
			final boolean hasCurrTS = (currT != null)
				&& (VehSettings.getCurrentTStop(db, currV, false) != null);
			final boolean enable = ((currT != null) || (currV.getLastTripID() != 0)) && ! hasCurrTS;
			item.setEnabled(enable);
			if (enable)
			{
				if (currT == null)
					item.setTitle(R.string.main_undo_end_trip);
				else if (canUndoContinueFromTStop(currT))
					item.setTitle(R.string.main_undo_continue);
				else
					item.setTitle(R.string.cancel_trip);
			} else {
				item.setTitle(R.string.undo);
			}
		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
		case R.id.menu_main_backup:
			// this activity's onPause will call db.close(),
			// so it's safe if we restore db from BackupsRestore.
			startActivity(new Intent(Main.this, BackupsMain.class));
			return true;

		case R.id.menu_main_undocancel:
			// several things can be undone, depending on current conditions within the trip.
			onUndoCancelItemSelected();
			return true;

		case R.id.menu_main_settings:
			startActivity(new Intent(Main.this, SettingsActivity.class));
			return true;

		case R.id.menu_main_about:
			showDialog(R.id.menu_main_about);
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/** Create long-press menu for Frequent buttons */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
	{
		if (v == btnBeginFreq)
			menu.add(Menu.NONE, R.id.main_btn_begin_freqtrip,
				 Menu.NONE, R.string.main_create_freq_from_recent);
		else
			super.onCreateContextMenu(menu, v, menuInfo);
	}

	/** Handle long-press on Frequent buttons: Call {@link #listRecentTripsForMakeFreq()} */
	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
		case R.id.main_btn_begin_freqtrip:
			listRecentTripsForMakeFreq();
			break;

		default:
			return super.onContextItemSelected(item);
		}
		return true;
	}

	/**
	 * Create the About dialog.
	 * The version number is determined from app resources.
	 * The build number is read from res/raw/gitversion.txt which is manually
	 * updated by the developer before building an APK.
	 */
	@Override
	protected Dialog onCreateDialog(int id)
	{
		Dialog dialog;
		switch(id)
		{
		case R.id.menu_main_about:
			{
				// R.string.app_about is the multi-line text.
				final TextView tv_about_text = new TextView(this);
				final SpannableStringBuilder about_str =
					new SpannableStringBuilder(getText(R.string.app_about));

				// Now try to append build number, from res/raw/gitversion.txt ; ignore "?"
				InputStream s = null;
				try
				{
					final Resources res = getApplicationContext().getResources();
					s = res.openRawResource(R.raw.gitversion);
					DataInputStream dtxt = new DataInputStream(s);
					String gitversion = dtxt.readLine();
					dtxt.close();
					if ((gitversion != null)
						&& (gitversion.length() > 0)
						&& (! gitversion.equals("?")))
					{
						about_str.append("\n");
						about_str.append(res.getString(R.string.build_number__fmt, gitversion));
							// "Build number: 66a175e"
					}
				}
				catch (Exception e) {}
				finally
				{
					if (s != null)
					{
						try { s.close(); }
						catch (Exception e) {}
					}
				}

				Linkify.addLinks(about_str, Linkify.WEB_URLS);
				tv_about_text.setText(about_str);
				tv_about_text.setMovementMethod(LinkMovementMethod.getInstance());

				AlertDialog.Builder aboutBuilder = new AlertDialog.Builder(this);
				aboutBuilder.setView(tv_about_text)
				  .setCancelable(true)
				  .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
				   {
					public void onClick(DialogInterface dialog, int id) {
						dialog.dismiss();
					}
				   });

				// get our version dynamically.
				// title format: About Shadowlands Roadtrip v0.9.07
				StringBuffer title = new StringBuffer(getResources().getString(R.string.about));
				title.append(' ');
				title.append(getResources().getString(R.string.app_name));
				try
				{
					PackageInfo pInfo = getPackageManager().getPackageInfo
						(getPackageName(), PackageManager.GET_META_DATA);
					if (pInfo != null)
					{
						String versName = pInfo.versionName;
						if ((versName != null) && (versName.length() > 0))
						{
							title.append(" v");
							title.append(versName);
						}
					}
				}
				catch (NameNotFoundException e) {}

				aboutBuilder.setTitle(title);
				dialog = aboutBuilder.create();
				// override default 0 spacing between text and edge of dialog
				((AlertDialog) dialog).setView(tv_about_text, 9, 9, 9, 9);
			}
			break;

		default:
			dialog = null;
		}

		return dialog;
	}

	/**
	 * Handle the "Undo"/"Cancel" menu/actionbar item: Ask the user to confirm the undo, then do so.
	 * To determine which trip action to undo, checks current conditions within the trip.
	 * See list below for details. {@link #onPrepareOptionsMenu(Menu)} checks the same conditions
	 * to enable and name the action bar / options menu item.
	 *<P>
	 * Current capabilities:
	 *<UL>
	 * <LI> No current or previous trip: Nothing to undo ({@link Vehicle#getLastTripID()} == 0)
	 * <LI> Trip has no {@link TStop}s yet: {@link #confirmCancelCurrentTrip()}
	 * <LI> Stopped at a TStop: Can't undo
	 * <LI> After continuing from a TStop: {@link #askUndoContinueFromTStop()}
	 * <LI> After ending a trip: {@link #askUndoEndTrip()}
	 *</UL>
	 * @since 0.9.50
	 */
	private void onUndoCancelItemSelected()
	{
		final Trip currT = VehSettings.getCurrentTrip(db, currV, false);
		if (currT == null)
		{
			askUndoEndTrip();
			return;
		}

		if (VehSettings.getCurrentTStop(db, currV, false) != null)
			return;  // can't undo at a TStop; onPrepare disabled the menu item

		if (canUndoContinueFromTStop(currT))
			askUndoContinueFromTStop();
		else
			confirmCancelCurrentTrip();
	}

	/**
	 * Could the user undo continuing from the current trip's previous stop,
	 * given current conditions?  Used for action bar setup.
	 * @param currT  Current trip, if any, from {@link VehSettings#getCurrentTrip(RDBAdapter, Vehicle, boolean)}
	 * @return  True if the conditions are met for {@link Trip#cancelContinueFromTStop()}
	 * @see #askUndoContinueFromTStop()
	 * @since 0.9.50
	 */
	private boolean canUndoContinueFromTStop(final Trip currT)
	{
		return (currT != null) && (VehSettings.getCurrentTStop(db, currV, false) == null)
			&& currT.hasIntermediateTStops();
	}

	/**
	 * Ask the user to confirm that they want to undo continuing from the previous stop.
	 * Called from {@link #onUndoCancelItemSelected()}. Shows a dialog with location info,
	 * how long ago travel was continued, and Undo/No buttons.
	 * If user confirms, calls {@link Trip#cancelContinueFromTStop()}.
	 * @see #canUndoContinueFromTStop(Trip)
	 * @since 0.9.50
	 */
	private void askUndoContinueFromTStop()
	{
		final Trip currT = VehSettings.getCurrentTrip(db, currV, false);
		boolean hasCurrTS = (currT != null) && (VehSettings.getCurrentTStop(db, currV, false) != null);
		if ((currT == null) || hasCurrTS || ! currT.hasIntermediateTStops())
			return;  // should not occur, checking here just in case

		final TStop prevTS = currT.readLatestTStop();
		if (prevTS == null)
			return;  // unlikely unless db inconsistency

		String locName = prevTS.readLocationText();
		if (locName == null)
			locName = "(null)";  // fallback if inconsistent

		_askUndoDialogWithTime
		(getResources().getString(R.string.main_undo_continue__text, locName),
		 prevTS.getTime_continue(), R.string.main_undo_continue__left_this_stop,
		 new DialogInterface.OnClickListener()
		 {
			public void onClick(DialogInterface dialog, int whichButton)
			{
				try
				{
					currT.cancelContinueFromTStop();
				}
				catch (IllegalStateException e)
				{
					Misc.showExceptionAlertDialog(Main.this, e);  // unlikely
				}

				updateDriverVehTripTextAndButtons();
			}
		 } );
	}

	/**
	 * Prompt user if wants to cancel the current trip (if that's possible).
	 * If they confirm, delete it and clear current-trip settings
	 * by calling {@link Trip#cancelAndDeleteCurrentTrip()}.
	 */
	public void confirmCancelCurrentTrip()
	{
		boolean canCancel = true;
		final Trip currT = VehSettings.getCurrentTrip(db, currV, false);
		TStop currTS = ((currT != null) ? VehSettings.getCurrentTStop(db, currV, false) : null);
		if (currTS != null)
		{
			canCancel = false;
		} else if (currT != null) {
			// Any TStops?
			canCancel = ! currT.hasIntermediateTStops();
		}

		if (! canCancel)
		{
			Toast.makeText(this, R.string.main_cancel_cannot_with_stops, Toast.LENGTH_SHORT).show();
			return;  // <--- Early return: Cannot cancel ---
		}

		// Prompt user if wants to revert back to locObjOrig.
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setMessage(R.string.main_cancel_are_you_sure);
		alert.setPositiveButton(R.string.cancel_trip, new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int whichButton)
			{
				final boolean isFreq = currT.isFrequent();
				try
				{
					currT.cancelAndDeleteCurrentTrip();
					VehSettings.setCurrentTrip(db, currV, null);
					if (isFreq)
						VehSettings.setCurrentFreqTrip(db, currV, null);
				}
				catch (IllegalStateException e) {
					Misc.showExceptionAlertDialog(Main.this, e);  // unlikely
				}

				checkCurrentDriverVehicleSettings();
				updateDriverVehTripTextAndButtons();
			}
		});
		alert.setNegativeButton(R.string.continu, null);

		alert.show();
	}

	/**
	 * Ask the user to confirm that they want to undo ending the previous trip.
	 * Called from {@link #onUndoCancelItemSelected()}. Shows a dialog with
	 * how long ago the trip was ended, and Undo/No buttons.
	 * If user confirms, calls {@link Trip#cancelEndPreviousTrip(RDBAdapter)}.
	 * @since 0.9.50
	 */
	private void askUndoEndTrip()
	{
		if (null != VehSettings.getCurrentTrip(db, currV, false))
			return;  // should not occur, menu item would be disabled; checking here just in case

		final Trip prevTrip = Trip.recentTripForVehicle(db, currV, false);
		if (prevTrip == null)
			return;  // should not occur, item would be disabled
			// Note: If ! prevTrip.isEnded() because of inconsistent data (unlikely),
			// calling Trip.cancelEndPreviousTrip will throw an exception
			// and showExceptionAlertDialog will show it, which is fine.

		_askUndoDialogWithTime
			(getResources().getString(R.string.main_undo_end_trip__text),
			 prevTrip.getTime_end(), R.string.main_undo_end_trip__this_trip_ended,
			 new DialogInterface.OnClickListener()
			 {
				public void onClick(DialogInterface dialog, int whichButton)
				{
					try
					{
						Trip.cancelEndPreviousTrip(db);
					}
					catch (IllegalStateException e)
					{
						Misc.showExceptionAlertDialog(Main.this, e);  // unlikely
					}

					updateDriverVehTripTextAndButtons();
				}
			 } );
	}

	/**
	 * Utility method to show a confirmation dialog before undoing a trip action that happened some time ago.
	 * Buttons are Undo (positive) and No (negative).
	 *<P>
	 * If {@code time} != 0, it will be formatted to a relative time like "3 hour ago"
	 * using {@link DateUtils#getRelativeTimeSpanString(long, long, long)
	 * DateUtils.getRelativeDateTimeString}(time * 1000L, {@link System#currentTimeMillis()},
	 * {@link DateUtils#SECOND_IN_MILLIS}), placed into {@code timeTextStringRsrcId},
	 * and appended to {@code text}.
	 * @param text  Main text of the dialog
	 * @param time  Time at which the undone action happened, or 0 if not known
	 * @param timeTextStringRsrcId  String into which to place {@code time}, from {@code R.string}. Will call
	 *        {@link Resources#getString(int, Object...) res.getString(timeTextStringRsrcId, formattedTime)}.
	 * @param onClickUndo  If Undo button is clicked, calls this method
	 * @since 0.9.50
	 */
	@SuppressLint("DefaultLocale")
	private void _askUndoDialogWithTime
		(CharSequence text, final int time, final int timeTextStringRsrcId,
		 final DialogInterface.OnClickListener onClickUndo)
	{
		if (time != 0)
		{
			StringBuilder sb = new StringBuilder(text);

			// "5 minutes ago", "3 days ago", etc; past 1 week ago it formats time's date instead.
			CharSequence timediff = DateUtils.getRelativeTimeSpanString
				(time * 1000L, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS);
			// Format may place timediff in middle of sentence, so force lowercase:
			// example Spanish "Hace 3 minutos" -> hace
			timediff = timediff.toString().toLowerCase();

			sb.append("\n\n");
			sb.append(getResources().getString(timeTextStringRsrcId, timediff));
			text = sb;
		}

		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setMessage(text);
		alert.setPositiveButton(R.string.undo, onClickUndo);
		alert.setNegativeButton(R.string.no, null);

		alert.show();
	}

	/**
	 * Check Settings tables for {@link VehSettings#CURRENT_DRIVER}, {@link Settings#CURRENT_VEHICLE}.
	 * Set {@link #currD} and {@link #currV}. If there's an inconsistency between
	 * Settings/VehSettings and Vehicle/Person table keys, delete the Settings entry.
	 * {@code currD} and {@link #currV} will be null unless they're set consistently in Settings tables.
	 *<P>
	 * For initial setup, if {@link VehSettings#CURRENT_AREA} is missing, set it.
	 *
	 * @return true if settings exist and are OK, false otherwise.
	 */
	private boolean checkCurrentDriverVehicleSettings()
	{
		Vehicle cv = Settings.getCurrentVehicle(db, true);
		if (cv == null)
		{
			return false;
		} else {
			// check current GeoArea, update if missing
			// Before setting CURRENT_VEHICLE, copy its CURRENT_AREA to new vehicle, or use first geoarea

			GeoArea currA = VehSettings.getCurrentArea(db, cv, true);
			if (currA == null)
			{
				// No GeoArea setting, or no current vehicle: Probably initial setup
				GeoArea[] areas = GeoArea.getAll(db, -1);
				if (areas != null)
					currA = areas[0];

				if (currA != null)
					VehSettings.setCurrentArea(db, cv, currA);
			}

			// check current driver
			return (VehSettings.getCurrentDriver(db, cv, true) != null);
		}
	}

	/**
	 * Update the text about current driver, vehicle and trip;
	 * hide or show start-stop buttons as appropriate.
	 * Sets text of {@link #tvCurrentSet}, {@link #changeDriverOrVeh},
	 * {@link #stopContinue}, etc.
	 * For current roadtrip, show source and destination GeoAreas.
	 * For categorized trip, show the category.
	 */
	private void updateDriverVehTripTextAndButtons()
	{
		currV = Settings.getCurrentVehicle(db, false);
		GeoArea currA = VehSettings.getCurrentArea(db, currV, false);
		Person currD = VehSettings.getCurrentDriver(db, currV, false);
		Trip currT = VehSettings.getCurrentTrip(db, currV, true);
		TStop currTS = ((currT != null) ? VehSettings.getCurrentTStop(db, currV, false) : null);
		FreqTrip currFT = VehSettings.getCurrentFreqTrip(db, currV, false);

		final Resources res = getResources();
		StringBuffer txt = new StringBuffer(res.getString(R.string.driver));
		txt.append(": ");
		txt.append(currD.toString());
		txt.append("\n");
		txt.append(res.getString(R.string.vehicle));
		txt.append(": ");
		txt.append(currV.toString());
		txt.append("\n");
		txt.append(res.getString(R.string.area__colon));
		txt.append(' ');
		if (currA != null)
			txt.append(currA.toString());

		if (currT == null)
		{
			txt.append("\n\n");
			txt.append(res.getString(R.string.main_no_current_trip));
			changeDriverOrVeh.setText(R.string.change_driver_vehicle);
		} else {
			txt.append("\n\n");

			if (currT.getRoadtripEndAreaID() == 0)
				txt.append(res.getString(R.string.main_trip_in_progress));
			else
				txt.append(res.getString(R.string.main_roadtrip_in_progress));

			String currTCateg = null;
			if (currT.getTripCategoryID() != 0)
			{
				try
				{
					TripCategory tc = new TripCategory(db, currT.getTripCategoryID());
					currTCateg = " [" + tc.getName() + "]";
				}
				catch (Throwable th) {}
			}

			if (currTCateg != null)
				txt.append(currTCateg);
			if (currFT != null)
			{
				txt.append("\n");
				txt.append(res.getString(R.string.main_from_frequent_trip));
				txt.append(' ');
				txt.append(currFT.toString());
			}
			changeDriverOrVeh.setText(R.string.view_drivers_chg_vehicle);
			if (currTS != null)
				stopContinue.setText(R.string.continu_from_stop);
			else
				stopContinue.setText(R.string.stop_continue);
		}

		tvCurrentSet.setText(txt);

		final int visTrip, visNotTrip;
		if (VehSettings.getCurrentTrip(db, currV, false) != null)
		{
			visTrip = View.VISIBLE;
			visNotTrip = View.INVISIBLE;
		} else {
			visTrip = View.INVISIBLE;
			visNotTrip = View.VISIBLE;
		}
		btnBeginTrip.setVisibility(visNotTrip);
		if (Settings.getBoolean(db, Settings.HIDE_FREQTRIP, false))
		{
			btnBeginFreq.setVisibility(View.GONE);
		} else {
			btnBeginFreq.setVisibility(visNotTrip);
		}
		endTrip.setVisibility(visTrip);
		stopContinue.setVisibility(visTrip);
	}

	@Override
	public void onPause()
	{
		super.onPause();
		if (db != null)
			db.close();
	}

	// TODO javadoc
	@Override
	public void onResume()
	{
		super.onResume();
		if (! checkCurrentDriverVehicleSettings())
		{
			Toast.makeText(getApplicationContext(),
				"Current driver/vehicle not found in db",
				Toast.LENGTH_SHORT).show();
			startActivity(new Intent(Main.this, AndroidStartup.class));
			finish();
			return;
		}

		// Give status
		updateDriverVehTripTextAndButtons();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		if (db != null)
			db.close();
	}

	/** Show {@link TripBegin} to begin a new non-frequent trip. */
	public void onClick_BtnBeginTrip(View v)
	{
		beginTrip(false);
	}

	/** Show {@link TripBegin} to begin a new frequent trip. */
	public void onClick_BtnBeginFreq(View v)
	{
		beginTrip(true);
	}

	public void onClick_BtnEndTrip(View v)
	{
		Intent tbi = new Intent(Main.this, TripTStopEntry.class);
		tbi.putExtra(TripTStopEntry.EXTRAS_FLAG_ENDTRIP, true);
		startActivity(tbi);
	}

	/**
	 * Go to {@link TripTStopEntry} to begin or finish a TStop.
	 */
	public void onClick_BtnStopContinue(View v)
	{
		startActivity(new Intent(Main.this, TripTStopEntry.class));
		// Afterward, onResume will set or clear currTS, update buttons, etc.
	}

	public void onClick_BtnChangeDriverVehicle(View v)
	{
		// If we have a current trip, ChangeDriverOrVehicle is view-only.
		startActivityForResult
		   (new Intent(Main.this, ChangeDriverOrVehicle.class),
			R.id.main_btn_change_driver_vehicle);
	}

	public void onClick_BtnShowLogbook(View v)
	{
		startActivity(new Intent(Main.this, LogbookShow.class));
	}

	public void onClick_BtnBackups(View v)
	{
		startActivity(new Intent(Main.this, BackupsMain.class));
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, Intent data)
	{
		if (resultCode == RESULT_CANCELED)
			return;

		if (requestCode == R.id.main_btn_change_driver_vehicle)
			updateDriverVehTripTextAndButtons();
	}

	/** Start the {@link TripBegin} activity with these flags */
	private void beginTrip(final boolean isFrequent)
	{
		Intent tbi = new Intent(Main.this, TripBegin.class);
		if (isFrequent)
			tbi.putExtra(TripBegin.EXTRAS_FLAG_FREQUENT, true);
		startActivity(tbi);
	}

	/**
	 * To make a new frequent trip from the most recent trip,
	 * check the db and start the {@link TripCreateFreq} activity.
	 * If the most-recent trip is already based on frequent,
	 * ask the user to confirm first.
	 */
	private void listRecentTripsForMakeFreq()
	{
		Trip t = Trip.recentTripForVehicle(db, currV, false);
		if (t == null)
		{
			Toast.makeText(this, R.string.main_no_recent_trips_for_vehicle, Toast.LENGTH_SHORT).show();
			return;
		}

		// TODO some listing activity for them,
		//   instead of just most-recent
		Intent tcfi = new Intent(Main.this, TripCreateFreq.class);
		tcfi.putExtra("_id", t.getID());
		if (! t.isFrequent())
		{
			startActivity(tcfi);
		} else {
			final Intent i = tcfi;  // req'd for inner class use
			AlertDialog.Builder alert = new AlertDialog.Builder(this);

			alert.setTitle(R.string.confirm);
			alert.setMessage(R.string.main_trip_based_on_frequent);
			alert.setPositiveButton(R.string.continu, new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int whichButton)
				{
					startActivity(i);
				}
			});
			alert.setNegativeButton(android.R.string.cancel, null);

			alert.show();
		}
	}

}
