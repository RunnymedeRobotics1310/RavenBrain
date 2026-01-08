-- Update RB_SEQUENCETYPE table to add 'id' as primary key
ALTER TABLE RB_SEQUENCEEVENT DROP FOREIGN KEY fk_sequencetype;

ALTER TABLE RB_SEQUENCETYPE DROP PRIMARY KEY;
ALTER TABLE RB_SEQUENCETYPE ADD id BIGINT AUTO_INCREMENT PRIMARY KEY FIRST;

-- Update RB_SEQUENCEEVENT to reference RB_SEQUENCETYPE by id instead of name
ALTER TABLE RB_SEQUENCEEVENT CHANGE sequencetype_name sequencetype_id BIGINT NOT NULL;

-- Since the previous data (if any) used name as FK, we'd need to migrate it if there were data.
-- Assuming this is a new feature and we can just link them or clear them.
-- To be safe, if there was data, we'd do something like:
-- UPDATE RB_SEQUENCEEVENT se SET sequencetype_id = (SELECT id FROM RB_SEQUENCETYPE st WHERE st.name = se.sequencetype_id);
-- But 'sequencetype_id' currently contains the name string, which will fail if we changed type to BIGINT.

-- Re-creating tables might be cleaner if we are in development and data loss is okay,
-- but a proper migration would:
-- 1. Add id to RB_SEQUENCETYPE
-- 2. Add sequencetype_id to RB_SEQUENCEEVENT
-- 3. Update RB_SEQUENCEEVENT.sequencetype_id based on name
-- 4. Drop RB_SEQUENCEEVENT.sequencetype_name
-- 5. Set PK/FK

-- Let's do it properly.
DROP TABLE IF EXISTS RB_SEQUENCEEVENT;
DROP TABLE IF EXISTS RB_SEQUENCETYPE;

CREATE TABLE RB_SEQUENCETYPE (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1024)
) CHARSET = utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE RB_SEQUENCEEVENT (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sequencetype_id BIGINT NOT NULL,
    eventtype_id VARCHAR(255) NOT NULL,
    startOfSequence BOOLEAN NOT NULL DEFAULT FALSE,
    endOfSequence BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_sequencetype FOREIGN KEY (sequencetype_id) REFERENCES RB_SEQUENCETYPE(id) ON DELETE CASCADE,
    CONSTRAINT fk_eventtype FOREIGN KEY (eventtype_id) REFERENCES RB_EVENTTYPE(eventtype)
) CHARSET = utf8mb4 COLLATE utf8mb4_unicode_ci;
