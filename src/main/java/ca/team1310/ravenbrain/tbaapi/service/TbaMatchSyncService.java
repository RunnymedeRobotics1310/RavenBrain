package ca.team1310.ravenbrain.tbaapi.service;

import ca.team1310.ravenbrain.tbaapi.fetch.TbaClientException;
import ca.team1310.ravenbrain.tbaapi.model.TbaMatch;
import ca.team1310.ravenbrain.tbaapi.model.TbaMatchAlliance;
import ca.team1310.ravenbrain.tournament.TeamTournamentService;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import ca.team1310.ravenbrain.tournament.TournamentWindow;
import ca.team1310.ravenbrain.tournament.WatchedTournamentService;
import io.micronaut.context.annotation.Property;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the write side of {@code RB_TBA_MATCH_VIDEO}. Periodically pulls TBA match data for every
 * <b>watched / owner-team active</b> tournament that has a {@code tba_event_key} set, reconstructs
 * video URLs, pre-computes canonical alliance tuples for read-time joins, and records the HTTP
 * status of each attempt.
 *
 * <p>Scope asymmetry vs. {@link TbaEventSyncService} is intentional: event sync covers every
 * tournament with a key (webcasts are low-cost, forever-useful TV-guide reference), while match
 * sync narrows to the active/watched set because per-event match payloads are ~80-100 rows and
 * past-event match videos are low-value for an active strat team.
 *
 * <p>This service <b>never</b> writes to {@code RB_TOURNAMENT} or {@code RB_MATCH_VIDEO}. The
 * ownership contract is: TBA sync owns {@code RB_TBA_MATCH_VIDEO}; admin CRUD owns
 * {@code RB_MATCH_VIDEO}; the two paths have disjoint write surfaces.
 *
 * <p>On a failed fetch (non-200 status, parse error, transport exception) previously-synced rows
 * for the event keep their {@code videos_json} and just get {@code last_status} bumped so the read
 * layer surfaces staleness.
 */
@Slf4j
@Singleton
public class TbaMatchSyncService {

  /**
   * Mirrors {@code TournamentApi.TBA_EVENT_KEY_REGEX}. Defensive-only — admin mapping is already
   * validated on write; this guard catches anything that snuck in before the constraint existed.
   */
  private static final Pattern TBA_EVENT_KEY_PATTERN = Pattern.compile("^20\\d{2}[a-z][a-z0-9]{1,15}$");

  /** TBA never returns Practice, but filter defensively if it ever starts. */
  private static final String PRACTICE_COMP_LEVEL = "pr";

  private final TbaClientService tbaClientService;
  private final TournamentService tournamentService;
  private final TournamentWindow tournamentWindow;
  private final TeamTournamentService teamTournamentService;
  private final WatchedTournamentService watchedTournamentService;
  private final TbaMatchVideoRepo tbaMatchVideoRepo;
  private final int teamNumber;

  TbaMatchSyncService(
      TbaClientService tbaClientService,
      TournamentService tournamentService,
      TournamentWindow tournamentWindow,
      TeamTournamentService teamTournamentService,
      WatchedTournamentService watchedTournamentService,
      TbaMatchVideoRepo tbaMatchVideoRepo,
      @Property(name = "raven-eye.team") int teamNumber) {
    this.tbaClientService = tbaClientService;
    this.tournamentService = tournamentService;
    this.tournamentWindow = tournamentWindow;
    this.teamTournamentService = teamTournamentService;
    this.watchedTournamentService = watchedTournamentService;
    this.tbaMatchVideoRepo = tbaMatchVideoRepo;
    this.teamNumber = teamNumber;
  }

  /**
   * Scheduled sync: every hour, refresh TBA match data for every watched/active tournament that
   * has a {@code tba_event_key}.
   */
  @Scheduled(fixedDelay = "${raven-eye.sync.tba-match-poll}")
  public void syncAllActiveTournaments() {
    List<TournamentRecord> targets = getActiveTournamentsToSync();
    if (targets.isEmpty()) {
      log.debug("TBA match sync idle: no watched/active tournaments with tba_event_key");
      return;
    }
    log.info("TBA match sync: refreshing {} tournament(s)", targets.size());
    int tournamentsSynced = 0;
    int rowsWritten = 0;
    int failures = 0;
    for (TournamentRecord t : targets) {
      try {
        int written = syncOne(t.tbaEventKey());
        if (written >= 0) {
          tournamentsSynced++;
          rowsWritten += written;
        } else {
          failures++;
        }
      } catch (Exception e) {
        log.error(
            "TBA match sync unexpected failure for event {}: {}",
            t.tbaEventKey(),
            e.getMessage(),
            e);
        failures++;
      }
    }
    log.info(
        "TBA match sync complete: {} tournament(s), {} match row(s) written, {} failure(s)",
        tournamentsSynced,
        rowsWritten,
        failures);
  }

  /**
   * Duplicated inline from {@code EventSyncService.getActiveTournamentsToSync()} because that
   * method is package-private in a different package. Match sync further narrows to tournaments
   * with a non-blank {@code tbaEventKey} that also passes the regex guard.
   */
  private List<TournamentRecord> getActiveTournamentsToSync() {
    Set<String> ownerIds =
        Set.copyOf(teamTournamentService.findTournamentIdsForTeam(teamNumber));
    Set<String> watchedIds = watchedTournamentService.getWatchedTournamentIds();
    return tournamentWindow.findUpcomingAndActive().stream()
        .filter(t -> ownerIds.contains(t.id()) || watchedIds.contains(t.id()))
        .filter(t -> t.tbaEventKey() != null && !t.tbaEventKey().isBlank())
        .filter(
            t -> {
              boolean ok = TBA_EVENT_KEY_PATTERN.matcher(t.tbaEventKey()).matches();
              if (!ok) {
                log.warn(
                    "Skipping TBA match sync for {}: tba_event_key '{}' failed format guard",
                    t.id(),
                    t.tbaEventKey());
              }
              return ok;
            })
        .toList();
  }

  /**
   * Sync a single TBA event's match list. Returns the number of rows persisted on success, or
   * {@code -1} on failure (status recorded on any previously-synced rows for observability).
   *
   * <p>Package-private so tests can drive a single sync without the scheduler.
   */
  int syncOne(String tbaEventKey) {
    Objects.requireNonNull(tbaEventKey, "tbaEventKey");
    try {
      TbaClientService.EventMatchesFetch fetch = tbaClientService.getEventMatches(tbaEventKey);
      if (fetch.result() != null) {
        return persistMatches(tbaEventKey, fetch.result().matches());
      }
      int status =
          switch (fetch.outcome()) {
            case NOT_FOUND -> 404;
            case ALREADY_PROCESSED -> 200;
            case EMPTY_BODY -> -1;
          };
      if (status == 200) {
        // Already processed — the previous sync already wrote rows, nothing to do. Don't bump
        // last_status because that would re-mark healthy rows as fresh-without-reason.
        log.debug("TBA match sync skipped for {}: already processed in this cycle", tbaEventKey);
        return 0;
      }
      bumpStatusOnExistingRows(tbaEventKey, status);
      return -1;
    } catch (TbaClientException e) {
      log.warn("TBA match fetch failed for {}: {}", tbaEventKey, e.getMessage());
      bumpStatusOnExistingRows(tbaEventKey, -1);
      return -1;
    } catch (TbaClientServiceException e) {
      log.warn("TBA match parse failed for {}: {}", tbaEventKey, e.getMessage());
      bumpStatusOnExistingRows(tbaEventKey, -2);
      return -1;
    }
  }

  private int persistMatches(String tbaEventKey, List<TbaMatch> matches) {
    if (matches == null || matches.isEmpty()) {
      log.debug("TBA returned empty match list for {}", tbaEventKey);
      return 0;
    }
    int written = 0;
    for (TbaMatch match : matches) {
      if (match == null || match.key() == null || match.compLevel() == null) {
        log.warn("Skipping TBA match with missing key or comp_level in event {}", tbaEventKey);
        continue;
      }
      if (PRACTICE_COMP_LEVEL.equalsIgnoreCase(match.compLevel())) {
        continue;
      }
      String redTeams = canonicalTeamTuple(match, Side.RED, tbaEventKey);
      String blueTeams = canonicalTeamTuple(match, Side.BLUE, tbaEventKey);
      if (redTeams == null || blueTeams == null) {
        log.warn(
            "Skipping TBA match {} in event {}: could not parse alliance team keys",
            match.key(),
            tbaEventKey);
        continue;
      }
      List<String> videos =
          WebcastUrlReconstructor.reconstructAndDedupMatchVideos(match.videos(), match.key());
      var record =
          new TbaMatchVideoRecord(
              match.key(),
              tbaEventKey,
              match.compLevel(),
              match.matchNumber(),
              redTeams,
              blueTeams,
              toJsonArray(videos),
              Instant.now(),
              200);
      upsert(record);
      written++;
    }
    log.debug("TBA match sync OK for {}: {} row(s) persisted", tbaEventKey, written);
    return written;
  }

  /**
   * Bump {@code last_status} on every row belonging to this event so the UI surfaces the
   * staleness. Preserves {@code videos_json}, {@code last_sync}, and the alliance tuples so the
   * last successful data keeps flowing through the read path.
   */
  private void bumpStatusOnExistingRows(String tbaEventKey, int status) {
    List<TbaMatchVideoRecord> existing = tbaMatchVideoRepo.findByTbaEventKey(tbaEventKey);
    if (existing.isEmpty()) {
      log.debug("TBA match sync failed for {} with no previously-synced rows to mark", tbaEventKey);
      return;
    }
    List<TbaMatchVideoRecord> updated = new ArrayList<>(existing.size());
    for (TbaMatchVideoRecord row : existing) {
      updated.add(
          new TbaMatchVideoRecord(
              row.tbaMatchKey(),
              row.tbaEventKey(),
              row.compLevel(),
              row.tbaMatchNumber(),
              row.redTeams(),
              row.blueTeams(),
              row.videosJson(),
              row.lastSync(),
              status));
    }
    tbaMatchVideoRepo.updateAll(updated);
  }

  private void upsert(TbaMatchVideoRecord record) {
    if (tbaMatchVideoRepo.existsById(record.tbaMatchKey())) {
      tbaMatchVideoRepo.update(record);
    } else {
      tbaMatchVideoRepo.save(record);
    }
  }

  private enum Side {
    RED,
    BLUE
  }

  private static String canonicalTeamTuple(TbaMatch match, Side side, String tbaEventKey) {
    if (match.alliances() == null) return null;
    TbaMatchAlliance alliance =
        side == Side.RED ? match.alliances().red() : match.alliances().blue();
    if (alliance == null || alliance.teamKeys() == null || alliance.teamKeys().isEmpty()) {
      return null;
    }
    List<Integer> numbers = new ArrayList<>(alliance.teamKeys().size());
    for (String teamKey : alliance.teamKeys()) {
      Integer parsed = parseTeamNumber(teamKey);
      if (parsed == null) {
        log.warn(
            "Invalid team key '{}' on match {} in event {}", teamKey, match.key(), tbaEventKey);
        return null;
      }
      numbers.add(parsed);
    }
    return numbers.stream()
        .sorted()
        .map(String::valueOf)
        .reduce((a, b) -> a + "," + b)
        .orElse("");
  }

  private static Integer parseTeamNumber(String teamKey) {
    if (teamKey == null || !teamKey.startsWith("frc")) return null;
    try {
      return Integer.parseInt(teamKey.substring(3));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String toJsonArray(List<String> urls) {
    if (urls == null || urls.isEmpty()) return "[]";
    StringBuilder sb = new StringBuilder(urls.size() * 40);
    sb.append('[');
    for (int i = 0; i < urls.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append('"').append(urls.get(i).replace("\"", "\\\"")).append('"');
    }
    sb.append(']');
    return sb.toString();
  }
}
