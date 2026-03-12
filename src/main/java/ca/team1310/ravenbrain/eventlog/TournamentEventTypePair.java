package ca.team1310.ravenbrain.eventlog;

import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record TournamentEventTypePair(
    @MappedProperty("tournamentid") String tournamentId,
    @MappedProperty("eventtype") String eventType) {}
