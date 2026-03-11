package ca.team1310.ravenbrain.frcapi.service;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

/**
 * API endpoint for triggering FRC data synchronization on demand.
 *
 * @author Tony
 * @since 2026-02-14
 */
@Controller("/api/frc-sync")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class FrcSyncApi {

  private final EventSyncService eventSyncService;

  FrcSyncApi(EventSyncService eventSyncService) {
    this.eventSyncService = eventSyncService;
  }

  /**
   * Force an immediate synchronization of all tournament and schedule data from the FRC API. This
   * triggers a full reload of tournaments for all supported years and refreshes schedules for the
   * current year.
   *
   * <p>Requires superuser access.
   */
  @Post
  @Secured({"ROLE_SUPERUSER"})
  public void forceSync() {
    eventSyncService.forceSync();
  }

  /**
   * Fetch schedule data for a single tournament from the FRC API. Uses the caching client so
   * repeated calls within the TTL window will not re-fetch from FRC.
   */
  @Post("/schedule/{tournamentId}")
  public HttpResponse<Void> fetchSchedule(@PathVariable String tournamentId) {
    boolean found = eventSyncService.fetchScheduleForTournament(tournamentId);
    return found ? HttpResponse.ok() : HttpResponse.notFound();
  }
}