package ca.team1310.ravenbrain.nexusapi.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record NexusAnnouncement(String content) {}
