package ca.team1310.ravenbrain.report;

import ca.team1310.ravenbrain.eventlog.EventLogRepository;
import ca.team1310.ravenbrain.eventtype.EventType;
import ca.team1310.ravenbrain.eventtype.EventTypeRepository;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class CustomTournamentStatsService {

  private static final Set<String> TARGET_EVENT_TYPES =
      Set.of(
          "auto-number-shot",
          "auto-number-missed",
          "scoring-number-success",
          "scoring-number-miss",
          "pickup-number");

  private final EventLogRepository eventLogRepository;
  private final EventTypeRepository eventTypeRepository;

  private final ConcurrentHashMap<String, CustomTournamentStats> cache = new ConcurrentHashMap<>();

  public CustomTournamentStatsService(
      EventLogRepository eventLogRepository, EventTypeRepository eventTypeRepository) {
    this.eventLogRepository = eventLogRepository;
    this.eventTypeRepository = eventTypeRepository;
  }

  @Serdeable
  public record EventTypeStat(String eventType, String eventTypeName, double averageAmount) {}

  @Serdeable
  public record CustomTournamentStats(String tournamentId, List<EventTypeStat> stats) {}

  public List<CustomTournamentStats> getStatsForTeam(int team) {
    var tournamentIds = eventLogRepository.findDistinctTournamentIdsByTeamNumber(team);

    Map<String, String> eventTypeNames = null; // lazy-loaded

    var result = new ArrayList<CustomTournamentStats>();
    for (var tournamentId : tournamentIds) {
      var cacheKey = team + ":" + tournamentId;
      var cached = cache.get(cacheKey);
      if (cached != null) {
        result.add(cached);
        continue;
      }

      if (eventTypeNames == null) {
        eventTypeNames =
            eventTypeRepository.findAll().stream()
                .collect(Collectors.toMap(EventType::eventtype, EventType::name));
      }

      var events =
          eventLogRepository.findAllByTeamNumberAndTournamentIdOrderByTimestampAsc(
              team, tournamentId);

      // Group by eventType, only for target event types
      var grouped = new LinkedHashMap<String, List<Double>>();
      for (var event : events) {
        if (TARGET_EVENT_TYPES.contains(event.eventType())) {
          grouped.computeIfAbsent(event.eventType(), k -> new ArrayList<>()).add(event.amount());
        }
      }

      var stats = new ArrayList<EventTypeStat>();
      for (var entry : grouped.entrySet()) {
        var amounts = entry.getValue();
        double sum = amounts.stream().mapToDouble(Double::doubleValue).sum();
        double avg =
            BigDecimal.valueOf(sum / amounts.size())
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        var displayName = eventTypeNames.getOrDefault(entry.getKey(), entry.getKey());
        stats.add(new EventTypeStat(entry.getKey(), displayName, avg));
      }

      var tournamentStats = new CustomTournamentStats(tournamentId, stats);
      cache.put(cacheKey, tournamentStats);
      result.add(tournamentStats);
    }

    return result;
  }

  public void invalidate(String tournamentId) {
    var suffix = ":" + tournamentId;
    cache.keySet().removeIf(key -> key.endsWith(suffix));
  }
}
