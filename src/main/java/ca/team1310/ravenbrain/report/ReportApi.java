/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.report;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;

/**
 * @author Tony Field
 * @since 2025-04-04 16:39
 */
@Controller("/api/report")
public class ReportApi {
  private final TeamReportService teamReportService;

  public ReportApi(TeamReportService teamReportService) {
    this.teamReportService = teamReportService;
  }

  @Serdeable
  public record TeamReportResponse(
      TeamReportService.TeamReport report, boolean success, String reason) {}

  @Get("/team/{teamId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT"})
  public TeamReportResponse getTeamReport(@QueryValue int teamId) {
    try {
      var report = teamReportService.getTeamReport(teamId);
      return new TeamReportResponse(report, true, null);
    } catch (Exception e) {
      return new TeamReportResponse(null, false, e.getMessage());
    }
  }
}
