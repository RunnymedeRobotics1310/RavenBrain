package ca.team1310.ravenbrain.nexusapi.model;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record NexusMatch(
    String label,
    String status,
    List<String> redTeams,
    List<String> blueTeams,
    NexusMatchTimes times) {}
