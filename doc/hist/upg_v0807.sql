-- upgrade from v0805 to v0807: (2010-10-15)

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

BEGIN TRANSACTION;
ALTER TABLE vehicle ADD COLUMN distance_storage varchar(2) not null DEFAULT 'MI';
ALTER TABLE vehicle ADD COLUMN expense_currency varchar(3) not null DEFAULT 'USD';
ALTER TABLE vehicle ADD COLUMN expense_curr_sym varchar(3) not null DEFAULT '$';
ALTER TABLE vehicle ADD COLUMN expense_curr_deci integer not null DEFAULT 2;
ALTER TABLE vehicle ADD COLUMN fuel_curr_deci integer not null DEFAULT 3;
ALTER TABLE vehicle ADD COLUMN fuel_type varchar(1) not null DEFAULT 'G';
ALTER TABLE vehicle ADD COLUMN fuel_qty_unit varchar(2) not null DEFAULT 'ga';
ALTER TABLE vehicle ADD COLUMN fuel_qty_deci integer not null DEFAULT 3;
COMMIT;

