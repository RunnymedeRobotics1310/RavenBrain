package ca.team1310.ravenbrain.sync;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SyncResult(
    int strategyAreas,
    int eventTypes,
    int sequenceTypes,
    int sequenceEvents,
    int tournaments,
    int schedules,
    String message) {}
