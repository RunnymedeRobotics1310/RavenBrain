package ca.team1310.ravenbrain.sync;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SyncResult(
    int strategyAreas,
    int eventTypes,
    int sequenceTypes,
    int sequenceEvents,
    int events,
    int comments,
    int alerts,
    int matchStrategyPlans,
    int matchStrategyDrawings,
    boolean tournamentsCleared,
    String message) {}
