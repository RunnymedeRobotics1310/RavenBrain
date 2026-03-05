package ca.team1310.ravenbrain.robotalert;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Controller("/api/robot-alert")
@Slf4j
public class RobotAlertApi {
  private final RobotAlertService robotAlertService;

  public RobotAlertApi(RobotAlertService robotAlertService) {
    this.robotAlertService = robotAlertService;
  }

  @Serdeable
  public record RobotAlertPostResult(RobotAlert alert, boolean success, String reason) {}

  @Post
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public List<RobotAlertPostResult> postAlerts(@Body List<RobotAlert> alerts) {
    var result = new ArrayList<RobotAlertPostResult>();
    for (RobotAlert record : alerts) {
      try {
        record = robotAlertService.save(record);
        result.add(new RobotAlertPostResult(record, true, null));
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
    return result;
  }

  @Get("/{tournamentId}")
  @Produces(APPLICATION_JSON)
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public List<RobotAlert> getByTournament(@PathVariable String tournamentId) {
    return robotAlertService.findAllByTournamentIdOrderByTeamNumberAscCreatedAtDesc(tournamentId);
  }
}
