package ca.team1310.ravenbrain.robotalert;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

@MappedEntity(value = "RB_ROBOT_ALERT")
@Serdeable
public record RobotAlert(
    @Id @GeneratedValue Long id,
    @MappedProperty("tournament_id") String tournamentId,
    @MappedProperty("team_number") int teamNumber,
    @MappedProperty("user_id") long userId,
    @MappedProperty("created_at") Instant createdAt,
    @MappedProperty("alert") String alert) {}
