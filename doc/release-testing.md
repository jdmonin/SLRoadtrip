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
- After starting the trip, main activity's menu should allow Cancel Trip
  - Should first show a dialog to confirm
  - After cancel: Main should show no current trip, Show Logbook shouldn't show the cancelled trip
- Start and finish a trip
  - When beginning trip, give it a category from the dropdown
  - Include at least 1 intermediate stop somewhere
  - Fill out a comment for at least 2 stops (intermediate, finishing stop)
    - For one of them, make a comment with a very long sentence
      - Should soft-wrap to the next line instead of scrolling horizontally
      - Should accept newlines (Enter key)
  - Once trip has at least 1 stop, should not be able to cancel trip
  - During trip, Logbook view should show "Current Trip in progress" at bottom of trip details
  - While stopped, test Trip Stop activity's "Save changes" button once instead of "Continue from stop"
    - Should be able to change comment, odometer, via, and save changes
    - Logbook should show those changes
    - From Main activity, should then be able to tap Continue button to re-show Trip Stop activity and actually continue
  - During each stop, fill in Via and the trip odometer
  - After first stop, test "Undo Continue from Stop" menu item
    - Should return you to being stopped there
  - Make a new Stop, Continue trip without entering a total or trip odometer value
    - When you hit continue, should ask "Are you sure"
  - Tap the odometer calculator button (pencil icon)
    - Test both Trip odometer and Total odometer calculator
    - Clear should zero the field in the calculator popup
    - Reset should give it the current odometer value
    - Save is grayed out until `=`, `+`, `-`, or another operator is tapped
    - Test both `+` and `-`
    - Test memory: M+, MR, M-, MR, MC, MR while entering values/using digit buttons
      - After MC is tapped: MC, MR buttons should be disabled
    - If this is vehicle's first trip, tapping PTE button should show a toast with text like
      "Vehicle has no Previous Trip"
    - For Trip odometer: If vehicle's previous trip didn't save an ending trip-odometer value,
      tapping PTE button should show a toast with text like
      "Previous Trip has no ending trip-odometer data"
    - Save button should update odometer in the activity
  - End trip by returning to the start location
    - For an upcoming test, enter a trip-odometer value
    - To test duplicate-prevention:
      - Type that location name, instead of tapping on the auto-complete
      - Change at least 1 letter's capitalization; should still use same Location record within DB
      - Test Via Route field dupe-prevention, with the same kind of typing capital change
      - For an upcoming test, note the Location and Via Route names typed here
  - Show Logbook afterwards: Should see trip details
    - Trip category, at start of trip in [brackets]
    - Via names
    - Mileage between stops in tenths
    - Location and Via names from "duplicate-prevention" test should be properly capitalized
    - Note the Total and Trip odometer values at end of trip
- Start a trip from a different location than end of previous trip
  - Stop, entering a new location, new via, and a distance on trip odometer
    - While stopped, tap the odometer calculator button for both Trip and Total odometers
      - Tap PTE button: Should load correct odometer value from end of previous trip
        and show a toast with text like "Loaded Previous Trip Ending odometer"
      - Hit Cancel to close calculator
  - End trip at any location
  - View logbook
  - Search Via Routes: Enter trip's starting location and newly entered location;
    should show the new via including distance
- Start a trip with a stop whose stop-at time is manually adjusted backwards  
  This tests tolerating a time typo when entering trips in "historical mode".
  - Start a trip; note the starting time
  - View logbook: The new trip should appear as Current trip
  - Stop, changing the stop-time from default to an hour or two before trip's starting time
  - View logbook: Trip should still be visible
  - Continue from that stop
  - View logbook: Trip should still be visible
  - End trip at any location
  - View logbook: Trip should still be visible
- Start a trip which finishes in a different GeoArea
  - Stop and continue at an existing location within starting area
    - To test duplicate-prevention:
      - Start typing the name of the location used in the recent "duplicate-prevention" test
      - Auto-complete should show 1 match, not 2 with the same name
      - Tap auto-complete to select location
    - Test Via field duplicate-prevention the same way
    - Type or auto-complete the name of the intermediate stop from the previous trip
    - Via Route dropdown should show previously entered route
    - Via Route auto-complete should also show a match and be tappable
  - Optionally stop again within the starting area
  - Include at least 1 stop in area "(none)", like a highway Rest Area/Travel Plaza
  - Stop again; should auto-complete locations in area "(none)"
  - Stop again: Enter name of a new geographic area, new location in that new area
  - After stop, Main activity "Current Area" should be the newly created one
  - Tap "End Trip"
    - Start typing just-created new location name; should auto-complete
    - Clear out Location field, type name of another new location
    - End the trip
- Start a trip from a different GeoArea than previous trip
  - First, tap "Begin Trip"
    - Look at the Starting Location field
    - Select a different Area from the dropdown; should clear out location field
    - Go back to Main activity instead of starting the trip
  - Tap "Begin Trip"
    - Change/add to the text in the starting location textfield
    - Now pick the new Area in dropdown; that changed name should not be cleared out
    - Location name field should auto-complete location names from the selected Area
  - After begin trip, main activity's Current Area should be the selected one
  - No need to end trip yet: Can use same trip for next test (gas test)
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
- List shows all drivers alphabetically (case-insensitive)
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
  - After add, should ask whether to change vehicle to new one: Tap "Change"
  - Tap "Begin Trip" with that vehicle, should use that selected Area
  - Go Back instead of starting the trip
- To continue testing, current vehicle must not be on a trip
  - Can cancel trip, if started one with no stops for testing
- Change or add vehicle's comment
- Change a vehicle's active/inactive status
  - Shouldn't be able to change current vehicle's status
  - After change, list shows updated status icon

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
  - Repeat this test, but choose another vehicle from dropdown in Go to Date dialog
  - Go back to first logbook screen
  - Test Earlier/Later buttons
    - Go to Date dialog: Choose a date more than 1 month in the past
    - Tap "Earlier Trips" button
    - Should see older trips above previously-earliest one
    - Scroll to bottom and note that trip's ending date and odometer
    - Tap "Later Trips"
    - Those newer trips should appear at the bottom, after the
      previously-latest one
- Search for Location should filter for the entered location
  - View search results for current vehicle
    - Location name matches should be highlighted (yellow background)
  - Look through log to note any location where any trip ends
  - Search for that location
    - Location name should be highlighted at end of trip, prefix `->` should not
  - Tap "Other Vehicle", show log for a different vehicle
  - Search a location for All vehicles; view search results
  - Choose "Recent Gas" or "Go to Date": Should show for that most recently shown vehicle, not current vehicle
  - Search a location and un-check All Vehicles; view search results:
    Should show for that most recently shown vehicle, not current vehicle
  - View a not-recently-used vehicle's log (not used within last 12 trips),
    search for location: Should show that vehicle's trips to location
  - Tap "Earlier Trips" button; should show that vehicle's previous trips to location
  - In dialog, select a different geoarea; dialog should then autocomplete locations in that area
  - Go back to first logbook screen. Or back to Main, tap Show Logbook
- Tap Recent Gas in action bar
  - Should show gas info, including calculated MPG between fill-up gas stops
  - Note the location name of a recent gas stop
  - Tap "Change Vehicle" button: Should show popup, select another vehicle, should show its gas stops if any
- Search for Location of gas stop
  - Enter name of recent gas stop
  - Location name matches should be highlighted (yellow background)
  - Gas info should not be highlighted
- Search Via Routes in action bar
  - Enter 2 locations that you know have connecting ViaRoutes
    - If necessary, make a trip first to create some
  - Tap "Search" button
  - Should show all ViaRoutes between them in either direction, including distance if entered from trip odometers
  - Bring up dialog again, change dropdown to a different area
  - Now, location should auto-complete to those in the new area
- Tap a trip to view its details
  - Brings up a message box that:
    - Shows vehicle, driver, starting time and location, distance, etc
    - For a roadtrip, lists start and end areas
    - Has list of trip stops' trip-odometer, location, comment
  - Tap a stop in the trip to view its details
    - Uses same "Trip Stop" activity used during trips to enter TStops
    - Is read-only, except comment field
    - Total/trip odometer values should be shown if entered, otherwise hidden including their labels
    - For a stop that has gas, Gas button has green icon, can tap it for gas details
  - Tap a stop that has a comment
    - Should be able to copy comment text to clipboard
    - Edit the comment, tap Save Changes
    - Trip detail popup should show updated comment text for that stop
  - Tap that stop again, status under comment field should show "Edited later"
  - Tap a stop without a comment to view details: Add a comment
  - Tap that stop again, status should show "Added later"
  - Edit that stop's comment, Save Changes
  - Tap that stop again yet, status should show both "Added" and "Edited"
  - Tap a different stop that has a comment
  - Remove that stop's comment, Save Changes
  - Tap that stop again yet, status should show "Removed later"
  - Re-add comment at that stop's comment, Save Changes
  - Tap that stop again yet, status should show "Removed and added later"

### Validate DB

- In Logbook view, Menu -> Validate does so, then shows results in a message box
- If it's been more than 10 days since last backup, should then ask whether you want to backup now
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
- Tap Backup Now button
  - Should show a "Backup successful" toast
  - List of backups should refresh to include new backup with today's date
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
- Tap Continue, walk through Initial Setup
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
Use sqlite-jdbc 3.15.1 or newer.

### Basics

- Version shown at bottom of main dialog is correct
  - Including DB schema version: Check against `DATABASE_VERSION` in bookedit/src/org/shadowlands/roadtrip/db/RDBSchema.java
- Click "View Backup" button
  - File dialog should appear; browse to open a backup file
  - BookEdit should open the backup's logbook in a new window
- In console or terminal that lauched BookEdit, should see schema version (`user_version`) when backup is opened
- Can browse current vehicle's most recent trips
- Click "Earlier Trips" button, shows them at top of log window
- Use Vehicles dropdown to view logs for another vehicle, if any in backup
- Click "Drivers" button, see list of driver(s) and tap for details
- Click "Vehicles" button, see list of vehicle(s), including active/inactive icon (green/gray), click one for details
- Quit and re-launch BookEdit, click View Backup: Dialog should start in folder of last-opened backup

### Other functionality

- "Validate DB" button does so, then shows results in a message box
- If you have a backup available from an earlier schema version:
  - Make a temporary copy
  - Click "View Backup" to open that copy
  - In console or terminal, should see schema version info and SQL commands from `upgradeCopyToCurrent` debug output
  - Should then be able to view info and browse trips from the upgraded backup copy
  - Close that viewer window
  - Click "Open" to open that same copy
  - Should ask whether to upgrade in place, make and upgrade a read-only copy, or cancel
  - Choose Cancel: Should do nothing
  - Click Open again, choose same copy, should get prompt, choose Read-only
  - Browse trips, close
  - Click Open again, choose same copy, should get prompt, choose Upgrade in Place
  - Browse trips, close
  - Click Open again, choose same copy, should not get prompt because it's been upgraded

### Optional tests

- If sqlite-jdbc driver jar is too old, won't be able to open files
  - Get a too-old version such as 3.6.23, rename to sqlite-jdbc.jar
  - Launch bookedit jar
  - Try to open a logbook or view backup
  - Error dialog should say it can't open the database
  - Console should print details and version:  
    ```
    org.sqlite.JDBC version: SQLiteJDBC pure (3.6.23.1 3.0)
    DB pragma integrity_check failed: class java.sql.SQLException [SQLITE_NOTADB]  File opened that is not a database file (file is encrypted or is not a database)
    ```


## Other checks

- Docs:
  - doc/versions.md should list all notable changes in this release
  - Check all URLs in doc files
  - README: Once all docs are up to date, date at bottom should be current month
- DB package consistency between BookEdit and Android app
  - Run this command at the root of the repo:  
    `diff -ur bookedit/src/org/shadowlands/roadtrip/db roadtrip-an/app/src/main/java/org/shadowlands/roadtrip/db`

    The only output should be:

        Only in roadtrip-an/app/src/main/java/org/shadowlands/roadtrip/db/: android
        Only in bookedit/src/org/shadowlands/roadtrip/db: jdbc
        Only in bookedit/src/org/shadowlands/roadtrip/db: script

  - Run this command, should see no output:  
    `diff -u bookedit/src/org/shadowlands/roadtrip/db/script/schema_v*.sql roadtrip-an/app/src/main/res/raw/schema_v*.sql`
  - Run this command, should see no output:  
    `diff -ur bookedit/src/org/shadowlands/roadtrip/model roadtrip-an/app/src/main/java/org/shadowlands/roadtrip/model`

