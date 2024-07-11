# Shadowlands Roadtrip version history

Older revisions were svn, more recent ones are git tags and commits.
The project repo was migrated from subversion to git on 2015-05-03;
for details see roadtrip-svn2git.sh comments.

See doc/hist/*.sql for previous schema updates.

## Release commits:

Because a git commit can't predict and include its own hash, a released
version's copy of `versions.md` does not contain its release date or
its most recent commit hash. The `release-#.#.##` tag in git points to the
commit which was tested for that release, and the released APK's
About box also shows that same commit hash.

After release and before continuing development, versions.md should be
updated to list the released version's release date and commit hash.

# Versions:

# 0.9.93

Being developed now; improvements and bugfixes since 0.9.92

## SLRoadtrip android app:
- Odometer calculator dialog:
  - Remember memory value between uses
- Set targetSdkVersion to API 23 (Android 6.0), minSdkVersion to 19 (4.4)
## Code internals:
- Rename master branch to main

# 0.9.92

2022-12-11: df60484

## SLRoadtrip android app:
- Show Logbook:
  - When stop has total but no trip odometer, calculate delta from prev stop's total odometer ignoring 10ths digit
  - View details of a past trip's TStop:
    - Can select and copy Location and Via Route text
  - Search by Location:
    - Bugfix: If 2 trips were on same date in different years, wasn't showing a date for 2nd trip

# 0.9.91

2020-01-31: ae769f3

## SLRoadtrip android app:
- Show Logbook:
  - Show dates in bold for visibility
  - Remember currently shown vehicle when starting all-vehicle Location Mode,
    for use in next view from that activity (Go to Date, Recent Gas, next Location search)
- Odometer calculator dialog:
  - Rearrange layout bottom rows: Make '=' wide instead of '0'
  - Added PTE button: Loads Previous Trip Ending odometer value if available
  - Backspace: Change symbol; fix bug where if first press is backspace, then digit, would erase previous contents

## Database schema:
- No changes to schema itself
- `Vehicle.last_tripid` field: Don't set to current trip when switching to different vehicle
  - In versions before 0.9.20, that field strictly held the vehicle's last completed trip
  - In 0.9.20 it started also being used to hold vehicle's current trip when changing current vehicle
    to a different one
  - 0.9.40 added the `veh_settings` table which holds each vehicle's current trip
  - Up through 0.9.90, the previous vehicle's `last_tripid` was also still set at vehicle change
  - This version 0.9.91 no longer sets `last_tripid` to current trip while switching to a different vehicle

# 0.9.90

2019-10-18: b24ffc1

## SLRoadtrip android app:
- Show Logbook:
  - Search by Location:
    - Show results with yellow highlight for location names,
      instead of all-caps used in previous versions
    - Add 'x' to clear location text field
    - Bugfix: No longer excludes some trips to searched location
      by infrequently-used vehicles
  - Bugfix: Previously if used Go to Date, then tap Earlier Trips,
    then Later Trips, those trips would be inserted above the end
- Main: Show current area during roadtrip, not starting area

## BookEdit desktop utility app:
- When first DB is opened, print SQLiteJDBC version to console for debugging
- When opening a DB, if integrity_check fails, print exception details to console for debugging

## Database schema:
- Add a few more brand names


# 0.9.80

2019-05-31: d137727

## SLRoadtrip android app:
- Bugfix: Previously did not record a new Via Route's distance from
  trip's starting location to first stop, if trip had different
  starting location or odometer change from end of previous trip
- Update odometer style to standard NumberPicker
- Logbook: View TStop details: To reduce clutter, hide odometer checkboxes;
  if odometer not entered, hide its entire row
- Add padding at screen edges for all activities, adjust odometer/time-picker spacing
- Development: Convert project to Android Studio/Gradle from Eclipse ADT

## BookEdit desktop utility app:
- Add vehicle dropdown at top to switch vehicles, instead of small button at bottom

# 0.9.70

2019-05-20: e4bf4f5

## SLRoadtrip android app:
- When starting a trip:
  - Can change vehicle's GeoArea
  - Removed redundant "Continue From" row, to de-clutter layout
- If a backup fails validation and can't be restored, show which level it failed at

## BookEdit desktop utility app:
- If validation fails, show technical details of failed data items in case they can be triaged or corrected

## Database schema and model:
- Vehicle:
  - To use a new vehicle immediately, give currency/unit fields their
    default values in object, not just in DB record
- RDBVerifier:
  - Give details about failures, keep going up to 100 failures
  - Also validate TStops' ViaRoute from/to locations

# 0.9.61

2017-04-02: d577e7e

## SLRoadtrip android app:
- Logbook validation dialog: If successful and more than 10 days since last backup,
  ask user if they would like to go to the Backups screen
- Backups: Always show directory path in activity title

## Database schema and model:
- LogbookTableModel location search: Also find trips starting from the location,
  not only those with TStops there
- Trip: New field so trips' starting location can be searchable
- TStop: New optional field for total expenses paid at the stop

# 0.9.60

2017-01-30: c34c351

## SLRoadtrip android app:
- LogbookShow can add and edit comments in past trips:
    - Tap on trip to see more details
    - From that detail view, tap TStop to add/edit/remove earlier comments
      or copy comments to clipboard
    - Search by Via Route from menu
    - Also shows "Current Trip in progress" indicator (not shown in 0.9.40 - 0.9.50)
- Stop during a Trip:
    - For easier use on roadtrips, first non-local stop can be outside all GeoAreas:
      Added "(none)" to area list shown with local trip's Change button
    - If Location or Via Route created at stop, save any changes to its case/capitalization
- End Trip:
    - Always show "Save Changes" button to save without ending trip now,
      even if not currently stopped
    - On roadtrips, use previous stop's GeoArea if possible instead of using
      trip's end area (which was guessed from first stop outside start area)

## Database schema and model:
- Location.getByDescr: Use areaID in query
- TStop: Support for adding/editing comment after continuing from stop,
  including new flags FLAG\_COMMENT\_ADDED, FLAG\_COMMENT\_EDITED, FLAG\_COMMENT\_REMOVED
- Trip.TLTR.getTripRowsTabbed: Don't append trailing tabs when final columns are blank
- VehSettings: During roadtrips, update CURRENT_AREA when new TStop is in a different area

# 0.9.50

2016-05-10: 62d38f2

## Focus on Flexibility and Usability:
- For flexibility and simpler UI, all trips start as local; can be converted
  to roadtrips at any stop.
  - Any stop during roadtrips can be in any geoarea, not only the starting and ending area
  - The roadtrip's ending area can be changed if needed when ending the trip
  - If all of a roadtrip's stops are in the starting area, it's converted back to a local trip when ended
- Trip stop comment max length increased to 2000 characters from 255
- More 'business logic' moved from app UI to DB methods: VehSettings.endCurrentTrip, etc

## SLRoadtrip android app:
- Can undo some trip actions: Continue Travel From Stop during a trip, End Trip
- LogbookShow: When searching for trips by location, show matching stops in all caps
  (in a later version these can be highlighted properly)
- TripBegin Recent Historical Mode: To more easily enter a series of trips occurring in the past,
  recognize when the previous trip was 'historical' when entered, and if it was entered recently
  ask if the current trip should also be historical or should start at the current time.
- LogbookShow: When picking from 'Other Vehicle' spinner, dismiss that dialog and
  immediately show new vehicle
- LogbookShow DatePicker: To always show year spinner, hide calendar pane on narrow screens
- Odometer calculator dialog:
  - Enable MR, MC buttons only if something is saved to memory
  - To prevent changing odometer to second operand instead of result,
    disable Save after any op button until Equals, Reset, or Clear is pressed
  - When saving to odometer, ignore negative sign
  - Show operation and memory status to left of input field
- While validating a backup to restore it, show file size and trip date range
- VehicleEntry: Fix bug where some fields were read-only when entering new vehicle while previously-
  current vehicle is on a trip

## BookEdit desktop utility app:
- Show TStop comments without superfluous square brackets

# 0.9.43

2015-06-07: e3dae2e

## SLRoadtrip android app:
- Hide Frequent Trip buttons by default on new installs. (Settings menu can un-hide them)
- About box: Show build number at bottom, not in title bar. (git hashes are unwieldy up there)

## BookEdit desktop utility app:
- First release to include a BookEdit jar: Ant build.xml creates slroadtrip-bookedit.jar
- View/edit details for all drivers and vehicles, including vehicle Active flag.
- Better error handling if driver/vehicle settings are missing in db.

## Database schema and model:
- New field "Date Added" for drivers, vehicles, GeoAreas
- Vehicle: Model year no longer required; has always allowed 0 in schema and apps

# 0.9.42

2015-05-06: 66a175e

Minor android improvements and bugfixes since 0.9.41:
- Change linked odometer at 1st whole-number change, not just 2nd and further ones
- Disable odometer button wraparound (0 -> 9999999) seen in trip odo
- TripTStopEntry calculator: Add Reset button, Clear no longer doubles as Reset

# (no release)

2015-05-03: 3393a0f/r431

Migration from svn to git, google code to github

# 0.9.41

2015-04-27: 06a4508/r428

- Less clutter in vehicle dropdowns: Show only Active or Inactive vehicles, with "Other..." entry
  to switch, in Bookedit and in android Logbook and Recent Gas.
- New Vehicle activity asks for GeoArea, instead of using the current vehicle's current area.
- When a new vehicle is added, ChangeDriverOrVehicle always asks whether to change the current vehicle.
- Bookedit: When opening an older-schema file for edit, asks whether to upgrade in place
  or make a read-only copy like it does for viewing older backups.

# 0.9.40

2014-08-27: d4e9957/r409

- Most current settings are now per-vehicle
- Refactor DB-restore code
- Bookedit can view backups having old schemas
- Android: Set build target and targetSdkVersion to 11 (v3.0) for ActionBar and Holo Light theme if available,
  but keep minSdkVersion at 8 (v2.2)

# 0.9.20

2013-10-22: 2bd5f39/r347

- Can edit vehicle & some related settings during trip
- Add landscape layouts
- Backup/Restore: Add Change Folder button
- TripBegin: Ask before using historical mode

# 0.9.20b (beta)

2013-01-15: 879acb3/r315

Beta to distribute for testing.
- Allow save without continuing from stop
- At startup, button to restore backup
- Schema: Vehicle +plate/tag#; trip +#passengers
- 1-step validation in LogbookShow
- beta: CSV log export

# 0.9.12

2012-05-06: a48d567/r271

- Add Settings screen to make some fields required, optional, or hidden
- LogbookShow add search for locations in geoarea "(none)"
- Move DB validation to background task

# 0.9.08

2012-04-01: 6351b55/r246

First version released as an APK
- List/Edit vehicles and drivers
- After adding new vehicle or driver, ask whether to change current setting
- Vehicle +"in use since" date field
- Add optional Category to trips/frequent trips
- Initial setup: Prompt for name of home GeoArea
- About dialog: Clickable links, get build number from res/raw/svnversion.txt

# 0.9.07 betas (unreleased)

2011-12-11: edd45dc/r173

- Bookedit LogbookTableModel: Show comment column
- Android:
  - LogbookShow: Button to show other vehicle
  - TripTStopEntry: Confirm if continue with no odos entered;
    remove the total-odometer 0.5mi "drift" correction introduced in r133, r137
  - Change backup path on SD card from /SLRoadtrip/db to /SLRoadtrip/backup

2011-09-22: a52abbb/r164

- Format dates/times (RTRDateTimeFormatter) from locale/user perferences
- TripTStopEntry calculator popup for odometer

2011-08-25: cbf45dc/r149

Mostly android improvements:
- LogbookTableModel.addEarlierTrips: Search again if LTM constructor limited trip date range
- Large screen/tablet support
- LogbookShow:
  - Check for current trip before deciding there are none
  - Go to Date: Can pick a vehicle
- TripTStopEntry:
  - Don't wipe out time\_continue if no time\_stop
  - Keep updating Continue Time to current minute, unless user changes it manually
  - Correct total-odometer drift only if greater than currT.readHighestOdoTotal()
- Add About dialog

2011-07-21: 5e8890c/r118

Add RDBVerifier
- Can run verifier from bookedit or LogbookShow

Android improvements:
- Validate backup file before restoring; warn if newer trips would be overwritten;
  handle earlier schema versions
- LogbookShow: Can search trips by location in any area, for all vehicles;
  'Go To Date' menu option
- Move backup dir to /sdcard/SLRoadtrip from /sdcard/app_back/org.shadowlands.roadtrip:  
  Is one-time correction, you must manually move your existing backups

# 0.9.06.2

2011-04-18: e70bca8/r64

Android improvements:
- LogbookShow: Location mode filter; include current trip
- TripTStopEntry: Geoarea buttons during roadtrips; dropdown button for ViaRoute;
  remember location's previous GasBrandGrade
- TripBegin: Fix exception when continue from previous location when odometer increases (Gap in vehicle history)
- Add list of recent gas stops
- Allow cancel current trip if no tstops yet

# 0.9.06.1

2011-02-19: 281a1fe/r25

- TripTStopEntry: keep data when device rotates

# 0.9.06

2011-02-13: e993e29/r24

- Finish TStopGas (db schema and android activity)
- TripTStopEntry during trip: more lenient odometer checks
- LogbookShow, bookedit: query and load trips a few weeks at a time

# 0.9.05

2010-11-30: 1d13b55/r5

- TStopGas sets flag_sides=256 in TStop

# 0.9.01

2010-11-20

- Add Frequent Trips; via\_route +odo\_dist

# 0.8.13

2010-11-11

- Add via\_route table for tstops and freqtrips, PREV\_LOCATION setting

# 0.8.12

2010-11-07

- TStop +geoarea id

# 0.8.09

2010-10-26

- Location table, CURRENT_VEHICLE setting
- Check settings at AndroidStartup, recover if missing

# 0.8.07

2010-10-15

- Android TripBegin: Historical Mode to add older trips to db later;
  used when previous trip was more than a few days ago
- Bookedit viewer: Add vehicle dropdown
- Add schema fields for vehicle distance/currency units/decimal places (US defaults)

# 0.8.05

2010-10-06

- First install on any device hardware

