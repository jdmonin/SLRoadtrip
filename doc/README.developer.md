This is the Developer ReadMe file for Shadowlands Roadtrip.
Currently this is an incomplete draft.

Shadowlands RoadTrip is a simple app to keep track of your vehicles' mileage,
trips, gas, and service. The project includes the Android app, and a Java Swing
application for viewing database backups on a PC or Mac.

## Contents

- How to get the latest source
- Programs included
- Java/Android versions required
- How to build
- Overview of source dir (location of schema files, etc)
- Before testing, reminder how to back up / export data
- Coding style
- Version history
- Release testing / Android new-version testing
- Future plans / feature roadmap
- Other source included (libraries), and software licenses

(those sections here)

The project development site is https://github.com/jdmonin/SLRoadtrip


## Java/Android versions required

To allow for broad device support, currently the app requires Android 4.4
(KitKat) or newer, and the BookEdit log viewer requires java 5 or newer.
The app uses newer features such as ActionBar if available.


## How to build

Most of these instructions are for Eclipse; full instructions for Android Studio are TBD soon.
- `roadtrip-an` is the Android app, currently developed in Android Studio.
- `bookedit` is the Java program for viewing SLRoadtrip backup files on your computer,
  currently developed in Eclipse.

Start by making a local clone or fork of the SLRoadtrip git repo.  If you're starting from
a previous released version (not the latest commit), you can start a new branch
from the version's tag with:

```
git checkout -b branch_0991 release-0.9.91
```

Otherwise just use:
```
git checkout main
```

### Quick instructions for Android Studio 3.4.1 (for Android app)

- Import project (gradle)
    - Browse to select the `roadtrip-an` directory within the SLRoadtrip repo
    - Open
- Build menu -> Make project
    - Some android components or dependencies might automatically download
    - Should successfully sync
- Preferences
    - Scheme: Project
    - Editor -> code style
        - Line separator: unix (\n)
        - -> XML
            - Tab size: 8
        - -> Java
            - Tab size: 8
            - Class count to use import with `*`: 99
            - Names count to use static import with `*`: 99
        - -> Other file types
            - Tab size: 8
            - [X] Use tab character
    - Inspections -> javadoc
        - Declaration has problems in javadoc ref
            - Un-check box: [ ] Report inaccessible symbols  
              Some versions of Android Studio report this error for java.lang classes like StringBuilder;
              actual syntax errors can be obscured by that clutter
- Start an emulator, then run `app` there
    - You may need to download an android emulator image first;
      current minimum SDK version is very low: 19 (4.4 KitKat)
    - In a moment you should see the Shadowlands Roadtrip welcome screen
    - Either start to enter some test data, or restore a backup you've sent into the emulator's Downloads folder
    - Once a driver and vehicle are created or restored, the app will start up at its main menu

### Full instructions for Eclipse (for BookEdit utility)

You can use the standard Java Eclipse, although after installing that it can be
useful to go to go to Add Software and download the Data Tools Platform from
Java EE (J2EE) to use the data browser.

You'll probably also want to download a SQLite jdbc driver jar to use with bookedit.
See https://github.com/xerial/sqlite-jdbc/releases -> Assets.

In Eclipse add the bookedit project:

- File menu -> New -> Project... -> Java -> From Exiting Ant Buildfile
- Ant buildfile: Within the checked-out copy from git, browse to build.xml in the bookedit folder
- Project name: slroadtrip-bookedit  [or another name if you prefer]
- Javac declaration: Hilight the sole javac task
- Set this checkbox: Link to the buildfile in the file system
- Click Finish

This should add slroadtrip-bookedit to the workspace list of projects (Package Explorer).  
Right-click it there and choose Properties to review the project settings:

- Source tab:
    - Should show slroadtrip-bookedit/src containing source folders for
      gnu.trove, org.shadowlands.roadtrip.bookedit, and other packages.
    - Be sure that "Allow output folders for source folders" is un-checked.
    - Set the default output folder to `slroadtrip-bookedit/bin/classes`.
- Projects tab: An empty list: there aren't any required projects
- Libraries tab: JRE system library
    - Add external JAR -> `sqlite-jdbc-*.jar`
- Order and Export tab: contains slroadtrip-bookedit/src, JRE system library, sqlite jar
- On OS X, on the left side of the properties window also check Resource ->
  Text file encoding, which needs to be UTF-8 and not MacRoman or another value.

Click OK.

Hilight bookedit in Package Explorer, then go to the Project menu -> Build Project.
After the build, check for any errors in the Problems pane and red X icons in the
Package Explorer.  Typically there will be no errors.

Once the build succeds, in the Package Explorer find bookedit/src/
org.shadowlands.roadtrip.bookedit/Main.java. Right-click it and choose
Run As -> Java Application.  You should see the BookEdit main window appear,
showing some buttons and the version and db schema version.  Choose Exit.
This also adds "Main" to your eclipse Run button dropdown; you can rename it
using the Run Configurations dialog.

To build and run slroadtrip-bookedit.jar (optional), right-click build.xml
-> Run as -> Ant Build. This will compile the classes and create the JAR.
Copy the sqlite jar into the same folder as slroadtrip-bookedit.jar and
rename it to `sqlite-jdbc.jar` (without any version number). At this point
you can run slroadtrip-bookedit.jar and view db backup files.

At this point you're ready to work on developing BookEdit or roadtrip-an.


## Overview of source dir

Some common files, mostly db-related, are duplicated between the android and
bookedit source directories: src/org/shadowlands/roadtrip/db and model, and
SQL scripts to create and upgrade the schema. The db package is a cross-platform
database API so the android app and BookEdit (JDBC) can use, update, and upgrade
the roadtrip database consistently.

Android: If you want the commit hash to show in the About box: Before building,
manually edit res/raw/gitversion.txt to contain the commit hash.
This is always done before releasing a version, so that the About box for
the app in the APK will display the final build info.
Never check in the updated gitversion.txt contents: the contents of
gitversion.txt in the repo should always be `?`.

In the bookedit source, the android directories are empty or missing.
In the android source, the bookedit and jdbc directories are empty or missing.
The SQL scripts (schemas and database upgrade scripts) are found in
bookedit src/org/shadowlands/roadtrip/db/script/\*.sql and in
android res/raw/\*.sql. The schema scripts have comments about table fields'
meaning and expected contents and related tables.

SQL Foreign Keys: To simplify Android support, for now Roadtrip manually
enforces foreign keys. Although Android 2.2 contained sqlite 3.6.22 with
support for FKs, they are off by default, and can't be turned on during a
transaction such as onUpgrade. Until Android 4.1 added onConfig, it was
difficult to configure the open database before the call to onUpgrade, so
for now we aren't using this feature of SQLite.

The bookedit and roadtrip-an version numbers should be fairly close to
each other. roadtrip-an's version is kept in AndroidMainfest.xml.
bookedit's version is kept in org/shadowlands/roadtrip/bookedit/Main.java.

More info about the SQLite versions and features available in Android is found
by comparing the version numbers in https://www.sqlite.org/changes.html against
those in https://stackoverflow.com/questions/2421189/version-of-sqlite-used-in-android .  
As of May 2019, that table is summarized as:
```
1.6 Cupcake, 2.1 Eclair:       3.5.9   2008-05-14
2.2 Froyo, 2.3 Gingerbread:    3.6.22  2010-01-06
3.0 Honeycomb, 4.0 ICS:        3.7.4   2010-12-08
4.1-4.3 Jellybean, 4.4 KitKat: 3.7.11  2012-03-20
5.0 Lollipop:                  3.8.4.3 2014-04-03
5.1 Lollipop:                  3.8.6   2014-08-15
6.0 Marshmallow:               3.8.10.2 2015-05-20
7.x Nougat:                    3.9.2   2015-11-02
8.0 Oreo:                      3.18.2  2017-06-17
8.1 Oreo:                      3.19.4 ~2017-08-18
9.0 Pie:                       3.22.0  2018-01-22
```
(dates from https://www.sqlite.org/changes.html)


(more sections here)



## Coding style

(more here)

Within Activities, fields are declared in this order:

- Anything static
- DB connection
- DB objects (Vehicle, Trip, etc)
- Flags and other non-GUI fields
- On-screen views and other GUI items

Most sorted dropdowns (such as gas_brandgrade) should be case-insensitive.
In SQLite you can specify this with `orderBy = "fieldname COLLATE NOCASE"`.



## Version history

See [versions.md](versions.md) for released versions. See doc/hist/\*.sql for previous schema updates.

The project repo was migrated from subversion to git on 2015-05-03,
for details see roadtrip-svn2git.sh comments.



## Release testing / Android new-version testing

See [release-testing.md](release-testing.md) for the outline of things to cover.
Used for release testing, and also for a new target API level
or new android version on an emulator or device.



## Future plans / feature roadmap

After v1.0:

- More logbook search options
    - Search for stops at location before/after given date
    - Search for trips with stops from location directly to another location
    - etc
- Logbook viewer: Ability to correct typos (location names, via routes, cost, etc)
- General schema for new event types at stop (vehicle service, restaurant, movie, etc)
  and/or tags to apply to location which can categorize like that
- Edit list of vehicle makes, trip categories, GeoAreas, etc
- "pass-through" TStop (rest area, toll, etc): Not a destination, just something on the way
    - At next tstop or end of trip, also write ViaRoutes from before this stop to the following one
- Frequent Trips GUI to reduce per-trip data entry (to grocery store, to work, etc)
- More reporting options (by trip category, by driver, monthly/yearly summary, etc)



## Other source included (libraries), and software licenses

Shadowlands Roadtrip is licensed under GPL v3 or later
(the GNU General Public License).  See the [/COPYING-GPLv3.txt](../COPYING-GPLv3.txt) file for details.

Included graphics are copyright (C) Jeremy D Monin, licensed (CC) By-SA 3.0, except for:

- ic_clear_black_18dp.png is from Google's Material Design icons v3.0.1, licensed Apache 2.0, retrieved 2019-09-27
  from https://github.com/google/material-design-icons/tree/master/content/drawable-*/ic_clear_black_18dp.png
- ic_settings_gray60_24dp.png is from Google's Material Design icons v3.0.1, licensed Apache 2.0, retrieved 2019-10-13
  from https://github.com/google/material-design-icons/tree/master/content/drawable-*/ic_settings_white_24dp.png
  and changed to 60% gray (#999; -102 br in Gimp brightness/contrast dialog)

Includes portions of GNU Trove 2.1.0 as source, which is licensed under
LGPL v2.1 or later (the GNU Lesser General Public License).
See src/gnu/grove/TIntObjectHashMap.java comments for details.

Includes the Android NumberPicker widget as modified source, via
com.quietlycoding.android.picker.NumberPicker, which is licensed under
the Apache License version 2.0.  See
src/com/quietlycoding/android/picker/NumberPicker.java comments for details.

Includes FileUtils.java modified from the summer 2010 source of AndiCar, which
is licensed under GPL v3. See https://code.google.com/p/andicar/ for details, or
src/org/shadowlands/roadtrip/android/util/FileUtils.java.

Includes CSVWriter as modified source, from opencsv 2.3, which
is licensed under the Apache License version 2.0.  See
https://sourceforge.net/projects/opencsv/ for details, or
src/au/com/bytecode/CSVWriter.java.
