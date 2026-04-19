package ca.team1310.ravenbrain.schedule;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import ca.team1310.ravenbrain.http.ResponseEtags;
import ca.team1310.ravenbrain.report.TournamentReportService;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-27 21:42
 */
@Controller("/api/schedule")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Slf4j
public class ScheduleApi {
  private final ScheduleService scheduleService;
  private final TournamentReportService tournamentReportService;

  ScheduleApi(ScheduleService scheduleService, TournamentReportService reportService) {
    this.scheduleService = scheduleService;
    this.tournamentReportService = reportService;
  }

  @Post
  @Consumes(APPLICATION_JSON)
  public void createScheduleItem(@Body ScheduleRecord item) {
    scheduleService.save(item);
  }

  @Get("/{tournamentId}")
  @Produces(APPLICATION_JSON)
  @Transactional(readOnly = true)
  public HttpResponse<?> getScheduleForTournament(
      @Parameter String tournamentId, HttpRequest<?> request) {
    String version =
        Long.toString(
            scheduleService
                .findMaxUpdatedAtForTournament(tournamentId)
                .orElse(Instant.EPOCH)
                .toEpochMilli());
    return ResponseEtags.withWeakEtag(
        request, version, () -> scheduleService.findAllByTournamentIdOrderByMatch(tournamentId));
  }

  @Post("/bulk")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public List<ScheduleRecord> getSchedulesForTournaments(@Body List<String> tournamentIds) {
    if (tournamentIds == null || tournamentIds.isEmpty()) {
      return List.of();
    }
    // POST endpoint with a request body — not naturally cacheable via HTTP ETag (caches key on
    // method+URL). Skip ETag wrapping for the bulk endpoint; clients that care about freshness
    // use the per-tournament GET above.
    return scheduleService.findAllByTournamentIdInListOrderByTournamentId(tournamentIds);
  }

  @Get("/teams-for-tournament/{tournamentId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public List<Integer> getTeamsForTournament(@QueryValue String tournamentId) {
    var teams = new TreeSet<Integer>();
    for (var s : scheduleService.findAllByTournamentIdOrderByMatch(tournamentId)) {
      teams.add(s.blue1());
      teams.add(s.blue2());
      teams.add(s.blue3());
      if (s.blue4() != 0) teams.add(s.blue4());
      teams.add(s.red1());
      teams.add(s.red2());
      teams.add(s.red3());
      if (s.red4() != 0) teams.add(s.red4());
    }
    return teams.stream().toList();
  }

  @Get("/tournament/{tournamentId}/{teamId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public TournamentReportService.TournamentReportResponse getTournamentReport(
      @QueryValue String tournamentId, @QueryValue int teamId) {
    return tournamentReportService.getTournamentReport(tournamentId, teamId);
  }
}
