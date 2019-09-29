-- Upgrade from v0961 to v0990: (2019-09-29)

-- This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
--
--  This file Copyright (C) 2019 Jeremy D Monin (jdmonin@nand.net)
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

UPDATE vehiclemake SET mname='Mercedes-Benz' WHERE mname='Mercedes';

-- A few more car brands: _ID 57-67

INSERT INTO vehiclemake(mname) values ('BAIC');
INSERT INTO vehiclemake(mname) values ('BYD');
INSERT INTO vehiclemake(mname) values ('Chang''an');
INSERT INTO vehiclemake(mname) values ('Dacia');
INSERT INTO vehiclemake(mname) values ('Dongfeng');
INSERT INTO vehiclemake(mname) values ('FAW');
INSERT INTO vehiclemake(mname) values ('Hino');
INSERT INTO vehiclemake(mname) values ('Mahindra');
INSERT INTO vehiclemake(mname) values ('SEAT');
INSERT INTO vehiclemake(mname) values ('Skoda');
INSERT INTO vehiclemake(mname) values ('Vauxhall');
