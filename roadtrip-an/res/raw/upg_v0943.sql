-- Upgrade from v0940 to v0943: (2015-05-26)

-- This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
--
--  This file Copyright (C) 2015 Jeremy D Monin (jdmonin@nand.net)
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

ALTER TABLE geoarea ADD COLUMN date_added int;
ALTER TABLE person  ADD COLUMN date_added int;
ALTER TABLE vehicle ADD COLUMN date_added int;

-- The other schema change for 0943 is that in
-- new installs, settings('HIDE_FREQTRIP') is 1.
-- That change does not affect current installs.
