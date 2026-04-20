package ca.team1310.ravenbrain.teamcapability;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Wire-level shape of one team's capability row. Combines TBA OPR, Statbotics EPA, and RavenBrain
 * scouting aggregates into a single structure with server-computed staleness flags, coverage
 * classification, and withdrawn detection.
 *
 * <p>See {@link TeamCapabilityEnricher} for the merge semantics.
 *
 * <ul>
 *   <li>{@code oprStale} — {@code true} when the backing {@code RB_TBA_EVENT_OPRS} row is missing
 *       or its {@code last_status != 200}.
 *   <li>{@code epaStale} — {@code true} when the backing {@code RB_STATBOTICS_TEAM_EVENT} row is
 *       missing or its {@code last_status != 200}.
 *   <li>{@code scoutingCoverage} — event-log-only classifier: {@code "full"} when all three scoring
 *       averages are non-null; {@code "none"} when all three are null AND quickCommentCount = 0
 *       AND robotAlertCount = 0; {@code "thin"} otherwise.
 *   <li>{@code withdrawn} — {@code true} when the team appears in the stored roster but is absent
 *       from the latest Statbotics team-event sync (only flagged when Statbotics returned at least
 *       one row for the event, to avoid false positives pre-first-sync).
 * </ul>
 */
@Serdeable
public record TeamCapabilityResponse(
    int teamNumber,
    @Nullable String teamName,
    @Nullable Double opr,
    boolean oprStale,
    @Nullable Double epaTotal,
    @Nullable Double epaAuto,
    @Nullable Double epaTeleop,
    @Nullable Double epaEndgame,
    @Nullable Double epaUnitless,
    @Nullable Double epaNorm,
    boolean epaStale,
    @Nullable Double autoAccuracy,
    @Nullable Double teleopSuccessRate,
    @Nullable Double pickupAverage,
    int quickCommentCount,
    int robotAlertCount,
    @Nullable String robotAlertMaxSeverity,
    String scoutingCoverage,
    boolean withdrawn) {}
