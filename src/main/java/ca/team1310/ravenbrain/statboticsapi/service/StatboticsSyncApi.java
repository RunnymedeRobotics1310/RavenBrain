package ca.team1310.ravenbrain.statboticsapi.service;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import jakarta.inject.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/**
 * Force-sync endpoint for the Statbotics data layer. Mirrors {@code TbaSyncApi} with two
 * Statbotics-specific additions:
 *
 * <ul>
 *   <li>{@link AtomicBoolean} gate — concurrent calls return 409 CONFLICT rather than double-run
 *       the sync loop.
 *   <li>5-minute interval guard — {@code lastSuccessfulSyncAt} is updated only after a sync
 *       completes without throwing. Calls arriving within 5 minutes of a successful sync return
 *       429 TOO MANY REQUESTS. Guards against SUPERUSER-token spam hammering the unauthenticated
 *       Statbotics API.
 * </ul>
 *
 * <p>ROLE_SUPERUSER only: this is a tournament-day utility for forcing a Statbotics refresh after
 * mapping a new {@code tba_event_key} without waiting for the hourly scheduler.
 */
@Slf4j
@Controller("/api/statbotics-sync")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class StatboticsSyncApi {

  /** Minimum interval between successful syncs — plan spec 5 minutes. */
  static final Duration MIN_SUCCESS_INTERVAL = Duration.ofMinutes(5);

  private final StatboticsTeamEventSyncService syncService;
  private final ExecutorService executorService;
  private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
  private final AtomicReference<Instant> lastSuccessfulSyncAt = new AtomicReference<>(null);

  StatboticsSyncApi(
      StatboticsTeamEventSyncService syncService,
      @Named(TaskExecutors.IO) ExecutorService executorService) {
    this.syncService = syncService;
    this.executorService = executorService;
  }

  /**
   * Trigger a Statbotics sync asynchronously. Returns 202 Accepted when the gate is acquired, 409
   * if one is already running, or 429 if the last successful sync completed within the 5-minute
   * interval guard.
   */
  @Post
  @Secured({"ROLE_SUPERUSER"})
  public HttpResponse<String> forceSync() {
    Instant last = lastSuccessfulSyncAt.get();
    if (last != null && Duration.between(last, Instant.now()).compareTo(MIN_SUCCESS_INTERVAL) < 0) {
      return HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
          .body(
              "Statbotics sync succeeded within the last "
                  + MIN_SUCCESS_INTERVAL.toMinutes()
                  + " minutes — please wait before forcing another sync");
    }
    if (!syncInProgress.compareAndSet(false, true)) {
      return HttpResponse.status(HttpStatus.CONFLICT).body("Statbotics sync already in progress");
    }
    executorService.submit(
        () -> {
          try {
            syncService.syncAllActiveTournaments();
            // Only mark success if the sync itself threw nothing. Individual tournament failures
            // inside the loop are swallowed there; a throw here means the whole invocation failed.
            lastSuccessfulSyncAt.set(Instant.now());
          } catch (Exception e) {
            log.error("Statbotics force sync failed", e);
          } finally {
            syncInProgress.set(false);
          }
        });
    return HttpResponse.accepted();
  }

  /** Check whether a Statbotics sync is currently running. */
  @Get("/status")
  @Secured({"ROLE_SUPERUSER"})
  public HttpResponse<String> syncStatus() {
    return syncInProgress.get() ? HttpResponse.ok("running") : HttpResponse.ok("idle");
  }
}
