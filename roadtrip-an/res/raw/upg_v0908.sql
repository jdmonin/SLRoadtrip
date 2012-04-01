-- Upgrade from v0906 to v0908: (2012-04-01)  [schema versions skip 0907]

-- This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
-- 
--  This file Copyright (C) 2012 Jeremy D Monin (jdmonin@nand.net)
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

ALTER TABLE person ADD COLUMN is_active int not null default 1;
ALTER TABLE person ADD COLUMN comment varchar(255);

ALTER TABLE vehicle ADD COLUMN is_active int not null default 1;

ALTER TABLE freqtrip ADD COLUMN is_roundtrip int not null default 0;

ALTER TABLE vehiclemake ADD COLUMN is_user_add int;

ALTER TABLE trip ADD COLUMN catid int;
CREATE INDEX "trip~cv" ON trip(catid, vid);

CREATE TABLE tripcategory ( _id integer PRIMARY KEY AUTOINCREMENT not null, cname varchar(255) not null unique, pos int not null, is_user_add int );

CREATE TABLE app_db_upgrade_hist ( db_vers_to int not null, db_vers_from not null, upg_time int not null );

-- vehiclemake adds 2012-04-01 v0908: citroen = 52 renault = 53
insert into vehiclemake(mname) values ('Citroen');
insert into vehiclemake(mname) values ('Renault');

-- tripcategory initial contents 2012-04-01 v0908:
insert into tripcategory(cname,pos) values ('Work', 1);
insert into tripcategory(cname,pos) values ('Personal', 2);
insert into tripcategory(cname,pos) values ('Volunteer', 3);
insert into tripcategory(cname,pos) values ('Moving', 4);
