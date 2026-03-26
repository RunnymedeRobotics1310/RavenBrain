package ca.team1310.ravenbrain.report;

import ca.team1310.ravenbrain.connect.UserService;
import ca.team1310.ravenbrain.eventlog.EventLogService;
import ca.team1310.ravenbrain.eventlog.TournamentEventTypePair;
import ca.team1310.ravenbrain.quickcomment.QuickComment;
import ca.team1310.ravenbrain.quickcomment.QuickCommentService;
import ca.team1310.ravenbrain.robotalert.RobotAlert;
import ca.team1310.ravenbrain.robotalert.RobotAlertService;
import ca.team1310.ravenbrain.sequencetype.SequenceEvent;
import ca.team1310.ravenbrain.sequencetype.SequenceType;
import ca.team1310.ravenbrain.sequencetype.SequenceTypeService;
import ca.team1310.ravenbrain.tournament.TournamentService;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Tony Field
 * @since 2025-04-04 16:33
 */
@Singleton
public class TeamReportService {

  @Serdeable
  public record TeamReportComment(
      String timestamp, String displayName, String role, String quickComment) {}

  @Serdeable
  public record TeamReportRobotAlert(
      String timestamp,
      String displayName,
      String tournamentId,
      String alert) {}

  @Serdeable
  public record SequenceReportLink(
      String sequenceTypeCode, String sequenceTypeName, String tournamentId, boolean drill) {}

  @Serdeable
  public record DefenceNote(
      String timestamp, String displayName, String tournamentId, int matchId, String note) {}

  @Serdeable
  public record TeamReport(
      List<TeamReportComment> comments,
      List<TeamReportRobotAlert> robotAlerts,
      List<SequenceReportLink> sequenceReportLinks,
      List<DefenceNote> defenceNotes,
      TournamentReportService.TournamentReportTable[] tournamentReports) {}

  private final QuickCommentService quickCommentService;
  private final RobotAlertService robotAlertService;
  private final TournamentReportService tournamentReportService;
  private final TournamentService tournamentService;
  private final UserService userService;
  private final EventLogService eventLogService;
  private final SequenceTypeService sequenceTypeService;

  public TeamReportService(
      QuickCommentService quickCommentService,
      RobotAlertService robotAlertService,
      TournamentReportService tournamentReportService,
      TournamentService tournamentService,
      UserService userService,
      EventLogService eventLogService,
      SequenceTypeService sequenceTypeService) {
    this.quickCommentService = quickCommentService;
    this.robotAlertService = robotAlertService;
    this.tournamentReportService = tournamentReportService;
    this.tournamentService = tournamentService;
    this.userService = userService;
    this.eventLogService = eventLogService;
    this.sequenceTypeService = sequenceTypeService;
  }

  public TeamReport getTeamReport(int team) {
    List<QuickComment> rawComments = quickCommentService.findAllByTeamOrderByTimestamp(team);

    Map<Long, String> userNameMap = new HashMap<>();
    rawComments.stream()
        .map(QuickComment::userId)
        .distinct()
        .forEach(
            id ->
                userNameMap.put(
                    id,
                    resolveDisplayName(id)));

    List<TeamReportComment> comments =
        rawComments.stream()
            .map(
                c ->
                    new TeamReportComment(
                        c.timestamp().toString(),
                        userNameMap.getOrDefault(c.userId(), "Unknown"),
                        c.role(),
                        c.quickComment()))
            .toList();

    // Build robot alerts with display names
    List<RobotAlert> rawAlerts = robotAlertService.findAllByTeamNumberOrderByCreatedAtDesc(team);

    for (var alert : rawAlerts) {
      userNameMap.computeIfAbsent(alert.userId(), this::resolveDisplayName);
    }

    List<TeamReportRobotAlert> robotAlerts =
        rawAlerts.stream()
            .map(
                a ->
                    new TeamReportRobotAlert(
                        a.createdAt().toString(),
                        userNameMap.getOrDefault(a.userId(), "Unknown"),
                        a.tournamentId(),
                        a.alert()))
            .toList();

    // Build sequence report links by checking which sequence types have matching events
    List<TournamentEventTypePair> pairs =
        eventLogService.listDistinctTournamentAndEventTypeByTeamNumber(team);

    // Group event types by tournament
    Map<String, Set<String>> eventTypesByTournament = new HashMap<>();
    for (var pair : pairs) {
      eventTypesByTournament
          .computeIfAbsent(pair.tournamentId(), k -> new HashSet<>())
          .add(pair.eventType());
    }

    // For each active sequence type, check which tournaments have at least one matching event type
    List<SequenceType> activeSequenceTypes =
        sequenceTypeService.list().stream().filter(st -> !st.disabled()).toList();

    List<SequenceReportLink> sequenceLinks = new ArrayList<>();
    for (var st : activeSequenceTypes) {
      Set<String> seqEventTypes =
          st.events().stream().map(se -> se.eventtype().eventtype()).collect(Collectors.toSet());
      for (var entry : eventTypesByTournament.entrySet()) {
        String tournamentId = entry.getKey();
        Set<String> tournamentEventTypes = entry.getValue();
        boolean hasMatch = tournamentEventTypes.stream().anyMatch(seqEventTypes::contains);
        if (hasMatch) {
          boolean isDrill = tournamentId.startsWith("DRILL-");
          sequenceLinks.add(new SequenceReportLink(st.code(), st.name(), tournamentId, isDrill));
        }
      }
    }

    // Build defence-strat notes
    var rawDefenceEvents = eventLogService.listEventsByTeamAndEventTypeWithNotes(team, "defence-strat");
    for (var e : rawDefenceEvents) {
      userNameMap.computeIfAbsent(e.userId(), this::resolveDisplayName);
    }
    List<DefenceNote> defenceNotes =
        rawDefenceEvents.stream()
            .map(
                e ->
                    new DefenceNote(
                        e.timestamp().toString(),
                        userNameMap.getOrDefault(e.userId(), "Unknown"),
                        e.tournamentId(),
                        e.matchId(),
                        e.note()))
            .toList();

    var tournamentReports = new ArrayList<TournamentReportService.TournamentReportTable>();
    for (var tournament : tournamentService.findAllSortByStartTime()) {
      var tournamentReport = tournamentReportService.getTournamentReport(tournament.id(), team);
      if (tournamentReport != null && tournamentReport.success()) {
        tournamentReports.add(tournamentReport.report());
      }
    }
    return new TeamReport(
        comments,
        robotAlerts,
        sequenceLinks,
        defenceNotes,
        tournamentReports.toArray(new TournamentReportService.TournamentReportTable[0]));
  }

  private String resolveDisplayName(long userId) {
    try {
      return userService.getUser(userId).displayName();
    } catch (Exception e) {
      return "Unknown";
    }
  }
}
