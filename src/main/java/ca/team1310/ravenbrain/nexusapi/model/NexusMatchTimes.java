package ca.team1310.ravenbrain.nexusapi.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record NexusMatchTimes(
    Long estimatedQueueTime,
    Long estimatedOnDeckTime,
    Long estimatedOnFieldTime,
    Long estimatedStartTime,
    Long actualOnFieldTime) {}
