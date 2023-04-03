Brief README for Shadowlands Roadtrip, A vehicle logbook for Android.
This is still beta, not yet version 1.0 software.

Contains:

- roadtrip-an, the Android application
- bookedit, the java viewer for backups from Android
- doc, the documentation

The project development site is https://github.com/jdmonin/SLRoadtrip
Further documentation will be provided soon, along with screenshots on
the project wiki.

For now, please see the 'doc' directory for details, such as [README.developer.md](doc/README.developer.md)
which includes a version history and roadmap.

To run the bookedit JAR, you will need to download `sqlite-jdbc-<version>.jar`
3.15.1 or newer from https://github.com/xerial/sqlite-jdbc/releases -> assets and
place it in the same folder as slroadtrip-bookedit.jar. You'll need to rename
the sqlite JAR to `sqlite-jdbc.jar` (without any version number). Then just
double-click slroadtrip-bookedit.jar to run it and view db backup files.


Known limitations:

- No way yet to search by odometer for a previous trip
- Can't yet create a Frequent Roadtrip, or a round-trip Freq Trip
- Two copies of the db library source (one each for bookedit and roadtrip-an)

Jeremy D Monin `<jdmonin@nand.net>`
April 2023
