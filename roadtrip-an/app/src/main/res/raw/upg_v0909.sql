-- Upgrade from v0908 to v0909: (2012-12-06)

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

ALTER TABLE trip ADD COLUMN passengers int;

ALTER TABLE tripcategory ADD COLUMN is_work_related int not null default 0;
update tripcategory set is_work_related = 1 where cname = 'Work';

ALTER TABLE vehicle ADD COLUMN plate varchar(64);
