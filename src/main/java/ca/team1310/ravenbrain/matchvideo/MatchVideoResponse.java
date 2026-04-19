package ca.team1310.ravenbrain.matchvideo;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Wire-level shape of a match video. The admin-owned entity is {@link MatchVideoRecord}; this
 * response type enriches it with per-entry source attribution and staleness for the read path.
 *
 * <ul>
 *   <li>{@code id} — the {@code RB_MATCH_VIDEO.id} for admin rows; {@code null} for TBA-sourced
 *       synthetic rows (they have no admin-table identity).
 *   <li>{@code source} — {@code "manual"} for admin-owned rows, {@code "tba"} for TBA sync output.
 *   <li>{@code stale} — {@code true} when the TBA row backing this entry last failed its sync
 *       ({@code last_status != 200}). Admin rows are never stale.
 * </ul>
 */
@Serdeable
public record MatchVideoResponse(
    @Nullable Long id,
    String tournamentId,
    String matchLevel,
    int matchNumber,
    String label,
    String videoUrl,
    String source,
    boolean stale) {}
