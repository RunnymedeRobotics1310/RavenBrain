package ca.team1310.ravenbrain.schedule;

import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.core.annotation.Nullable;
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
    @Nullable @MappedProperty("starttime") String startTime,
    int red1,
    int red2,
    int red3,
    int red4,
    int blue1,
    int blue2,
    int blue3,
    int blue4,
    @Nullable @MappedProperty("redscore") Integer redScore,
    @Nullable @MappedProperty("bluescore") Integer blueScore,
    @Nullable @MappedProperty("redrp") Integer redRp,
    @Nullable @MappedProperty("bluerp") Integer blueRp,
    @MappedProperty("winningalliance") int winningAlliance) {}
