# Old V8
CREATE TABLE RB_USER
(
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    login           VARCHAR(255)  NOT NULL UNIQUE,
    display_name    VARCHAR(255)  NOT NULL,
    password_hash   VARCHAR(255)  NOT NULL,
    roles           VARCHAR(1024) NOT NULL,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    forgot_password BOOLEAN       NOT NULL DEFAULT FALSE
);


# Old v9
CREATE TABLE RB_STRATEGYAREA
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(1024)
);


# Old v10
ALTER TABLE RB_STRATEGYAREA
    ADD COLUMN frcyear INT NOT NULL;


# Old v11
CREATE TABLE IF NOT EXISTS RB_EVENTTYPE
(
    eventtype       VARCHAR(255) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     VARCHAR(1024),
    frcyear         INT          NOT NULL,
    strategyarea_id BIGINT,
    CONSTRAINT fk_strategyarea FOREIGN KEY (strategyarea_id) REFERENCES RB_STRATEGYAREA (id),
    CONSTRAINT eventtype_format CHECK (eventtype REGEXP '^[0-9a-zA-Z-]+$')
) CHARSET = utf8mb4
  COLLATE utf8mb4_unicode_ci;

INSERT INTO RB_STRATEGYAREA (id, name, description, frcyear)
VALUES (-1, 'N/A', 'Not Applicable', 2025)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO RB_EVENTTYPE (eventtype, name, description, frcyear, strategyarea_id)
VALUES ('auto-start-left', 'auto-start-left', 'auto-start-left', 2025, -1),
       ('preloaded-nothing', 'preloaded-nothing', 'preloaded-nothing', 2025, -1),
       ('set-phase-AUTO', 'set-phase-AUTO', 'set-phase-AUTO', 2025, -1),
       ('auto-start-center', 'auto-start-center', 'auto-start-center', 2025, -1),
       ('pickup-coral-station-left', 'pickup-coral-station-left', 'pickup-coral-station-left', 2025, -1),
       ('defence-started', 'defence-started', 'defence-started', 2025, -1),
       ('score-reef-l3', 'score-reef-l3', 'score-reef-l3', 2025, -1),
       ('climb-park', 'climb-park', 'climb-park', 2025, -1),
       ('set-phase-COMMENTS', 'set-phase-COMMENTS', 'set-phase-COMMENTS', 2025, -1),
       ('set-phase-PRE-MATCH', 'set-phase-PRE-MATCH', 'set-phase-PRE-MATCH', 2025, -1),
       ('comment', 'comment', 'comment', 2025, -1),
       ('star-rating-3', 'star-rating-3', 'star-rating-3', 2025, -1),
       ('pickup-coral-floor', 'pickup-coral-floor', 'pickup-coral-floor', 2025, -1),
       ('score-reef-l4', 'score-reef-l4', 'score-reef-l4', 2025, -1),
       ('pickup-coral-auto-center', 'pickup-coral-auto-center', 'pickup-coral-auto-center', 2025, -1),
       ('pickup-algae-floor', 'pickup-algae-floor', 'pickup-algae-floor', 2025, -1),
       ('drop-algae', 'drop-algae', 'drop-algae', 2025, -1),
       ('climb-deep', 'climb-deep', 'climb-deep', 2025, -1),
       ('feedback-play-defence', 'feedback-play-defence', 'feedback-play-defence', 2025, -1),
       ('coral-rp', 'coral-rp', 'coral-rp', 2025, -1),
       ('climb-none', 'climb-none', 'climb-none', 2025, -1),
       ('star-rating-0', 'star-rating-0', 'star-rating-0', 2025, -1),
       ('preloaded-coral', 'preloaded-coral', 'preloaded-coral', 2025, -1),
       ('auto-start-right', 'auto-start-right', 'auto-start-right', 2025, -1),
       ('pickup-algae-reef', 'pickup-algae-reef', 'pickup-algae-reef', 2025, -1),
       ('remove-algae', 'remove-algae', 'remove-algae', 2025, -1),
       ('drop-coral', 'drop-coral', 'drop-coral', 2025, -1),
       ('penalty-off-limit-contact', 'penalty-off-limit-contact', 'penalty-off-limit-contact', 2025, -1),
       ('score-algae-net', 'score-algae-net', 'score-algae-net', 2025, -1),
       ('pickup-coral-auto-left', 'pickup-coral-auto-left', 'pickup-coral-auto-left', 2025, -1),
       ('score-reef-l2', 'score-reef-l2', 'score-reef-l2', 2025, -1),
       ('climb-shallow', 'climb-shallow', 'climb-shallow', 2025, -1),
       ('pickup-algae-auto-center', 'pickup-algae-auto-center', 'pickup-algae-auto-center', 2025, -1),
       ('feedback-foul-often', 'feedback-foul-often', 'feedback-foul-often', 2025, -1),
       ('pickup-coral-auto-right', 'pickup-coral-auto-right', 'pickup-coral-auto-right', 2025, -1),
       ('pickup-algae-auto-left', 'pickup-algae-auto-left', 'pickup-algae-auto-left', 2025, -1),
       ('pickup-algae-auto-right', 'pickup-algae-auto-right', 'pickup-algae-auto-right', 2025, -1),
       ('score-algae-processor', 'score-algae-processor', 'score-algae-processor', 2025, -1),
       ('pickup-coral-station-right', 'pickup-coral-station-right', 'pickup-coral-station-right', 2025, -1),
       ('score-reef-l1', 'score-reef-l1', 'score-reef-l1', 2025, -1),
       ('feedback-shut-down', 'feedback-shut-down', 'feedback-shut-down', 2025, -1),
       ('barge-rp', 'barge-rp', 'barge-rp', 2025, -1),
       ('auto-rp', 'auto-rp', 'auto-rp', 2025, -1),
       ('feedback-fell-over', 'feedback-fell-over', 'feedback-fell-over', 2025, -1),
       ('feedback-recover', 'feedback-recover', 'feedback-recover', 2025, -1),
       ('star-rating-1', 'star-rating-1', 'star-rating-1', 2025, -1),
       ('penalty-throwing-algae', 'penalty-throwing-algae', 'penalty-throwing-algae', 2025, -1),
       ('penalty-pin', 'penalty-pin', 'penalty-pin', 2025, -1),
       ('feedback-score-consistently', 'feedback-score-consistently', 'feedback-score-consistently', 2025, -1),
       ('defence-stopped', 'defence-stopped', 'defence-stopped', 2025, -1),
       ('penalty-other', 'penalty-other', 'penalty-other', 2025, -1),
       ('penalty-field-damage', 'penalty-field-damage', 'penalty-field-damage', 2025, -1),
       ('penalty-too-many-game-pieces', 'penalty-too-many-game-pieces', 'penalty-too-many-game-pieces', 2025, -1),
       ('star-rating-2', 'star-rating-2', 'star-rating-2', 2025, -1),
       ('score-reef-miss', 'score-reef-miss', 'score-reef-miss', 2025, -1),
       ('feedback-drove-fast', 'feedback-drove-fast', 'feedback-drove-fast', 2025, -1),
       ('penalty-zone-violation', 'penalty-zone-violation', 'penalty-zone-violation', 2025, -1),
       ('star-rating-4', 'star-rating-4', 'star-rating-4', 2025, -1),
       ('rematch', 'rematch', 'rematch', 2025, -1),
       ('PRE-MATCH-rematch', 'PRE-MATCH-rematch', 'PRE-MATCH-rematch', 2025, -1),
       ('AUTO-auto-start-center', 'AUTO-auto-start-center', 'AUTO-auto-start-center', 2025, -1),
       ('AUTO-preloaded-nothing', 'AUTO-preloaded-nothing', 'AUTO-preloaded-nothing', 2025, -1),
       ('PRE-MATCH-set-phase-AUTO', 'PRE-MATCH-set-phase-AUTO', 'PRE-MATCH-set-phase-AUTO', 2025, -1),
       ('AUTO-auto-start-left', 'AUTO-auto-start-left', 'AUTO-auto-start-left', 2025, -1),
       ('AUTO-remove-algae', 'AUTO-remove-algae', 'AUTO-remove-algae', 2025, -1),
       ('AUTO-score-reef-l4', 'AUTO-score-reef-l4', 'AUTO-score-reef-l4', 2025, -1),
       ('AUTO-pickup-algae-reef', 'AUTO-pickup-algae-reef', 'AUTO-pickup-algae-reef', 2025, -1),
       ('AUTO-score-algae-processor', 'AUTO-score-algae-processor', 'AUTO-score-algae-processor', 2025, -1),
       ('AUTO-pickup-coral-floor', 'AUTO-pickup-coral-floor', 'AUTO-pickup-coral-floor', 2025, -1),
       ('AUTO-score-reef-miss', 'AUTO-score-reef-miss', 'AUTO-score-reef-miss', 2025, -1),
       ('AUTO-pickup-coral-auto-center', 'AUTO-pickup-coral-auto-center', 'AUTO-pickup-coral-auto-center', 2025, -1),
       ('TELEOP-defence-started', 'TELEOP-defence-started', 'TELEOP-defence-started', 2025, -1),
       ('TELEOP-defence-stopped', 'TELEOP-defence-stopped', 'TELEOP-defence-stopped', 2025, -1),
       ('TELEOP-remove-algae', 'TELEOP-remove-algae', 'TELEOP-remove-algae', 2025, -1),
       ('TELEOP-pickup-algae-floor', 'TELEOP-pickup-algae-floor', 'TELEOP-pickup-algae-floor', 2025, -1),
       ('TELEOP-score-algae-processor', 'TELEOP-score-algae-processor', 'TELEOP-score-algae-processor', 2025, -1),
       ('TELEOP-pickup-coral-floor', 'TELEOP-pickup-coral-floor', 'TELEOP-pickup-coral-floor', 2025, -1),
       ('TELEOP-score-reef-l2', 'TELEOP-score-reef-l2', 'TELEOP-score-reef-l2', 2025, -1),
       ('ENDGAME-set-phase-COMMENTS', 'ENDGAME-set-phase-COMMENTS', 'ENDGAME-set-phase-COMMENTS', 2025, -1),
       ('ENDGAME-climb-deep', 'ENDGAME-climb-deep', 'ENDGAME-climb-deep', 2025, -1),
       ('COMMENTS-feedback-shut-down', 'COMMENTS-feedback-shut-down', 'COMMENTS-feedback-shut-down', 2025, -1),
       ('COMMENTS-comment', 'COMMENTS-comment', 'COMMENTS-comment', 2025, -1),
       ('COMMENTS-coral-rp', 'COMMENTS-coral-rp', 'COMMENTS-coral-rp', 2025, -1),
       ('COMMENTS-set-phase-PRE-MATCH', 'COMMENTS-set-phase-PRE-MATCH', 'COMMENTS-set-phase-PRE-MATCH', 2025, -1),
       ('COMMENTS-star-rating-3', 'COMMENTS-star-rating-3', 'COMMENTS-star-rating-3', 2025, -1),
       ('star-rating-5', 'star-rating-5', 'star-rating-5', 2025, -1),
       ('feedback-effective-defence', 'feedback-effective-defence', 'feedback-effective-defence', 2025, -1),
       ('AUTO-auto-start-right', 'AUTO-auto-start-right', 'AUTO-auto-start-right', 2025, -1),
       ('AUTO-pickup-coral-station-right', 'AUTO-pickup-coral-station-right', 'AUTO-pickup-coral-station-right', 2025,
        -1),
       ('TELEOP-pickup-coral-station-right', 'TELEOP-pickup-coral-station-right', 'TELEOP-pickup-coral-station-right',
        2025, -1),
       ('TELEOP-score-reef-l4', 'TELEOP-score-reef-l4', 'TELEOP-score-reef-l4', 2025, -1),
       ('TELEOP-score-reef-miss', 'TELEOP-score-reef-miss', 'TELEOP-score-reef-miss', 2025, -1),
       ('TELEOP-drop-algae', 'TELEOP-drop-algae', 'TELEOP-drop-algae', 2025, -1),
       ('TELEOP-pickup-coral-station-left', 'TELEOP-pickup-coral-station-left', 'TELEOP-pickup-coral-station-left',
        2025, -1),
       ('ENDGAME-climb-park', 'ENDGAME-climb-park', 'ENDGAME-climb-park', 2025, -1),
       ('COMMENTS-feedback-score-consistently', 'COMMENTS-feedback-score-consistently',
        'COMMENTS-feedback-score-consistently', 2025, -1),
       ('COMMENTS-feedback-drove-fast', 'COMMENTS-feedback-drove-fast', 'COMMENTS-feedback-drove-fast', 2025, -1),
       ('COMMENTS-auto-rp', 'COMMENTS-auto-rp', 'COMMENTS-auto-rp', 2025, -1),
       ('COMMENTS-barge-rp', 'COMMENTS-barge-rp', 'COMMENTS-barge-rp', 2025, -1),
       ('COMMENTS-star-rating-5', 'COMMENTS-star-rating-5', 'COMMENTS-star-rating-5', 2025, -1),
       ('TELEOP-drop-coral', 'TELEOP-drop-coral', 'TELEOP-drop-coral', 2025, -1),
       ('TELEOP-score-reef-l3', 'TELEOP-score-reef-l3', 'TELEOP-score-reef-l3', 2025, -1),
       ('COMMENTS-star-rating-2', 'COMMENTS-star-rating-2', 'COMMENTS-star-rating-2', 2025, -1),
       ('COMMENTS-star-rating-4', 'COMMENTS-star-rating-4', 'COMMENTS-star-rating-4', 2025, -1),
       ('AUTO-score-reef-l1', 'AUTO-score-reef-l1', 'AUTO-score-reef-l1', 2025, -1),
       ('TELEOP-penalty-zone-violation', 'TELEOP-penalty-zone-violation', 'TELEOP-penalty-zone-violation', 2025, -1),
       ('COMMENTS-feedback-play-defence', 'COMMENTS-feedback-play-defence', 'COMMENTS-feedback-play-defence', 2025, -1),
       ('COMMENTS-star-rating-1', 'COMMENTS-star-rating-1', 'COMMENTS-star-rating-1', 2025, -1),
       ('ENDGAME-climb-none', 'ENDGAME-climb-none', 'ENDGAME-climb-none', 2025, -1),
       ('AUTO-pickup-coral-station-left', 'AUTO-pickup-coral-station-left', 'AUTO-pickup-coral-station-left', 2025, -1),
       ('TELEOP-score-reef-l1', 'TELEOP-score-reef-l1', 'TELEOP-score-reef-l1', 2025, -1),
       ('TELEOP-pickup-algae-reef', 'TELEOP-pickup-algae-reef', 'TELEOP-pickup-algae-reef', 2025, -1),
       ('ENDGAME-penalty-zone-violation', 'ENDGAME-penalty-zone-violation', 'ENDGAME-penalty-zone-violation', 2025, -1),
       ('ENDGAME-climb-shallow', 'ENDGAME-climb-shallow', 'ENDGAME-climb-shallow', 2025, -1),
       ('AUTO-drop-coral', 'AUTO-drop-coral', 'AUTO-drop-coral', 2025, -1),
       ('AUTO-set-phase-AUTO', 'AUTO-set-phase-AUTO', 'AUTO-set-phase-AUTO', 2025, -1),
       ('TELEOP-score-algae-net', 'TELEOP-score-algae-net', 'TELEOP-score-algae-net', 2025, -1),
       ('TELEOP-penalty-pin', 'TELEOP-penalty-pin', 'TELEOP-penalty-pin', 2025, -1),
       ('COMMENTS-feedback-effective-defence', 'COMMENTS-feedback-effective-defence',
        'COMMENTS-feedback-effective-defence', 2025, -1),
       ('AUTO-preloaded-coral', 'AUTO-preloaded-coral', 'AUTO-preloaded-coral', 2025, -1),
       ('AUTO-set-phase-TELEOP', 'AUTO-set-phase-TELEOP', 'AUTO-set-phase-TELEOP', 2025, -1),
       ('TELEOP-penalty-off-limit-contact', 'TELEOP-penalty-off-limit-contact', 'TELEOP-penalty-off-limit-contact',
        2025, -1),
       ('TELEOP-set-phase-ENDGAME', 'TELEOP-set-phase-ENDGAME', 'TELEOP-set-phase-ENDGAME', 2025, -1),
       ('COMMENTS-feedback-fell-over', 'COMMENTS-feedback-fell-over', 'COMMENTS-feedback-fell-over', 2025, -1),
       ('ENDGAME-penalty-pin', 'ENDGAME-penalty-pin', 'ENDGAME-penalty-pin', 2025, -1),
       ('COMMENTS-feedback-foul-often', 'COMMENTS-feedback-foul-often', 'COMMENTS-feedback-foul-often', 2025, -1),
       ('TELEOP-set-phase-AUTO', 'TELEOP-set-phase-AUTO', 'TELEOP-set-phase-AUTO', 2025, -1),
       ('AUTO-score-reef-l3', 'AUTO-score-reef-l3', 'AUTO-score-reef-l3', 2025, -1),
       ('AUTO-score-reef-l2', 'AUTO-score-reef-l2', 'AUTO-score-reef-l2', 2025, -1),
       ('AUTO-drop-algae', 'AUTO-drop-algae', 'AUTO-drop-algae', 2025, -1),
       ('AUTO-score-algae-net', 'AUTO-score-algae-net', 'AUTO-score-algae-net', 2025, -1),
       ('ENDGAME-set-phase-TELEOP', 'ENDGAME-set-phase-TELEOP', 'ENDGAME-set-phase-TELEOP', 2025, -1),
       ('COMMENTS-feedback-play-collector', 'COMMENTS-feedback-play-collector', 'COMMENTS-feedback-play-collector',
        2025, -1),
       ('AUTO-penalty-off-limit-contact', 'AUTO-penalty-off-limit-contact', 'AUTO-penalty-off-limit-contact', 2025, -1),
       ('PRE-MATCH-remove-algae', 'PRE-MATCH-remove-algae', 'PRE-MATCH-remove-algae', 2025, -1),
       ('PRE-MATCH-set-phase-TELEOP', 'PRE-MATCH-set-phase-TELEOP', 'PRE-MATCH-set-phase-TELEOP', 2025, -1),
       ('PRE-MATCH-pickup-algae-reef', 'PRE-MATCH-pickup-algae-reef', 'PRE-MATCH-pickup-algae-reef', 2025, -1),
       ('PRE-MATCH-score-algae-processor', 'PRE-MATCH-score-algae-processor', 'PRE-MATCH-score-algae-processor', 2025,
        -1),
       ('COMMENTS-feedback-beached', 'COMMENTS-feedback-beached', 'COMMENTS-feedback-beached', 2025, -1),
       ('AUTO-pickup-algae-auto-center', 'AUTO-pickup-algae-auto-center', 'AUTO-pickup-algae-auto-center', 2025, -1),
       ('COMMENTS-climb-park', 'COMMENTS-climb-park', 'COMMENTS-climb-park', 2025, -1),
       ('COMMENTS-set-phase-COMMENTS', 'COMMENTS-set-phase-COMMENTS', 'COMMENTS-set-phase-COMMENTS', 2025, -1),
       ('COMMENTS-set-phase-AUTO', 'COMMENTS-set-phase-AUTO', 'COMMENTS-set-phase-AUTO', 2025, -1),
       ('TELEOP-auto-start-left', 'TELEOP-auto-start-left', 'TELEOP-auto-start-left', 2025, -1),
       ('COMMENTS-penalty-field-damage', 'COMMENTS-penalty-field-damage', 'COMMENTS-penalty-field-damage', 2025, -1),
       ('COMMENTS-climb-none', 'COMMENTS-climb-none', 'COMMENTS-climb-none', 2025, -1),
       ('AUTO-pickup-algae-auto-right', 'AUTO-pickup-algae-auto-right', 'AUTO-pickup-algae-auto-right', 2025, -1),
       ('AUTO-penalty-opponent-contact', 'AUTO-penalty-opponent-contact', 'AUTO-penalty-opponent-contact', 2025, -1),
       ('AUTO-pickup-algae-auto-left', 'AUTO-pickup-algae-auto-left', 'AUTO-pickup-algae-auto-left', 2025, -1),
       ('COMMENTS-mistake', 'COMMENTS-mistake', 'COMMENTS-mistake', 2025, -1),
       ('ENDGAME-attempted-climb', 'ENDGAME-attempted-climb', 'ENDGAME-attempted-climb', 2025, -1),
       ('ENDGAME-score-reef-l3', 'ENDGAME-score-reef-l3', 'ENDGAME-score-reef-l3', 2025, -1),
       ('ENDGAME-score-reef-l4', 'ENDGAME-score-reef-l4', 'ENDGAME-score-reef-l4', 2025, -1),
       ('ENDGAME-pickup-algae-reef', 'ENDGAME-pickup-algae-reef', 'ENDGAME-pickup-algae-reef', 2025, -1),
       ('ENDGAME-remove-algae', 'ENDGAME-remove-algae', 'ENDGAME-remove-algae', 2025, -1),
       ('ENDGAME-score-algae-processor', 'ENDGAME-score-algae-processor', 'ENDGAME-score-algae-processor', 2025, -1),
       ('ENDGAME-pickup-coral-station-left', 'ENDGAME-pickup-coral-station-left', 'ENDGAME-pickup-coral-station-left',
        2025, -1),
       ('ENDGAME-pickup-coral-station-right', 'ENDGAME-pickup-coral-station-right',
        'ENDGAME-pickup-coral-station-right', 2025, -1),
       ('ENDGAME-score-reef-miss', 'ENDGAME-score-reef-miss', 'ENDGAME-score-reef-miss', 2025, -1),
       ('ENDGAME-score-reef-l2', 'ENDGAME-score-reef-l2', 'ENDGAME-score-reef-l2', 2025, -1),
       ('TELEOP-penalty-too-many-game-pieces', 'TELEOP-penalty-too-many-game-pieces',
        'TELEOP-penalty-too-many-game-pieces', 2025, -1),
       ('COMMENTS-attempted-climb', 'COMMENTS-attempted-climb', 'COMMENTS-attempted-climb', 2025, -1),
       ('AUTO-leave-starting-line', 'AUTO-leave-starting-line', 'AUTO-leave-starting-line', 2025, -1),
       ('AUTO-pickup-coral-auto-right', 'AUTO-pickup-coral-auto-right', 'AUTO-pickup-coral-auto-right', 2025, -1),
       ('COMMENTS-climb-deep', 'COMMENTS-climb-deep', 'COMMENTS-climb-deep', 2025, -1),
       ('AUTO-pickup-coral-auto-left', 'AUTO-pickup-coral-auto-left', 'AUTO-pickup-coral-auto-left', 2025, -1);


# Old v12
ALTER TABLE RB_EVENT
    ADD CONSTRAINT fk_event_eventtype
        FOREIGN KEY (eventtype) REFERENCES RB_EVENTTYPE (eventtype);


# Old v13
CREATE TABLE IF NOT EXISTS RB_SEQUENCETYPE
(
    name        VARCHAR(255) PRIMARY KEY,
    description VARCHAR(1024)
) CHARSET = utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS RB_SEQUENCEEVENT
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    sequencetype_name VARCHAR(255) NOT NULL,
    eventtype_id      VARCHAR(255) NOT NULL,
    startOfSequence   BOOLEAN      NOT NULL DEFAULT FALSE,
    endOfSequence     BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_sequencetype FOREIGN KEY (sequencetype_name) REFERENCES RB_SEQUENCETYPE (name) ON DELETE CASCADE,
    CONSTRAINT fk_eventtype FOREIGN KEY (eventtype_id) REFERENCES RB_EVENTTYPE (eventtype)
) CHARSET = utf8mb4
  COLLATE utf8mb4_unicode_ci;



# Old v14
-- Update RB_SEQUENCETYPE table to add 'id' as primary key
ALTER TABLE RB_SEQUENCEEVENT
    DROP FOREIGN KEY fk_sequencetype;

ALTER TABLE RB_SEQUENCETYPE
    DROP PRIMARY KEY;
ALTER TABLE RB_SEQUENCETYPE
    ADD id BIGINT AUTO_INCREMENT PRIMARY KEY FIRST;

-- Update RB_SEQUENCEEVENT to reference RB_SEQUENCETYPE by id instead of name
ALTER TABLE RB_SEQUENCEEVENT
    CHANGE sequencetype_name sequencetype_id BIGINT NOT NULL;

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

CREATE TABLE RB_SEQUENCETYPE
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(1024)
) CHARSET = utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE RB_SEQUENCEEVENT
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    sequencetype_id BIGINT       NOT NULL,
    eventtype_id    VARCHAR(255) NOT NULL,
    startOfSequence BOOLEAN      NOT NULL DEFAULT FALSE,
    endOfSequence   BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_sequencetype FOREIGN KEY (sequencetype_id) REFERENCES RB_SEQUENCETYPE (id) ON DELETE CASCADE,
    CONSTRAINT fk_eventtype FOREIGN KEY (eventtype_id) REFERENCES RB_EVENTTYPE (eventtype)
) CHARSET = utf8mb4
  COLLATE utf8mb4_unicode_ci;


# Old v15
ALTER TABLE RB_SEQUENCETYPE
    ADD COLUMN frcyear INT NOT NULL DEFAULT 2025;


# Old v16
ALTER TABLE RB_SEQUENCETYPE
    ALTER COLUMN frcyear DROP DEFAULT;


# Old v17
ALTER TABLE RB_SEQUENCETYPE
    ADD COLUMN disabled BOOLEAN NOT NULL DEFAULT FALSE;


# Old v18
DELETE
FROM RB_USER;
ALTER TABLE RB_EVENT
    ADD COLUMN userid bigint AFTER eventtimestamp;

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (1, "superuser", "SuperUser", "secret", "ROLE_SUPERUSER", 1, 0);

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (2, "Avocado", "Avocado", "secret", "ROLE_ADMIN", 0, 0);
UPDATE RB_EVENT
set userid = 2
where scoutname = 'Avocado';

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (3, "L", "L", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT
set userid = 3
where scoutname = "L";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (4, "O", "O", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT
set userid = 4
where scoutname = "O";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (5, "T", "T", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT
set userid = 5
where scoutname = "T";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (6, "U", "U", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT
set userid = 6
where scoutname = "U";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (7, "JohnDataScout", "John DataScout", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT
set userid = 7
where scoutname = "John DataScout";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (8, "Quentin", "Quentin", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT
set userid = 8
where scoutname = "Quentin";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (9, "Tony", "Tony", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT
set userid = 9
where scoutname = "Tony";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (10, "Y", "Y", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT
set userid = 10
where scoutname = "Y";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (11, "RachealEng", "Racheal Eng", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT
set userid = 11
where scoutname = "Racheal Eng";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (12, "Nick", "Nick", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT
set userid = 12
where scoutname = "Nick";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (13, "Leia", "Leia", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT
set userid = 13
where scoutname = "Leia";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (14, "Practice", "Practice", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT
set userid = 14
where scoutname = "Practice";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (15, "Maya", "Maya", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT
set userid = 15
where scoutname = "Maya";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (16, "Alan", "Alan", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT
set userid = 16
where scoutname = "Alan";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (17, "Jackson", "Jackson", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT
set userid = 17
where scoutname = "Jackson";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (18, "AaronLe", "Aaron Le", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT
set userid = 18
where scoutname = "Aaron Le";

insert into RB_USER (id, login, display_name, password_hash, roles, enabled, forgot_password)
VALUES (19, "Meghan", "Meghan", "secret", "ROLE_MEMBER", 0, 0);
UPDATE RB_EVENT
set userid = 19
where scoutname = "Meghan";

ALTER TABLE RB_EVENT
    drop constraint time_scout_type;
ALTER TABLE RB_EVENT
    drop column scoutname;

ALTER TABLE RB_EVENT
    MODIFY userid BIGINT NOT NULL;
ALTER TABLE RB_EVENT
    add constraint time_user_type unique (eventtimestamp, userid, eventtype);


