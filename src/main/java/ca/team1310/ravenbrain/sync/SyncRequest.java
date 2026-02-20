package ca.team1310.ravenbrain.sync;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SyncRequest(String sourceUrl, String sourceUser, String sourcePassword) {}
