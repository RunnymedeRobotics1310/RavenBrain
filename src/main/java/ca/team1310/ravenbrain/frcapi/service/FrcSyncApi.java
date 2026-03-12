package ca.team1310.ravenbrain.frcapi.service;

import ca.team1310.ravenbrain.frcapi.fetch.FrcClientException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * API endpoint for triggering FRC data synchronization on demand.
 *
 * @author Tony
 * @since 2026-02-14
 */
@Slf4j
@Controller("/api/frc-sync")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class FrcSyncApi {

  private final EventSyncService eventSyncService;
  private final ExecutorService executorService;
  private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

  FrcSyncApi(
      EventSyncService eventSyncService,
      @Named(TaskExecutors.IO) ExecutorService executorService) {
    this.eventSyncService = eventSyncService;
    this.executorService = executorService;
  }

  /**
   * Force an immediate synchronization of all tournament and schedule data from the FRC API. This
   * triggers a full reload of tournaments for all supported years and refreshes schedules for the
   * current year. Runs asynchronously and returns 202 Accepted immediately.
   *
   * <p>Requires superuser access.
   */
  @Post
  @Secured({"ROLE_SUPERUSER"})
  public HttpResponse<String> forceSync() {
    if (!syncInProgress.compareAndSet(false, true)) {
      return HttpResponse.status(HttpStatus.CONFLICT).body("Sync already in progress");
    }
    executorService.submit(
        () -> {
          try {
            eventSyncService.forceSync();
          } catch (Exception e) {
            log.error("Force sync failed", e);
          } finally {
            syncInProgress.set(false);
          }
        });
    return HttpResponse.accepted();
  }

  /** Check whether a force sync is currently running. */
  @Get("/status")
  @Secured({"ROLE_SUPERUSER"})
  public HttpResponse<String> syncStatus() {
    return syncInProgress.get()
        ? HttpResponse.ok("running")
        : HttpResponse.ok("idle");
  }

  /**
   * Fetch schedule data for a single tournament from the FRC API. Uses the caching client so
   * repeated calls within the TTL window will not re-fetch from FRC.
   */
  @Post("/schedule/{tournamentId}")
  public HttpResponse<String> fetchSchedule(@PathVariable String tournamentId) {
    boolean found;
    try {
      found = eventSyncService.fetchScheduleForTournament(tournamentId);
    } catch (FrcClientException e) {
      log.error("Schedule fetch failed for {}: {}", tournamentId, e.getMessage());
      return HttpResponse.serverError("FRC API unavailable: " + e.getMessage());
    }
    return found ? HttpResponse.ok() : HttpResponse.notFound();
  }
}