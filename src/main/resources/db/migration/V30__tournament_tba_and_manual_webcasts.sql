-- Rename the existing webcasts column to manual_webcasts to make admin ownership unambiguous.
-- Prior to this migration, EventSyncService.saveEvents() was silently overwriting this column
-- with the FRC API response on every weekly cron, making it neither reliably admin-owned nor
-- reliably auto-populated. From V30 onward: manual_webcasts is admin-only (via PUT/DELETE
-- /api/tournament/{id}/webcast), and TBA-sourced webcasts live in RB_TBA_EVENT (V32).
ALTER TABLE RB_TOURNAMENT
    CHANGE COLUMN webcasts manual_webcasts TEXT NULL;

-- Add the nullable TBA event key used to look up TBA data for this tournament.
-- Auto-populated by EventSyncService.saveEvents() on tournament creation using the convention
-- year + event.code().toLowerCase() (e.g., 2026 + "ONTO" -> "2026onto"). Admins can override
-- via PUT /api/tournament/{id}/tba-event-key when the auto-derived value is wrong (common for
-- district championships with divisional keys).
ALTER TABLE RB_TOURNAMENT
    ADD COLUMN tba_event_key VARCHAR(31) NULL;
