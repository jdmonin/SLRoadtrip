-- org.shadowlands.roadtrip
-- version 0.8.05 schema for SQLite 3.4 or higher  2010-09-06
-- Remember: Any schema changes must also be made
-- within the java accessor classes.
-- This file is Copyright (C) 2010 Jeremy D Monin <jeremy@nand.net>

PRAGMA user_version = 0805;

-- odometer values (odo) are 10 x KM or 10 x MI (eg, 520.2 stored as integer 5202)
--    KM or MI is set at db creation (DISTANCE_STORAGE in appinfo table) and not changed.
-- gps values (_lat, _lon) currently stored as pairs of floats (unit = +- degrees)
-- all date-time values are stored as unix time integers:
--    like System.currentTimeMillis(): UTC seconds since the unix epoch
--    You can use perl on the command line to easily calculate these values:
--    perl -e 'use Time::Local; print scalar timelocal(0, 30, 13, 9, 5, 2001); print "\n"; '
--    992107800
--    perl -e 'print localtime(992107800) . "\n"; '
--    Sat Jun  9 13:30:00 2001
--    Note that month range is 0-11 in perl's timelocal and timegm functions.

-- android requires a PK field called _id in all tables

PRAGMA auto_vacuum = INCREMENTAL;

create table appinfo ( _id integer PRIMARY KEY AUTOINCREMENT not null, aifield varchar(32) not null unique, aivalue varchar(64) not null );
	-- Important keys: (See also db.AppInfo javadocs, where some of these are static final string fields)
	-- DISTANCE_STORAGE: KM or MI
	-- DB_CREATE_SCHEMAVERSION: 0805
	-- DB_CREATE_DATETIME
	-- DB_CREATE_APPNAME: 'org.shadowlands.roadtrip'
	-- DB_BACKUP_PREVFILE: copied from previous DB_BACKUP_THISFILE when user asks to back up; doesn't include path, only filename
	-- DB_BACKUP_PREVTIME: (unix format) time of DB_BACKUP_PREVFILE
	-- DB_BACKUP_THISFILE: written just before closing db for backup copy; if backup fails, clear it afterwards (copy it back from DB_BACKUP_PREVFILE)
	-- DB_BACKUP_THISTIME: (unix format) time of DB_BACKUP_THISFILE
	-- DB_CURRENT_SCHEMAVERSION (0800 unless upgraded)
	-- DB_UPGRADED_DATETIME (may not be present)
	-- DB_UPGRADED_APPNAME (may not be present)

insert into appinfo (aifield, aivalue) values ('DB_CREATE_SCHEMAVERSION', '0805');
insert into appinfo (aifield, aivalue) values ('DB_CURRENT_SCHEMAVERSION', '0805');

create table settings ( _id integer PRIMARY KEY AUTOINCREMENT not null, sname varchar(32) not null unique, svalue varchar(64), ivalue int );
	-- DISTANCE_DISPLAY: KM or MI
	-- CURRENT_VEHICLE (int rownum within vehicles)
	-- CURRENT_DRIVER (int rownum within people)
	-- CURRENT_TRIP (int rownum, or 0)
	-- CURRENT_STOP (int rownum, or 0) - or, last stop if not on a trip

create table geoarea ( _id integer PRIMARY KEY AUTOINCREMENT not null, aname varchar(255) not null );

create table person ( _id integer PRIMARY KEY AUTOINCREMENT not null, is_driver int not null, name varchar(255) not null unique, contact_uri varchar(255) );

create index "person~d" ON person(is_driver);

create table vehiclemake ( _id integer PRIMARY KEY AUTOINCREMENT not null, mname varchar(255) not null unique );
	-- see bottom of file for inserts into vehiclemake

create table vehicle ( _id integer PRIMARY KEY AUTOINCREMENT not null, nickname varchar(255), driverid int not null, makeid int not null, model varchar(255), year integer not null, date_from integer, date_to integer, vin varchar(64), odo_orig integer not null, odo_curr integer not null, last_tripid integer, comment varchar(255) );
    -- To reduce write freq, update odo_curr only at end of each trip, not at each trip stop.
    -- Also update last_trip_id at the end of each trip.
    -- If the vehicle has never finished a trip, last_trip_id is 0 or null.

create table trip ( _id integer PRIMARY KEY AUTOINCREMENT not null, vid integer not null, did int not null, odo_start int not null, odo_end int, aid int, tstopid_start int, time_start int not null, time_end int, start_lat float, start_lon float, end_lat float, end_lon float, freqtripid int, comment varchar(255), roadtrip_end_aid int, has_continue int not null default 0 );
	-- if tstopid_start not null, it's the endpoint of a previous trip with the same odo_total.
	--    This gives the starting location (descr and/or locid) for the trip.
	-- odo_end is 0 until trip is completed

create index "trip~odo" ON trip(vid, odo_start);
create index "trip~d" ON trip(vid, time_start);

create table freqtrip ( _id integer PRIMARY KEY AUTOINCREMENT not null, aid int, start_lat float, start_lon float, end_lat float, end_lon float, odo_total int not null, roadtrip_end_aid int, desc varchar(255), typ_timeofday int, flag_weekends int not null default 0, flag_weekdays int not null default 0);
	-- if typ_timeofday not null, it's a 24-hour time hhmm as integer digits.

-- create index "freqtrip~aid" ON freqtrip(aid); (later? TODO)

-- Convention for chronological order of stops within a trip:
--    (Needed because any useful field can be null)
--    Stops are inserted in their chronological order.
--    So, ORDER BY _id will give the proper ordering.
-- The last stop of a trip may be referenced by a field in
--    the next trip, for its locid and descr.
--    So, the last stop's odo_total must not be null,
--	and either its locid or its descr must not be null.
--	The last stop's time_continue must be null.
-- If this is not the case, then a trip's first tstop must have
--    the same odo_total as the trip's odo_start,
--	and 0 odo_trip, and null time_stop,
--	and either its locid or its descr must not be null.

create table tstop ( _id integer PRIMARY KEY AUTOINCREMENT not null, tripid int not null, odo_total int, odo_trip int, time_stop int, time_continue int, locid int, geo_lat float, geo_lon float, flag_sides int not null default 0, descr varchar(255), via_route varchar(255), comment varchar(255));
	-- flag_sides: sidetables (exercise, food, gas, car-service)

create index "tstop~t" ON tstop(tripid);

-- NOTE: tstop_gas._id == associated tstop._id
--   Fillup: 1 or 0 (Fill the tank, or partial)
create table tstop_gas ( _id integer PRIMARY KEY not null, quant int not null, price_per int not null, price_total int not null, fillup int not null, station varchar(255));

create table location ( _id integer PRIMARY KEY AUTOINCREMENT not null, aid int, geo_lat float, geo_lon float, desc varchar(255) );

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
commit;

-- master-data inserts done --

-- (TODO) remaining side tables


