package ca.team1310.ravenbrain.statboticsapi.service;

import ca.team1310.ravenbrain.statboticsapi.fetch.StatboticsClientException;
import ca.team1310.ravenbrain.statboticsapi.model.StatboticsTeamEvent;
import ca.team1310.ravenbrain.statboticsapi.model.StatboticsTeamEventBreakdown;
import ca.team1310.ravenbrain.statboticsapi.model.StatboticsTeamEventEpa;
import ca.team1310.ravenbrain.tournament.TeamTournamentService;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import ca.team1310.ravenbrain.tournament.TournamentWindow;
import ca.team1310.ravenbrain.tournament.WatchedTournamentService;
import io.micronaut.context.annotation.Property;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the write side of {@code RB_STATBOTICS_TEAM_EVENT}. Periodically pulls Statbotics
 * per-team-per-event EPA data for every <b>watched / owner-team active</b> tournament that has a
 * {@code tba_event_key} set, extracts flat EPA + breakdown columns, and records the HTTP status of
 * each attempt.
 *
 * <p>Scope filter mirrors {@link ca.team1310.ravenbrain.tbaapi.service.TbaMatchSyncService}: only
 * tournaments in the (owner-team ∪ watched) ∩ upcoming-and-active set with a non-blank
 * {@code tbaEventKey}. The 3-line filter is duplicated inline rather than promoted because the
 * canonical {@code getActiveTournamentsToSync} lives private in the {@code frcapi} package.
 *
 * <p>On a failed fetch (non-200 status, parse error, transport exception) previously-synced rows
 * for the event keep their {@code breakdown_json} and flat EPA columns and just get
 * {@code last_status} bumped so the read layer surfaces staleness.
 *
 * <p><b>Breakdown size cap:</b> any {@code epa.breakdown} sub-object that serializes past 8 KB is
 * rejected with {@code last_status = 422}; flat EPA columns and prior breakdown_json are
 * preserved. Protects against upstream schema expansion or proxy-injected bloat.
 */
@Slf4j
@Singleton
public class StatboticsTeamEventSyncService {

  /** 8 KB default cap on serialized breakdown_json, per the Unit 2 spec. Configurable so tests
   * can assert the cap gate without having to craft multi-kilobyte JSON payloads by hand. */
  static final int DEFAULT_BREAKDOWN_JSON_MAX_BYTES = 8192;

  private final StatboticsClientService clientService;
  private final TournamentService tournamentService;
  private final TournamentWindow tournamentWindow;
  private final TeamTournamentService teamTournamentService;
  private final WatchedTournamentService watchedTournamentService;
  private final StatboticsTeamEventRepo repo;
  private final ObjectMapper objectMapper;
  private final int teamNumber;
  private final int breakdownJsonMaxBytes;

  StatboticsTeamEventSyncService(
      StatboticsClientService clientService,
      TournamentService tournamentService,
      TournamentWindow tournamentWindow,
      TeamTournamentService teamTournamentService,
      WatchedTournamentService watchedTournamentService,
      StatboticsTeamEventRepo repo,
      ObjectMapper objectMapper,
      @Property(name = "raven-eye.team") int teamNumber,
      @Property(
              name = "raven-eye.statbotics-api.breakdown-json-max-bytes",
              defaultValue = "8192")
          int breakdownJsonMaxBytes) {
    this.clientService = clientService;
    this.tournamentService = tournamentService;
    this.tournamentWindow = tournamentWindow;
    this.teamTournamentService = teamTournamentService;
    this.watchedTournamentService = watchedTournamentService;
    this.repo = repo;
    this.objectMapper = objectMapper;
    this.teamNumber = teamNumber;
    this.breakdownJsonMaxBytes = breakdownJsonMaxBytes;
  }

  /**
   * Scheduled sync: refresh Statbotics data for every watched/active tournament that has a
   * {@code tba_event_key}. Cadence from {@code raven-eye.sync.statbotics-team-event-poll}.
   */
  @Scheduled(fixedDelay = "${raven-eye.sync.statbotics-team-event-poll}")
  public void syncAllActiveTournaments() {
    List<TournamentRecord> targets = getActiveTournamentsToSync();
    if (targets.isEmpty()) {
      log.debug(
          "Statbotics sync idle: no watched/active tournaments with tba_event_key");
      return;
    }
    log.info("Statbotics sync: refreshing {} tournament(s)", targets.size());
    int tournamentsSynced = 0;
    int rowsWritten = 0;
    int failures = 0;
    for (TournamentRecord t : targets) {
      try {
        int written = syncOne(t.tbaEventKey(), t.id());
        if (written >= 0) {
          tournamentsSynced++;
          rowsWritten += written;
        } else {
          failures++;
        }
      } catch (Exception e) {
        log.error(
            "Statbotics sync unexpected failure for event {}: {}",
            t.tbaEventKey(),
            e.getMessage(),
            e);
        failures++;
      }
    }
    log.info(
        "Statbotics sync complete: {} tournament(s), {} team-event row(s) written, {} failure(s)",
        tournamentsSynced,
        rowsWritten,
        failures);
  }

  /**
   * Duplicated inline from {@code EventSyncService.getActiveTournamentsToSync()} because that
   * method is package-private in a different package. Matches {@link
   * ca.team1310.ravenbrain.tbaapi.service.TbaMatchSyncService} precisely, minus the regex guard
   * (Statbotics encodes whatever key the admin mapped — if Statbotics considers it invalid it
   * will return 404 and the failure path records it).
   */
  private List<TournamentRecord> getActiveTournamentsToSync() {
    Set<String> ownerIds =
        Set.copyOf(teamTournamentService.findTournamentIdsForTeam(teamNumber));
    Set<String> watchedIds = watchedTournamentService.getWatchedTournamentIds();
    return tournamentWindow.findUpcomingAndActive().stream()
        .filter(t -> ownerIds.contains(t.id()) || watchedIds.contains(t.id()))
        .filter(t -> t.tbaEventKey() != null && !t.tbaEventKey().isBlank())
        .toList();
  }

  /**
   * Sync a single TBA event's Statbotics team-event list. Returns the number of rows persisted on
   * success, or {@code -1} on failure (status recorded on any previously-synced rows for
   * observability).
   *
   * <p>Package-private so tests can drive a single sync without the scheduler.
   *
   * @param tbaEventKey the Statbotics / TBA event key (e.g. {@code "2026onto"})
   * @param tournamentId the RB_TOURNAMENT id mapped to this event, or {@code null} if none
   *     (denormalized for read-time convenience; not a foreign key)
   */
  int syncOne(String tbaEventKey, String tournamentId) {
    Objects.requireNonNull(tbaEventKey, "tbaEventKey");
    try {
      StatboticsClientService.TeamEventsFetch fetch =
          clientService.getTeamEventsByEvent(tbaEventKey);
      if (fetch.result() != null) {
        int written = persistTeamEvents(tbaEventKey, tournamentId, fetch.result().teamEvents());
        clientService.markProcessed(fetch.result().responseId());
        return written;
      }
      int status =
          switch (fetch.outcome()) {
            case NOT_FOUND -> 404;
            case ALREADY_PROCESSED -> 200;
            case EMPTY_BODY -> -1;
          };
      if (status == 200) {
        // Already processed earlier in the cycle — nothing to do.
        log.debug(
            "Statbotics sync skipped for {}: already processed in this cycle", tbaEventKey);
        return 0;
      }
      bumpStatusOnExistingRows(tbaEventKey, status);
      return -1;
    } catch (StatboticsClientException e) {
      log.warn("Statbotics fetch failed for {}: {}", tbaEventKey, e.getMessage());
      bumpStatusOnExistingRows(tbaEventKey, -1);
      return -1;
    } catch (StatboticsClientServiceException e) {
      log.warn("Statbotics parse failed for {}: {}", tbaEventKey, e.getMessage());
      bumpStatusOnExistingRows(tbaEventKey, -2);
      return -1;
    }
  }

  private int persistTeamEvents(
      String tbaEventKey, String tournamentId, List<StatboticsTeamEvent> teamEvents) {
    if (teamEvents == null || teamEvents.isEmpty()) {
      log.debug("Statbotics returned empty team-event list for {}", tbaEventKey);
      return 0;
    }
    // Preload prior rows so 422 (breakdown-too-large) path can preserve prior breakdown_json
    // without a second DB round-trip per team.
    Map<Integer, StatboticsTeamEventRecord> priorByTeam = new HashMap<>();
    for (StatboticsTeamEventRecord r : repo.findByTbaEventKey(tbaEventKey)) {
      priorByTeam.put(r.teamNumber(), r);
    }

    int written = 0;
    Instant now = Instant.now();
    for (StatboticsTeamEvent te : teamEvents) {
      if (te == null || te.team() == null) {
        log.warn("Skipping Statbotics team-event row with missing team in event {}", tbaEventKey);
        continue;
      }
      int team = te.team();
      StatboticsTeamEventEpa epa = te.epa();
      StatboticsTeamEventBreakdown breakdown = epa == null ? null : epa.breakdown();

      // Serialize the breakdown for persistence + size check. Null when the whole sub-object is
      // absent — flat columns will be NULL too.
      String breakdownJson;
      try {
        breakdownJson = breakdown == null ? null : objectMapper.writeValueAsString(breakdown);
      } catch (IOException e) {
        log.warn(
            "Failed to serialize breakdown for team {} in event {}: {}",
            team,
            tbaEventKey,
            e.getMessage());
        breakdownJson = null;
      }

      if (breakdownJson != null
          && breakdownJson.getBytes(StandardCharsets.UTF_8).length > breakdownJsonMaxBytes) {
        log.warn(
            "Statbotics breakdown for team {} in event {} exceeds {}-byte cap — preserving prior "
                + "row and marking last_status = 422",
            team,
            tbaEventKey,
            breakdownJsonMaxBytes);
        StatboticsTeamEventRecord prior = priorByTeam.get(team);
        StatboticsTeamEventRecord rejected =
            new StatboticsTeamEventRecord(
                tbaEventKey,
                team,
                tournamentId,
                prior == null ? null : prior.epaTotal(),
                prior == null ? null : prior.epaAuto(),
                prior == null ? null : prior.epaTeleop(),
                prior == null ? null : prior.epaEndgame(),
                prior == null ? null : prior.epaUnitless(),
                prior == null ? null : prior.epaNorm(),
                prior == null ? null : prior.breakdownJson(),
                prior == null ? null : prior.lastSync(),
                422);
        repo.upsert(rejected);
        continue;
      }

      StatboticsTeamEventRecord record =
          new StatboticsTeamEventRecord(
              tbaEventKey,
              team,
              tournamentId,
              breakdown == null ? null : breakdown.totalPoints(),
              breakdown == null ? null : breakdown.autoPoints(),
              breakdown == null ? null : breakdown.teleopPoints(),
              breakdown == null ? null : breakdown.endgamePoints(),
              epa == null ? null : epa.unitless(),
              epa == null ? null : epa.norm(),
              breakdownJson,
              now,
              200);
      repo.upsert(record);
      written++;
    }
    log.debug("Statbotics sync OK for {}: {} row(s) persisted", tbaEventKey, written);
    return written;
  }

  /**
   * Bump {@code last_status} on every row belonging to this event so the UI surfaces staleness.
   * Preserves flat EPA columns, {@code breakdown_json}, and {@code last_sync} so the last
   * successful data keeps flowing through the read path.
   */
  private void bumpStatusOnExistingRows(String tbaEventKey, int status) {
    List<StatboticsTeamEventRecord> existing = repo.findByTbaEventKey(tbaEventKey);
    if (existing.isEmpty()) {
      log.debug(
          "Statbotics sync failed for {} with no previously-synced rows to mark", tbaEventKey);
      return;
    }
    List<StatboticsTeamEventRecord> updated = new ArrayList<>(existing.size());
    for (StatboticsTeamEventRecord row : existing) {
      updated.add(
          new StatboticsTeamEventRecord(
              row.tbaEventKey(),
              row.teamNumber(),
              row.tournamentId(),
              row.epaTotal(),
              row.epaAuto(),
              row.epaTeleop(),
              row.epaEndgame(),
              row.epaUnitless(),
              row.epaNorm(),
              row.breakdownJson(),
              row.lastSync(),
              status));
    }
    for (StatboticsTeamEventRecord r : updated) {
      repo.update(r);
    }
  }
}
