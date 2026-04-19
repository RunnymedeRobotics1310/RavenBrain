-- V34: Add updated_at columns to reference and cacheable-data tables so the HTTP-layer
-- conditional-GET story has a cheap, monotonic ETag version source per table.
--
-- Tables affected:
--   RB_TOURNAMENT    - backs /api/tournament and its derivatives
--   RB_SCHEDULE      - backs /api/schedule/*
--   RB_STRATEGYAREA  - backs /api/strategy-areas
--   RB_EVENTTYPE     - backs /api/event-types
--   RB_SEQUENCETYPE  - backs /api/sequence-types
--
-- Each column:
--   - DEFAULT CURRENT_TIMESTAMP(3)          -- backfills existing rows at migration time
--   - ON UPDATE CURRENT_TIMESTAMP(3)        -- auto-bumps on any row update (MySQL native)
--   - TIMESTAMP(3)                          -- millisecond precision; suitable as ETag source
--
-- Controllers build their weak ETag from MAX(updated_at) across the relevant rows, then run
-- the body query in the same @Transactional(REPEATABLE_READ) block so ETag and body are snapshot-
-- consistent.

ALTER TABLE RB_TOURNAMENT
    ADD COLUMN updated_at TIMESTAMP(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE RB_SCHEDULE
    ADD COLUMN updated_at TIMESTAMP(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE RB_STRATEGYAREA
    ADD COLUMN updated_at TIMESTAMP(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE RB_EVENTTYPE
    ADD COLUMN updated_at TIMESTAMP(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE RB_SEQUENCETYPE
    ADD COLUMN updated_at TIMESTAMP(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3);
