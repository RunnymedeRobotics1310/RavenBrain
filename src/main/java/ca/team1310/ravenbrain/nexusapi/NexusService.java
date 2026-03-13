package ca.team1310.ravenbrain.nexusapi;

import ca.team1310.ravenbrain.nexusapi.fetch.NexusCachingClient;
import ca.team1310.ravenbrain.nexusapi.model.NexusEventResponse;
import ca.team1310.ravenbrain.nexusapi.model.NexusMatch;
import io.micronaut.context.annotation.Property;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class NexusService {
  private static final List<String> STATUS_PRIORITY =
      List.of("On field", "On deck", "Now queuing", "Queuing soon");

  private final NexusCachingClient cachingClient;
  private final ObjectMapper objectMapper;
  private final int teamNumber;

  NexusService(
      NexusCachingClient cachingClient,
      ObjectMapper objectMapper,
      @Property(name = "raven-eye.team") int teamNumber) {
    this.cachingClient = cachingClient;
    this.objectMapper = objectMapper;
    this.teamNumber = teamNumber;
  }

  public Optional<NexusQueueStatus> getQueueStatus(String tournamentId) {
    if (!cachingClient.isEnabled()) {
      return Optional.empty();
    }

    String eventKey = toNexusEventKey(tournamentId);
    Optional<String> json = cachingClient.fetch("event/" + eventKey);
    if (json.isEmpty()) {
      return Optional.empty();
    }

    try {
      NexusEventResponse event = objectMapper.readValue(json.get(), NexusEventResponse.class);
      return buildQueueStatus(event);
    } catch (IOException e) {
      log.warn("Failed to parse Nexus response for {}: {}", tournamentId, e.getMessage());
      return Optional.empty();
    }
  }

  private Optional<NexusQueueStatus> buildQueueStatus(NexusEventResponse event) {
    String teamStr = String.valueOf(teamNumber);
    NexusMatch bestMatch = null;
    String bestAlliance = null;
    int bestPriority = Integer.MAX_VALUE;

    for (NexusMatch match : event.matches()) {
      if (match.status() == null) continue;

      // Nexus keeps "On field" status forever after a match is played.
      // Allow "On field" to show while the match is recent (within 10 min of estimated start),
      // but skip it once the match is clearly over.
      if (match.times() != null && match.times().actualOnFieldTime() != null) {
        long estStart = match.times().estimatedStartTime() != null
            ? match.times().estimatedStartTime()
            : match.times().actualOnFieldTime();
        long tenMinutesAgo = Instant.now().toEpochMilli() - 600_000;
        if (estStart < tenMinutesAgo) continue;
      }

      int priority = STATUS_PRIORITY.indexOf(match.status());
      if (priority < 0) continue;

      String alliance = findTeamAlliance(match, teamStr);
      if (alliance == null) continue;

      if (priority < bestPriority) {
        bestPriority = priority;
        bestMatch = match;
        bestAlliance = alliance;
      }
    }

    String teamStatus = bestMatch != null ? bestMatch.status() : null;
    String teamMatchLabel = bestMatch != null ? bestMatch.label() : null;
    Long estimatedQueueTime =
        bestMatch != null && bestMatch.times() != null
            ? bestMatch.times().estimatedQueueTime()
            : null;
    Long estimatedStartTime =
        bestMatch != null && bestMatch.times() != null
            ? bestMatch.times().estimatedStartTime()
            : null;

    return Optional.of(
        new NexusQueueStatus(
            event.nowQueuing(),
            teamStatus,
            teamMatchLabel,
            bestAlliance,
            estimatedQueueTime,
            estimatedStartTime,
            event.announcements()));
  }

  private static String findTeamAlliance(NexusMatch match, String teamStr) {
    if (match.redTeams() != null && match.redTeams().contains(teamStr)) {
      return "red";
    }
    if (match.blueTeams() != null && match.blueTeams().contains(teamStr)) {
      return "blue";
    }
    return null;
  }

  static String toNexusEventKey(String tournamentId) {
    return tournamentId.toLowerCase();
  }
}
