ALTER TABLE RB_TELEMETRY_SESSION
    ADD COLUMN tournament_id   VARCHAR(127) NULL,
    ADD COLUMN match_label     VARCHAR(16)  NULL,
    ADD COLUMN match_level     VARCHAR(50)  NULL,
    ADD COLUMN match_number    INT          NULL,
    ADD COLUMN playoff_round   VARCHAR(8)   NULL,
    ADD COLUMN fms_event_name  VARCHAR(64)  NULL;

CREATE INDEX idx_telemetry_session_match
    ON RB_TELEMETRY_SESSION (tournament_id, match_level, match_number);
