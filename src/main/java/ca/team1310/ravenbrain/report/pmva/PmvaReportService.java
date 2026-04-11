package ca.team1310.ravenbrain.report.pmva;

import ca.team1310.ravenbrain.eventlog.EventLogRecord;
import ca.team1310.ravenbrain.eventlog.EventLogRepository;
import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import ca.team1310.ravenbrain.report.pmva.PmvaReport.*;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates the PMVA (Post Match Video Analysis) report for the owner team. Supports both
 * single-tournament reports (see {@link #generate(String)}) and season-wide aggregation (see
 * {@link #generateForSeason(int)}). Season aggregation runs the same pipeline but keys matches by
 * a composite {@code tournamentId:level:matchId} so matches from different tournaments with the
 * same number don't collide.
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
  private final TournamentService tournamentService;
  private final int teamNumber;

  public PmvaReportService(
      EventLogRepository eventLogRepository,
      TournamentService tournamentService,
      @Property(name = "raven-eye.team") int teamNumber) {
    this.eventLogRepository = eventLogRepository;
    this.tournamentService = tournamentService;
    this.teamNumber = teamNumber;
  }

  public int getOwnerTeamNumber() {
    return teamNumber;
  }

  /** List tournaments that have PMVA data for the owner team. */
  public List<String> listTournamentsWithData() {
    return eventLogRepository.findDistinctPmvaTournamentIds(teamNumber);
  }

  /**
   * Resolve the most recent season that has PMVA data for the owner team, falling back to the
   * current calendar year if none exist.
   */
  public int getCurrentSeason() {
    return listTournamentsWithData().stream()
        .map(this::findTournamentOrNull)
        .filter(Objects::nonNull)
        .mapToInt(TournamentRecord::season)
        .max()
        .orElseGet(() -> Instant.now().atZone(ZoneOffset.UTC).getYear());
  }

  /** Generate a PMVA report for a single tournament. */
  public PmvaReport generate(String tournamentId) {
    var allEvents =
        eventLogRepository.findAllByTeamNumberAndTournamentIdOrderByTimestampAsc(
            teamNumber, tournamentId);
    return buildReport(allEvents);
  }

  /**
   * Generate a PMVA report aggregating all tournaments in a given season. Events from every
   * tournament in the season are fed through the same pipeline, keyed by {@code
   * tournamentId:level:matchId} so per-tournament matches remain distinct.
   */
  public PmvaReport generateForSeason(int season) {
    var tournamentsInSeason =
        listTournamentsWithData().stream()
            .map(this::findTournamentOrNull)
            .filter(Objects::nonNull)
            .filter(t -> t.season() == season)
            .sorted(Comparator.comparing(TournamentRecord::startTime))
            .toList();

    if (tournamentsInSeason.isEmpty()) {
      return emptyReport();
    }

    var allEvents = new ArrayList<EventLogRecord>();
    for (var t : tournamentsInSeason) {
      allEvents.addAll(
          eventLogRepository.findAllByTeamNumberAndTournamentIdOrderByTimestampAsc(
              teamNumber, t.id()));
    }
    return buildReport(allEvents);
  }

  // ── Core pipeline ────────────────────────────────────────────────────────

  /**
   * Build a PMVA report from a pre-fetched event list. Shared by the single-tournament and
   * season-wide entry points. The match grouping key is always {@code
   * tournamentId:level:matchId}, so this works whether the events come from one tournament or
   * many.
   */
  private PmvaReport buildReport(List<EventLogRecord> allEvents) {
    var pmvaEvents =
        allEvents.stream()
            .filter(e -> MATCH_LEVELS.contains(e.level()))
            .filter(e -> e.eventType().startsWith("pmva-"))
            .toList();

    var matchKeys =
        pmvaEvents.stream().map(PmvaReportService::matchKey).collect(
            Collectors.toCollection(LinkedHashSet::new));
    int matchCount = matchKeys.size();

    if (matchCount == 0) {
      return emptyReport();
    }

    var eventsByMatch = new LinkedHashMap<String, List<EventLogRecord>>();
    for (var event : pmvaEvents) {
      eventsByMatch.computeIfAbsent(matchKey(event), k -> new ArrayList<>()).add(event);
    }

    var general = buildGeneralSection(pmvaEvents, matchCount);
    var shooting = buildShootingSection(eventsByMatch, matchCount);

    return new PmvaReport(teamNumber, matchCount, general, shooting);
  }

  private static String matchKey(EventLogRecord e) {
    return e.tournamentId() + ":" + e.level().name() + ":" + e.matchId();
  }

  private PmvaReport emptyReport() {
    return new PmvaReport(
        teamNumber,
        0,
        new GeneralSection(
            0, 0, 0.0, List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
        new ShootingSection(
            new LoadingStats(0, 0, 0, List.of(), List.of()),
            null, null, null, null, null, null,
            emptySwiSummary()));
  }

  private static SwiSummary emptySwiSummary() {
    return new SwiSummary(0, 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of());
  }

  // ── General Section ──────────────────────────────────────────────────────

  private GeneralSection buildGeneralSection(List<EventLogRecord> allEvents, int matchCount) {

    int breakdownCount = 0;
    int noBreakdownCount = 0;
    var breakdownMatches = new ArrayList<MatchBreakdown>();
    var breakdownNotes = new ArrayList<PmvaReport.MatchComment>();
    var intakeComments = new ArrayList<PmvaReport.MatchComment>();
    var shooterComments = new ArrayList<PmvaReport.MatchComment>();
    var generalComments = new ArrayList<PmvaReport.MatchComment>();
    var suggestions = new ArrayList<PmvaReport.MatchComment>();

    // Video link per (tournament, match) for cross-referencing.
    var videoLinksByMatch = new HashMap<String, String>();
    for (var event : allEvents) {
      if ("pmva-video-link".equals(event.eventType()) && hasNote(event)) {
        videoLinksByMatch.put(matchKey(event), event.note());
      }
    }

    for (var event : allEvents) {
      switch (event.eventType()) {
        case "pmva-breakdown" -> {
          breakdownCount++;
          breakdownMatches.add(
              new MatchBreakdown(
                  event.tournamentId(),
                  event.matchId(),
                  event.level().name(),
                  hasNote(event) ? event.note() : "",
                  videoLinksByMatch.get(matchKey(event))));
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

  // ── Shooting Section ─────────────────────────────────────────────────────

  /** Internal representation of a detected hopper sequence (load + shoot cycle). */
  private record HopperSequence(
      String tournamentId,
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

  private ShootingSection buildShootingSection(
      Map<String, List<EventLogRecord>> eventsByMatch, int matchCount) {
    var allSequences = extractHopperSequences(eventsByMatch);
    var loading = buildLoadingStats(allSequences, eventsByMatch);

    var shootingAll = buildShootingView("all", allSequences, matchCount);

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

    var swi = buildSwiSummary(eventsByMatch, matchCount);

    return new ShootingSection(
        loading,
        shootingAll,
        shootingClose,
        shootingMid,
        shootingFar,
        shootingMoving,
        shootingIntaking,
        swi);
  }

  private static List<HopperSequence> filterBy(
      List<HopperSequence> sequences, java.util.function.Predicate<HopperSequence> predicate) {
    return sequences.stream().filter(predicate).toList();
  }

  private List<HopperSequence> extractHopperSequences(
      Map<String, List<EventLogRecord>> eventsByMatch) {
    var sequences = new ArrayList<HopperSequence>();

    for (var entry : eventsByMatch.entrySet()) {
      // Per-match sequence index resets at the start of each match.
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
      String currentTournamentId = "";

      for (var event : entry.getValue()) {
        currentMatchId = event.matchId();
        currentLevel = event.level().name();
        currentTournamentId = event.tournamentId();

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
                    currentTournamentId, currentMatchId, currentLevel, sequenceIndex,
                    loadAmount, hopperFull, shots, scores, misses, stuck,
                    unloadSeconds, position, moving, intaking));
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
      return new LoadingStats(0, 0, 0, List.of(), List.of());
    }

    double avgFillCount =
        allSequences.stream().mapToInt(HopperSequence::loadAmount).average().orElse(0);

    int hopperFullCount = (int) allSequences.stream().filter(HopperSequence::hopperFull).count();
    double hopperFilledPct = safeDivide(hopperFullCount, allSequences.size()) * 100.0;

    var nonIntaking = allSequences.stream().filter(s -> !s.intaking()).toList();
    double maxFillExcluding =
        nonIntaking.stream().mapToInt(HopperSequence::loadAmount).max().orElse(0);

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

    // Group sequences by (tournamentId, level, matchId) for per-match cycle stats.
    var byMatch =
        sequences.stream()
            .collect(
                Collectors.groupingBy(
                    s -> s.tournamentId() + ":" + s.level() + ":" + s.matchId(),
                    LinkedHashMap::new,
                    Collectors.toList()));

    var matchCycles = new ArrayList<MatchCycleData>();
    for (var entry : byMatch.entrySet()) {
      var matchSeqs = entry.getValue();
      var first = matchSeqs.get(0);
      double avgLoad =
          matchSeqs.stream().mapToInt(HopperSequence::loadAmount).average().orElse(0);
      matchCycles.add(
          new MatchCycleData(
              first.tournamentId(),
              first.matchId(),
              first.level(),
              matchSeqs.size(),
              matchSeqs.stream().mapToInt(HopperSequence::shots).sum(),
              matchSeqs.stream().mapToInt(HopperSequence::scores).sum(),
              matchSeqs.stream().mapToInt(HopperSequence::misses).sum(),
              matchSeqs.stream().mapToInt(HopperSequence::stuck).sum(),
              round2(avgLoad)));
    }

    var sequenceShots = new ArrayList<SequenceShotData>();
    for (var s : sequences) {
      sequenceShots.add(
          new SequenceShotData(
              s.tournamentId(),
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

  // ── SWI Summary ──────────────────────────────────────────────────────────

  private SwiSummary buildSwiSummary(
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
      String currentTournamentId = "";

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
            currentTournamentId = event.tournamentId();
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
                      currentTournamentId,
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
      return new SwiSummary(
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

    var byMatch =
        swiSequences.stream()
            .collect(
                Collectors.groupingBy(
                    s -> s.tournamentId() + ":" + s.level() + ":" + s.matchId(),
                    LinkedHashMap::new,
                    Collectors.toList()));

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
              first.tournamentId(),
              first.matchId(),
              first.level(),
              matchSeqs.size(),
              mScores,
              mMisses,
              round2(safeDivide(mScores, mTotal) * 100.0),
              round2(safeDivide(mDuration, matchSeqs.size()))));
    }

    return new SwiSummary(
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
      String tournamentId,
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
    return new PmvaReport.MatchComment(
        event.tournamentId(), event.matchId(), event.level().name(), event.note());
  }

  private static double safeDivide(double numerator, double denominator) {
    if (denominator == 0) return 0.0;
    return numerator / denominator;
  }

  private static double round2(double value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }

  private TournamentRecord findTournamentOrNull(String id) {
    try {
      return tournamentService.findById(id).orElse(null);
    } catch (Exception e) {
      log.debug("Tournament {} not found while computing PMVA season report", id);
      return null;
    }
  }
}
