package ca.team1310.ravenbrain.config;

import ca.team1310.ravenbrain.http.ResponseEtags;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Delivers RavenEye's sync cadences and tournament-window bounds from the unified
 * {@code raven-eye.sync.*} configuration block. Clients fetch these on app startup so the
 * cadence constants live server-side and there is one source of truth.
 *
 * <p>Two variants:
 *
 * <ul>
 *   <li>{@code GET /api/config/client-sync} — authenticated. Full cadence set including the
 *       RavenEye→RavenBrain poll interval, FRC poll cadences, report-body TTL, and skew-module
 *       max-age.
 *   <li>{@code GET /api/config/client-sync/public} — anonymous. Returns only the
 *       tournament-window lead/tail. Sufficient for unauthenticated surfaces (kiosk, home page)
 *       that need to know "is there a tournament window right now" without revealing the full
 *       sync-cadence posture.
 * </ul>
 *
 * <p>Both responses carry weak ETags. The version source is the application boot timestamp —
 * config doesn't change at runtime, so once a client has cached this response, every poll
 * returns 304 until the server restarts.
 */
@Controller("/api/config/client-sync")
public class ClientSyncConfigApi {

  /** Boot-time version. Constant for the lifetime of the JVM. */
  private static final String BOOT_VERSION = Long.toString(System.currentTimeMillis());

  private final long raveneyePollMs;
  private final String frcSchedulePoll;
  private final String frcScoresPoll;
  private final String tbaEventPoll;
  private final String tbaMatchPoll;
  private final int tournamentWindowLeadHours;
  private final int tournamentWindowTailHours;
  private final int reportBodyTtlDays;
  private final int skewOffsetMaxAgeHours;

  ClientSyncConfigApi(
      @Property(name = "raven-eye.sync.raveneye-poll-ms", defaultValue = "30000") long raveneyePollMs,
      @Property(name = "raven-eye.sync.frc-schedule-poll", defaultValue = "3m") String frcSchedulePoll,
      @Property(name = "raven-eye.sync.frc-scores-poll", defaultValue = "30s") String frcScoresPoll,
      @Property(name = "raven-eye.sync.tba-event-poll", defaultValue = "1h") String tbaEventPoll,
      @Property(name = "raven-eye.sync.tba-match-poll", defaultValue = "1h") String tbaMatchPoll,
      @Property(name = "raven-eye.sync.tournament-window-lead-hours", defaultValue = "12")
          int tournamentWindowLeadHours,
      @Property(name = "raven-eye.sync.tournament-window-tail-hours", defaultValue = "10")
          int tournamentWindowTailHours,
      @Property(name = "raven-eye.sync.report-body-ttl-days", defaultValue = "60")
          int reportBodyTtlDays,
      @Property(name = "raven-eye.sync.skew-offset-max-age-hours", defaultValue = "12")
          int skewOffsetMaxAgeHours) {
    this.raveneyePollMs = raveneyePollMs;
    this.frcSchedulePoll = frcSchedulePoll;
    this.frcScoresPoll = frcScoresPoll;
    this.tbaEventPoll = tbaEventPoll;
    this.tbaMatchPoll = tbaMatchPoll;
    this.tournamentWindowLeadHours = tournamentWindowLeadHours;
    this.tournamentWindowTailHours = tournamentWindowTailHours;
    this.reportBodyTtlDays = reportBodyTtlDays;
    this.skewOffsetMaxAgeHours = skewOffsetMaxAgeHours;
  }

  /** Full cadence set for authenticated clients. */
  @Serdeable
  public record FullSyncConfig(
      long raveneyePollMs,
      String frcSchedulePoll,
      String frcScoresPoll,
      String tbaEventPoll,
      String tbaMatchPoll,
      int tournamentWindowLeadHours,
      int tournamentWindowTailHours,
      int reportBodyTtlDays,
      int skewOffsetMaxAgeHours) {}

  /** Subset suitable for anonymous callers (kiosk, home page). */
  @Serdeable
  public record PublicSyncConfig(int tournamentWindowLeadHours, int tournamentWindowTailHours) {}

  @Get
  @Produces(MediaType.APPLICATION_JSON)
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public HttpResponse<?> getFull(HttpRequest<?> request) {
    return ResponseEtags.withWeakEtag(
        request,
        BOOT_VERSION,
        () ->
            new FullSyncConfig(
                raveneyePollMs,
                frcSchedulePoll,
                frcScoresPoll,
                tbaEventPoll,
                tbaMatchPoll,
                tournamentWindowLeadHours,
                tournamentWindowTailHours,
                reportBodyTtlDays,
                skewOffsetMaxAgeHours));
  }

  @Get("/public")
  @Produces(MediaType.APPLICATION_JSON)
  @Secured(SecurityRule.IS_ANONYMOUS)
  public HttpResponse<?> getPublic(HttpRequest<?> request) {
    return ResponseEtags.withWeakEtag(
        request,
        BOOT_VERSION,
        () -> new PublicSyncConfig(tournamentWindowLeadHours, tournamentWindowTailHours));
  }
}
