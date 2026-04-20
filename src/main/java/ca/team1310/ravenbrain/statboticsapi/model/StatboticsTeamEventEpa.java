package ca.team1310.ravenbrain.statboticsapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * The {@code epa} sub-object from a Statbotics team-event record. Carries the top-level
 * normalized / unitless scores plus the nested {@link StatboticsTeamEventBreakdown} that holds the
 * per-phase point totals.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} so fields Statbotics adds later (conf
 * intervals, mean / peak / trend, etc.) never break parsing.
 */
@Serdeable
@JsonIgnoreProperties(ignoreUnknown = true)
public record StatboticsTeamEventEpa(
    @Nullable Double unitless,
    @Nullable Double norm,
    @Nullable StatboticsTeamEventBreakdown breakdown) {}
