-- upgrade from v0813 to v0901: (2010-11-16)

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

-- freqtrip table unused before v0900, so OK to drop/recreate.
DROP TABLE freqtrip;
create table freqtrip ( _id integer PRIMARY KEY AUTOINCREMENT not null, a_id int, start_locid integer not null, end_locid integer not null, end_odo_trip int not null, roadtrip_end_aid int, descr varchar(255), end_via_id int, typ_timeofday int, flag_weekends int not null default 0, flag_weekdays int not null default 0);
create index "freqtrip~l" ON freqtrip(start_locid);

ALTER TABLE freqtrip_tstop ADD COLUMN via_id int;
create index "freqtrip_tstop~f" ON freqtrip_tstop(freqtripid);

ALTER TABLE via_route ADD COLUMN odo_dist int;
