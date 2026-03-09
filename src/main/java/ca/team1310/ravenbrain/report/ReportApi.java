package ca.team1310.ravenbrain.report;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import ca.team1310.ravenbrain.report.drill.DrillReportService;
import ca.team1310.ravenbrain.report.seq.SequenceReport;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/**
 * @author Tony Field
 * @since 2025-04-04 16:39
 */
@Controller("/api/report")
public class ReportApi {
  private final TeamReportService teamReportService;
  private final DrillReportService drillReportService;

  public ReportApi(TeamReportService teamReportService, DrillReportService drillReportService) {
    this.teamReportService = teamReportService;
    this.drillReportService = drillReportService;
  }

  @Serdeable
  public record TeamReportResponse(
      TeamReportService.TeamReport report, boolean success, String reason) {}

  @Serdeable
  public record DrillReportResponse(SequenceReport report, boolean success, String reason) {}

  @Get("/team/{teamId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public TeamReportResponse getTeamReport(@QueryValue int teamId) {
    try {
      var report = teamReportService.getTeamReport(teamId);
      return new TeamReportResponse(report, true, null);
    } catch (Exception e) {
      return new TeamReportResponse(null, false, e.getMessage());
    }
  }

  @Get("/drill-sessions")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public List<String> getDrillSessions() {
    return drillReportService.listDrillSessions();
  }

  @Get("/drill/{tournamentId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public DrillReportResponse getDrillReport(
      @PathVariable String tournamentId,
      @QueryValue int team,
      @QueryValue int year) {
    try {
      var report = drillReportService.getDrillReport(team, tournamentId, year);
      return new DrillReportResponse(report, true, null);
    } catch (Exception e) {
      return new DrillReportResponse(null, false, e.getMessage());
    }
  }
}
