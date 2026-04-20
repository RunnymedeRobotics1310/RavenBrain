package ca.team1310.ravenbrain.report;

import ca.team1310.ravenbrain.eventlog.EventLogRecord;
import ca.team1310.ravenbrain.eventlog.EventLogRepository;
import ca.team1310.ravenbrain.quickcomment.QuickComment;
import ca.team1310.ravenbrain.quickcomment.QuickCommentService;
import ca.team1310.ravenbrain.robotalert.RobotAlert;
import ca.team1310.ravenbrain.robotalert.RobotAlertService;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Batched per-tournament scouting aggregates.
 *
 * <p>{@link #getAggregatesForAllTeams(String)} returns one {@link TournamentAggregates} row per
 * team at the tournament with event-log, quick-comment and robot-alert data. Results are computed
 * with at most three DB queries (one per concern) to avoid the N+1 pattern of {@link
 * CustomTournamentStatsService#getStatsForTeam(int)}.
 *
 * <p>Results are cached per tournamentId; {@link #invalidate(String)} is called from {@code
 * EventApi}, {@code QuickCommentApi} and {@code RobotAlertApi} whenever a write lands.
 *
 * <p>Whitelisted event types ({@link #TARGET_EVENT_TYPES}) mirror the set used by {@link
 * CustomTournamentStatsService} — all other event-log rows are ignored for aggregate computation.
 */
@Slf4j
@Singleton
public class TournamentAggregatesService {

  static final String AUTO_SHOT = "auto-number-shot";
  static final String AUTO_MISSED = "auto-number-missed";
  static final String SCORE_SUCCESS = "scoring-number-success";
  static final String SCORE_MISS = "scoring-number-miss";
  static final String PICKUP = "pickup-number";

  static final Set<String> TARGET_EVENT_TYPES =
      Set.of(AUTO_SHOT, AUTO_MISSED, SCORE_SUCCESS, SCORE_MISS, PICKUP);

  /** Severity ordering: info < warning < critical. Used to compute the max severity per team. */
  private static final Map<String, Integer> SEVERITY_RANK =
      Map.of("info", 0, "warning", 1, "critical", 2);

  private final EventLogRepository eventLogRepository;
  private final QuickCommentService quickCommentService;
  private final RobotAlertService robotAlertService;

  private final ConcurrentHashMap<String, Map<Integer, TournamentAggregates>> cache =
      new ConcurrentHashMap<>();

  public TournamentAggregatesService(
      EventLogRepository eventLogRepository,
      QuickCommentService quickCommentService,
      RobotAlertService robotAlertService) {
    this.eventLogRepository = eventLogRepository;
    this.quickCommentService = quickCommentService;
    this.robotAlertService = robotAlertService;
  }

  /**
   * Returns a map keyed by team number of aggregated scouting signals for {@code tournamentId}.
   *
   * <p>The map contains every team that has at least one event-log, quick-comment-authored-team,
   * or robot-alert row for the tournament. Teams with only partial data still appear; missing
   * values are represented as {@code null} on {@link TournamentAggregates} (for averages) or
   * {@code 0} (for counts).
   *
   * <p>Callers that need the full tournament roster (including teams with no scouting data) can
   * combine this map with {@code TeamTournamentService.findTeamNumbersForTournament(tournamentId)}
   * and emit {@link #emptyFor(int)} for missing keys. Unit 5's enricher does this.
   */
  public Map<Integer, TournamentAggregates> getAggregatesForAllTeams(String tournamentId) {
    Map<Integer, TournamentAggregates> cached = cache.get(tournamentId);
    if (cached != null) {
      return cached;
    }

    List<EventLogRecord> eventLogs = eventLogRepository.findAllByTournamentId(tournamentId);
    List<RobotAlert> robotAlerts =
        robotAlertService.findAllByTournamentIdOrderByTeamNumberAscCreatedAtDesc(tournamentId);

    // Aggregate whitelisted event-log sums + counts by (teamNumber, eventType).
    Map<Integer, Map<String, double[]>> sumsByTeamAndType = new HashMap<>();
    for (EventLogRecord e : eventLogs) {
      if (!TARGET_EVENT_TYPES.contains(e.eventType())) {
        continue;
      }
      var byType =
          sumsByTeamAndType.computeIfAbsent(e.teamNumber(), k -> new HashMap<>());
      double[] sumAndCount = byType.computeIfAbsent(e.eventType(), k -> new double[] {0.0, 0.0});
      sumAndCount[0] += e.amount();
      sumAndCount[1] += 1.0;
    }

    // Robot-alert counts + max severity per team for this tournament.
    Map<Integer, int[]> alertCounts = new HashMap<>();
    Map<Integer, String> alertMaxSeverity = new HashMap<>();
    for (RobotAlert a : robotAlerts) {
      int team = a.teamNumber();
      alertCounts.computeIfAbsent(team, k -> new int[1])[0] += 1;
      String sev = parseSeverity(a.alert());
      String prior = alertMaxSeverity.get(team);
      if (prior == null || rank(sev) > rank(prior)) {
        alertMaxSeverity.put(team, sev);
      }
    }

    // Quick-comment counts per team (RB_COMMENT is not tournament-scoped). Fetched only for teams
    // we care about to keep this cheap; see QuickCommentService.findAllByTeamOrderByTimestamp.
    Set<Integer> teamsToReport = new java.util.TreeSet<>();
    teamsToReport.addAll(sumsByTeamAndType.keySet());
    teamsToReport.addAll(alertCounts.keySet());

    Map<Integer, Integer> commentCounts = new HashMap<>();
    for (int team : teamsToReport) {
      commentCounts.put(team, quickCommentService.findAllByTeamOrderByTimestamp(team).size());
    }

    // Compose into TournamentAggregates per team (TreeMap for deterministic iteration by team #).
    Map<Integer, TournamentAggregates> result = new TreeMap<>();
    for (int team : teamsToReport) {
      Map<String, double[]> byType =
          sumsByTeamAndType.getOrDefault(team, new LinkedHashMap<>());
      Double autoAccuracy = rate(byType.get(AUTO_SHOT), byType.get(AUTO_MISSED));
      Double teleopSuccessRate = rate(byType.get(SCORE_SUCCESS), byType.get(SCORE_MISS));
      Double pickupAverage = average(byType.get(PICKUP));
      int alertCount = alertCounts.getOrDefault(team, new int[1])[0];
      String maxSeverity = alertMaxSeverity.get(team);
      int commentCount = commentCounts.getOrDefault(team, 0);
      result.put(
          team,
          new TournamentAggregates(
              team,
              autoAccuracy,
              teleopSuccessRate,
              pickupAverage,
              commentCount,
              alertCount,
              maxSeverity));
    }

    Map<Integer, TournamentAggregates> immutable = Map.copyOf(result);
    cache.put(tournamentId, immutable);
    return immutable;
  }

  /**
   * Returns an empty aggregate row for a team with no scouting data. Used by downstream enrichers
   * that need a row for every team in the roster even when the team is invisible to this service.
   */
  public static TournamentAggregates emptyFor(int teamNumber) {
    return new TournamentAggregates(teamNumber, null, null, null, 0, 0, null);
  }

  /** Drop the cached result for {@code tournamentId}. Called on any scouting write for the id. */
  public void invalidate(String tournamentId) {
    cache.remove(tournamentId);
  }

  /**
   * Drop every cached tournament result. Used by writers that affect cross-tournament data (e.g.
   * {@code RB_COMMENT} is not tournament-scoped, so any new comment could change the
   * {@code quickCommentCount} for the commenter's team in any cached tournament).
   */
  public void invalidateAll() {
    cache.clear();
  }

  // ---------- helpers ----------

  /** Successful attempts / (successful + missed). Null when neither bucket has any data. */
  private static Double rate(double[] success, double[] miss) {
    double s = success == null ? 0.0 : success[0];
    double m = miss == null ? 0.0 : miss[0];
    double total = s + m;
    if (total <= 0.0) {
      return null;
    }
    return s / total;
  }

  /** Arithmetic mean of recorded amounts. Null when no data. */
  private static Double average(double[] sumAndCount) {
    if (sumAndCount == null || sumAndCount[1] <= 0.0) {
      return null;
    }
    return sumAndCount[0] / sumAndCount[1];
  }

  /**
   * Parse a severity marker from the start of an alert string.
   *
   * <p>The {@code RB_ROBOT_ALERT} schema currently has no severity column; scouts record
   * free-text. This parser accepts an optional leading bracketed marker — {@code [info]},
   * {@code [warning]}, {@code [critical]} (case-insensitive) — and defaults untagged alerts to
   * {@code "info"}. When {@code RB_ROBOT_ALERT.severity} lands as a first-class column, this
   * parser will be deleted in favour of the stored value.
   */
  static String parseSeverity(String alert) {
    if (alert == null) {
      return "info";
    }
    String trimmed = alert.trim();
    if (trimmed.startsWith("[")) {
      int close = trimmed.indexOf(']');
      if (close > 1) {
        String tag = trimmed.substring(1, close).trim().toLowerCase(Locale.ROOT);
        if (SEVERITY_RANK.containsKey(tag)) {
          return tag;
        }
      }
    }
    return "info";
  }

  private static int rank(String severity) {
    return SEVERITY_RANK.getOrDefault(severity, 0);
  }
}
