package ca.team1310.ravenbrain.schedule;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import ca.team1310.ravenbrain.tournament.WatchedTournamentService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;

/**
 * API endpoint for the Team Schedule report. Returns schedule data enriched with start times,
 * scores, and ranking points from the FRC API.
 */
@Controller("/api/schedule/team-schedule")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Slf4j
public class TeamScheduleApi {
  private final TeamScheduleService teamScheduleService;
  private final WatchedTournamentService watchedTournamentService;

  TeamScheduleApi(
      TeamScheduleService teamScheduleService,
      WatchedTournamentService watchedTournamentService) {
    this.teamScheduleService = teamScheduleService;
    this.watchedTournamentService = watchedTournamentService;
  }

  @Get("/{tournamentId}")
  @Produces(APPLICATION_JSON)
  @Secured(SecurityRule.IS_ANONYMOUS)
  public HttpResponse<TeamScheduleService.TeamScheduleResponse> getTeamSchedule(
      @PathVariable String tournamentId) {
    watchedTournamentService.watch(tournamentId);
    return HttpResponse.ok(teamScheduleService.getTeamSchedule(tournamentId));
  }
}
