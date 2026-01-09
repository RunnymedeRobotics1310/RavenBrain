package ca.team1310.ravenbrain.report.seq;

import ca.team1310.ravenbrain.eventlog.EventLogRecord;
import ca.team1310.ravenbrain.eventlog.EventLogService;
import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import ca.team1310.ravenbrain.sequencetype.SequenceType;
import ca.team1310.ravenbrain.sequencetype.SequenceTypeService;
import ca.team1310.ravenbrain.tournament.TournamentService;
import java.util.List;

/**
 * @author Tony Field
 * @since 2026-01-08 11:21
 */
public class SequenceReportService {
  private final EventLogService eventLogService;
  private final SequenceTypeService sequenceTypeService;
  private final TournamentService tournamentService;

  public SequenceReportService(
      EventLogService eventLogService,
      SequenceTypeService sequenceTypeService,
      TournamentService tournamentService) {
    this.eventLogService = eventLogService;
    this.sequenceTypeService = sequenceTypeService;
    this.tournamentService = tournamentService;
  }

  public SequenceReport getMatchReportForTeam(
      int teamId, String tournamentId, int matchId, List<TournamentLevel> levels) {
    var records = eventLogService.listEventsForTournament(teamId, tournamentId, levels);
    int year = tournamentService.findYearForTournament(records.get(0).tournamentId());
    return generateReport(year, records);
  }

  public SequenceReport getSeasonReportForTeam(
      int teamId, int frcyear, List<TournamentLevel> levels) {
    var records = eventLogService.listEventsForSeason(teamId, frcyear, levels);
    return generateReport(frcyear, records);
  }

  private SequenceReport generateReport(int year, List<EventLogRecord> records) {
    List<SequenceType> types =
        sequenceTypeService.findByFrcyear(year).stream().filter(s -> !s.disabled()).toList();

    // todo: fixme: implement
    // create a blank list of sequenceinfo
    // for each ELR
    //   look to see if it is the start of any sequence (helper function)
    //   if so, remember it and the sequence
    //   look for a subsbequent event that is the end of the sequence
    //   take them and create a sequenceinfo with everything in between (helper function)
    //   if you find another start before you find an end, ignore the earlier start
    // for each sequenceinfo
    //   calculate intervalstats
    //   calculate sequence stats
    //   assemble into report
    // return report

    return null;
  }
}
