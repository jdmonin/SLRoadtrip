-- org.shadowlands.roadtrip
-- version 0.8.00 test data  2010-03-29 (for schema 0.8.00)

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

-- Assumes an empty, newly created db.
-- assumes Honda == vehiclemake 15, for instance

insert into person(_id, is_driver, name) values (5, 1, 'Brian');

insert into vehicle ( _id, nickname, driverid, makeid, model, year, date_from, date_to, vin, comment) values (4, '97 accord', 5, 15, 'Accord', 1997, 982170000, 1249853880, '1HGC05606VA270000', 'testdata');

-- FAILURE_OK:
insert into appinfo (aifield, aivalue) values ('DISTANCE_STORAGE', 'MI');

begin transaction;
insert into settings (sname, ivalue) values ('CURRENT_DRIVER', 5);
insert into settings (sname, ivalue) values ('CURRENT_VEHICLE', 4);
insert into settings (sname, svalue) values ('DISTANCE_DISPLAY', 'MI');
end transaction;

-- sample trip on 21 Dec 2008
--    to calculate unix-format dates/times in perl:
--    use Time::Local;              # month gets -1 (0 to 11)
--    print scalar timelocal (0, 38, 15, 21, 11, 2008); print "\n";
--    1229891880

insert into trip (_id, vid, did, odo_start, odo_end, time_start, time_end, comment) values (7, 4, 5, 2353720, 2353820, 1229891880, 1229905140, 'stores, dinner, home' );

-- trip details:
--       235372       home
-- 15:38
-- 15:51
--       235377 (4.5) barnes & noble plaza on NFB
-- 17:50
-- 17:52
--       235377 (4.7) pho saigon restau.
-- 18:54
-- 19:01
--       235380 (7.8) weg on NFB        [snowy]
-- 19:12
-- 19:19        9.9   -> home on smith st
--       235382

begin transaction;
insert into tstop (_id, tripid, odo_total, odo_trip, descr) values (10, 7, 2353720, 0, 'home');
insert into tstop (_id, tripid, odo_total, odo_trip, time_stop, time_start, descr, via_route) values (11, 7, 2353770, 45, 1229892660, 1229899800, 'barnes & noble plaza on NFB', 'NFB' );
insert into tstop (_id, tripid, odo_total, odo_trip, time_stop, time_start, descr) values (12, 7, 2353770, 47, 1229899920, 1229903640, 'pho saigon restau.' );
insert into tstop (_id, tripid, odo_total, odo_trip, time_stop, time_start, descr, comment) values (13, 7, 2353800, 78, 1229904060, 1229904720, 'weg on NFB', 'snowy' );
insert into tstop (_id, tripid, odo_total, odo_trip, time_stop, descr) values (14, 7, 2353820, 99, 1229905140, '-> home on smith st' );
commit;


-- 2 sample trips on 24 Dec 2008

-- trip details:
--	235468		home
-- 07:48
-- 07:56
--	235471 (3.6)	home depot NFB
-- 08:02
-- 08:18		14.5	-> work, via I290 -> I190
--	235482
-- 15:22			[second trip begins at 15:22]
--			outlet store  [this stop has a start-time of 15:28, but no stop-time]
-- 15:28
-- 15:33
--	235484 (2.1)	tops buf Grant St
-- 15:48
-- 15:57
--	235487 (4.7)	elmwood Home Depot, via ny198 -> Elmwood
-- 16:06
-- 16:12
--	235487 (5.4)	target elmwood/delaware
--
--	235492 (10.2)	Gas: Partial: 4.025 @ $1.879 [$7.56] delta sonic Dela.
--
-- 16:51
--	235493 (11.2)	card dropoff @ friend's  [this stop has a stop-time of 16:51, but no start-time]
-- 17:19
--	235496 (14.1)	dave pickup @ dave's
--
--	235501 (18.9)	dinner @ burger place
-- 18:21
-- 18:34
--	235507 (24.3)	dave apt
-- 23:04
-- 23:11	27.9	-> home
--	235510

begin transaction;

insert into trip (_id, vid, did, odo_start, odo_end, time_start, time_end, comment) values (8, 4, 5, 2354680, 2354820, 1230122880, 1230124680, null );

insert into tstop (_id, tripid, odo_total, odo_trip, descr) values (20, 8, 2354680, 0, 'home');
insert into tstop (_id, tripid, odo_total, odo_trip, time_stop, time_start, descr) values (21, 8, 2354710, 36, 1230123360, 1230123720, 'home depot NFB' );
insert into tstop (_id, tripid, odo_total, odo_trip, descr, via_route) values (22, 8, 2354820, 145, 'work', 'I290 -> I190' );

commit;

-- 2nd trip uses 1st trip's final tstop (_id 22) as its tstopid_start

begin transaction;

insert into trip (_id, vid, did, odo_start, odo_end, time_start, time_end, comment, tstopid_start) values (9, 4, 5, 2354820, 2355100, 1230150120, 1230178260, null, 22 );

insert into tstop (_id, tripid, time_start, descr) values (30, 9, 1230150480, 'outlet store' );
insert into tstop (_id, tripid, odo_total, odo_trip, time_stop, time_start, descr) values (31, 9, 2354840, 21, 1230150780, 1230151680, 'tops buf Grant St' );
insert into tstop (_id, tripid, odo_total, odo_trip, time_stop, time_start, descr, via_route) values (32, 9, 2354870, 47, 1230152220, 1230152760, 'elmwood Home Depot', 'ny198 -> Elmwood' );
insert into tstop (_id, tripid, odo_total, odo_trip, time_stop, descr) values (33, 9, 2354870, 54, 1230153120, 'target elmwood/delaware' );
insert into tstop (_id, tripid, odo_total, odo_trip,descr) values (34, 9, 2354920, 102, 'delta sonic dela.' );
	-- (TODO) gas sidetable
insert into tstop (_id, tripid, odo_total, odo_trip, time_stop, descr) values (35, 9, 2354930, 112, 1230155460, 'card dropoff @ friend''s' );
insert into tstop (_id, tripid, odo_total, odo_trip, time_stop, descr) values (36, 9, 2354960, 141, 1230157140, 'dave pickup @ dave''s' );
insert into tstop (_id, tripid, odo_total, odo_trip, time_start, descr) values (37, 9, 2355010, 189, 1230160860, 'dinner @ burget place Maple' );
	-- (TODO) meal sidetable
insert into tstop (_id, tripid, odo_total, odo_trip, time_stop, time_start, descr) values (38, 9, 2355070, 243, 1230161640, 1230177840, 'dave apt' );
insert into tstop (_id, tripid, odo_total, odo_trip, descr) values (39, 9, 2355100, 279, 'home' );

commit;
