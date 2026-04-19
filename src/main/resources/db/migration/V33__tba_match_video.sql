-- TBA match-video cache. Keyed by the TBA match key (e.g. "2026onto_qm14") so we can upsert by PK
-- without losing identity when tba_event_key on a tournament is remapped or cleared. Read-time
-- joins use (tba_event_key, red_teams, blue_teams) — the alliance tuple is drift-proof across
-- qualification rescheduling and covers all playoff bracket formats without decoding comp_level.
--
-- tba_event_key : TBA event identifier (matches RB_TOURNAMENT.tba_event_key).
-- comp_level    : TBA competition level ("qm", "qf", "sf", "f", "ef"). Stored for debugging —
--                 the read-time join does not filter on it.
-- red_teams     : canonical sorted comma-separated team numbers (e.g. "1310,2056,4917").
-- blue_teams    : canonical sorted comma-separated team numbers.
-- videos_json   : canonicalized, deduplicated JSON array of URL strings (see
--                 WebcastUrlReconstructor.reconstructAndDedupMatchVideos).
-- last_sync     : timestamp of the most recent successful (status=200) sync.
-- last_status   : HTTP status of the most recent sync attempt. 200 means videos_json is fresh;
--                 anything else means the list represents the last successful data (read layer
--                 surfaces staleness via the stale flag).
CREATE TABLE IF NOT EXISTS RB_TBA_MATCH_VIDEO
(
    tba_match_key    VARCHAR(63) PRIMARY KEY,
    tba_event_key    VARCHAR(31) NOT NULL,
    comp_level       VARCHAR(4)  NOT NULL,
    tba_match_number INT         NOT NULL,
    red_teams        VARCHAR(63) NOT NULL,
    blue_teams       VARCHAR(63) NOT NULL,
    videos_json      TEXT         NULL,
    last_sync        TIMESTAMP(3) NULL,
    last_status      INT          NULL
);

CREATE INDEX IDX_RB_TBA_MATCH_VIDEO_ALLIANCE
    ON RB_TBA_MATCH_VIDEO (tba_event_key, red_teams, blue_teams);
