package ca.team1310.ravenbrain.report;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Per-team scouting aggregates for a single tournament.
 *
 * <p>Consumed by {@code TeamCapabilityEnricher} (Unit 5) to compose the public capability response.
 * Nullable averages signal "no data"; the enricher interprets those nulls into the {@code
 * scoutingCoverage} classification ({@code "full" | "thin" | "none"}).
 *
 * <p>Averages come from {@code RB_EVENT} rows filtered to a fixed whitelist of event types (see
 * {@link TournamentAggregatesService#TARGET_EVENT_TYPES}). Quick-comment count is per-team
 * all-time (the {@code RB_COMMENT} table is not tournament-scoped). Robot-alert count and max
 * severity are per-team within this tournament only.
 *
 * @param teamNumber the team these aggregates describe
 * @param autoAccuracy auto-number-shot / (auto-number-shot + auto-number-missed), or null when the
 *     team has no auto attempts
 * @param teleopSuccessRate scoring-number-success / (success + miss), or null when the team has no
 *     teleop scoring attempts
 * @param pickupAverage mean of pickup-number amounts, or null when the team has no pickups
 * @param quickCommentCount per-team count of rows in {@code RB_COMMENT}; always {@code >= 0}
 * @param robotAlertCount per-team count of rows in {@code RB_ROBOT_ALERT} for this tournament;
 *     always {@code >= 0}
 * @param robotAlertMaxSeverity highest severity observed among this team's alerts for this
 *     tournament (one of {@code "info"}, {@code "warning"}, {@code "critical"}), or null when the
 *     team has no alerts. Severity is parsed from a leading bracketed marker in the alert text
 *     ({@code "[critical] ..."}) — untagged alerts default to {@code "info"}. The column is a
 *     forward-compatible surface for a future severity field on {@code RB_ROBOT_ALERT}.
 */
@Serdeable
public record TournamentAggregates(
    int teamNumber,
    Double autoAccuracy,
    Double teleopSuccessRate,
    Double pickupAverage,
    int quickCommentCount,
    int robotAlertCount,
    String robotAlertMaxSeverity) {}
