-- One-time purge: telemetry entries with ts < 2026-04-15 13:00:00 UTC
-- (epoch 1776430800) were corrupt. Data before this cutoff will never be
-- re-uploaded, so this is a destructive terminal cleanup.
DELETE FROM RB_TELEMETRY_ENTRY WHERE ts < FROM_UNIXTIME(1776430800);
