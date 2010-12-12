-- upgrade from v0905 to v0906: (2010-12-12)

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

-- Gas brand/grade, for tstop_gas
create table gas_brandgrade ( _id integer PRIMARY KEY AUTOINCREMENT not null, name varchar(255) not null );

-- Note that tstop_gas.station isn't auto-converted to new gas_brandgrades.
--    This is not acceptable when there are users of the software,
--    but at this early point, our user is okay with it.
ALTER TABLE tstop_gas ADD COLUMN gas_brandgrade_id int;

--    latest_gas_brandgrade_id is for the auto-fill default at gas stop locations.
ALTER TABLE location ADD COLUMN latest_gas_brandgrade_id int;
