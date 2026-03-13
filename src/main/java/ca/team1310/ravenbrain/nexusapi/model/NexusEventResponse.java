package ca.team1310.ravenbrain.nexusapi.model;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record NexusEventResponse(
    String nowQueuing,
    List<NexusMatch> matches,
    List<NexusAnnouncement> announcements,
    Long dataAsOfTime) {}
