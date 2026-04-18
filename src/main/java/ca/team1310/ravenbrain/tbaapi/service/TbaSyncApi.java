package ca.team1310.ravenbrain.tbaapi.service;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import jakarta.inject.Named;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Force-sync endpoint for the TBA data layer. Mirrors {@code FrcSyncApi.forceSync()} including
 * the {@link AtomicBoolean} gate — concurrent calls return 409 CONFLICT rather than double-run
 * the sync loop.
 *
 * <p>ROLE_SUPERUSER only: this is a tournament-day utility for re-pulling TBA data after an admin
 * corrects a {@code tba_event_key} mapping without waiting for the hourly scheduler.
 */
@Slf4j
@Controller("/api/tba-sync")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class TbaSyncApi {

  private final TbaEventSyncService syncService;
  private final ExecutorService executorService;
  private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

  TbaSyncApi(
      TbaEventSyncService syncService,
      @Named(TaskExecutors.IO) ExecutorService executorService) {
    this.syncService = syncService;
    this.executorService = executorService;
  }

  /** Trigger a TBA sync asynchronously. Returns 202 Accepted or 409 if one is already running. */
  @Post
  @Secured({"ROLE_SUPERUSER"})
  public HttpResponse<String> forceSync() {
    if (!syncInProgress.compareAndSet(false, true)) {
      return HttpResponse.status(HttpStatus.CONFLICT).body("TBA sync already in progress");
    }
    executorService.submit(
        () -> {
          try {
            syncService.syncAllMappedTournaments();
          } catch (Exception e) {
            log.error("TBA force sync failed", e);
          } finally {
            syncInProgress.set(false);
          }
        });
    return HttpResponse.accepted();
  }

  /** Check whether a TBA sync is currently running. */
  @Get("/status")
  @Secured({"ROLE_SUPERUSER"})
  public HttpResponse<String> syncStatus() {
    return syncInProgress.get() ? HttpResponse.ok("running") : HttpResponse.ok("idle");
  }
}
