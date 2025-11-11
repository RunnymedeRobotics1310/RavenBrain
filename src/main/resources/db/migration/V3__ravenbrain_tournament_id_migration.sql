-- update tournament ids in RB_TOURNAMENT table
UPDATE RB_TOURNAMENT
SET id = "ONNEW"
where id = "newmarket";

UPDATE RB_TOURNAMENT
SET id = "ONSCA"
where id = "centennial";

UPDATE RB_TOURNAMENT
SET id = "ONNOB"
where id = "northbay";

UPDATE RB_TOURNAMENT
SET id = "ONCMP1"
where id = "dcmp_ontario_science";

UPDATE RB_TOURNAMENT
SET id = "ONCMP2"
where id = "dcmp_ontario_technology";

UPDATE RB_TOURNAMENT
SET id = "ONCMP"
where id = "dcmp_ontario_finals";

-- update tournament ids in RB_EVENT table
UPDATE RB_EVENT
SET tournamentid = "2025ONNEW"
where tournamentid = "newmarket";
UPDATE RB_EVENT
SET tournamentid = "2025ONSCA"
where tournamentid = "centennial";
UPDATE RB_EVENT
SET tournamentid = "2025ONNOB"
where tournamentid = "northbay";
UPDATE RB_EVENT
SET tournamentid = "2025ONCMP1"
where tournamentid = "dcmp_ontario_science";
UPDATE RB_EVENT
SET tournamentid = "2025ONCMP2"
where tournamentid = "dcmp_ontario_technology";
UPDATE RB_EVENT
SET tournamentid = "2025ONCMP"
where tournamentid = "dcmp_ontario_finals";

-- update tournament ids in RB_SCHEDULE table
UPDATE RB_SCHEDULE
SET tournamentid = "2025ONNEW"
where tournamentid = "newmarket";
UPDATE RB_SCHEDULE
SET tournamentid = "2025ONSCA"
where tournamentid = "centennial";
UPDATE RB_SCHEDULE
SET tournamentid = "2025ONNOB"
where tournamentid = "northbay";
UPDATE RB_SCHEDULE
SET tournamentid = "2025ONCMP1"
where tournamentid = "dcmp_ontario_science";
UPDATE RB_SCHEDULE
SET tournamentid = "2025ONCMP2"
where tournamentid = "dcmp_ontario_technology";
UPDATE RB_SCHEDULE
SET tournamentid = "2025ONCMP"
where tournamentid = "dcmp_ontario_finals";

-- add a season column to tournament table
ALTER TABLE RB_TOURNAMENT ADD season int NULL after id;
-- initialize data post-migration
UPDATE RB_TOURNAMENT set season = 2025;
-- modify column to be not null
ALTER TABLE RB_TOURNAMENT MODIFY season int NOT NULL;

-- remap id column to code and redefine id to be seasonCODE
ALTER TABLE RB_TOURNAMENT ADD code varchar(127) NULL after id;
UPDATE RB_TOURNAMENT set code = "ONNEW" where id = "ONNEW";
UPDATE RB_TOURNAMENT set code = "ONSCA" where id = "ONSCA";
UPDATE RB_TOURNAMENT set code = "ONNOB" where id = "ONNOB";
UPDATE RB_TOURNAMENT set code = "ONCMP1" where id = "ONCMP1";
UPDATE RB_TOURNAMENT set code = "ONCMP2" where id = "ONCMP2";
UPDATE RB_TOURNAMENT set code = "ONCMP" where id = "ONCMP";
UPDATE RB_TOURNAMENT set code = "demo-test" where id = "demo-test";
UPDATE RB_TOURNAMENT set code = "centennial_practice" where id = "centennial_practice";
ALTER TABLE RB_TOURNAMENT MODIFY code varchar(127) NOT NULL;
UPDATE RB_TOURNAMENT set id = "2025ONNEW" where code = "ONNEW";
UPDATE RB_TOURNAMENT set id = "2025ONSCA" where code = "ONSCA";
UPDATE RB_TOURNAMENT set id = "2025ONNOB" where code = "ONNOB";
UPDATE RB_TOURNAMENT set id = "2025ONCMP1" where code = "ONCMP1";
UPDATE RB_TOURNAMENT set id = "2025ONCMP2" where code = "ONCMP2";
UPDATE RB_TOURNAMENT set id = "2025ONCMP" where code = "ONCMP";
UPDATE RB_TOURNAMENT set id = "2025demo-test" where code = "demo-test";
UPDATE RB_TOURNAMENT set id = "2025centennial_practice" where code = "centennial_practice";
