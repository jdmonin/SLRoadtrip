-- Upgrade from v0909 to v0940: (2014-02-15)

-- This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
-- 
--  This file Copyright (C) 2014 Jeremy D Monin (jdmonin@nand.net)
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

-- Per-vehicle settings:

CREATE TABLE veh_settings ( _id integer PRIMARY KEY AUTOINCREMENT not null, vid int not null, sname varchar(32) not null, svalue varchar(64), ivalue int );

INSERT INTO veh_settings(vid,sname,svalue,ivalue) SELECT 0, sname, svalue, ivalue FROM settings WHERE sname IN ('CURRENT_DRIVER', 'CURRENT_TRIP', 'CURRENT_TSTOP', 'CURRENT_AREA', 'CURRENT_FREQTRIP', 'CURRENT_FREQTRIP_TSTOPLIST', 'PREV_LOCATION');

UPDATE veh_settings SET vid=(select ivalue from settings where sname='CURRENT_VEHICLE');

CREATE UNIQUE INDEX "veh_settings~vs" ON veh_settings(vid,sname);

DELETE FROM settings WHERE sname IN ('CURRENT_DRIVER', 'CURRENT_TRIP', 'CURRENT_TSTOP', 'CURRENT_AREA', 'CURRENT_FREQTRIP', 'CURRENT_FREQTRIP_TSTOPLIST', 'PREV_LOCATION');

-- vehiclemake adds 2014-02-15 v0940: geely = 54 saic = 55 tata = 56

INSERT INTO vehiclemake(mname) values ('Geely');
INSERT INTO vehiclemake(mname) values ('SAIC');
INSERT INTO vehiclemake(mname) values ('Tata');
