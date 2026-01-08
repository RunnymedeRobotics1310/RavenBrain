ALTER TABLE RB_COMMENT
    ADD COLUMN userid bigint AFTER commenttimestamp;

UPDATE RB_COMMENT
set userid = 9
where scoutname = 'Tony';
UPDATE RB_COMMENT
set userid = 13
where scoutname = 'Leia';
UPDATE RB_COMMENT
set userid = 8
where scoutname = 'Quentin';
UPDATE RB_COMMENT
set userid = 17
where scoutname = 'Jackson';

ALTER TABLE RB_COMMENT
    MODIFY userid BIGINT NOT NULL;
ALTER TABLE RB_COMMENT
    DROP CONSTRAINT time_scout;
ALTER TABLE RB_COMMENT
    add constraint time_user unique (commenttimestamp, userid);
ALTER TABLE RB_COMMENT
    DROP COLUMN scoutname;


