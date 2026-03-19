package ca.team1310.ravenbrain.report.pmva;

import ca.team1310.ravenbrain.eventlog.EventLogRecord;
import ca.team1310.ravenbrain.eventlog.EventLogRepository;
import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import ca.team1310.ravenbrain.report.pmva.PmvaReport.*;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates the PMVA (Post Match Video Analysis) report for the owner team at a given tournament.
 *
 * @author Tony Field
 * @since 2026-03-16
 */
@Slf4j
@Singleton
public class PmvaReportService {

  private static final Set<TournamentLevel> MATCH_LEVELS =
      Set.of(TournamentLevel.Qualification, TournamentLevel.Playoff);

  // Shooting position end events
  private static final Map<String, String> POSITION_END_EVENTS =
      Map.of(
          "pmva-shoot-position-close", "close",
          "pmva-shoot-position-mid", "mid",
          "pmva-shoot-position-far", "far",
          "pmva-shoot-position-varied", "varied");

  private final EventLogRepository eventLogRepository;
  private final int teamNumber;

  public PmvaReportService(
      EventLogRepository eventLogRepository,
      @Property(name = "raven-eye.team") int teamNumber) {
    this.eventLogRepository = eventLogRepository;
    this.teamNumber = teamNumber;
  }

  /** List tournaments that have PMVA data for the owner team. */
  public List<String> listTournamentsWithData() {
    return eventLogRepository.findDistinctPmvaTournamentIds(teamNumber);
  }

  /** Generate the full PMVA report for a tournament. */
  public PmvaReport generate(String tournamentId) {
    // Fetch and filter events
    var allEvents =
        eventLogRepository.findAllByTeamNumberAndTournamentIdOrderByTimestampAsc(
            teamNumber, tournamentId);
    var pmvaEvents =
        allEvents.stream()
            .filter(e -> MATCH_LEVELS.contains(e.level()))
            .filter(e -> e.eventType().startsWith("pmva-"))
            .toList();

    // Group by match
    var matchKeys =
        pmvaEvents.stream()
            .map(e -> e.level().name() + ":" + e.matchId())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    int matchCount = matchKeys.size();

    if (matchCount == 0) {
      return emptyReport();
    }

    // Group events by match for per-match processing
    var eventsByMatch = new LinkedHashMap<String, List<EventLogRecord>>();
    for (var event : pmvaEvents) {
      var key = event.level().name() + ":" + event.matchId();
      eventsByMatch.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
    }

    var general = buildGeneralSection(pmvaEvents, eventsByMatch);
    var hopper = buildHopperSection(eventsByMatch, matchCount);
    var swi = buildSwiSection(eventsByMatch, matchCount);

    return new PmvaReport(teamNumber, matchCount, general, hopper, swi);
  }

  private PmvaReport emptyReport() {
    return new PmvaReport(
        teamNumber,
        0,
        new GeneralSection(
            0,
            0,
            0.0,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()),
        new HopperSection(
            new LoadingStats(0, 0, 0, 0, List.of()), null, null, null, null, null),
        new SwiSection(0, 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of()));
  }

  // ── General Section ──────────────────────────────────────────────────────

  private GeneralSection buildGeneralSection(
      List<EventLogRecord> allEvents, Map<String, List<EventLogRecord>> eventsByMatch) {

    int breakdownCount = 0;
    int noBreakdownCount = 0;
    var breakdownMatches = new ArrayList<MatchBreakdown>();
    var breakdownNotes = new ArrayList<PmvaReport.MatchComment>();
    var intakeComments = new ArrayList<PmvaReport.MatchComment>();
    var shooterComments = new ArrayList<PmvaReport.MatchComment>();
    var generalComments = new ArrayList<PmvaReport.MatchComment>();
    var suggestions = new ArrayList<PmvaReport.MatchComment>();

    // Build a map of video links per match for cross-referencing
    var videoLinksByMatch = new HashMap<String, String>();
    for (var event : allEvents) {
      if ("pmva-video-link".equals(event.eventType()) && hasNote(event)) {
        var key = event.level().name() + ":" + event.matchId();
        videoLinksByMatch.put(key, event.note());
      }
    }

    for (var event : allEvents) {
      switch (event.eventType()) {
        case "pmva-breakdown" -> {
          breakdownCount++;
          var matchKey = event.level().name() + ":" + event.matchId();
          breakdownMatches.add(
              new MatchBreakdown(
                  event.matchId(),
                  event.level().name(),
                  hasNote(event) ? event.note() : "",
                  videoLinksByMatch.get(matchKey)));
          if (hasNote(event)) {
            breakdownNotes.add(toComment(event));
          }
        }
        case "pmva-no-breakdown" -> noBreakdownCount++;
        case "pmva-intake-comments" -> {
          if (hasNote(event)) intakeComments.add(toComment(event));
        }
        case "pmva-shooter-comments" -> {
          if (hasNote(event)) shooterComments.add(toComment(event));
        }
        case "pmva-general-comments" -> {
          if (hasNote(event)) generalComments.add(toComment(event));
        }
        case "pmva-look-into-suggestion" -> {
          if (hasNote(event)) suggestions.add(toComment(event));
        }
        default -> {
          // skip non-general events
        }
      }
    }

    int total = breakdownCount + noBreakdownCount;
    double breakdownPct = safeDivide(breakdownCount, total) * 100.0;

    return new GeneralSection(
        breakdownCount,
        noBreakdownCount,
        round2(breakdownPct),
        breakdownMatches,
        breakdownNotes,
        intakeComments,
        shooterComments,
        generalComments,
        suggestions);
  }

  // ── Hopper Section ────────────────────────────────────────────────────────

  private HopperSection buildHopperSection(
      Map<String, List<EventLogRecord>> eventsByMatch, int matchCount) {
    var loading = buildLoadingStats(eventsByMatch);
    var allUnloadSequences = extractUnloadSequences(eventsByMatch);

    // Collect hopper shooting comments from the event stream
    var stuckComments = new ArrayList<PmvaReport.MatchComment>();
    var unloadGeneralComments = new ArrayList<PmvaReport.MatchComment>();
    for (var matchEvents : eventsByMatch.values()) {
      for (var event : matchEvents) {
        if ("pmva-stuck-comments".equals(event.eventType()) && hasNote(event)) {
          stuckComments.add(toComment(event));
        } else if ("pmva-unload-general-comments".equals(event.eventType()) && hasNote(event)) {
          unloadGeneralComments.add(toComment(event));
        }
      }
    }

    var shootingAll =
        buildShootingStats("all", allUnloadSequences, matchCount, stuckComments, unloadGeneralComments);

    // Per-position
    var byPosition =
        allUnloadSequences.stream().collect(Collectors.groupingBy(UnloadSequence::position));
    var shootingClose =
        buildShootingStatsOrNull("close", byPosition.get("close"), matchCount);
    var shootingMid =
        buildShootingStatsOrNull("mid", byPosition.get("mid"), matchCount);
    var shootingFar =
        buildShootingStatsOrNull("far", byPosition.get("far"), matchCount);
    var shootingVaried =
        buildShootingStatsOrNull("varied", byPosition.get("varied"), matchCount);

    return new HopperSection(
        loading, shootingAll, shootingClose, shootingMid, shootingFar, shootingVaried);
  }

  private LoadingStats buildLoadingStats(Map<String, List<EventLogRecord>> eventsByMatch) {
    var fillCounts = new ArrayList<Double>();
    var ratings = new ArrayList<Double>();
    int hopperFullCount = 0;
    int hopperNotFullCount = 0;
    var loadComments = new ArrayList<PmvaReport.MatchComment>();

    for (var entry : eventsByMatch.entrySet()) {
      boolean inSequence = false;
      double currentFillCount = 0;

      for (var event : entry.getValue()) {
        switch (event.eventType()) {
          case "pmva-start-load" -> {
            if (inSequence) {
              log.debug("Discarding unclosed load sequence in match {}", entry.getKey());
            }
            inSequence = true;
            currentFillCount = 0;
          }
          case "pmva-load-count" -> {
            if (inSequence) currentFillCount += event.amount();
          }
          case "pmva-load-rating" -> {
            if (inSequence) ratings.add(event.amount());
          }
          case "pmva-load-comments" -> {
            if (hasNote(event)) loadComments.add(toComment(event));
          }
          case "pmva-hopper-full" -> {
            if (inSequence) {
              fillCounts.add(currentFillCount);
              hopperFullCount++;
              inSequence = false;
            }
          }
          case "pmva-hopper-not-full" -> {
            if (inSequence) {
              fillCounts.add(currentFillCount);
              hopperNotFullCount++;
              inSequence = false;
            }
          }
          default -> {
            // skip
          }
        }
      }
      if (inSequence) {
        log.debug("Discarding unclosed load sequence at end of match {}", entry.getKey());
      }
    }

    double avgFill = average(fillCounts);
    double maxFill = fillCounts.stream().mapToDouble(Double::doubleValue).max().orElse(0);
    int totalHopperEnd = hopperFullCount + hopperNotFullCount;
    double hopperFilledPct = safeDivide(hopperFullCount, totalHopperEnd) * 100.0;
    double avgRating = average(ratings);

    return new LoadingStats(
        round2(avgFill), round2(maxFill), round2(hopperFilledPct), round2(avgRating), loadComments);
  }

  /** Internal representation of a detected unload sequence. */
  private record UnloadSequence(
      int matchId,
      String level,
      String position,
      int scoreCount,
      int missCount,
      double unloadSeconds,
      double stuckCount) {}

  private List<UnloadSequence> extractUnloadSequences(
      Map<String, List<EventLogRecord>> eventsByMatch) {
    var sequences = new ArrayList<UnloadSequence>();

    for (var entry : eventsByMatch.entrySet()) {
      boolean inSequence = false;
      int scoreCount = 0;
      int missCount = 0;
      double unloadSeconds = 0;
      double stuckCount = 0;
      int currentMatchId = 0;
      String currentLevel = "";

      for (var event : entry.getValue()) {
        if ("pmva-shoot-one".equals(event.eventType())) {
          if (inSequence) {
            // Previous sequence never closed — discard it
            log.debug("Discarding unclosed unload sequence in match {}", currentMatchId);
          }
          inSequence = true;
          scoreCount = 1; // pmva-shoot-one counts as the first shot
          missCount = 0;
          unloadSeconds = 0;
          stuckCount = 0;
          currentMatchId = event.matchId();
          currentLevel = event.level().name();
        } else if (inSequence) {
          switch (event.eventType()) {
            case "pmva-score-one" -> scoreCount++;
            case "pmva-miss-one" -> missCount++;
            case "pmva-unload-seconds" -> unloadSeconds = event.amount();
            case "pmva-count-stuck-in-hopper" -> stuckCount = event.amount();
            default -> {
              // Check if this is a position end event
              String position = POSITION_END_EVENTS.get(event.eventType());
              if (position != null) {
                sequences.add(
                    new UnloadSequence(
                        currentMatchId,
                        currentLevel,
                        position,
                        scoreCount,
                        missCount,
                        unloadSeconds,
                        stuckCount));
                inSequence = false;
              }
            }
          }
        }
      }
      if (inSequence) {
        log.debug("Discarding unclosed unload sequence at end of match {}", entry.getKey());
      }
    }
    return sequences;
  }

  private ShootingStats buildShootingStatsOrNull(
      String position, List<UnloadSequence> sequences, int matchCount) {
    if (sequences == null || sequences.isEmpty()) return null;
    return buildShootingStats(position, sequences, matchCount, List.of(), List.of());
  }

  private ShootingStats buildShootingStats(
      String position,
      List<UnloadSequence> sequences,
      int matchCount,
      List<PmvaReport.MatchComment> stuckComments,
      List<PmvaReport.MatchComment> generalComments) {
    if (sequences.isEmpty()) {
      return new ShootingStats(
          position, 0, List.of(), 0, 0, 0, 0, 0, 0, stuckComments, generalComments);
    }

    int totalScores = sequences.stream().mapToInt(UnloadSequence::scoreCount).sum();
    int totalShots =
        sequences.stream().mapToInt(s -> s.scoreCount() + s.missCount()).sum();
    double totalUnloadSeconds =
        sequences.stream().mapToDouble(UnloadSequence::unloadSeconds).sum();
    double totalStuck = sequences.stream().mapToDouble(UnloadSequence::stuckCount).sum();

    double avgScorePerMatch = safeDivide(totalScores, matchCount);
    double avgHitRate = safeDivide(totalScores, totalShots) * 100.0;
    double avgUnloadSeconds = safeDivide(totalUnloadSeconds, sequences.size());
    double shotsPerSecond = safeDivide(totalShots, totalUnloadSeconds);
    double scoresPerSecond = safeDivide(totalScores, totalUnloadSeconds);
    double avgStuck = safeDivide(totalStuck, sequences.size());

    // Per-match data
    var byMatch =
        sequences.stream()
            .collect(
                Collectors.groupingBy(
                    s -> s.level() + ":" + s.matchId(), LinkedHashMap::new, Collectors.toList()));

    var perMatch = new ArrayList<MatchShootingData>();
    for (var entry : byMatch.entrySet()) {
      var matchSeqs = entry.getValue();
      var first = matchSeqs.get(0);
      int matchScores = matchSeqs.stream().mapToInt(UnloadSequence::scoreCount).sum();
      int matchShots =
          matchSeqs.stream().mapToInt(s -> s.scoreCount() + s.missCount()).sum();
      perMatch.add(
          new MatchShootingData(
              first.matchId(),
              first.level(),
              matchSeqs.size(),
              matchScores,
              matchShots,
              round2(safeDivide(matchScores, matchShots) * 100.0)));
    }

    return new ShootingStats(
        position,
        sequences.size(),
        perMatch,
        round2(avgScorePerMatch),
        round2(avgHitRate),
        round2(avgUnloadSeconds),
        round2(shotsPerSecond),
        round2(scoresPerSecond),
        round2(avgStuck),
        stuckComments,
        generalComments);
  }

  // ── SWI Section ───────────────────────────────────────────────────────────

  private SwiSection buildSwiSection(
      Map<String, List<EventLogRecord>> eventsByMatch, int matchCount) {

    var swiSequences = new ArrayList<SwiSequenceData>();
    var stuckComments = new ArrayList<PmvaReport.MatchComment>();
    var generalComments = new ArrayList<PmvaReport.MatchComment>();
    var positionComments = new ArrayList<PmvaReport.MatchComment>();

    for (var matchEvents : eventsByMatch.values()) {
      boolean inSequence = false;
      int scoreCount = 0;
      int missCount = 0;
      double stuckCount = 0;
      int currentMatchId = 0;
      String currentLevel = "";

      for (var event : matchEvents) {
        switch (event.eventType()) {
          case "pmva-swi-start" -> {
            if (inSequence) {
              log.debug("Discarding unclosed SWI sequence in match {} {}", currentLevel, currentMatchId);
            }
            inSequence = true;
            scoreCount = 0;
            missCount = 0;
            stuckCount = 0;
            currentMatchId = event.matchId();
            currentLevel = event.level().name();
          }
          case "pmva-swi-score-one" -> {
            if (inSequence) scoreCount++;
          }
          case "pmva-swi-miss-one" -> {
            if (inSequence) missCount++;
          }
          case "pmva-swi-stuck-count" -> {
            if (inSequence) stuckCount += event.amount();
          }
          case "pmva-swi-stuck-comments" -> {
            if (hasNote(event)) stuckComments.add(toComment(event));
          }
          case "pmva-swi-general-comments" -> {
            if (hasNote(event)) generalComments.add(toComment(event));
          }
          case "pmva-swi-position-comments" -> {
            if (hasNote(event)) positionComments.add(toComment(event));
          }
          case "pmva-swi-duration-seconds" -> {
            if (inSequence) {
              swiSequences.add(
                  new SwiSequenceData(
                      currentMatchId,
                      currentLevel,
                      scoreCount,
                      missCount,
                      stuckCount,
                      event.amount()));
              inSequence = false;
            }
          }
          default -> {
            // skip
          }
        }
      }
      if (inSequence) {
        log.debug("Discarding unclosed SWI sequence at end of match {} {}", currentLevel, currentMatchId);
      }
    }

    if (swiSequences.isEmpty()) {
      return new SwiSection(
          0, 0, 0, 0, 0, List.of(), stuckComments, generalComments, positionComments);
    }

    int totalSequences = swiSequences.size();
    int totalScores = swiSequences.stream().mapToInt(SwiSequenceData::scoreCount).sum();
    int totalShots =
        swiSequences.stream().mapToInt(s -> s.scoreCount() + s.missCount()).sum();
    double totalStuck = swiSequences.stream().mapToDouble(SwiSequenceData::stuckCount).sum();
    double totalDuration = swiSequences.stream().mapToDouble(SwiSequenceData::durationSeconds).sum();

    double avgSeqPerMatch = safeDivide(totalSequences, matchCount);
    double avgScoresPerSeq = safeDivide(totalScores, totalSequences);
    double avgScorePctPerSeq = safeDivide(totalScores, totalShots) * 100.0;
    double avgStuckPerSeq = safeDivide(totalStuck, totalSequences);
    double avgDuration = safeDivide(totalDuration, totalSequences);

    // Per-match data
    var byMatch =
        swiSequences.stream()
            .collect(
                Collectors.groupingBy(
                    s -> s.level() + ":" + s.matchId(), LinkedHashMap::new, Collectors.toList()));

    var perMatch = new ArrayList<MatchSwiData>();
    for (var entry : byMatch.entrySet()) {
      var matchSeqs = entry.getValue();
      var first = matchSeqs.get(0);
      int mScores = matchSeqs.stream().mapToInt(SwiSequenceData::scoreCount).sum();
      int mMisses = matchSeqs.stream().mapToInt(SwiSequenceData::missCount).sum();
      int mTotal = mScores + mMisses;
      double mDuration =
          matchSeqs.stream().mapToDouble(SwiSequenceData::durationSeconds).sum();
      perMatch.add(
          new MatchSwiData(
              first.matchId(),
              first.level(),
              matchSeqs.size(),
              mScores,
              mMisses,
              round2(safeDivide(mScores, mTotal) * 100.0),
              round2(safeDivide(mDuration, matchSeqs.size()))));
    }

    return new SwiSection(
        round2(avgSeqPerMatch),
        round2(avgScoresPerSeq),
        round2(avgScorePctPerSeq),
        round2(avgStuckPerSeq),
        round2(avgDuration),
        perMatch,
        stuckComments,
        generalComments,
        positionComments);
  }

  private record SwiSequenceData(
      int matchId,
      String level,
      int scoreCount,
      int missCount,
      double stuckCount,
      double durationSeconds) {}

  // ── Utilities ─────────────────────────────────────────────────────────────

  private static boolean hasNote(EventLogRecord event) {
    return event.note() != null && !event.note().isBlank();
  }

  private static PmvaReport.MatchComment toComment(EventLogRecord event) {
    return new PmvaReport.MatchComment(event.matchId(), event.level().name(), event.note());
  }

  private static double safeDivide(double numerator, double denominator) {
    if (denominator == 0) return 0.0;
    return numerator / denominator;
  }

  private static double average(List<Double> values) {
    if (values.isEmpty()) return 0.0;
    return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
  }

  private static double round2(double value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }
}
