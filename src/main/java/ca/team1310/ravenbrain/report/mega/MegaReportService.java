package ca.team1310.ravenbrain.report.mega;

import ca.team1310.ravenbrain.eventlog.EventLogRecord;
import ca.team1310.ravenbrain.eventlog.EventLogRepository;
import ca.team1310.ravenbrain.eventtype.EventType;
import ca.team1310.ravenbrain.eventtype.EventTypeRepository;
import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import jakarta.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class MegaReportService {

  private final EventLogRepository eventLogRepository;
  private final EventTypeRepository eventTypeRepository;

  public MegaReportService(
      EventLogRepository eventLogRepository, EventTypeRepository eventTypeRepository) {
    this.eventLogRepository = eventLogRepository;
    this.eventTypeRepository = eventTypeRepository;
  }

  public List<String> listTournamentsWithData() {
    return eventLogRepository.findDistinctNonDrillTournamentIds();
  }

  public List<Integer> listTeamsForTournament(String tournamentId) {
    return eventLogRepository.findDistinctTeamNumbersByTournamentId(tournamentId);
  }

  public MegaReport generateReport(int team, String tournamentId, int year) {
    var records =
        eventLogRepository
            .findAllByTeamNumberAndTournamentIdOrderByTimestampAsc(team, tournamentId)
            .stream()
            .filter(
                e ->
                    e.level() == TournamentLevel.Qualification
                        || e.level() == TournamentLevel.Playoff)
            .toList();

    // Build event type lookup for the year
    Map<String, EventType> eventTypeMap =
        eventTypeRepository.findByFrcyear(year).stream()
            .collect(Collectors.toMap(EventType::eventtype, e -> e));

    // Determine which event types actually appear in the data
    Set<String> usedEventTypes = new LinkedHashSet<>();
    for (var record : records) {
      usedEventTypes.add(record.eventType());
    }

    // Build columns in a stable order
    List<MegaReportColumn> columns =
        usedEventTypes.stream()
            .map(
                et -> {
                  var type = eventTypeMap.get(et);
                  return new MegaReportColumn(
                      et,
                      type != null ? type.name() : et,
                      type != null && type.showQuantity());
                })
            .toList();

    // Group records by (level, matchId)
    record MatchKey(TournamentLevel level, int matchId) {}

    Map<MatchKey, List<EventLogRecord>> grouped = new LinkedHashMap<>();
    for (var record : records) {
      grouped
          .computeIfAbsent(new MatchKey(record.level(), record.matchId()), k -> new ArrayList<>())
          .add(record);
    }

    // Build rows
    List<MegaReportRow> rows = new ArrayList<>();
    for (var entry : grouped.entrySet()) {
      var key = entry.getKey();
      var matchRecords = entry.getValue();

      Map<String, Double> values = new LinkedHashMap<>();
      for (var col : columns) {
        double value;
        if (col.isQuantity()) {
          // Sum the amounts for quantity-based event types
          value =
              matchRecords.stream()
                  .filter(r -> r.eventType().equals(col.eventtype()))
                  .mapToDouble(EventLogRecord::amount)
                  .sum();
        } else {
          // Count occurrences for non-quantity event types
          value =
              matchRecords.stream().filter(r -> r.eventType().equals(col.eventtype())).count();
        }
        values.put(col.eventtype(), value);
      }

      rows.add(new MegaReportRow(key.matchId(), key.level(), values));
    }

    return new MegaReport(columns, rows);
  }
}
