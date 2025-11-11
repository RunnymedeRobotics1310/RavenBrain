package ca.team1310.ravenbrain.report;

import ca.team1310.ravenbrain.quickcomment.QuickComment;
import ca.team1310.ravenbrain.quickcomment.QuickCommentService;
import ca.team1310.ravenbrain.tournament.TournamentService;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Tony Field
 * @since 2025-04-04 16:33
 */
@Singleton
public class TeamReportService {
  @Serdeable
  public record TeamReport(
      List<QuickComment> comments,
      TournamentReportService.TournamentReportTable[] tournamentReports) {}

  private final QuickCommentService quickCommentService;
  private final TournamentReportService tournamentReportService;
  private final TournamentService tournamentService;

  public TeamReportService(
      QuickCommentService quickCommentService,
      TournamentReportService tournamentReportService,
      TournamentService tournamentService) {
    this.quickCommentService = quickCommentService;
    this.tournamentReportService = tournamentReportService;
    this.tournamentService = tournamentService;
  }

  public TeamReport getTeamReport(int team) {
    List<QuickComment> comments = quickCommentService.findAllByTeamOrderByTimestamp(team);
    var tournamentReports = new ArrayList<TournamentReportService.TournamentReportTable>();
    for (var tournament : tournamentService.findAllSortByStartTime()) {
      var tournamentReport = tournamentReportService.getTournamentReport(tournament.getId(), team);
      if (tournamentReport != null && tournamentReport.success()) {
        tournamentReports.add(tournamentReport.report());
      }
    }
    return new TeamReport(
        comments, tournamentReports.toArray(new TournamentReportService.TournamentReportTable[0]));
  }
}
