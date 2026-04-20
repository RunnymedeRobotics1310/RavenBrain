package ca.team1310.ravenbrain.robotalert;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import ca.team1310.ravenbrain.report.TournamentAggregatesService;
import ca.team1310.ravenbrain.report.cache.ReportCacheService;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Controller("/api/robot-alert")
@Slf4j
public class RobotAlertApi {
  private final RobotAlertService robotAlertService;
  private final TournamentAggregatesService tournamentAggregatesService;
  private final ReportCacheService reportCacheService;

  public RobotAlertApi(
      RobotAlertService robotAlertService,
      TournamentAggregatesService tournamentAggregatesService,
      ReportCacheService reportCacheService) {
    this.robotAlertService = robotAlertService;
    this.tournamentAggregatesService = tournamentAggregatesService;
    this.reportCacheService = reportCacheService;
  }

  @Serdeable
  public record RobotAlertPostResult(RobotAlert alert, boolean success, String reason) {}

  @Post
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public List<RobotAlertPostResult> postAlerts(@Body List<RobotAlert> alerts) {
    var result = new ArrayList<RobotAlertPostResult>();
    var invalidatedTournaments = new HashSet<String>();
    for (RobotAlert record : alerts) {
      try {
        record = robotAlertService.save(record);
        result.add(new RobotAlertPostResult(record, true, null));
        invalidatedTournaments.add(record.tournamentId());
      } catch (DataAccessException e) {
        if (e.getMessage().contains("Duplicate entry")) {
          log.warn("Duplicate Robot Alert: {}", record);
          result.add(new RobotAlertPostResult(record, true, null));
        } else {
          log.error("Failed to save Robot Alert: {}", record, e);
          result.add(new RobotAlertPostResult(record, false, e.getMessage()));
        }
      } catch (Exception e) {
        log.error("Failed to save Robot Alert: {}", record, e);
        result.add(new RobotAlertPostResult(record, false, e.getMessage()));
      }
    }
    reportCacheService.invalidateByPrefix("team-summary:");
    for (String tournamentId : invalidatedTournaments) {
      tournamentAggregatesService.invalidate(tournamentId);
    }
    return result;
  }

  @Get("/{tournamentId}")
  @Produces(APPLICATION_JSON)
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public List<RobotAlert> getByTournament(@PathVariable String tournamentId) {
    return robotAlertService.findAllByTournamentIdOrderByTeamNumberAscCreatedAtDesc(tournamentId);
  }

  @Post("/bulk")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public List<RobotAlert> getByTournaments(@Body List<String> tournamentIds) {
    if (tournamentIds == null || tournamentIds.isEmpty()) {
      return List.of();
    }
    return robotAlertService.findAllByTournamentIdInListOrderByTournamentId(tournamentIds);
  }
}
