package ca.team1310.ravenbrain.nexusapi;

import ca.team1310.ravenbrain.nexusapi.model.NexusAnnouncement;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record NexusQueueStatus(
    String nowQueuing,
    String teamStatus,
    String teamMatchLabel,
    String teamAlliance,
    Long estimatedQueueTime,
    Long estimatedStartTime,
    List<NexusAnnouncement> announcements) {}
