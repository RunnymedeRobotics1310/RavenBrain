package ca.team1310.ravenbrain.report;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import ca.team1310.ravenbrain.eventlog.EventLogService;
import ca.team1310.ravenbrain.report.drill.DrillReportService;
import ca.team1310.ravenbrain.report.mega.MegaReport;
import ca.team1310.ravenbrain.report.mega.MegaReportService;
import ca.team1310.ravenbrain.report.seq.SequenceReport;
import ca.team1310.ravenbrain.report.seq.SequenceReportService;
import ca.team1310.ravenbrain.report.seq.TournamentSequenceReport;
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
  private final SequenceReportService sequenceReportService;
  private final EventLogService eventLogService;
  private final MegaReportService megaReportService;

  public ReportApi(
      TeamReportService teamReportService,
      DrillReportService drillReportService,
      SequenceReportService sequenceReportService,
      EventLogService eventLogService,
      MegaReportService megaReportService) {
    this.teamReportService = teamReportService;
    this.drillReportService = drillReportService;
    this.sequenceReportService = sequenceReportService;
    this.eventLogService = eventLogService;
    this.megaReportService = megaReportService;
  }

  @Serdeable
  public record TeamReportResponse(
      TeamReportService.TeamReport report, boolean success, String reason) {}

  @Serdeable
  public record DrillReportResponse(SequenceReport report, boolean success, String reason) {}

  @Serdeable
  public record TournamentSequenceReportResponse(
      TournamentSequenceReport report, boolean success, String reason) {}

  @Serdeable
  public record MegaReportResponse(MegaReport report, boolean success, String reason) {}

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
      @QueryValue int year,
      @QueryValue(defaultValue = "0") long sequenceTypeId) {
    try {
      var report =
          sequenceTypeId > 0
              ? drillReportService.getDrillReport(team, tournamentId, year, sequenceTypeId)
              : drillReportService.getDrillReport(team, tournamentId, year);
      return new DrillReportResponse(report, true, null);
    } catch (Exception e) {
      return new DrillReportResponse(null, false, e.getMessage());
    }
  }

  @Get("/sequence/teams")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public List<Integer> getSequenceTeams() {
    return eventLogService.listDistinctTeamNumbers();
  }

  @Get("/sequence/tournaments")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public List<String> getSequenceTournaments(@QueryValue int team) {
    return eventLogService.listDistinctTournamentIdsByTeamNumber(team);
  }

  @Get("/sequence/tournament/{tournamentId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public TournamentSequenceReportResponse getTournamentSequenceReport(
      @PathVariable String tournamentId,
      @QueryValue int team,
      @QueryValue int year,
      @QueryValue long sequenceTypeId) {
    try {
      var report =
          sequenceReportService.getTournamentSequenceReport(
              team, tournamentId, year, sequenceTypeId);
      return new TournamentSequenceReportResponse(report, true, null);
    } catch (Exception e) {
      return new TournamentSequenceReportResponse(null, false, e.getMessage());
    }
  }

  @Get("/mega/tournaments")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public List<String> getMegaReportTournaments() {
    return megaReportService.listTournamentsWithData();
  }

  @Get("/mega/teams/{tournamentId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public List<Integer> getMegaReportTeams(@PathVariable String tournamentId) {
    return megaReportService.listTeamsForTournament(tournamentId);
  }

  @Get("/mega/{tournamentId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public MegaReportResponse getMegaReport(
      @PathVariable String tournamentId,
      @QueryValue int team,
      @QueryValue int year) {
    try {
      var report = megaReportService.generateReport(team, tournamentId, year);
      return new MegaReportResponse(report, true, null);
    } catch (Exception e) {
      return new MegaReportResponse(null, false, e.getMessage());
    }
  }
}
