package ca.team1310.ravenbrain.matchvideo;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

@MappedEntity(value = "RB_MATCH_VIDEO")
@Serdeable
public record MatchVideoRecord(
    @Id @GeneratedValue Long id,
    @MappedProperty("tournament_id") String tournamentId,
    @MappedProperty("match_level") String matchLevel,
    @MappedProperty("match_number") int matchNumber,
    @MappedProperty("label") String label,
    @MappedProperty("video_url") String videoUrl) {}
