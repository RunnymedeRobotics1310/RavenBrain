package ca.team1310.ravenbrain.matchvideo;

import ca.team1310.ravenbrain.schedule.ScheduleRecord;
import ca.team1310.ravenbrain.schedule.ScheduleService;
import ca.team1310.ravenbrain.tbaapi.service.TbaMatchVideoRecord;
import ca.team1310.ravenbrain.tbaapi.service.TbaMatchVideoRepo;
import ca.team1310.ravenbrain.tbaapi.service.WebcastUrlReconstructor;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Merges admin-owned {@link MatchVideoRecord} rows with TBA-sourced {@link TbaMatchVideoRecord}
 * rows at read time. Match identity is the canonicalized sorted red+blue alliance tuple from
 * {@code RB_SCHEDULE} — drift-proof across qualification rescheduling and every playoff bracket
 * format. One batched DB lookup per call regardless of match count.
 *
 * <p>Merge rules:
 *
 * <ul>
 *   <li>Admin rows come first within each {@code (tournamentId, matchLevel, matchNumber)} group so
 *       admin-curated ordering wins.
 *   <li>If the same canonicalized URL appears as both admin and TBA, the admin entry wins (its
 *       meaningful label is preserved; the TBA duplicate is dropped).
 *   <li>Collision (multiple TBA matches for the same alliance tuple at a single event — rare,
 *       driven by surrogate/replay matches) is resolved by preferring the TBA match whose
 *       {@code tba_match_number} is closest to the schedule row's match number.
 *   <li>Staleness is status-only per R10: {@code stale = (tbaRow.last_status != 200)}. Time-based
 *       staleness does not apply — TBA volunteers upload videos days or weeks after an event.
 * </ul>
 */
@Singleton
public class MatchVideoEnricher {

  private final MatchVideoService matchVideoService;
  private final ScheduleService scheduleService;
  private final TournamentService tournamentService;
  private final TbaMatchVideoRepo tbaMatchVideoRepo;

  MatchVideoEnricher(
      MatchVideoService matchVideoService,
      ScheduleService scheduleService,
      TournamentService tournamentService,
      TbaMatchVideoRepo tbaMatchVideoRepo) {
    this.matchVideoService = matchVideoService;
    this.scheduleService = scheduleService;
    this.tournamentService = tournamentService;
    this.tbaMatchVideoRepo = tbaMatchVideoRepo;
  }

  /** Enrich every match-video entry for one tournament — admin + TBA, merged. */
  public List<MatchVideoResponse> enrich(String tournamentId) {
    List<MatchVideoRecord> adminRows = matchVideoService.findAllByTournamentId(tournamentId);
    Optional<TournamentRecord> tournament = tournamentService.findById(tournamentId);
    String tbaEventKey =
        tournament
            .map(TournamentRecord::tbaEventKey)
            .filter(k -> k != null && !k.isBlank())
            .orElse(null);
    List<TbaMatchVideoRecord> tbaRows =
        tbaEventKey == null ? List.of() : tbaMatchVideoRepo.findByTbaEventKey(tbaEventKey);
    List<ScheduleRecord> scheduleRows =
        scheduleService.findAllByTournamentIdOrderByMatch(tournamentId);

    // TBA lookup keyed by "redCSV|blueCSV". Symmetric entry under "blueCSV|redCSV" so a
    // red/blue-swap in TBA's payload still joins — in practice alliances retain their color
    // through a match, but the symmetry is cheap insurance against a rare ordering oddity.
    Map<String, List<TbaMatchVideoRecord>> tbaByTuple = new HashMap<>();
    for (TbaMatchVideoRecord t : tbaRows) {
      String primary = t.redTeams() + "|" + t.blueTeams();
      tbaByTuple.computeIfAbsent(primary, k -> new ArrayList<>()).add(t);
      String swapped = t.blueTeams() + "|" + t.redTeams();
      if (!swapped.equals(primary)) {
        tbaByTuple.computeIfAbsent(swapped, k -> new ArrayList<>()).add(t);
      }
    }

    Map<String, List<MatchVideoRecord>> adminByKey = new LinkedHashMap<>();
    for (MatchVideoRecord a : adminRows) {
      String k = a.matchLevel() + ":" + a.matchNumber();
      adminByKey.computeIfAbsent(k, kk -> new ArrayList<>()).add(a);
    }

    List<MatchVideoResponse> out = new ArrayList<>();
    Set<String> seenKeys = new LinkedHashSet<>();

    // Iterate schedule rows first: their alliance composition is the only way TBA rows enter.
    for (ScheduleRecord s : scheduleRows) {
      String key = s.level().name() + ":" + s.match();
      if (!seenKeys.add(key)) continue;
      emit(
          tournamentId,
          s.level().name(),
          s.match(),
          s,
          adminByKey.get(key),
          tbaByTuple,
          out);
    }
    // Admin rows whose (level, match) is not in RB_SCHEDULE still surface — preserves the prior
    // behaviour of GET /api/match-video/{tournamentId}.
    for (Map.Entry<String, List<MatchVideoRecord>> e : adminByKey.entrySet()) {
      if (seenKeys.contains(e.getKey())) continue;
      MatchVideoRecord first = e.getValue().get(0);
      emit(
          tournamentId,
          first.matchLevel(),
          first.matchNumber(),
          null,
          e.getValue(),
          tbaByTuple,
          out);
    }
    return out;
  }

  /** Enrich just the videos for a single (level, match). Same algorithm, filtered output. */
  public List<MatchVideoResponse> enrich(String tournamentId, String matchLevel, int matchNumber) {
    return enrich(tournamentId).stream()
        .filter(r -> r.matchLevel().equals(matchLevel) && r.matchNumber() == matchNumber)
        .toList();
  }

  private static void emit(
      String tournamentId,
      String matchLevel,
      int matchNumber,
      ScheduleRecord schedule,
      List<MatchVideoRecord> adminRows,
      Map<String, List<TbaMatchVideoRecord>> tbaByTuple,
      List<MatchVideoResponse> out) {
    // Admin entries first, so if the admin added a curated label for a URL that TBA also lists,
    // the admin entry keeps its label (step below dedups TBA matches against admin canonical set).
    Set<String> adminCanonical = new LinkedHashSet<>();
    if (adminRows != null) {
      for (MatchVideoRecord a : adminRows) {
        String canon = WebcastUrlReconstructor.canonicalize(a.videoUrl());
        if (canon != null) adminCanonical.add(canon);
        out.add(
            new MatchVideoResponse(
                a.id(),
                tournamentId,
                matchLevel,
                matchNumber,
                a.label(),
                a.videoUrl(),
                "manual",
                false));
      }
    }

    if (schedule == null) return;
    String tuple = canonicalAllianceTuple(schedule);
    if (tuple == null) return;
    List<TbaMatchVideoRecord> candidates = tbaByTuple.get(tuple);
    if (candidates == null || candidates.isEmpty()) return;

    TbaMatchVideoRecord chosen =
        candidates.size() == 1
            ? candidates.get(0)
            : candidates.stream()
                .min(
                    Comparator.comparingInt(
                        c -> Math.abs(c.tbaMatchNumber() - schedule.match())))
                .orElseThrow();

    boolean stale = chosen.lastStatus() == null || chosen.lastStatus() != 200;
    for (String raw : parseJsonStringArray(chosen.videosJson())) {
      String canon = WebcastUrlReconstructor.canonicalize(raw);
      if (canon == null || canon.isBlank()) continue;
      if (adminCanonical.contains(canon)) continue;
      out.add(
          new MatchVideoResponse(
              null, tournamentId, matchLevel, matchNumber, "TBA", canon, "tba", stale));
    }
  }

  /** Build canonical sorted "redCSV|blueCSV" tuple from a schedule row, skipping zero slots. */
  private static String canonicalAllianceTuple(ScheduleRecord s) {
    String red = csvOfNonZero(s.red1(), s.red2(), s.red3(), s.red4());
    String blue = csvOfNonZero(s.blue1(), s.blue2(), s.blue3(), s.blue4());
    if (red.isEmpty() || blue.isEmpty()) return null;
    return red + "|" + blue;
  }

  private static String csvOfNonZero(int... teams) {
    return Arrays.stream(teams)
        .filter(n -> n > 0)
        .sorted()
        .mapToObj(String::valueOf)
        .collect(Collectors.joining(","));
  }

  /**
   * Minimal JSON-array-of-strings parser matching {@code TournamentEnricher.parseWebcasts}'s
   * philosophy — avoids pulling a full ObjectMapper into the read path for a shape we produce
   * ourselves.
   */
  private static List<String> parseJsonStringArray(String json) {
    if (json == null || json.isBlank()) return List.of();
    String raw = json.trim();
    if (!raw.startsWith("[") || !raw.endsWith("]") || raw.length() < 2) return List.of();
    String inner = raw.substring(1, raw.length() - 1).trim();
    if (inner.isEmpty()) return List.of();
    List<String> out = new ArrayList<>();
    for (String part : inner.split(",")) {
      String cleaned = part.trim();
      if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
        cleaned = cleaned.substring(1, cleaned.length() - 1);
      }
      if (!cleaned.isEmpty()) out.add(cleaned);
    }
    return out;
  }
}
