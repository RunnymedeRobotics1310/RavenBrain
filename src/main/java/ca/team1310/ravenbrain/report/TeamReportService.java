package ca.team1310.ravenbrain.report;

import ca.team1310.ravenbrain.connect.UserService;
import ca.team1310.ravenbrain.eventlog.EventLogRecord;
import ca.team1310.ravenbrain.eventlog.EventLogService;
import ca.team1310.ravenbrain.eventlog.TournamentEventTypePair;
import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import ca.team1310.ravenbrain.quickcomment.QuickComment;
import ca.team1310.ravenbrain.quickcomment.QuickCommentService;
import ca.team1310.ravenbrain.robotalert.RobotAlert;
import ca.team1310.ravenbrain.robotalert.RobotAlertService;
import ca.team1310.ravenbrain.schedule.ScheduleRecord;
import ca.team1310.ravenbrain.schedule.ScheduleService;
import ca.team1310.ravenbrain.sequencetype.SequenceEvent;
import ca.team1310.ravenbrain.sequencetype.SequenceType;
import ca.team1310.ravenbrain.sequencetype.SequenceTypeService;
import ca.team1310.ravenbrain.tournament.TournamentService;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * @author Tony Field
 * @since 2025-04-04 16:33
 */
@Singleton
public class TeamReportService {

  @Serdeable
  public record TeamReportComment(
      String timestamp, String displayName, String role, String quickComment) {}

  @Serdeable
  public record TeamReportRobotAlert(
      String timestamp,
      String displayName,
      String tournamentId,
      String alert) {}

  @Serdeable
  public record SequenceReportLink(
      String sequenceTypeCode, String sequenceTypeName, String tournamentId, boolean drill) {}

  @Serdeable
  public record DefenceNote(
      String timestamp, String displayName, String tournamentId, int matchId, String note) {}

  @Serdeable
  public record CountPerMatchStat(
      String tournamentId, double averageCountPerMatch, int matchCount, int totalCount) {}

  @Serdeable
  public record FuelPickupStats(
      String tournamentId, int ballPitCount, int homeCount, int outpostCount) {}

  @Serdeable
  public record ScoringMatchDatum(
      String matchLabel,
      int matchNumber,
      TournamentLevel level,
      Integer teamMatchScore,
      double avgSuccess,
      double avgMiss) {}

  @Serdeable
  public record ScoringBarChart(
      String tournamentId,
      double overallAvgSuccess,
      double overallAvgMiss,
      List<ScoringMatchDatum> matches) {}

  @Serdeable
  public record PickupMatchDatum(
      String matchLabel,
      int matchNumber,
      TournamentLevel level,
      Integer teamMatchScore,
      double avgPickups) {}

  @Serdeable
  public record PickupBarChart(
      String tournamentId, double overallAvgPickups, List<PickupMatchDatum> matches) {}

  @Serdeable
  public record TeamReport(
      List<TeamReportComment> comments,
      List<TeamReportRobotAlert> robotAlerts,
      List<SequenceReportLink> sequenceReportLinks,
      List<DefenceNote> defenceNotes,
      List<CountPerMatchStat> shootToHomeStats,
      List<FuelPickupStats> fuelPickupStats,
      List<ScoringBarChart> scoringCharts,
      List<PickupBarChart> pickupCharts,
      TournamentReportService.TournamentReportTable[] tournamentReports) {}

  private record MatchKey(TournamentLevel level, int matchNumber) {}

  private final QuickCommentService quickCommentService;
  private final RobotAlertService robotAlertService;
  private final TournamentReportService tournamentReportService;
  private final TournamentService tournamentService;
  private final UserService userService;
  private final EventLogService eventLogService;
  private final SequenceTypeService sequenceTypeService;
  private final ScheduleService scheduleService;

  public TeamReportService(
      QuickCommentService quickCommentService,
      RobotAlertService robotAlertService,
      TournamentReportService tournamentReportService,
      TournamentService tournamentService,
      UserService userService,
      EventLogService eventLogService,
      SequenceTypeService sequenceTypeService,
      ScheduleService scheduleService) {
    this.quickCommentService = quickCommentService;
    this.robotAlertService = robotAlertService;
    this.tournamentReportService = tournamentReportService;
    this.tournamentService = tournamentService;
    this.userService = userService;
    this.eventLogService = eventLogService;
    this.sequenceTypeService = sequenceTypeService;
    this.scheduleService = scheduleService;
  }

  public TeamReport getTeamReport(int team) {
    List<QuickComment> rawComments = quickCommentService.findAllByTeamOrderByTimestamp(team);

    Map<Long, String> userNameMap = new HashMap<>();
    rawComments.stream()
        .map(QuickComment::userId)
        .distinct()
        .forEach(
            id ->
                userNameMap.put(
                    id,
                    resolveDisplayName(id)));

    List<TeamReportComment> comments =
        rawComments.stream()
            .map(
                c ->
                    new TeamReportComment(
                        c.timestamp().toString(),
                        userNameMap.getOrDefault(c.userId(), "Unknown"),
                        c.role(),
                        c.quickComment()))
            .toList();

    // Build robot alerts with display names
    List<RobotAlert> rawAlerts =
        robotAlertService.findAllByTeamNumberOrderByCreatedAtDesc(team).stream()
            .filter(a -> !a.tournamentId().startsWith("DRILL-"))
            .toList();

    for (var alert : rawAlerts) {
      userNameMap.computeIfAbsent(alert.userId(), this::resolveDisplayName);
    }

    List<TeamReportRobotAlert> robotAlerts =
        rawAlerts.stream()
            .map(
                a ->
                    new TeamReportRobotAlert(
                        a.createdAt().toString(),
                        userNameMap.getOrDefault(a.userId(), "Unknown"),
                        a.tournamentId(),
                        a.alert()))
            .toList();

    // Build sequence report links by checking which sequence types have matching events
    List<TournamentEventTypePair> pairs =
        eventLogService.listDistinctTournamentAndEventTypeByTeamNumber(team);

    // Group event types by tournament
    Map<String, Set<String>> eventTypesByTournament = new HashMap<>();
    for (var pair : pairs) {
      eventTypesByTournament
          .computeIfAbsent(pair.tournamentId(), k -> new HashSet<>())
          .add(pair.eventType());
    }

    // For each active sequence type, check which tournaments have at least one matching event type
    List<SequenceType> activeSequenceTypes =
        sequenceTypeService.list().stream().filter(st -> !st.disabled()).toList();

    List<SequenceReportLink> sequenceLinks = new ArrayList<>();
    for (var st : activeSequenceTypes) {
      Set<String> seqEventTypes =
          st.events().stream().map(se -> se.eventtype().eventtype()).collect(Collectors.toSet());
      for (var entry : eventTypesByTournament.entrySet()) {
        String tournamentId = entry.getKey();
        Set<String> tournamentEventTypes = entry.getValue();
        boolean hasMatch = tournamentEventTypes.stream().anyMatch(seqEventTypes::contains);
        if (hasMatch) {
          boolean isDrill = tournamentId.startsWith("DRILL-");
          sequenceLinks.add(new SequenceReportLink(st.code(), st.name(), tournamentId, isDrill));
        }
      }
    }

    // Build defence-strat notes
    var rawDefenceEvents =
        eventLogService.listEventsByTeamAndEventTypeWithNotes(team, "defence-strat").stream()
            .filter(e -> !e.tournamentId().startsWith("DRILL-"))
            .toList();
    for (var e : rawDefenceEvents) {
      userNameMap.computeIfAbsent(e.userId(), this::resolveDisplayName);
    }
    List<DefenceNote> defenceNotes =
        rawDefenceEvents.stream()
            .map(
                e ->
                    new DefenceNote(
                        e.timestamp().toString(),
                        userNameMap.getOrDefault(e.userId(), "Unknown"),
                        e.tournamentId(),
                        e.matchId(),
                        e.note()))
            .toList();

    // Build shoot-to-home count-per-match averages by tournament
    var shootToHomeEvents = eventLogService.listEventsByTeamAndEventType(team, "shoot-to-home");
    // Group by non-drill tournament, then count per match
    Map<String, Map<Integer, Integer>> shootByTournamentMatch = new LinkedHashMap<>();
    for (var e : shootToHomeEvents) {
      if (!e.tournamentId().startsWith("DRILL-")) {
        shootByTournamentMatch
            .computeIfAbsent(e.tournamentId(), k -> new LinkedHashMap<>())
            .merge(e.matchId(), 1, Integer::sum);
      }
    }
    List<CountPerMatchStat> shootToHomeStats = new ArrayList<>();
    for (var entry : shootByTournamentMatch.entrySet()) {
      var matchCounts = entry.getValue().values();
      int total = matchCounts.stream().mapToInt(Integer::intValue).sum();
      int numMatches = matchCounts.size();
      double avg =
          BigDecimal.valueOf((double) total / numMatches)
              .setScale(2, RoundingMode.HALF_UP)
              .doubleValue();
      shootToHomeStats.add(new CountPerMatchStat(entry.getKey(), avg, numMatches, total));
    }

    // Build fuel pickup counts by tournament
    var ballPitEvents = eventLogService.listEventsByTeamAndEventType(team, "pickup-ball-pit");
    var homeEvents = eventLogService.listEventsByTeamAndEventType(team, "pickup-home");
    var outpostEvents = eventLogService.listEventsByTeamAndEventType(team, "pickup-outpost");

    Map<String, int[]> fuelCounts = new LinkedHashMap<>();
    for (var e : ballPitEvents) {
      if (!e.tournamentId().startsWith("DRILL-")) {
        fuelCounts.computeIfAbsent(e.tournamentId(), k -> new int[3])[0]++;
      }
    }
    for (var e : homeEvents) {
      if (!e.tournamentId().startsWith("DRILL-")) {
        fuelCounts.computeIfAbsent(e.tournamentId(), k -> new int[3])[1]++;
      }
    }
    for (var e : outpostEvents) {
      if (!e.tournamentId().startsWith("DRILL-")) {
        fuelCounts.computeIfAbsent(e.tournamentId(), k -> new int[3])[2]++;
      }
    }
    List<FuelPickupStats> fuelPickupStats = new ArrayList<>();
    for (var entry : fuelCounts.entrySet()) {
      int[] c = entry.getValue();
      fuelPickupStats.add(new FuelPickupStats(entry.getKey(), c[0], c[1], c[2]));
    }

    // Build bar chart data for Average Scores/Misses and Average Fuel Pickups
    Map<String, Map<MatchKey, List<Double>>> successByTm =
        groupForBarChart(
            eventLogService.listEventsByTeamAndEventType(team, "scoring-number-success"));
    Map<String, Map<MatchKey, List<Double>>> missByTm =
        groupForBarChart(
            eventLogService.listEventsByTeamAndEventType(team, "scoring-number-miss"));
    Map<String, Map<MatchKey, List<Double>>> pickupByTm =
        groupForBarChart(eventLogService.listEventsByTeamAndEventType(team, "pickup-number"));

    Map<String, Map<MatchKey, ScheduleRecord>> scheduleCache = new HashMap<>();

    List<ScoringBarChart> scoringCharts = new ArrayList<>();
    Set<String> scoringTournaments = new LinkedHashSet<>();
    scoringTournaments.addAll(successByTm.keySet());
    scoringTournaments.addAll(missByTm.keySet());
    for (String tournamentId : scoringTournaments) {
      Map<MatchKey, List<Double>> successMap =
          successByTm.getOrDefault(tournamentId, Collections.emptyMap());
      Map<MatchKey, List<Double>> missMap =
          missByTm.getOrDefault(tournamentId, Collections.emptyMap());
      List<MatchKey> orderedKeys = mergedOrderedKeys(successMap.keySet(), missMap.keySet());
      if (orderedKeys.isEmpty()) continue;

      Map<MatchKey, ScheduleRecord> schedule =
          scheduleCache.computeIfAbsent(tournamentId, this::loadSchedule);

      List<ScoringMatchDatum> matches = new ArrayList<>();
      double sumSuccess = 0;
      double sumMiss = 0;
      for (MatchKey key : orderedKeys) {
        double avgSuccess = round2(mean(successMap.get(key)));
        double avgMiss = round2(mean(missMap.get(key)));
        Integer teamScore = teamMatchScore(schedule.get(key), team);
        matches.add(
            new ScoringMatchDatum(
                matchLabel(key.level(), key.matchNumber()),
                key.matchNumber(),
                key.level(),
                teamScore,
                avgSuccess,
                avgMiss));
        sumSuccess += avgSuccess;
        sumMiss += avgMiss;
      }
      double overallAvgSuccess = round2(sumSuccess / matches.size());
      double overallAvgMiss = round2(sumMiss / matches.size());
      scoringCharts.add(
          new ScoringBarChart(tournamentId, overallAvgSuccess, overallAvgMiss, matches));
    }

    List<PickupBarChart> pickupCharts = new ArrayList<>();
    for (String tournamentId : pickupByTm.keySet()) {
      Map<MatchKey, List<Double>> pickupMap = pickupByTm.get(tournamentId);
      List<MatchKey> orderedKeys = mergedOrderedKeys(pickupMap.keySet(), Collections.emptySet());
      if (orderedKeys.isEmpty()) continue;

      Map<MatchKey, ScheduleRecord> schedule =
          scheduleCache.computeIfAbsent(tournamentId, this::loadSchedule);

      List<PickupMatchDatum> matches = new ArrayList<>();
      double sumPickups = 0;
      for (MatchKey key : orderedKeys) {
        double avgPickups = round2(mean(pickupMap.get(key)));
        Integer teamScore = teamMatchScore(schedule.get(key), team);
        matches.add(
            new PickupMatchDatum(
                matchLabel(key.level(), key.matchNumber()),
                key.matchNumber(),
                key.level(),
                teamScore,
                avgPickups));
        sumPickups += avgPickups;
      }
      double overallAvgPickups = round2(sumPickups / matches.size());
      pickupCharts.add(new PickupBarChart(tournamentId, overallAvgPickups, matches));
    }

    var tournamentReports = new ArrayList<TournamentReportService.TournamentReportTable>();
    for (var tournament : tournamentService.findAllSortByStartTime()) {
      var tournamentReport = tournamentReportService.getTournamentReport(tournament.id(), team);
      if (tournamentReport != null && tournamentReport.success()) {
        if (tournamentReport.report().dataRows().length > 0) {
          tournamentReports.add(tournamentReport.report());
        }
      }
    }
    return new TeamReport(
        comments,
        robotAlerts,
        sequenceLinks,
        defenceNotes,
        shootToHomeStats,
        fuelPickupStats,
        scoringCharts,
        pickupCharts,
        tournamentReports.toArray(new TournamentReportService.TournamentReportTable[0]));
  }

  private Map<String, Map<MatchKey, List<Double>>> groupForBarChart(List<EventLogRecord> events) {
    Map<String, Map<MatchKey, List<Double>>> result = new LinkedHashMap<>();
    for (var e : events) {
      if (e.tournamentId().startsWith("DRILL-")) continue;
      if (e.level() != TournamentLevel.Qualification && e.level() != TournamentLevel.Playoff)
        continue;
      result
          .computeIfAbsent(e.tournamentId(), k -> new LinkedHashMap<>())
          .computeIfAbsent(new MatchKey(e.level(), e.matchId()), k -> new ArrayList<>())
          .add(e.amount());
    }
    return result;
  }

  private List<MatchKey> mergedOrderedKeys(Set<MatchKey> a, Set<MatchKey> b) {
    Set<MatchKey> union = new HashSet<>();
    union.addAll(a);
    union.addAll(b);
    return union.stream()
        .sorted(Comparator.comparing(MatchKey::level).thenComparingInt(MatchKey::matchNumber))
        .toList();
  }

  private Map<MatchKey, ScheduleRecord> loadSchedule(String tournamentId) {
    Map<MatchKey, ScheduleRecord> map = new HashMap<>();
    for (var sr : scheduleService.findAllByTournamentIdOrderByMatch(tournamentId)) {
      map.put(new MatchKey(sr.level(), sr.match()), sr);
    }
    return map;
  }

  private Integer teamMatchScore(ScheduleRecord sr, int team) {
    if (sr == null) return null;
    if (sr.red1() == team || sr.red2() == team || sr.red3() == team || sr.red4() == team) {
      return sr.redScore();
    }
    if (sr.blue1() == team || sr.blue2() == team || sr.blue3() == team || sr.blue4() == team) {
      return sr.blueScore();
    }
    return null;
  }

  private String matchLabel(TournamentLevel level, int matchNumber) {
    String prefix = level == TournamentLevel.Qualification ? "Q" : "E";
    return prefix + matchNumber;
  }

  private double mean(List<Double> values) {
    if (values == null || values.isEmpty()) return 0.0;
    return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
  }

  private double round2(double v) {
    return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }

  private String resolveDisplayName(long userId) {
    try {
      return userService.getUser(userId).displayName();
    } catch (Exception e) {
      return "Unknown";
    }
  }
}
