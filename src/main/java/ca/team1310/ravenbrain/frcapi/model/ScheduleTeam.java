package ca.team1310.ravenbrain.frcapi.model;

import io.micronaut.serde.annotation.Serdeable;

/**
 * @author Tony Field
 * @since 2025-09-22 00:44
 */
@Serdeable
public record ScheduleTeam(int teamNumber, String station, boolean surrogate) {}
