# Shadowlands Roadtrip Release Testing

Things to cover during release testing.

Also for testing a new target API level, or new android version on an emulator or device.


## Android app (roadtrip-an)

Before release: Along with these tests, the candidate build should also be used for several days of
daily trips with actual vehicle(s).

### Basics

- Main Activity should show current driver, vehicle, and area, and whether there is a current trip
- Buttons in Main should reflect whether current trip (Begin Trip or Continue/End Trip)
- Action bar -> About: Should bring up an About popup
  - Version is correct
  - Build number at bottom has the commit ID being tested
    - If not: Edit roadtrip-an/app/src/main/res/raw/gitversion.txt and rebuild APK
    - Do not commit changes to gitversion.txt; its contents in repo should remain `?`
  - Summary is correct (same as "app_about" in res/values/strings.xml)
  - Copyright years include current year
  - URLs in summary are clickable (github, license)
- Action bar -> Settings: Should bring up the Settings activity
  - Currently, only default settings must be tested
  - These configurations not always tested: Frequent Trips, Passengers entry field
  - Also not always tested: Hide Via Route, Require Trip Category

### Making trips

- Start a trip
- After starting a trip, main activity's menu should allow Cancel Trip
  - Dialog to confirm should show how long ago the trip started
- Start and finish a trip
  - Include at least 1 intermediate stop somewhere
  - Once trip has at least one stop, should not be able to cancel trip
  - While stopped, test Trip Stop activity's "Save changes" button once instead of "Continue from stop"
    - Should be able to change comment, odometer, via, and save changes
	- Logbook should show those changes
	- From Main activity, should then be able to tap Continue button to re-show Trip Stop activity and actually continue
  - During each stop, fill in Via and the trip odometer
  - After first stop, test "Undo Continue from Stop"
    - Should return you to being stopped there
  - Tap the odometer calculator button (pencil icon)
    - Test both Trip odometer and Total odometer calculator
	- Clear should zero the field in the calculator popup
	- Reset should give it the current odometer value
	- Save is grayed out until `=`, `+`, `-`, or another operator is tapped
	- Test both `+` and `-`
	- Test memory: M+, MR, M-, MR, MC, MR while entering values in popup
	- Save button should update odometer in the activity
  - End trip by returning to the start location
    - To test duplicate-prevention:
      - Type that location name, instead of tapping on the auto-complete
      - Change at least 1 letter's capitalization, should still use same Location within DB
      - Test Via Route field duplication the same way
  - Show Logbook afterwards: Should see trip details, including Via names and mileage between stops in tenths
- Start a trip which finishes in a different GeoArea
  - Include at least 1 stop in the starting area
    - To test duplicate-prevention: Start typing the name of the start location;
      auto-complete should show 1 match, not 2 with the same name
    - Test Via field duplicate-prevention the same way
    - Type or auto-complete the name of the intermediate stop from the previous trip
    - Via Route dropdown should show previously entered route
    - Via Route auto-complete should also show a match and be tappable
  - Include at least 1 stop in area "(none)", like a highway Rest Area
  - Stop again; should auto-complete locations in area "(none)"
  - Stop again, typing name of a new geographic area
  - Main activity "Current Area" should be the newly created one
  - Stop again; should auto-complete the location in the new area
  - Finish the trip
- Start a trip from a different GeoArea than previous trip
  - First, tap "Begin Trip" and note the starting location
  - When changing to the new Area, starting location name should be cleared out
  - Go back to Main activity instead of starting the trip
  - Tap "Begin Trip"
  - Change/add to the text in the starting location textfield
  - Now when changing to the new Area, that changed name should not be cleared out
  - Location name field should auto-complete location names from the selected Area
- Start and finish a trip which includes a stop for gas
  - Use the "fill up" checkbox
  - Save changes but don't continue yet
  - Now click Continue; Trip Stop activity should show green icon on Gas button
  - Should be able to click Gas button, change price or quantity, have it saved when continue trip from stop
  - Show Logbook: Should show updated Gas info at last stop
- Start another trip, stop at the same gas location
  - Gas brand/grade should default to the one from previous stop
  - Use the "fill up" checkbox again, for a later logbook test
  - End that trip
- After ending a trip, main activity's menu should allow to Undo End Trip
  - After undo: That trip should be current, and stopped at its last stop
    with buttons to Continue or End Trip

### Driver list

In Main activity, hit "Change Driver / Vehicle" button.

- "New driver" button should bring up the activity to enter new info; hit Back
- Hit "Edit drivers" button
- List shows all drivers alphabetically
- Tap a driver for details; should be properly formatted, including "added on" date
- Can edit name and/or comment
- New Driver button
  - Enter info for the new driver
  - List should show that new driver
  - Tap for details; should be properly formatted

### Vehicle list

In Main activity, hit "Change Driver / Vehicle" button.

- "New vehicle" button should bring up the activity to enter new info; hit Back
- Hit "Edit vehicles" button
- List should have vehicle active/inactive icons (green/gray)
- Tap a vehicle for details; should be properly formatted, including dates and odometers
- Make sure current vehicle is on a trip
  - Can just begin a trip, doesn't need to have any stops
- Current vehicle details should be read-only while on a trip
- Can add a new vehicle
  - Give the new vehicle a different Starting Area
  - Tap "Begin Trip" with that vehicle, should use that selected Area
  - Go Back instead of starting the trip
- To continue testing, current vehicle must not be on a trip
  - Can cancel trip, if started one with no stops for testing
- Can change or add vehicle's comment
- Can change a vehicle's active/inactive status; list shows updated status icon

### Logbook

In Main activity, tap "Show Logbook" button.

- Should see most recent trips for current vehicle
- Action bar button: Change vehicle
  - Select a vehicle
  - Should see that vehicle's most recent trips
  - Go back to first logbook screen
- Action bar button: Go to Date
  - Select a date, optionally another vehicle
  - Should see vehicle's trips on/after that date, if any
  - "Earlier Trips" button should work, or show toast indicating no earlier trips
  - Go back to first logbook screen
- Search for Location should filter for the entered location
  - Test for current vehicle, for All vehicles
  - Select a different area; dialog should then autocomplete locations in that area
  - Go back to first logbook screen
- Recent Gas button should show gas info, including calculated MPG between fill-up gas stops
- Search Via Routes button
  - Enter two locations
  - "Search" button should show all ViaRoutes between them in either direction, including distance if entered from trip odometers
  - Bring up dialog again, change dropdown to a different area
  - Now, location should auto-complete to those in the new area

### Validate DB

- In Logbook view, Menu -> Validate does so, then shows results in a message box
- If it's been several weeks since last backup, should then ask whether you want to backup now;
  - Say No, should do nothing
  - Validate again and say Yes, should take you to Backup List activity

### Backup/Restore/Initial Setup

#### Backup

From main activity, hit the Backups button.

- Should show dates for last backup time (if any), last trip time
- Should see list of all backup files
- Change Folder button:
  - Change Folder should let you type other paths (later versions can be more user-friendly)
  - Hit Change Folder, then clear the path field in the dialog, hit Change
  - Should then take you to Downloads folder as default location
- Backup Now button should show a toast with new backup's filename
- Using Android Studio device file explorer, or an Android file manager app:
  - Should be able to see that backup
  - Copy that backup off the device onto a computer for testing
  - Open the backup using BookEdit; should see recent trips as expected

#### Initial Setup

- On the device, clear Shadowlands Roadtrip data temporarily for testing
  - Android Settings -> Apps -> Recent/running apps
  - Tap Shadowlands Roadtrip
  - Force Stop
  - Storage -> Clear storage
- Start Shadowlands Roadtrip app
- Should see Welcome/initial setup activity
- Tap Restore button, should take you to Backups activity
- Go Back to Welcome activity
- Walk through Initial Setup
  - Should prompt you for a driver name, local geo area
  - Should have you enter a vehicle's info
  - Then, should be at main activity
- Validate new info
  - Hit "Change Driver/Vehicle" button
  - "Edit drivers": Should see new driver
  - "Edit vehicles": Should see new vehicle, tap for details
  - Go back to main activity
- Begin and end a quick local trip with 1 stop
- "Show Logbook": Should see that trip

#### Restore

- In main activity, hit "Backups" button
- Should show list of previous backups
- Tap most recent backup (from Backups testing above); Restore activity should open
- Should show backup file info: full path, size, backup time, trip date range
- Should begin validating the file, with some progress info
  - While validate is running, "Restore" button should be disabled/grayed out
  - When complete, "File validated" message should appear under file info
  - "Restore" button should then be enabled
- Tap Restore button
  - Should prompt you to replace all current data
  - Tap Cancel, should do nothing
  - Tap Restore again
  - Should prompt you to confirm overwriting newer trips
- After restore, will take you to main activity
- "Show Logbook" should show most recent trips from the restored backup
- Start and finish a new trip from the restored backup's current location

### Other functionality

- Optional test: Show Logbook -> Export
  - Enter a full path, writes a CSV there with current vehicle's trips
- Optional test: Non-standard Settings configurations  
  The current standard settings are:
  - [X] Hide Frequent Trip buttons
  - [X] Hide trip Passengers entry field
  - [ ] Hide Via Route entry field
  - [ ] Require trip category

## BookEdit utility

Test with a copy of a log backup (made using Android app), not the original backup.

### Basics

- Version shown at bottom of main dialog is correct
  - Including DB schema version: Check against `DATABASE_VERSION` in bookedit/src/org/shadowlands/roadtrip/db/RDBSchema.java
- Can open the backup using "View Backup" button's dialog
- In console or terminal that lauched BookEdit, should see schema version (`user_version`) when backup is opened
- Can browse current vehicle's most recent trips
- Can tap "Earlier Trips" button, shows them at top of log window
- Can tap "Vehicle" button to view logs of another vehicle, if any in backup
- Can see list of driver(s) and tap for details
- Can see list of vehicle(s), including active/inactive icon (green/gray), and tap for details
- Quit and re-launch BookEdit, tap View Backup: Dialog should start in folder of last-opened backup

### Other functionality

- "Validate DB" button does so, then shows results in a message box
- If you have a backup available from an earlier schema version:
  - Make a temporary copy
  - Use "View Backup" to open that copy
  - In console or terminal, should see schema version info and SQL commands from `upgradeCopyToCurrent` debug output
  - Should then be able to view info and browse trips from the upgraded backup copy

## Other checks

- doc/versions.md should list all notable changes in this release
- DB package consistency between Bookedit and Android app
  - Run this command at the root of the repo:  
    `diff -ur bookedit/src/org/shadowlands/roadtrip/db roadtrip-an/app/src/main/java/org/shadowlands/roadtrip/db`

    The only output should be:

        Only in roadtrip-an/app/src/main/java/org/shadowlands/roadtrip/db/: android
        Only in bookedit/src/org/shadowlands/roadtrip/db: jdbc
        Only in bookedit/src/org/shadowlands/roadtrip/db: script
