package ca.team1310.ravenbrain.statboticsapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * The stable per-phase-points subset of the Statbotics {@code epa.breakdown} sub-object. Field
 * names are the cross-season-stable point totals we read directly into flat columns on {@code
 * RB_STATBOTICS_TEAM_EVENT}. Any additional season-specific fields Statbotics returns (coral
 * counts, algae scores, cage climbs, etc.) are preserved verbatim in the
 * {@code breakdown_json} TEXT column and are not modelled here.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} so unknown / new / polymorphic fields
 * (e.g. 2026's {@code traversal_rp} which varies between scalar and array) never break parsing.
 */
@Serdeable
@JsonIgnoreProperties(ignoreUnknown = true)
public record StatboticsTeamEventBreakdown(
    @Nullable @JsonProperty("total_points") Double totalPoints,
    @Nullable @JsonProperty("auto_points") Double autoPoints,
    @Nullable @JsonProperty("teleop_points") Double teleopPoints,
    @Nullable @JsonProperty("endgame_points") Double endgamePoints) {}
