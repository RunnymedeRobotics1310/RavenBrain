package ca.team1310.ravenbrain.report;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ca.team1310.ravenbrain.eventlog.EventLogRecord;
import ca.team1310.ravenbrain.eventlog.EventLogRepository;
import ca.team1310.ravenbrain.frcapi.model.Alliance;
import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import ca.team1310.ravenbrain.quickcomment.QuickComment;
import ca.team1310.ravenbrain.quickcomment.QuickCommentService;
import ca.team1310.ravenbrain.robotalert.RobotAlert;
import ca.team1310.ravenbrain.robotalert.RobotAlertService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit test of {@link TournamentAggregatesService} with all repository dependencies mocked. Covers
 * the batching + aggregation logic, whitelist filtering, severity resolution, and cache
 * invalidation semantics without touching the database.
 */
class TournamentAggregatesServiceTest {

  private static final String TOURN = "2026TEST";

  private EventLogRepository eventLogRepository;
  private QuickCommentService quickCommentService;
  private RobotAlertService robotAlertService;
  private TournamentAggregatesService service;

  @BeforeEach
  void setUp() {
    eventLogRepository = mock(EventLogRepository.class);
    quickCommentService = mock(QuickCommentService.class);
    robotAlertService = mock(RobotAlertService.class);

    when(eventLogRepository.findAllByTournamentId(anyString())).thenReturn(List.of());
    when(robotAlertService.findAllByTournamentIdOrderByTeamNumberAscCreatedAtDesc(anyString()))
        .thenReturn(List.of());
    when(quickCommentService.findAllByTeamOrderByTimestamp(anyInt())).thenReturn(List.of());

    service =
        new TournamentAggregatesService(
            eventLogRepository, quickCommentService, robotAlertService);
  }

  // ---------- helpers ----------

  private static EventLogRecord event(int team, String eventType, double amount) {
    return new EventLogRecord(
        0,
        Instant.parse("2026-03-20T12:00:00Z"),
        1L,
        TOURN,
        TournamentLevel.Qualification,
        1,
        Alliance.red,
        team,
        eventType,
        amount,
        null);
  }

  private static QuickComment comment(int team, String text) {
    return new QuickComment(
        null,
        1L,
        "ROLE_DATASCOUT",
        team,
        Instant.parse("2026-03-20T12:00:00Z"),
        text);
  }

  private static RobotAlert alert(int team, String text) {
    return new RobotAlert(null, TOURN, team, 1L, Instant.parse("2026-03-20T12:00:00Z"), text);
  }

  // ---------- happy paths ----------

  @Test
  void threeTeamsWithFullEventLogCoverage_returnsPopulatedAverages() {
    when(eventLogRepository.findAllByTournamentId(TOURN))
        .thenReturn(
            List.of(
                // Team 1310 — 4/5 auto, 3/4 teleop, pickups 2 + 4 (avg 3.0)
                event(1310, "auto-number-shot", 4),
                event(1310, "auto-number-missed", 1),
                event(1310, "scoring-number-success", 3),
                event(1310, "scoring-number-miss", 1),
                event(1310, "pickup-number", 2),
                event(1310, "pickup-number", 4),
                // Team 2056 — 8/10 auto, 2/2 teleop, pickups 1 (avg 1.0)
                event(2056, "auto-number-shot", 8),
                event(2056, "auto-number-missed", 2),
                event(2056, "scoring-number-success", 2),
                event(2056, "pickup-number", 1),
                // Team 4917 — 1/1 auto, 0/2 teleop, pickups 5 + 5 (avg 5.0)
                event(4917, "auto-number-shot", 1),
                event(4917, "scoring-number-miss", 2),
                event(4917, "pickup-number", 5),
                event(4917, "pickup-number", 5)));

    Map<Integer, TournamentAggregates> result = service.getAggregatesForAllTeams(TOURN);

    assertEquals(3, result.size());

    TournamentAggregates t1310 = result.get(1310);
    assertNotNull(t1310);
    assertEquals(0.8, t1310.autoAccuracy(), 1e-9);
    assertEquals(0.75, t1310.teleopSuccessRate(), 1e-9);
    assertEquals(3.0, t1310.pickupAverage(), 1e-9);
    assertEquals(0, t1310.quickCommentCount());
    assertEquals(0, t1310.robotAlertCount());
    assertNull(t1310.robotAlertMaxSeverity());

    TournamentAggregates t2056 = result.get(2056);
    assertEquals(0.8, t2056.autoAccuracy(), 1e-9);
    assertEquals(1.0, t2056.teleopSuccessRate(), 1e-9);
    assertEquals(1.0, t2056.pickupAverage(), 1e-9);

    TournamentAggregates t4917 = result.get(4917);
    assertEquals(1.0, t4917.autoAccuracy(), 1e-9);
    assertEquals(0.0, t4917.teleopSuccessRate(), 1e-9);
    assertEquals(5.0, t4917.pickupAverage(), 1e-9);
  }

  @Test
  void teamWithNoEventLogsButWithQuickComments_returnsTeamWithNullAveragesAndCommentCount() {
    // Team 7848 has an alert so it enters the result set; comments are per-team all-time.
    when(robotAlertService.findAllByTournamentIdOrderByTeamNumberAscCreatedAtDesc(TOURN))
        .thenReturn(List.of(alert(7848, "wheels falling off")));
    when(quickCommentService.findAllByTeamOrderByTimestamp(7848))
        .thenReturn(List.of(comment(7848, "driver is new"), comment(7848, "solid defence")));

    Map<Integer, TournamentAggregates> result = service.getAggregatesForAllTeams(TOURN);

    assertEquals(1, result.size());
    TournamentAggregates row = result.get(7848);
    assertNotNull(row);
    assertNull(row.autoAccuracy());
    assertNull(row.teleopSuccessRate());
    assertNull(row.pickupAverage());
    assertEquals(2, row.quickCommentCount());
    assertEquals(1, row.robotAlertCount());
    assertEquals("info", row.robotAlertMaxSeverity());
  }

  // ---------- edge cases ----------

  @Test
  void tournamentWithZeroTeams_returnsEmptyMap() {
    Map<Integer, TournamentAggregates> result = service.getAggregatesForAllTeams(TOURN);

    assertTrue(result.isEmpty());
  }

  @Test
  void mixedEventTypes_onlyWhitelistedContributeToAverages() {
    when(eventLogRepository.findAllByTournamentId(TOURN))
        .thenReturn(
            List.of(
                event(1310, "auto-number-shot", 4),
                event(1310, "auto-number-missed", 1),
                // Non-whitelisted — must be ignored entirely
                event(1310, "end-zone-touched", 99),
                event(1310, "foul-committed", 42),
                event(1310, "random-non-whitelisted-type", 7)));

    Map<Integer, TournamentAggregates> result = service.getAggregatesForAllTeams(TOURN);

    assertEquals(1, result.size());
    TournamentAggregates row = result.get(1310);
    assertEquals(0.8, row.autoAccuracy(), 1e-9);
    // No whitelisted teleop / pickup rows → nulls.
    assertNull(row.teleopSuccessRate());
    assertNull(row.pickupAverage());
  }

  @Test
  void criticalAlertWinsOverInfoAlertOnSameTeam() {
    when(robotAlertService.findAllByTournamentIdOrderByTeamNumberAscCreatedAtDesc(TOURN))
        .thenReturn(
            List.of(
                alert(1310, "[critical] motor controller smoking"),
                alert(1310, "[info] robot looks clean in the pit")));

    Map<Integer, TournamentAggregates> result = service.getAggregatesForAllTeams(TOURN);

    TournamentAggregates row = result.get(1310);
    assertNotNull(row);
    assertEquals(2, row.robotAlertCount());
    assertEquals("critical", row.robotAlertMaxSeverity());
  }

  @Test
  void warningAlertDoesNotDowngradeCritical() {
    when(robotAlertService.findAllByTournamentIdOrderByTeamNumberAscCreatedAtDesc(TOURN))
        .thenReturn(
            List.of(
                alert(1310, "[critical] tipped over"),
                alert(1310, "[warning] intake occasionally jams")));

    Map<Integer, TournamentAggregates> result = service.getAggregatesForAllTeams(TOURN);

    assertEquals("critical", result.get(1310).robotAlertMaxSeverity());
  }

  @Test
  void untaggedAlertDefaultsToInfo() {
    when(robotAlertService.findAllByTournamentIdOrderByTeamNumberAscCreatedAtDesc(TOURN))
        .thenReturn(List.of(alert(1310, "plain free-text with no bracketed severity")));

    Map<Integer, TournamentAggregates> result = service.getAggregatesForAllTeams(TOURN);

    assertEquals("info", result.get(1310).robotAlertMaxSeverity());
  }

  @Test
  void autoAccuracyIsNullWhenNoAutoAttemptsExist() {
    when(eventLogRepository.findAllByTournamentId(TOURN))
        .thenReturn(List.of(event(1310, "pickup-number", 3)));

    Map<Integer, TournamentAggregates> result = service.getAggregatesForAllTeams(TOURN);

    TournamentAggregates row = result.get(1310);
    assertNull(row.autoAccuracy());
    assertNull(row.teleopSuccessRate());
    assertEquals(3.0, row.pickupAverage(), 1e-9);
  }

  // ---------- caching / invalidation ----------

  @Test
  void secondCall_servesFromCache_noRepositoryTraffic() {
    when(eventLogRepository.findAllByTournamentId(TOURN))
        .thenReturn(List.of(event(1310, "pickup-number", 3)));

    service.getAggregatesForAllTeams(TOURN);
    service.getAggregatesForAllTeams(TOURN);

    // Exactly one call per repository method — second invocation is cache-served.
    verify(eventLogRepository, times(1)).findAllByTournamentId(TOURN);
    verify(robotAlertService, times(1))
        .findAllByTournamentIdOrderByTeamNumberAscCreatedAtDesc(TOURN);
  }

  @Test
  void invalidate_reflectsNewEventLogOnNextCall() {
    List<EventLogRecord> initial = new ArrayList<>();
    initial.add(event(1310, "pickup-number", 3));
    when(eventLogRepository.findAllByTournamentId(TOURN))
        .thenReturn(initial)
        .thenReturn(
            List.of(
                event(1310, "pickup-number", 3),
                event(1310, "pickup-number", 7)));

    Map<Integer, TournamentAggregates> first = service.getAggregatesForAllTeams(TOURN);
    assertEquals(3.0, first.get(1310).pickupAverage(), 1e-9);

    service.invalidate(TOURN);

    Map<Integer, TournamentAggregates> second = service.getAggregatesForAllTeams(TOURN);
    assertEquals(5.0, second.get(1310).pickupAverage(), 1e-9);
    verify(eventLogRepository, times(2)).findAllByTournamentId(TOURN);
  }

  @Test
  void invalidateAll_dropsEveryTournamentsCache() {
    String other = "OTHER_TOURN";
    when(eventLogRepository.findAllByTournamentId(TOURN))
        .thenReturn(List.of(event(1310, "pickup-number", 2)));
    when(eventLogRepository.findAllByTournamentId(other))
        .thenReturn(List.of(event(2056, "pickup-number", 4)));

    service.getAggregatesForAllTeams(TOURN);
    service.getAggregatesForAllTeams(other);
    service.invalidateAll();
    service.getAggregatesForAllTeams(TOURN);
    service.getAggregatesForAllTeams(other);

    verify(eventLogRepository, times(2)).findAllByTournamentId(TOURN);
    verify(eventLogRepository, times(2)).findAllByTournamentId(other);
  }

  @Test
  void emptyFor_producesANullyPlaceholderForUnseenTeams() {
    TournamentAggregates empty = TournamentAggregatesService.emptyFor(4613);
    assertEquals(4613, empty.teamNumber());
    assertNull(empty.autoAccuracy());
    assertNull(empty.teleopSuccessRate());
    assertNull(empty.pickupAverage());
    assertEquals(0, empty.quickCommentCount());
    assertEquals(0, empty.robotAlertCount());
    assertNull(empty.robotAlertMaxSeverity());
  }

  @Test
  void parseSeverity_recognisesCaseInsensitiveBracketedMarkers() {
    assertEquals("critical", TournamentAggregatesService.parseSeverity("[CRITICAL] tipped"));
    assertEquals("warning", TournamentAggregatesService.parseSeverity("[Warning] intake"));
    assertEquals("info", TournamentAggregatesService.parseSeverity("[info] looks ready"));
    assertEquals("info", TournamentAggregatesService.parseSeverity("[bogus] mystery tag"));
    assertEquals("info", TournamentAggregatesService.parseSeverity("untagged alert"));
    assertEquals("info", TournamentAggregatesService.parseSeverity(null));
  }
}
