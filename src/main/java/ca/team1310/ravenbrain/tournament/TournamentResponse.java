package ca.team1310.ravenbrain.tournament;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.util.List;

/**
 * Wire-level shape of a tournament. The entity is {@link TournamentRecord}; the wire representation
 * enriches it with fields computed at read time:
 *
 * <ul>
 *   <li>{@code webcasts} — the canonicalized union of admin-owned manual webcasts and TBA-sourced
 *       webcasts, with exact duplicates collapsed. This is what RavenEye renders.
 *   <li>{@code webcastsFromTba} — the subset of {@code webcasts} that came from TBA. The public
 *       invariant is: for any {@code u ∈ webcasts}, {@code u ∈ webcastsFromTba} means the source
 *       is TBA; otherwise the source is Manual override. The admin UI derives the "From TBA" vs
 *       "Manual override" badge from this membership.
 *   <li>{@code webcastsLastSync} — when the underlying {@code RB_TBA_EVENT} row last recorded a
 *       successful ({@code last_status == 200}) sync. Null when {@code tba_event_key} is unset or
 *       no sync has ever succeeded.
 *   <li>{@code webcastsStale} — true when any of: last_sync is older than the stale threshold, the
 *       last sync attempt did not return 200, the {@code tba_event_key} is set but no {@code
 *       RB_TBA_EVENT} row exists yet.
 * </ul>
 */
@Serdeable
public record TournamentResponse(
    String id,
    String code,
    int season,
    String name,
    Instant startTime,
    Instant endTime,
    int weekNumber,
    @Nullable String tbaEventKey,
    List<String> webcasts,
    List<String> webcastsFromTba,
    @Nullable Instant webcastsLastSync,
    boolean webcastsStale,
    /**
     * Server-computed tournament-window bounds: {@code startTime − leadHours} from
     * {@code raven-eye.sync.tournament-window-lead-hours}.
     */
    Instant activeFrom,
    /**
     * Server-computed tournament-window bounds: {@code endTime + tailHours} from
     * {@code raven-eye.sync.tournament-window-tail-hours}. The client computes its own
     * {@code active} boolean by comparing these timestamps against {@code serverNow()}, which
     * stays correct on devices with skewed clocks. Server intentionally does not emit a derived
     * {@code active} field because its truth changes continuously with {@code now} — emitting it
     * would mismatch the ETag, which is data-driven.
     */
    Instant activeUntil) {}
