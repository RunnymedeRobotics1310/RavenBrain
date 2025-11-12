ALTER TABLE RB_EVENT
    ADD level varchar(127) NULL after tournamentid;

UPDATE RB_EVENT
SET level = 'Qualification';

UPDATE RB_EVENT
SET level = 'Practice'
WHERE tournamentid = 'centennial_practice';

ALTER TABLE RB_SCHEDULE
    DROP constraint tourn_match;
ALTER TABLE RB_SCHEDULE
    ADD UNIQUE KEY tourn_lvl_match (tournamentId, level, matchnum);

ALTER TABLE RB_EVENT
    MODIFY level varchar(127) NOT NULL;

UPDATE RB_EVENT
set tournamentid = '2025ONSCA'
where tournamentid = 'centennial_practice';

UPDATE RB_SCHEDULE
set tournamentid = '2025ONSCA'
where tournamentid = 'centennial_practice';

DELETE
FROM RB_TOURNAMENT
WHERE id = '2025centennial_practice';
