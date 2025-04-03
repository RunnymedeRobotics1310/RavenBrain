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
  public record TournamentReportCell(String colId, String value) {}

  public record TournamentReportRow(TournamentReportCell[] values) {}

  public record TournamentReportTable(
      TournamentReportRow[] headerRows,
      TournamentReportRow[] dataRows,
      TournamentReportRow[] footerRows) {}

  public record TournamentReportResponse(
      TournamentReportTable report, boolean success, String reason) {}

  @Get("/tournament/{tournamentId}/{teamId}")
  @Produces(APPLICATION_JSON)
  @Secured({"EXPERT_SCOUT"})
  public TournamentReportResponse getTournamentReport(
      @PathVariable String tournamentId, @PathVariable int teamId) {
    return new TournamentReportResponse(null, false, "Not implemented yet");
  }
}
