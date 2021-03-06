-- upgrade from v0807 to v0809: (2010-10-23)

-- This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
-- 
--  Copyright (C) 2010 Jeremy D Monin (jdmonin@nand.net)
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

-- freqtrip, location table fields change; tables unused before v0809, so OK to drop/recreate.
DROP TABLE location;
create table location ( _id integer PRIMARY KEY AUTOINCREMENT not null, a_id int, geo_lat float, geo_lon float, loc_descr varchar(255) not null );
DROP TABLE freqtrip;
create table freqtrip ( _id integer PRIMARY KEY AUTOINCREMENT not null, a_id int, start_locid integer not null, end_locid integer not null, start_lat float, start_lon float, end_lat float, end_lon float, end_odo int not null, roadtrip_end_aid int, descr varchar(255), via_route varchar(255), typ_timeofday int, flag_weekends int not null default 0, flag_weekdays int not null default 0);
create table freqtrip_tstop ( _id integer PRIMARY KEY AUTOINCREMENT not null, freqtripid int not null, locid int not null, odo_trip int);
