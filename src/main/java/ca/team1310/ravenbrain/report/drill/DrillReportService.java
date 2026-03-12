package ca.team1310.ravenbrain.report.drill;

import ca.team1310.ravenbrain.eventlog.EventLogService;
import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import ca.team1310.ravenbrain.report.seq.SequenceReport;
import ca.team1310.ravenbrain.report.seq.SequenceReportService;
import jakarta.inject.Singleton;
import java.util.List;

/**
 * @author Tony Field
 * @since 2026-03-06
 */
@Singleton
public class DrillReportService {
  private final EventLogService eventLogService;
  private final SequenceReportService sequenceReportService;

  public DrillReportService(
      EventLogService eventLogService, SequenceReportService sequenceReportService) {
    this.eventLogService = eventLogService;
    this.sequenceReportService = sequenceReportService;
  }

  public List<String> listDrillSessions() {
    return eventLogService.listDrillTournamentIds();
  }

  public List<String> listDrillSessionsWithSequences(int teamId, int frcYear, long sequenceTypeId) {
    List<String> allSessions = eventLogService.listDrillTournamentIds();
    return allSessions.stream()
        .filter(
            tournamentId -> {
              var report = getDrillReport(teamId, tournamentId, frcYear, sequenceTypeId);
              return !report.sequences().isEmpty();
            })
        .toList();
  }

  public SequenceReport getDrillReport(int teamId, String tournamentId, int frcYear) {
    var records =
        eventLogService.listEventsForTournament(teamId, tournamentId, List.of(TournamentLevel.Practice));
    return sequenceReportService.generateReport(frcYear, records);
  }

  public SequenceReport getDrillReport(
      int teamId, String tournamentId, int frcYear, long sequenceTypeId) {
    var records =
        eventLogService.listEventsForTournament(
            teamId, tournamentId, List.of(TournamentLevel.Practice));
    return sequenceReportService.generateReport(frcYear, records, sequenceTypeId);
  }
}
