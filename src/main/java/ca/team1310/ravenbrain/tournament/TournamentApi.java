package ca.team1310.ravenbrain.tournament;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.Introspected;
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
  private final int teamNumber;

  public TournamentApi(
      TournamentService tournamentService,
      TeamTournamentService teamTournamentService,
      @Property(name = "raven-eye.team") int teamNumber) {
    this.tournamentService = tournamentService;
    this.teamTournamentService = teamTournamentService;
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
            null);
    tournamentService.save(t);
  }

  @Get
  @Produces(APPLICATION_JSON)
  @Secured(SecurityRule.IS_ANONYMOUS)
  public List<TournamentRecord> getTournaments() {
    return tournamentService.findAllSortByStartTime();
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
  public List<TournamentRecord> getActiveTeamTournaments() {
    Set<String> teamIds = Set.copyOf(teamTournamentService.findTournamentIdsForTeam(teamNumber));
    return tournamentService.findActiveTournaments().stream()
        .filter(t -> teamIds.contains(t.id()))
        .toList();
  }

  @Get("/team")
  @Produces(APPLICATION_JSON)
  public List<TournamentRecord> getTeamTournaments() {
    Set<String> teamIds = Set.copyOf(teamTournamentService.findTournamentIdsForTeam(teamNumber));
    return tournamentService.findUpcomingAndActiveTournaments().stream()
        .filter(t -> teamIds.contains(t.id()))
        .toList();
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

  private TournamentRecord withWebcasts(TournamentRecord t, String webcasts) {
    return new TournamentRecord(
        t.id(), t.code(), t.season(), t.name(), t.startTime(), t.endTime(), t.weekNumber(),
        webcasts);
  }

  @Put("/{id}/webcast")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public HttpResponse<?> addWebcast(String id, @Body WebcastRequest request) {
    var existing = tournamentService.findById(id);
    if (existing.isEmpty()) return HttpResponse.notFound();
    var tournament = existing.get();
    List<String> urls = parseWebcasts(tournament.webcasts());
    if (!urls.contains(request.url())) {
      urls.add(request.url());
    }
    var updated = withWebcasts(tournament, toWebcastJson(urls));
    tournamentService.update(updated);
    log.info("Added webcast {} to tournament {}", request.url(), id);
    return HttpResponse.ok(updated);
  }

  @Delete("/{id}/webcast")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public HttpResponse<?> removeWebcast(String id, @Body WebcastRequest request) {
    var existing = tournamentService.findById(id);
    if (existing.isEmpty()) return HttpResponse.notFound();
    var tournament = existing.get();
    List<String> urls = parseWebcasts(tournament.webcasts());
    urls.remove(request.url());
    var updated = withWebcasts(tournament, toWebcastJson(urls));
    tournamentService.update(updated);
    log.info("Removed webcast {} from tournament {}", request.url(), id);
    return HttpResponse.ok(updated);
  }
}
