/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.report;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-04-02 21:39
 */
@Controller("/api/report")
@Slf4j
public class ReportApi {

  /*
      export type TournamentReportCell = {
    colId: string;
    value: string | number | undefined;
  };
  export type TournamentReportRow = {
    values: TournamentReportCell[];
  };
  export type TournamentReportTable = {
    headerRows: TournamentReportRow[];
    dataRows: TournamentReportRow[];
    footerRows: TournamentReportRow[];
  };


       */
  @Serdeable
  public record TournamentReportCell(String colId, String value) {}

  @Serdeable
  public record TournamentReportRow(TournamentReportCell[] values) {}

  @Serdeable
  public record TournamentReportTable(
      TournamentReportRow[] headerRows,
      TournamentReportRow[] dataRows,
      TournamentReportRow[] footerRows) {}

  @Serdeable
  public record TournamentReportResponse(
      TournamentReportTable report, boolean success, String reason) {}

  @Get("/tournament/{tournamentId}/{teamId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT"})
  public TournamentReportResponse getTournamentReport(
      @PathVariable String tournamentId, @PathVariable int teamId) {
    log.info("Getting tournament report for tournament {} and team {}", tournamentId, teamId);
    final TournamentReportResponse resp;

    log.info(
        "Finished getting tournament report for tournament {} and team {}", tournamentId, teamId);
    resp = new TournamentReportResponse(null, false, "Not implemented yet");
    return resp;
  }
}
