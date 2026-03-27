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

    var general = buildGeneralSection(pmvaEvents, eventsByMatch, matchCount);
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
            new LoadingStats(0, 0, 0, 0, List.of(), List.of()),
            null, null, null, null, null, null),
        new SwiSection(0, 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of()));
  }

  // ── General Section ──────────────────────────────────────────────────────

  private GeneralSection buildGeneralSection(
      List<EventLogRecord> allEvents, Map<String, List<EventLogRecord>> eventsByMatch, int matchCount) {

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

    double breakdownPct = safeDivide(breakdownCount, matchCount) * 100.0;

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

  /** Internal representation of a detected hopper sequence (load + shoot cycle). */
  private record HopperSequence(
      int matchId,
      String level,
      int sequenceIndex,
      int loadAmount,
      boolean hopperFull,
      int shots,
      int scores,
      int misses,
      int stuck,
      double unloadSeconds,
      String position,
      boolean moving,
      boolean intaking) {}

  private HopperSection buildHopperSection(
      Map<String, List<EventLogRecord>> eventsByMatch, int matchCount) {
    var allSequences = extractHopperSequences(eventsByMatch);
    var loading = buildLoadingStats(allSequences, eventsByMatch);

    var shootingAll = buildShootingView("all", allSequences, matchCount);

    // Per-position (overlapping filters)
    var shootingClose =
        buildShootingViewOrNull("close", filterBy(allSequences, s -> "close".equals(s.position())), matchCount);
    var shootingMid =
        buildShootingViewOrNull("mid", filterBy(allSequences, s -> "mid".equals(s.position())), matchCount);
    var shootingFar =
        buildShootingViewOrNull("far", filterBy(allSequences, s -> "far".equals(s.position())), matchCount);
    var shootingMoving =
        buildShootingViewOrNull("moving", filterBy(allSequences, HopperSequence::moving), matchCount);
    var shootingIntaking =
        buildShootingViewOrNull("intaking", filterBy(allSequences, HopperSequence::intaking), matchCount);

    return new HopperSection(
        loading, shootingAll, shootingClose, shootingMid, shootingFar, shootingMoving, shootingIntaking);
  }

  private static List<HopperSequence> filterBy(
      List<HopperSequence> sequences, java.util.function.Predicate<HopperSequence> predicate) {
    return sequences.stream().filter(predicate).toList();
  }

  private List<HopperSequence> extractHopperSequences(
      Map<String, List<EventLogRecord>> eventsByMatch) {
    var sequences = new ArrayList<HopperSequence>();

    for (var entry : eventsByMatch.entrySet()) {
      int sequenceIndex = 0;
      // Accumulator state
      int loadAmount = 0;
      boolean hopperFull = false;
      int shots = 0;
      int scores = 0;
      int misses = 0;
      int stuck = 0;
      double unloadSeconds = 0;
      String position = "";
      boolean moving = false;
      boolean intaking = false;
      int currentMatchId = 0;
      String currentLevel = "";

      for (var event : entry.getValue()) {
        currentMatchId = event.matchId();
        currentLevel = event.level().name();

        switch (event.eventType()) {
          case "pmva-load" -> loadAmount = (int) event.amount();
          case "pmva-load-hopper-full" -> hopperFull = true;
          case "pmva-load-hopper-not-full" -> hopperFull = false;
          case "pmva-shoot" -> shots = (int) event.amount();
          case "pmva-shoot-score" -> scores = (int) event.amount();
          case "pmva-shoot-miss" -> misses = (int) event.amount();
          case "pmva-shoot-time" -> unloadSeconds = event.amount();
          case "pmva-shoot-stuck-in-hopper" -> stuck = (int) event.amount();
          case "pmva-shoot-close" -> position = "close";
          case "pmva-shoot-mid" -> position = "mid";
          case "pmva-shoot-far" -> position = "far";
          case "pmva-shoot-moving" -> moving = true;
          case "pmva-shoot-intaking" -> intaking = true;
          case "pmva-shoot-end" -> {
            sequenceIndex++;
            sequences.add(
                new HopperSequence(
                    currentMatchId, currentLevel, sequenceIndex,
                    loadAmount, hopperFull, shots, scores, misses, stuck,
                    unloadSeconds, position, moving, intaking));
            // Reset accumulator
            loadAmount = 0;
            hopperFull = false;
            shots = 0;
            scores = 0;
            misses = 0;
            stuck = 0;
            unloadSeconds = 0;
            position = "";
            moving = false;
            intaking = false;
          }
          default -> {
            // skip non-hopper events
          }
        }
      }
    }
    return sequences;
  }

  private LoadingStats buildLoadingStats(
      List<HopperSequence> allSequences,
      Map<String, List<EventLogRecord>> eventsByMatch) {
    if (allSequences.isEmpty()) {
      return new LoadingStats(0, 0, 0, 0, List.of(), List.of());
    }

    double avgFillCount =
        allSequences.stream().mapToInt(HopperSequence::loadAmount).average().orElse(0);

    int hopperFullCount = (int) allSequences.stream().filter(HopperSequence::hopperFull).count();
    double hopperFilledPct = safeDivide(hopperFullCount, allSequences.size()) * 100.0;

    // Exclude intaking sequences for max and rating
    var nonIntaking = allSequences.stream().filter(s -> !s.intaking()).toList();
    double maxFillExcluding =
        nonIntaking.stream().mapToInt(HopperSequence::loadAmount).max().orElse(0);
    double avgFillExcluding =
        nonIntaking.stream().mapToInt(HopperSequence::loadAmount).average().orElse(0);
    double rating = Math.min(5.0, safeDivide(avgFillExcluding, maxFillExcluding) * 5.0);

    // Collect comments from event stream
    var loadComments = new ArrayList<PmvaReport.MatchComment>();
    var shootComments = new ArrayList<PmvaReport.MatchComment>();
    for (var matchEvents : eventsByMatch.values()) {
      for (var event : matchEvents) {
        if ("pmva-load-comments".equals(event.eventType()) && hasNote(event)) {
          loadComments.add(toComment(event));
        } else if ("pmva-shoot-note".equals(event.eventType()) && hasNote(event)) {
          shootComments.add(toComment(event));
        }
      }
    }

    return new LoadingStats(
        round2(avgFillCount),
        round2(hopperFilledPct),
        round2(maxFillExcluding),
        round2(rating),
        loadComments,
        shootComments);
  }

  private ShootingView buildShootingViewOrNull(
      String filter, List<HopperSequence> sequences, int matchCount) {
    if (sequences == null || sequences.isEmpty()) return null;
    return buildShootingView(filter, sequences, matchCount);
  }

  private ShootingView buildShootingView(
      String filter, List<HopperSequence> sequences, int matchCount) {
    if (sequences.isEmpty()) {
      return new ShootingView(filter, 0, List.of(), List.of(), 0, 0);
    }

    // Per-match cycle data
    var byMatch =
        sequences.stream()
            .collect(
                Collectors.groupingBy(
                    s -> s.level() + ":" + s.matchId(), LinkedHashMap::new, Collectors.toList()));

    var matchCycles = new ArrayList<MatchCycleData>();
    for (var entry : byMatch.entrySet()) {
      var matchSeqs = entry.getValue();
      var first = matchSeqs.get(0);
      matchCycles.add(
          new MatchCycleData(
              first.matchId(),
              first.level(),
              matchSeqs.size(),
              matchSeqs.stream().mapToInt(HopperSequence::shots).sum(),
              matchSeqs.stream().mapToInt(HopperSequence::scores).sum(),
              matchSeqs.stream().mapToInt(HopperSequence::misses).sum(),
              matchSeqs.stream().mapToInt(HopperSequence::stuck).sum()));
    }

    // Per-sequence shot data
    var sequenceShots = new ArrayList<SequenceShotData>();
    for (var s : sequences) {
      sequenceShots.add(
          new SequenceShotData(
              s.matchId(),
              s.level(),
              s.sequenceIndex(),
              s.shots(),
              s.scores(),
              s.misses(),
              s.stuck(),
              round2(s.unloadSeconds()),
              round2(safeDivide(s.shots(), s.unloadSeconds())),
              round2(safeDivide(s.scores(), s.unloadSeconds()))));
    }

    // Aggregate stats
    double avgCycles = safeDivide(sequences.size(), matchCount);
    int maxCycles = matchCycles.stream().mapToInt(MatchCycleData::cycleCount).max().orElse(0);

    return new ShootingView(
        filter,
        sequences.size(),
        matchCycles,
        sequenceShots,
        round2(avgCycles),
        maxCycles);
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
