-- Add score and schedule columns to RB_SCHEDULE
ALTER TABLE RB_SCHEDULE
    ADD starttime VARCHAR(10) NULL AFTER matchnum,
    ADD red4 INT NOT NULL DEFAULT 0 AFTER red3,
    ADD blue4 INT NOT NULL DEFAULT 0 AFTER blue3,
    ADD redrp INT NULL AFTER redscore,
    ADD bluerp INT NULL AFTER bluescore,
    ADD winningalliance INT NOT NULL DEFAULT 0 AFTER bluerp;
