package ca.team1310.ravenbrain.tbaapi.service;

import ca.team1310.ravenbrain.tbaapi.fetch.TbaClientException;
import ca.team1310.ravenbrain.tbaapi.model.TbaEvent;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the write side of {@code RB_TBA_EVENT}. Periodically pulls TBA data for every tournament
 * that has a {@code tba_event_key} set, persists the reconstructed webcast URL list (and the raw
 * TBA payload, for forensics), and records the HTTP status of each attempt.
 *
 * <p>This service <b>never</b> writes to {@code RB_TOURNAMENT}. The ownership contract is: TBA
 * sync owns {@code RB_TBA_EVENT}; admin CRUD owns {@code RB_TOURNAMENT.manual_webcasts}; the two
 * paths have disjoint write surfaces, so there is no race to mitigate.
 *
 * <p>On a failed fetch (non-200 status, parse error, transport exception) the previous {@code
 * webcasts_json} is preserved and {@code last_status} is updated so the read layer (Unit 5) can
 * surface the staleness to the UI.
 */
@Slf4j
@Singleton
public class TbaEventSyncService {

  private final TbaClientService tbaClientService;
  private final TournamentService tournamentService;
  private final TbaEventRepo tbaEventRepo;

  TbaEventSyncService(
      TbaClientService tbaClientService,
      TournamentService tournamentService,
      TbaEventRepo tbaEventRepo) {
    this.tbaClientService = tbaClientService;
    this.tournamentService = tournamentService;
    this.tbaEventRepo = tbaEventRepo;
  }

  /** Scheduled sync: every hour, refresh TBA data for all tournaments that are mapped to a key. */
  @Scheduled(fixedDelay = "1h")
  public void syncAllMappedTournaments() {
    List<TournamentRecord> mapped =
        tournamentService.findAll().stream()
            .filter(t -> t.tbaEventKey() != null && !t.tbaEventKey().isBlank())
            .toList();
    if (mapped.isEmpty()) {
      log.debug("No tournaments with tba_event_key set — TBA sync idle this tick");
      return;
    }
    log.info("TBA sync: refreshing {} mapped tournament(s)", mapped.size());
    int successes = 0;
    int failures = 0;
    for (TournamentRecord t : mapped) {
      try {
        if (syncOne(t.tbaEventKey())) {
          successes++;
        } else {
          failures++;
        }
      } catch (Exception e) {
        log.error("TBA sync unexpected failure for event {}: {}", t.tbaEventKey(), e.getMessage(), e);
        failures++;
      }
    }
    log.info("TBA sync complete: {} success, {} failure", successes, failures);
  }

  /**
   * Sync a single tournament by its TBA event key. Returns {@code true} on successful fetch +
   * write, {@code false} if TBA returned a non-200 status or the call was skipped.
   *
   * <p>Package-private so tests can invoke a single sync without scheduling.
   */
  boolean syncOne(String tbaEventKey) {
    Objects.requireNonNull(tbaEventKey, "tbaEventKey");
    try {
      TbaClientService.EventFetch fetch = tbaClientService.getEvent(tbaEventKey);
      if (fetch.result() != null) {
        persistSuccess(tbaEventKey, fetch.result());
        return true;
      }
      // Skipped (already processed, not found, or empty body) — record status for UI staleness.
      int status =
          switch (fetch.outcome()) {
            case NOT_FOUND -> 404;
            case ALREADY_PROCESSED -> 200;
            case EMPTY_BODY -> -1;
          };
      persistStatusOnly(tbaEventKey, status);
      return status == 200;
    } catch (TbaClientException e) {
      log.warn("TBA fetch failed for {}: {}", tbaEventKey, e.getMessage());
      persistStatusOnly(tbaEventKey, -1);
      return false;
    } catch (TbaClientServiceException e) {
      log.warn("TBA parse failed for {}: {}", tbaEventKey, e.getMessage());
      persistStatusOnly(tbaEventKey, -2);
      return false;
    }
  }

  private void persistSuccess(String tbaEventKey, TbaClientService.EventFetchResult result) {
    TbaEvent event = result.event();
    List<String> canonicalUrls =
        WebcastUrlReconstructor.reconstructAndDedup(event == null ? null : event.webcasts());
    String webcastsJson = toJsonArray(canonicalUrls);
    var record =
        new TbaEventRecord(tbaEventKey, webcastsJson, result.rawBody(), Instant.now(), 200);
    upsert(record);
    tbaClientService.markProcessed(result.responseId());
    log.debug(
        "TBA sync OK for {}: {} webcast URL(s) persisted", tbaEventKey, canonicalUrls.size());
  }

  private void persistStatusOnly(String tbaEventKey, int status) {
    // Preserve any prior successful webcasts_json / raw_event_json by loading first.
    var existing = tbaEventRepo.findById(tbaEventKey);
    var record =
        existing
            .map(
                e ->
                    new TbaEventRecord(
                        e.eventKey(),
                        e.webcastsJson(),
                        e.rawEventJson(),
                        e.lastSync(), // keep the timestamp of the last *successful* sync
                        status))
            .orElseGet(() -> new TbaEventRecord(tbaEventKey, null, null, null, status));
    upsert(record);
  }

  private void upsert(TbaEventRecord record) {
    if (tbaEventRepo.existsById(record.eventKey())) {
      tbaEventRepo.update(record);
    } else {
      tbaEventRepo.save(record);
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
