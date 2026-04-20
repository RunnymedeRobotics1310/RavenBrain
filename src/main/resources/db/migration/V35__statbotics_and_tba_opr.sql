-- Three related tables for the Team Capability Rankings P1 feature:
--   1. RB_STATBOTICS_RESPONSES — HTTP response cache for the Statbotics API. Mirrors
--      RB_TBA_RESPONSES (V31) exactly. The etag / lastmodified columns are structurally preserved
--      for operational parity but are always NULL / current-instant because Statbotics emits
--      neither ETag nor Last-Modified headers. NOT NULL on primitive-backed columns keeps record
--      construction safe against NULL reads.
--   2. RB_STATBOTICS_TEAM_EVENT — per-team-per-event EPA + breakdown snapshot from Statbotics'
--      /v3/team_events batch endpoint. Keyed by (tba_event_key, team_number). Breakdown is stored
--      as JSON TEXT for season-specific drill-down; flat EPA columns are cross-season stable.
--   3. RB_TBA_EVENT_OPRS — per-team-per-event OPR / DPR / CCWM from TBA's /event/{key}/oprs.
--      Keyed by (tba_event_key, team_number). Populated by Unit 3 (TBA OPR sync).
--
-- MySQL 8.4 auto-commits each DDL statement individually, so all three use CREATE TABLE IF NOT
-- EXISTS (matching V32 / V33 style) to make the migration re-runnable if a mid-migration failure
-- leaves partial state.

CREATE TABLE IF NOT EXISTS RB_STATBOTICS_RESPONSES
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    lastcheck    TIMESTAMP(3) NOT NULL,
    lastmodified TIMESTAMP(3) NOT NULL,
    etag         VARCHAR(127) NULL,
    processed    BIT          NOT NULL,
    statuscode   INT          NOT NULL,
    url          VARCHAR(255) NOT NULL,
    body         LONGTEXT     NULL
);

CREATE TABLE IF NOT EXISTS RB_STATBOTICS_TEAM_EVENT
(
    tba_event_key   VARCHAR(31) NOT NULL,
    team_number     INT         NOT NULL,
    tournament_id   VARCHAR(127) NULL,
    epa_total       DOUBLE      NULL,
    epa_auto        DOUBLE      NULL,
    epa_teleop      DOUBLE      NULL,
    epa_endgame     DOUBLE      NULL,
    epa_unitless    DOUBLE      NULL,
    epa_norm        DOUBLE      NULL,
    breakdown_json  TEXT        NULL,
    last_sync       TIMESTAMP(3) NULL,
    last_status     INT         NULL,
    PRIMARY KEY (tba_event_key, team_number)
);

CREATE INDEX IDX_RB_STATBOTICS_TEAM_EVENT_KEY
    ON RB_STATBOTICS_TEAM_EVENT (tba_event_key);

CREATE TABLE IF NOT EXISTS RB_TBA_EVENT_OPRS
(
    tba_event_key VARCHAR(31) NOT NULL,
    team_number   INT         NOT NULL,
    opr           DOUBLE      NULL,
    dpr           DOUBLE      NULL,
    ccwm          DOUBLE      NULL,
    last_sync     TIMESTAMP(3) NULL,
    last_status   INT         NULL,
    PRIMARY KEY (tba_event_key, team_number)
);

CREATE INDEX IDX_RB_TBA_EVENT_OPRS_KEY
    ON RB_TBA_EVENT_OPRS (tba_event_key);
