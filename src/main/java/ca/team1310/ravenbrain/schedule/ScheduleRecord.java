package ca.team1310.ravenbrain.schedule;

import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

/**
 * @author Tony Field
 * @since 2025-03-23 13:59
 */
@MappedEntity(value = "RB_SCHEDULE")
@Serdeable
public record ScheduleRecord(
    @Id @GeneratedValue long id,
    @MappedProperty("tournamentid") String tournamentId,
    TournamentLevel level,
    @MappedProperty("matchnum") int match,
    int red1,
    int red2,
    int red3,
    int blue1,
    int blue2,
    int blue3) {}
