package ca.team1310.ravenbrain.schedule;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import ca.team1310.ravenbrain.report.TournamentReportService;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
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
  public List<ScheduleRecord> getScheduleForTournament(@Parameter String tournamentId) {
    return scheduleService.findAllByTournamentIdOrderByMatch(tournamentId);
  }

  @Get("/teams-for-tournament/{tournamentId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT"})
  public List<Integer> getTeamsForTournament(@QueryValue String tournamentId) {
    var teams = new TreeSet<Integer>();
    for (var s : scheduleService.findAllByTournamentIdOrderByMatch(tournamentId)) {
      teams.add(s.blue1);
      teams.add(s.blue2);
      teams.add(s.blue3);
      teams.add(s.red1);
      teams.add(s.red2);
      teams.add(s.red3);
    }
    return teams.stream().toList();
  }

  @Get("/tournament/{tournamentId}/{teamId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT"})
  public TournamentReportService.TournamentReportResponse getTournamentReport(
      @QueryValue String tournamentId, @QueryValue int teamId) {
    return tournamentReportService.getTournamentReport(tournamentId, teamId);
  }
}
