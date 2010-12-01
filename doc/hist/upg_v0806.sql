-- upgrade from v0805 to v0806: (2010-10-15)
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

