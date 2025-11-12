ALTER TABLE RB_SCHEDULE
    ADD level varchar(127) NULL after tournamentid;

UPDATE RB_SCHEDULE
SET level = 'Qualification'
where matchnum > 0
  AND matchnum < 101;


UPDATE RB_SCHEDULE
SET level = 'Playoff'
where matchnum > 100;

UPDATE RB_SCHEDULE
SET level = 'Practice'
where tournamentid = 'centennial_practice';

UPDATE RB_SCHEDULE
SET level = 'Playoff'
where tournamentid = '2025ONCMP';



ALTER TABLE RB_SCHEDULE
    MODIFY level varchar(127) NOT NULL;
