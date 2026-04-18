package ca.team1310.ravenbrain.tournament;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-26 07:17
 */
@Controller("/api/tournament")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Slf4j
public class TournamentApi {
  private final TournamentService tournamentService;
  private final TeamTournamentService teamTournamentService;
  private final TournamentEnricher enricher;
  private final int teamNumber;

  public TournamentApi(
      TournamentService tournamentService,
      TeamTournamentService teamTournamentService,
      TournamentEnricher enricher,
      @Property(name = "raven-eye.team") int teamNumber) {
    this.tournamentService = tournamentService;
    this.teamTournamentService = teamTournamentService;
    this.enricher = enricher;
    this.teamNumber = teamNumber;
  }

  @Introspected
  @Serdeable
  record TournamentDTO(
      String id,
      int season,
      String code,
      String name,
      LocalDateTime startTime,
      LocalDateTime endTime,
      int weekNumber) {}

  @Post
  @Consumes(APPLICATION_JSON)
  public void createTournament(@Body TournamentDTO tournamentRecord) {
    log.debug("Saving tournament record: {}", tournamentRecord);
    var t =
        new TournamentRecord(
            tournamentRecord.id(),
            tournamentRecord.code(),
            tournamentRecord.season(),
            tournamentRecord.name(),
            tournamentRecord.startTime().toInstant(ZoneOffset.UTC),
            tournamentRecord.endTime().toInstant(ZoneOffset.UTC),
            tournamentRecord.weekNumber(),
            null,
            null);
    tournamentService.save(t);
  }

  @Get
  @Produces(APPLICATION_JSON)
  @Secured(SecurityRule.IS_ANONYMOUS)
  public List<TournamentResponse> getTournaments() {
    return enricher.enrich(tournamentService.findAllSortByStartTime());
  }

  @Get("/team-ids")
  @Produces(APPLICATION_JSON)
  @Secured(SecurityRule.IS_ANONYMOUS)
  public List<String> getTeamTournamentIds() {
    return teamTournamentService.findTournamentIdsForTeam(teamNumber);
  }

  @Get("/active-team")
  @Produces(APPLICATION_JSON)
  @Secured(SecurityRule.IS_ANONYMOUS)
  public List<TournamentResponse> getActiveTeamTournaments() {
    Set<String> teamIds = Set.copyOf(teamTournamentService.findTournamentIdsForTeam(teamNumber));
    return enricher.enrich(
        tournamentService.findActiveTournaments().stream()
            .filter(t -> teamIds.contains(t.id()))
            .toList());
  }

  @Get("/team")
  @Produces(APPLICATION_JSON)
  public List<TournamentResponse> getTeamTournaments() {
    Set<String> teamIds = Set.copyOf(teamTournamentService.findTournamentIdsForTeam(teamNumber));
    return enricher.enrich(
        tournamentService.findUpcomingAndActiveTournaments().stream()
            .filter(t -> teamIds.contains(t.id()))
            .toList());
  }

  @Introspected
  @Serdeable
  record WebcastRequest(String url) {}

  private List<String> parseWebcasts(String webcasts) {
    List<String> urls = new ArrayList<>();
    if (webcasts != null && !webcasts.isBlank()) {
      String raw = webcasts.trim();
      if (raw.startsWith("[")) {
        raw = raw.substring(1, raw.length() - 1);
        for (String part : raw.split(",")) {
          String cleaned = part.trim();
          if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
          }
          if (!cleaned.isEmpty()) {
            urls.add(cleaned);
          }
        }
      }
    }
    return urls;
  }

  private String toWebcastJson(List<String> urls) {
    if (urls.isEmpty()) return null;
    return "["
        + urls.stream().map(u -> "\"" + u + "\"").reduce((a, b) -> a + "," + b).orElse("")
        + "]";
  }

  private TournamentRecord withManualWebcasts(TournamentRecord t, String manualWebcasts) {
    return new TournamentRecord(
        t.id(),
        t.code(),
        t.season(),
        t.name(),
        t.startTime(),
        t.endTime(),
        t.weekNumber(),
        manualWebcasts,
        t.tbaEventKey());
  }

  @Put("/{id}/webcast")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_SUPERUSER", "ROLE_ADMIN"})
  public HttpResponse<?> addWebcast(String id, @Body WebcastRequest request) {
    var existing = tournamentService.findById(id);
    if (existing.isEmpty()) return HttpResponse.notFound();
    var tournament = existing.get();
    List<String> urls = parseWebcasts(tournament.manualWebcasts());
    if (!urls.contains(request.url())) {
      urls.add(request.url());
    }
    var updated = withManualWebcasts(tournament, toWebcastJson(urls));
    tournamentService.update(updated);
    log.info("Added webcast {} to tournament {}", request.url(), id);
    return HttpResponse.ok(enricher.enrich(updated));
  }

  @Introspected
  @Serdeable
  record TbaEventKeyRequest(@Nullable String tbaEventKey) {}

  private static final Pattern TBA_EVENT_KEY_PATTERN =
      Pattern.compile("^20\\d{2}[a-z][a-z0-9]{1,15}$");

  /**
   * Set or clear the TBA event key for a tournament. Accepts a JSON body
   * {@code {"tbaEventKey": "2026onto"}} to set the key, or {@code {"tbaEventKey": null}} (or an
   * empty string) to clear it. The response body echoes the canonicalized (lowercased) key so the
   * client can update its displayed value to match what was saved.
   */
  @Put("/{id}/tba-event-key")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_SUPERUSER", "ROLE_ADMIN"})
  public HttpResponse<?> setTbaEventKey(String id, @Body TbaEventKeyRequest request) {
    var existing = tournamentService.findById(id);
    if (existing.isEmpty()) return HttpResponse.notFound();
    String raw = request == null ? null : request.tbaEventKey();
    String normalized;
    if (raw == null || raw.isBlank()) {
      normalized = null; // explicit clear
    } else {
      String lower = raw.trim().toLowerCase();
      if (!TBA_EVENT_KEY_PATTERN.matcher(lower).matches()) {
        return HttpResponse.badRequest(
            "Invalid TBA event key. Expected format: 4-digit year + code, e.g. '2026onto'.");
      }
      normalized = lower;
    }
    var t = existing.get();
    var updated =
        new TournamentRecord(
            t.id(),
            t.code(),
            t.season(),
            t.name(),
            t.startTime(),
            t.endTime(),
            t.weekNumber(),
            t.manualWebcasts(),
            normalized);
    tournamentService.update(updated);
    log.info("Set tba_event_key for tournament {} to {}", id, normalized);
    return HttpResponse.ok(enricher.enrich(updated));
  }

  @Delete("/{id}/webcast")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_SUPERUSER", "ROLE_ADMIN"})
  public HttpResponse<?> removeWebcast(String id, @Body WebcastRequest request) {
    var existing = tournamentService.findById(id);
    if (existing.isEmpty()) return HttpResponse.notFound();
    var tournament = existing.get();
    List<String> urls = parseWebcasts(tournament.manualWebcasts());
    urls.remove(request.url());
    var updated = withManualWebcasts(tournament, toWebcastJson(urls));
    tournamentService.update(updated);
    log.info("Removed webcast {} from tournament {}", request.url(), id);
    return HttpResponse.ok(enricher.enrich(updated));
  }
}
