-- org.shadowlands.roadtrip
-- version 0.9.40 schema (2014-02-15) for SQLite 3.4 or higher
-- with newer comments (2015-05-06).
--
-- The db schema version is sometimes lower than the app version, never higher.
--
-- Remember: When you upgrade the schema version, be sure to
-- make all code changes listed in RDBSchema's class javadoc, and
-- add the upgrade script to RtrDBOpenHelper.getSQLScript().
-- Remember: Any schema changes must also be made
-- within the java accessor classes.  Changes to important tables
-- or transactional tables should also be changed in RDBVerifier.
-- Be sure to test upgrading in the emulator to your new schema,
-- and doing a fresh install with the new schema, then restoring a
-- previous backup that has an older schema (which will also upgrade).

PRAGMA user_version = 0940;

-- This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
-- 
--  This file Copyright (C) 2010-2015 Jeremy D Monin (jdmonin@nand.net)
-- 
--  This program is free software: you can redistribute it and/or modify
--  it under the terms of the GNU General Public License as published by
--  the Free Software Foundation, either version 3 of the License, or
--  (at your option) any later version.
-- 
--  This program is distributed in the hope that it will be useful,
--  but WITHOUT ANY WARRANTY; without even the implied warranty of
--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
--  GNU General Public License for more details.
-- 
--  You should have received a copy of the GNU General Public License
--  along with this program.  If not, see http://www.gnu.org/licenses/ .


-- NOTES:

-- odometer values (odo) are 10 x KM or 10 x MI (eg, 520.2 stored as integer 5202)
--    KM or MI is set at vehicle creation and not changed.
-- gps values (_lat, _lon) currently stored as pairs of floats (unit = +- degrees)
-- all date-time values are stored as unix time integers:
--    like System.currentTimeMillis(): UTC seconds since the unix epoch
--    You can use perl on the command line to easily calculate these values:
--                                       timelocal is (s, m, h, d, mon-1, yr)
--    perl -e 'use Time::Local; print scalar timelocal(0, 30, 13, 9, 5, 2001); print "\n"; '
--    992107800
--    perl -e 'print localtime(992107800) . "\n"; '
--    Sat Jun  9 13:30:00 2001
--    Note that month range is 0-11 in perl's timelocal and timegm functions.

-- proper operation requires at least 1 geoarea, driver (person), and vehicle;
--    when lauched, the android app prompts to create these if missing.

-- android requires a PK field called _id in all tables

PRAGMA auto_vacuum = INCREMENTAL;

create table appinfo ( _id integer PRIMARY KEY AUTOINCREMENT not null, aifield varchar(32) not null unique, aivalue varchar(64) not null );
	-- Important keys: (See also db.AppInfo javadocs, where some of these are static final string fields)
	-- DB_CREATE_SCHEMAVERSION: '0909' if created with schema v0909
	-- DB_CREATE_DATETIME
	-- DB_CREATE_APPNAME: 'org.shadowlands.roadtrip'
	-- DB_BACKUP_PREVFILE: copied from previous DB_BACKUP_THISFILE when user asks to back up; doesn't include path, only filename
	-- DB_BACKUP_PREVTIME: (unix format) time of DB_BACKUP_PREVFILE
	-- DB_BACKUP_THISDIR:  optional directory path, for user preferences, of the backup being written. Do not include trailing slash.
	--                       	 value is '' if using the default backup location DBBackup.getDBBackupPath(Context).
	-- DB_BACKUP_THISFILE: written just before closing db for backup copy; if backup fails, clear it afterwards (copy it back from DB_BACKUP_PREVFILE)
	-- DB_BACKUP_THISTIME: (unix format) time of DB_BACKUP_THISFILE
	-- DB_CURRENT_SCHEMAVERSION '0940' if upgraded to current schema version

insert into appinfo (aifield, aivalue) values ('DB_CREATE_SCHEMAVERSION', '0940');
insert into appinfo (aifield, aivalue) values ('DB_CURRENT_SCHEMAVERSION', '0940');

create table settings ( _id integer PRIMARY KEY AUTOINCREMENT not null, sname varchar(32) not null unique, svalue varchar(64), ivalue int );
	-- General current settings. See also veh_settings.
	-- Each setting uses svalue or ivalue. Empty strings (svalues) are stored as null, not as a string of length 0.
	--
	-- DISTANCE_DISPLAY: KM or MI
	-- CURRENT_VEHICLE (int _id within vehicles) -- if this changes, update CURRENT_TRIP too; see vehicle.last_tripid comment
	-- REQUIRE_TRIPCAT (bool) -- is trip category required for new trips?  Added in app version 0.9.12.
	-- LOGVIEW_ODO_TRIP_DELTA (int) -- logview trip odometers normal (0), delta (1), or both (2)  Added in 0.9.12.
	-- HIDE_FREQTRIP (bool) -- Hide the Frequent Trip buttons?  Added in app version 0.9.12.
	-- HIDE_VIA (bool) -- Hide the Via entry field?  Added in app version 0.9.12.
	-- SHOW_TRIP_PAX (bool) -- Show the optional Passenger Count field for trip?  Added in app version 0.9.13.

create table veh_settings ( _id integer PRIMARY KEY AUTOINCREMENT not null, vid int not null, sname varchar(32) not null, svalue varchar(64), ivalue int );
	-- Per-vehicle settings, added in v0940: More flexible than adding fields to the vehicle table.
	-- Before v0.9.40, these were in the settings table, updated when the current vehicle changed.
	-- Each setting uses svalue or ivalue. Empty strings (svalues) are stored as null, not as a string of length 0.
	-- vid is the vehicle (int _id within vehicle); foreign keys aren't as easily enforced before android 4.1,
	-- so for now they aren't declared. See doc/README.developer for details.
	--
	-- CURRENT_AREA (int _id within geoarea)
	--      During a roadtrip, the starting area (setting changes only when trip ends)
	-- CURRENT_DRIVER (int _id within people)
	-- CURRENT_TRIP (int _id, or 0) -- if the CURRENT_VEHICLE changes, update this setting too
	-- CURRENT_TSTOP (int _id, or 0) -- 0 when not stopped, 0 when not on a trip
	-- PREV_LOCATION (int _id, or 0) -- added in v0813; may be 0 between trips, especially if current vehicle has no prev trips.
	--      When at a TStop, the previous stop's location ID; at the trip's first stop, should be the trip start location.
	--      Used during trips to get ViaRoutes between PREV_LOCATION and current TStop's location.
	-- CURRENT_FREQTRIP (int _id, or 0) -- added in v0900; 0 when not on a freqtrip; 0 when CURRENT_TRIP is 0
	-- CURRENT_FREQTRIP_TSTOPLIST (empty string, or comma-delimited _id)
	--      added in v0900; unused IDs for this freqtrip in freqtrip_tstop

create unique index "veh_settings~vs" ON veh_settings(vid,sname);

create table app_db_upgrade_hist ( db_vers_to int not null, db_vers_from not null, upg_time int not null );
    -- May be empty, if db never upgraded
    -- db_vers_from, db_vers_to are schema version numbers, like 908
    -- upg_time is unix format

create table geoarea ( _id integer PRIMARY KEY AUTOINCREMENT not null, aname varchar(255) not null );

create table person ( _id integer PRIMARY KEY AUTOINCREMENT not null, is_driver int not null, name varchar(255) not null unique, contact_uri varchar(255), is_active int not null default 1, comment varchar(255) );

create index "person~d" ON person(is_driver);

create table vehiclemake ( _id integer PRIMARY KEY AUTOINCREMENT not null, mname varchar(255) not null unique, is_user_add int );
	-- see bottom of file for inserts into vehiclemake

create table vehicle ( _id integer PRIMARY KEY AUTOINCREMENT not null, nickname varchar(255), driverid int not null, makeid int not null, model varchar(255), year integer not null, date_from integer, date_to integer, vin varchar(64), plate varchar(64), odo_orig integer not null, odo_curr integer not null, last_tripid integer, distance_storage varchar(2) not null, expense_currency varchar(3) not null, expense_curr_sym varchar(3) not null, expense_curr_deci integer not null, fuel_curr_deci integer not null, fuel_type varchar(1) not null, fuel_qty_unit varchar(2) not null, fuel_qty_deci integer not null, comment varchar(255), is_active int not null default 1 );
    -- To reduce write freq, update odo_curr only at end of each trip, not at each trip stop.
    -- Also update last_tripid at the end of each trip, or if a trip was in progress and then the current vehicle changed.
    --   If the vehicle has never finished a trip, last_tripid is 0 or null.
    --   last_tripid is used to find the vehicle's previous stopping point, when starting a new trip.
    --   If the CURRENT_VEHICLE setting changes during this vehicle's CURRENT_TRIP: Update last_tripid for this vehicle, and
    --   then check the new vehicle's last_tripid.  If that trip's odo_end is 0, that trip is in progress, the new CURRENT_TRIP.
    -- distance_storage is 'KM' or 'MI'
    -- expense_currency is, for example, 'USD' or 'CAD'
    -- expense_curr_sym is, for example, '$'
    -- expense_curr_deci and fuel_qty_deci are # of digits after decimal
    -- fuel_curr_deci is per-unit price # digits after decimal
    -- fuel_qty_unit is 'ga' or 'L'
    -- fuel_type is 'G' gas, 'D' diesel

create table tripcategory ( _id integer PRIMARY KEY AUTOINCREMENT not null, cname varchar(255) not null unique, rank int not null, is_work_related int not null default 0, is_user_add int );
	-- rank is a place number for on-screen order (instead of alphabetical listing)
	-- see bottom of file for inserts into tripcategory

create table trip ( _id integer PRIMARY KEY AUTOINCREMENT not null, vid integer not null, did int not null, catid int, odo_start int not null, odo_end int, aid int, tstopid_start int, time_start int not null, time_end int, start_lat float, start_lon float, end_lat float, end_lon float, freqtripid int, comment varchar(255), passengers int, roadtrip_end_aid int, has_continue int not null default 0 );
	-- vid is vehicle, did is driver
	-- if tstopid_start not null, it's the endpoint of a previous trip with the same odo_total.
	--    This gives the starting location (descr and/or locid) for the trip.
	--    Otherwise, see below under 'chronological order of stops within a trip'.
	-- odo_end is 0 until trip is completed, and then it's a required field.
	-- aid is the geoarea ID (most other tables use a_id)
	-- catid is the optional trip category, or null (table tripcategory)
	-- The starting and ending location are taken from the trip's TStops.
	-- A vehicle's trips are inserted in their chronological order.
	--    So, ORDER BY trip._id will give the proper order for any one vehicle's trips.
	--    If selecting trips for multiple vehicles, order by time_start instead.
	-- Convention for chronological order of stops within a trip:
	--    (Needed because any useful field can be null)
	--    Stops are inserted in their chronological order.
	--    So, ORDER BY tstop._id will give the proper order within a trip.
	-- Each trip ends with an "ending TStop", even if it has no intermediate stops.
	-- This last stop of a trip may be referenced by a field in
	--    the next trip, for its locid and descr.
	--  The ending TStop's odo_total must match the trip's odo_end,
	--	  and either its locid or its descr must not be null.
	--	  Its time_continue must be null.
	-- If the trip doesn't begin at the previous trip's ending tstop,
	--  the trip's first tstop record is its "starting TStop" giving the starting location,
	--      and must have the same odo_total as the trip's odo_start,
	--	and 0 odo_trip, and null time_stop,
	--	and either its locid or its descr must not be null.


create index "trip~odo" ON trip(vid, odo_start);
create index "trip~d" ON trip(vid, time_start);
create index "trip~cv" ON trip(catid, vid);

create table freqtrip ( _id integer PRIMARY KEY AUTOINCREMENT not null, a_id int, start_locid integer not null, end_locid integer not null, end_odo_trip int not null, roadtrip_end_aid int, descr varchar(255), end_via_id int, typ_timeofday int, flag_weekends int not null default 0, flag_weekdays int not null default 0, is_roundtrip int not null default 0, catid int );
	-- start_locid, end_locid are location IDs.
	-- fields with same name as a trip field have the same meaning.
	-- if typ_timeofday not null, it's a 24-hour time stored as hours*60 + minutes.

create index "freqtrip~l" ON freqtrip(start_locid);

create table tstop ( _id integer PRIMARY KEY AUTOINCREMENT not null, tripid int not null, odo_total int, odo_trip int, time_stop int, time_continue int, locid int, a_id int, geo_lat float, geo_lon float, flag_sides int not null default 0, descr varchar(255), via_id int, via_route varchar(255), comment varchar(255));
	-- For convention for chronological order of stops within a trip,
	--    and for seeing which is the "starting TStop" and "ending TStop",
	--    see trip table's comments.
	-- odo_total is the vehicle's overall odometer, odo_trip is distance within this trip at this tstop.
	-- a_id is the tstop's geoarea.
	--    For local trips: a_id is null (look at trip.aid instead)
	--    For roadtrips:
	--       A roadtrip's ending tstop's area id should be the ending area.
	--       A roadtrip's starting tstop's area id is ignored, because it could be the
	--         ending tstop of a local trip.
	--       Other stops during roadtrip: a_id is set for any roadtrip stop
	--         which is within the starting or ending geoarea.
	--         for stops 'in the middle' (neither start or end area), a_id is null.
	--     0 for a_id is ok for a local tstop, but not ok for start/end location of trip.
	-- via_route, via_id are the route from the previous tstop's location;
	--    they are ignored for the tstop which starts a trip. (via_id goes to the via_route table)
	-- descr is null for all new rows (starting with app version 0.9.05), because the separate
	--    location record (locid) stores the description.  Older data may use descr.
	-- flag_sides: bitmask, indicates this row has sidetables (exercise, food, gas, car-service).
	--    also used for temporary flags.  In TStop.java see FLAG_*, TEMPFLAG_*.
	--    0x100 (256) FLAG_GAS -> tstop_gas table entry

create index "tstop~t" ON tstop(tripid);

-- tstop_gas is a "side table" for tstop.
--   When a tstop has a tstop_gas, the FLAG_GAS bit is set in its flag_sides field.
-- NOTE: tstop_gas._id == associated tstop._id
--   Fillup: 1 or 0 (Fill the tank, or partial)
--   Station: obsolete for 0.9.06+, use gas_brandgrade_id instead
--   vid: Vehicle ID (denormalization for query performance)
create table tstop_gas ( _id integer PRIMARY KEY not null, quant int not null, price_per int not null, price_total int not null, fillup int not null, vid integer not null, gas_brandgrade_id int);
create index "tstopgas~v" ON tstop_gas(vid);

-- Gas brand/grade, for tstop_gas
create table gas_brandgrade ( _id integer PRIMARY KEY AUTOINCREMENT not null, name varchar(255) not null );

-- Used for TStop description auto-fill, and freqtrip.
--    a_id is the geoarea ID, or null for roadtrip locations between areas. 
--    latest_gas_brandgrade_id is for the auto-fill default at gas stop locations.
create table location ( _id integer PRIMARY KEY AUTOINCREMENT not null, a_id int, geo_lat float, geo_lon float, loc_descr varchar(255) not null, latest_gas_brandgrade_id int );

-- Via routes, between locations
create table via_route ( _id integer PRIMARY KEY AUTOINCREMENT not null, locid_from int not null, locid_to int not null, odo_dist int, via_descr varchar(255) not null);
--	odo_dist is the optional trip-odometer distance for this route between locid_from and locid_to.
create index "via_route~l" ON via_route(locid_from);

-- Frequent Trip tstop/location linkage:
create table freqtrip_tstop ( _id integer PRIMARY KEY AUTOINCREMENT not null, freqtripid int not null, locid int not null, via_id int, odo_trip int);
create index "freqtrip_tstop~f" ON freqtrip_tstop(freqtripid);

-- master-data inserts begin --

begin transaction;
insert into vehiclemake(mname) values ('Acura');
insert into vehiclemake(mname) values ('Audi');
insert into vehiclemake(mname) values ('BMW');
insert into vehiclemake(mname) values ('Buick');
insert into vehiclemake(mname) values ('Cadillac');
insert into vehiclemake(mname) values ('Chevrolet');
insert into vehiclemake(mname) values ('Chrysler');
insert into vehiclemake(mname) values ('Dodge');
insert into vehiclemake(mname) values ('Fiat');
insert into vehiclemake(mname) values ('Ferrari');
insert into vehiclemake(mname) values ('Ford');
insert into vehiclemake(mname) values ('GMC');
insert into vehiclemake(mname) values ('Harley');
insert into vehiclemake(mname) values ('Holden');
insert into vehiclemake(mname) values ('Honda');
insert into vehiclemake(mname) values ('Hummer');
insert into vehiclemake(mname) values ('Hyundai');
insert into vehiclemake(mname) values ('Infiniti');
insert into vehiclemake(mname) values ('Isuzu');
insert into vehiclemake(mname) values ('Jaguar');
insert into vehiclemake(mname) values ('Jeep');
insert into vehiclemake(mname) values ('Kawasaki');
insert into vehiclemake(mname) values ('Kenworth');
insert into vehiclemake(mname) values ('Kia');
insert into vehiclemake(mname) values ('Land Rover');
insert into vehiclemake(mname) values ('Lexus');
insert into vehiclemake(mname) values ('Lincoln');
insert into vehiclemake(mname) values ('Mack');
insert into vehiclemake(mname) values ('Mazda');
insert into vehiclemake(mname) values ('Mercedes');
insert into vehiclemake(mname) values ('Mercury');
insert into vehiclemake(mname) values ('Mini');
insert into vehiclemake(mname) values ('Mitsubishi');
insert into vehiclemake(mname) values ('Nissan');
insert into vehiclemake(mname) values ('Oldsmobile');
insert into vehiclemake(mname) values ('Opel');
insert into vehiclemake(mname) values ('Peugeot');
insert into vehiclemake(mname) values ('Plymouth');
insert into vehiclemake(mname) values ('Pontiac');
insert into vehiclemake(mname) values ('Porsche');
insert into vehiclemake(mname) values ('Saab');
insert into vehiclemake(mname) values ('Saturn');
insert into vehiclemake(mname) values ('Scion');
insert into vehiclemake(mname) values ('Smart');
insert into vehiclemake(mname) values ('Subaru');
insert into vehiclemake(mname) values ('Suzuki');
insert into vehiclemake(mname) values ('Tesla');
insert into vehiclemake(mname) values ('Toyota');
insert into vehiclemake(mname) values ('Triumph');
insert into vehiclemake(mname) values ('Volkswagen');
insert into vehiclemake(mname) values ('Volvo');
-- End of original sequence; acura is _id 1, and volvo is 51
-- vehiclemake adds 2012-04-01 v0908: citroen = 52 renault = 53
insert into vehiclemake(mname) values ('Citroen');
insert into vehiclemake(mname) values ('Renault');
-- vehiclemake adds 2014-02-15 v0940: geely = 54 saic = 55 tata = 56
insert into vehiclemake(mname) values ('Geely');
insert into vehiclemake(mname) values ('SAIC');
insert into vehiclemake(mname) values ('Tata');
commit;

begin transaction;
-- tripcategory initial contents 2012-12-06 v0909:
insert into tripcategory(cname,rank,is_work_related) values ('Work', 1, 1);
insert into tripcategory(cname,rank) values ('Personal', 2);
insert into tripcategory(cname,rank) values ('Volunteer', 3);
insert into tripcategory(cname,rank) values ('Moving', 4);
commit;

-- master-data inserts done --

-- (TODO) remaining side tables


