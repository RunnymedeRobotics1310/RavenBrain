package ca.team1310.ravenbrain.telemetry;

import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

@MappedEntity(value = "RB_TELEMETRY_SESSION")
@Serdeable
public record TelemetrySession(
    @Id @GeneratedValue Long id,
    @MappedProperty("session_id") String sessionId,
    @MappedProperty("team_number") int teamNumber,
    @MappedProperty("robot_ip") String robotIp,
    @MappedProperty("started_at") Instant startedAt,
    @Nullable @MappedProperty("ended_at") Instant endedAt,
    @MappedProperty("entry_count") int entryCount,
    @MappedProperty("uploaded_count") int uploadedCount,
    @MappedProperty("created_at") Instant createdAt,
    @Nullable @MappedProperty("tournament_id") String tournamentId,
    @Nullable @MappedProperty("match_label") String matchLabel,
    @Nullable @MappedProperty("match_level") TournamentLevel matchLevel,
    @Nullable @MappedProperty("match_number") Integer matchNumber,
    @Nullable @MappedProperty("playoff_round") String playoffRound,
    @Nullable @MappedProperty("fms_event_name") String fmsEventName) {}
