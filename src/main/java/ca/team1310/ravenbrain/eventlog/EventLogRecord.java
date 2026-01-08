package ca.team1310.ravenbrain.eventlog;

import ca.team1310.ravenbrain.frcapi.model.Alliance;
import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

/**
 * @author Tony Field
 * @since 2025-03-23 13:58
 */
@MappedEntity(value = "RB_EVENT")
@Serdeable
public record EventLogRecord(
    @Id long id,
    @MappedProperty("eventtimestamp") Instant timestamp,
    @MappedProperty("scoutname") String scoutName,
    @MappedProperty("tournamentid") String tournamentId,
    TournamentLevel level,
    @MappedProperty("matchid") int matchId,
    Alliance alliance,
    @MappedProperty("teamnumber") int teamNumber,
    @MappedProperty("eventtype") String eventType,
    double amount,
    String note) {}
