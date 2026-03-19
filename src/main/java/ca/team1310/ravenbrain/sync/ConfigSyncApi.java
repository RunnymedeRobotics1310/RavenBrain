package ca.team1310.ravenbrain.sync;

import ca.team1310.ravenbrain.eventlog.EventLogService;
import ca.team1310.ravenbrain.quickcomment.QuickCommentService;
import ca.team1310.ravenbrain.robotalert.RobotAlertService;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import java.util.List;

@Controller("/api/config-sync")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class ConfigSyncApi {

  private final ConfigSyncService configSyncService;
  private final EventLogService eventLogService;
  private final QuickCommentService quickCommentService;
  private final RobotAlertService robotAlertService;

  ConfigSyncApi(
      ConfigSyncService configSyncService,
      EventLogService eventLogService,
      QuickCommentService quickCommentService,
      RobotAlertService robotAlertService) {
    this.configSyncService = configSyncService;
    this.eventLogService = eventLogService;
    this.quickCommentService = quickCommentService;
    this.robotAlertService = robotAlertService;
  }

  @Post
  @Secured({"ROLE_SUPERUSER"})
  public SyncResult sync(@Body SyncRequest request) {
    return configSyncService.syncFromSource(request);
  }

  @Get("/scouting-data")
  @Secured({"ROLE_SUPERUSER"})
  public ScoutingDataResult scoutingData() {
    return new ScoutingDataResult(
        eventLogService.findAll(),
        quickCommentService.findAllOrderByTeamAndTimestamp(),
        List.copyOf(robotAlertService.findAll()));
  }
}
