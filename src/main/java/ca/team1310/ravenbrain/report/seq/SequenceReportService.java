package ca.team1310.ravenbrain.report.seq;

import ca.team1310.ravenbrain.eventlog.EventLogRecord;
import ca.team1310.ravenbrain.eventlog.EventLogService;
import ca.team1310.ravenbrain.eventtype.EventType;
import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import ca.team1310.ravenbrain.sequencetype.SequenceEvent;
import ca.team1310.ravenbrain.sequencetype.SequenceType;
import ca.team1310.ravenbrain.sequencetype.SequenceTypeService;
import ca.team1310.ravenbrain.tournament.TournamentService;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Tony Field
 * @since 2026-01-08 11:21
 */
@Singleton
public class SequenceReportService {
  private final EventLogService eventLogService;
  private final SequenceTypeService sequenceTypeService;
  private final TournamentService tournamentService;

  public SequenceReportService(
      EventLogService eventLogService,
      SequenceTypeService sequenceTypeService,
      TournamentService tournamentService) {
    this.eventLogService = eventLogService;
    this.sequenceTypeService = sequenceTypeService;
    this.tournamentService = tournamentService;
  }

  public SequenceReport getMatchReportForTeam(
      int teamId, String tournamentId, int matchId, List<TournamentLevel> levels) {
    var records = eventLogService.listEventsForTournament(teamId, tournamentId, levels);
    if (records.isEmpty()) {
      return emptyReport();
    }
    int year = tournamentService.findYearForTournament(records.get(0).tournamentId());
    return generateReport(year, records);
  }

  public SequenceReport getSeasonReportForTeam(
      int teamId, int frcyear, List<TournamentLevel> levels) {
    var records = eventLogService.listEventsForSeason(teamId, frcyear, levels);
    return generateReport(frcyear, records);
  }

  public SequenceReport generateReport(int year, List<EventLogRecord> records) {
    if (records.isEmpty()) {
      return emptyReport();
    }

    List<SequenceType> types =
        sequenceTypeService.findByFrcyear(year).stream().filter(s -> !s.disabled()).toList();

    if (types.isEmpty()) {
      return emptyReport();
    }

    // Build lookup maps: for each SequenceType, which event type codes are start vs end
    // Also build a map from event type code to EventType entity
    Map<String, EventType> eventTypeMap = new HashMap<>();
    Map<Long, Set<String>> startCodes = new HashMap<>();
    Map<Long, Set<String>> endCodes = new HashMap<>();

    for (SequenceType st : types) {
      if (st.events() == null) continue;
      Set<String> starts =
          st.events().stream()
              .filter(SequenceEvent::startOfSequence)
              .map(se -> se.eventtype().eventtype())
              .collect(Collectors.toSet());
      Set<String> ends =
          st.events().stream()
              .filter(SequenceEvent::endOfSequence)
              .map(se -> se.eventtype().eventtype())
              .collect(Collectors.toSet());
      startCodes.put(st.id(), starts);
      endCodes.put(st.id(), ends);

      for (SequenceEvent se : st.events()) {
        eventTypeMap.put(se.eventtype().eventtype(), se.eventtype());
      }
    }

    // Track pending starts per sequence type
    // pendingStart maps sequenceTypeId -> the record that started a sequence
    Map<Long, EventLogRecord> pendingStart = new HashMap<>();

    // Completed sequences
    List<SequenceInfo> sequences = new ArrayList<>();

    for (EventLogRecord record : records) {
      String code = record.eventType();

      for (SequenceType st : types) {
        Long stId = st.id();
        boolean isStart = startCodes.getOrDefault(stId, Set.of()).contains(code);
        boolean isEnd = endCodes.getOrDefault(stId, Set.of()).contains(code);

        if (isStart) {
          // Start (or replace pending start) for this sequence type
          pendingStart.put(stId, record);
        }

        if (isEnd && pendingStart.containsKey(stId)) {
          EventLogRecord start = pendingStart.remove(stId);
          // Don't create a sequence if start and end are the same record
          if (start != record) {
            sequences.add(buildSequenceInfo(start, record, eventTypeMap));
          }
        }
      }
    }

    if (sequences.isEmpty()) {
      return emptyReport();
    }

    // Compute aggregates
    List<Long> durations = sequences.stream().map(SequenceInfo::duration).toList();
    long avg = (long) durations.stream().mapToLong(Long::longValue).average().orElse(0);
    long fastest = durations.stream().mapToLong(Long::longValue).min().orElse(0);
    long slowest = durations.stream().mapToLong(Long::longValue).max().orElse(0);
    long stddev = computeStdDev(durations, avg);

    // Compute interval stats across all sequences
    List<IntervalStats> intervalStats = computeIntervalStats(sequences);

    return new SequenceReport(sequences, avg, fastest, slowest, stddev, intervalStats);
  }

  private SequenceInfo buildSequenceInfo(
      EventLogRecord start, EventLogRecord end, Map<String, EventType> eventTypeMap) {
    Instant startTime = start.timestamp();
    Instant endTime = end.timestamp();
    long duration = Duration.between(startTime, endTime).toMillis();

    EventType startType = eventTypeMap.get(start.eventType());
    EventType endType = eventTypeMap.get(end.eventType());

    TimedSequenceEvent startEvent = new TimedSequenceEvent(startType, startTime, 0, 0);
    TimedSequenceEvent endEvent =
        new TimedSequenceEvent(endType, endTime, duration, duration);

    List<TimedSequenceEvent> events = List.of(startEvent, endEvent);
    List<IntervalDuration> intervals = List.of(new IntervalDuration(startType, endType, duration));

    return new SequenceInfo(start.teamNumber(), 0, events, intervals, duration);
  }

  private List<IntervalStats> computeIntervalStats(List<SequenceInfo> sequences) {
    // Group interval durations by start-end event type pair
    Map<String, List<Long>> grouped = new HashMap<>();
    Map<String, EventType> startTypes = new HashMap<>();
    Map<String, EventType> endTypes = new HashMap<>();

    for (SequenceInfo seq : sequences) {
      for (IntervalDuration id : seq.intervals()) {
        String key = id.start().eventtype() + "->" + id.end().eventtype();
        grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(id.duration());
        startTypes.put(key, id.start());
        endTypes.put(key, id.end());
      }
    }

    List<IntervalStats> stats = new ArrayList<>();
    for (var entry : grouped.entrySet()) {
      String key = entry.getKey();
      List<Long> durs = entry.getValue();
      long avg = (long) durs.stream().mapToLong(Long::longValue).average().orElse(0);
      long fast = durs.stream().mapToLong(Long::longValue).min().orElse(0);
      long slow = durs.stream().mapToLong(Long::longValue).max().orElse(0);
      long sd = computeStdDev(durs, avg);
      stats.add(new IntervalStats(startTypes.get(key), endTypes.get(key), avg, fast, slow, sd));
    }
    return stats;
  }

  private long computeStdDev(List<Long> values, long mean) {
    if (values.size() <= 1) return 0;
    double variance =
        values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum() / values.size();
    return (long) Math.sqrt(variance);
  }

  private SequenceReport emptyReport() {
    return new SequenceReport(List.of(), 0, 0, 0, 0, List.of());
  }
}
