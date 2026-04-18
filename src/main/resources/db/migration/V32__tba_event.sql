-- TBA event-level cache. Keyed by TBA event key (e.g., "2026onto") rather than RB_TOURNAMENT.id,
-- so that clearing or re-mapping tba_event_key on a tournament does not invalidate cached TBA
-- data. The read path joins RB_TOURNAMENT.tba_event_key = RB_TBA_EVENT.event_key at query time.
--
-- webcasts_json     : canonicalized, deduplicated JSON array of URL strings reconstructed from
--                     the TBA Event.webcasts list (see WebcastUrlReconstructor).
-- raw_event_json    : full TBA event response body, retained for tournament-day debugging.
-- last_sync         : timestamp of the most recent successful (status=200) sync.
-- last_status       : HTTP status of the most recent sync attempt. 200 means webcasts_json is
--                     fresh; anything else means webcasts_json may be stale but represents the
--                     last successful data (per R5a: serve last-known-good on failure).
CREATE TABLE IF NOT EXISTS RB_TBA_EVENT
(
    event_key       VARCHAR(31) PRIMARY KEY,
    webcasts_json   TEXT         NULL,
    raw_event_json  LONGTEXT     NULL,
    last_sync       TIMESTAMP(3) NULL,
    last_status     INT          NULL
);
