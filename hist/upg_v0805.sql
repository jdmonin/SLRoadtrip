-- upgrade from v0800 to v0805: (2010-09-06)
BEGIN TRANSACTION;
ALTER TABLE vehicle ADD COLUMN odo_orig integer not null DEFAULT 0;
ALTER TABLE vehicle ADD COLUMN odo_curr integer not null DEFAULT 0;
ALTER TABLE vehicle ADD COLUMN last_tripid integer;
COMMIT;

