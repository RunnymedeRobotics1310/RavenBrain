package ca.team1310.ravenbrain.tournament;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

/**
 * @author Tony Field
 * @since 2025-03-23 13:58
 */
@MappedEntity(value = "RB_TOURNAMENT")
@Serdeable
public record TournamentRecord(
    @Id String id,
    @MappedProperty("code") String code,
    @MappedProperty("season") int season,
    @MappedProperty("tournamentname") String name,
    @MappedProperty("starttime") Instant startTime,
    @MappedProperty("endtime") Instant endTime) {}
