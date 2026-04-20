package ca.team1310.ravenbrain.teamcapability;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ca.team1310.ravenbrain.report.TournamentAggregates;
import ca.team1310.ravenbrain.report.TournamentAggregatesService;
import ca.team1310.ravenbrain.statboticsapi.service.StatboticsTeamEventRecord;
import ca.team1310.ravenbrain.statboticsapi.service.StatboticsTeamEventRepo;
import ca.team1310.ravenbrain.tbaapi.service.TbaEventOprsRecord;
import ca.team1310.ravenbrain.tbaapi.service.TbaEventOprsRepo;
import ca.team1310.ravenbrain.tournament.TeamTournamentService;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit test of {@link TeamCapabilityEnricher} with all dependencies mocked — exercises the merge
 * algorithm, staleness semantics, withdrawal detection, coverage classification, and default sort
 * without touching the DB.
 */
class TeamCapabilityEnricherTest {

  private static final String T_ID = "2026TEST";
  private static final String TBA_KEY = "2026test";

  private TeamTournamentService teamTournamentService;
  private TournamentService tournamentService;
  private TbaEventOprsRepo tbaEventOprsRepo;
  private StatboticsTeamEventRepo statboticsTeamEventRepo;
  private TournamentAggregatesService tournamentAggregatesService;
  private TeamCapabilityEnricher enricher;

  @BeforeEach
  void setUp() {
    teamTournamentService = mock(TeamTournamentService.class);
    tournamentService = mock(TournamentService.class);
    tbaEventOprsRepo = mock(TbaEventOprsRepo.class);
    statboticsTeamEventRepo = mock(StatboticsTeamEventRepo.class);
    tournamentAggregatesService = mock(TournamentAggregatesService.class);

    when(teamTournamentService.findTeamNumbersForTournament(anyString())).thenReturn(List.of());
    when(teamTournamentService.findTeamNamesForTournament(anyString())).thenReturn(Map.of());
    when(tournamentService.findById(anyString())).thenReturn(Optional.empty());
    when(tbaEventOprsRepo.findByTbaEventKey(anyString())).thenReturn(List.of());
    when(statboticsTeamEventRepo.findByTbaEventKey(anyString())).thenReturn(List.of());
    when(tournamentAggregatesService.getAggregatesForAllTeams(anyString())).thenReturn(Map.of());

    enricher =
        new TeamCapabilityEnricher(
            teamTournamentService,
            tournamentService,
            tbaEventOprsRepo,
            statboticsTeamEventRepo,
            tournamentAggregatesService);
  }

  private static TournamentRecord tournamentWith(String tbaKey) {
    return new TournamentRecord(
        T_ID,
        "TEST",
        2026,
        "Test",
        Instant.parse("2026-03-20T00:00:00Z"),
        Instant.parse("2026-03-22T00:00:00Z"),
        3,
        null,
        tbaKey);
  }

  private static TbaEventOprsRecord opr(int team, Double oprValue, int status) {
    return new TbaEventOprsRecord(TBA_KEY, team, oprValue, null, null, Instant.now(), status);
  }

  private static StatboticsTeamEventRecord epa(
      int team, Double total, Double auto, Double teleop, Double endgame, int status) {
    return new StatboticsTeamEventRecord(
        TBA_KEY, team, T_ID, total, auto, teleop, endgame, null, null, null, Instant.now(), status);
  }

  private static TournamentAggregates agg(
      int team, Double autoAcc, Double teleopRate, Double pickup, int comments, int alerts) {
    return new TournamentAggregates(team, autoAcc, teleopRate, pickup, comments, alerts, null);
  }

  // ---------- happy paths ----------

  @Test
  void threeTeamsWithFullData_returnsAllPopulatedAndCoverageFull() {
    when(teamTournamentService.findTeamNumbersForTournament(T_ID))
        .thenReturn(List.of(1310, 2056, 254));
    when(teamTournamentService.findTeamNamesForTournament(T_ID))
        .thenReturn(Map.of(1310, "Runnymede", 2056, "OP Robotics", 254, "The Cheesy Poofs"));
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(tbaEventOprsRepo.findByTbaEventKey(TBA_KEY))
        .thenReturn(List.of(opr(1310, 40.0, 200), opr(2056, 55.0, 200), opr(254, 70.0, 200)));
    when(statboticsTeamEventRepo.findByTbaEventKey(TBA_KEY))
        .thenReturn(
            List.of(
                epa(1310, 42.0, 12.0, 24.0, 6.0, 200),
                epa(2056, 58.0, 16.0, 32.0, 10.0, 200),
                epa(254, 72.0, 20.0, 40.0, 12.0, 200)));
    when(tournamentAggregatesService.getAggregatesForAllTeams(T_ID))
        .thenReturn(
            Map.of(
                1310, agg(1310, 0.8, 0.7, 3.0, 5, 0),
                2056, agg(2056, 0.9, 0.85, 4.0, 2, 1),
                254, agg(254, 0.95, 0.9, 5.0, 10, 0)));

    List<TeamCapabilityResponse> out = enricher.enrich(T_ID);
    assertEquals(3, out.size());
    for (TeamCapabilityResponse r : out) {
      assertNotNull(r.opr());
      assertNotNull(r.epaTotal());
      assertFalse(r.oprStale());
      assertFalse(r.epaStale());
      assertEquals("full", r.scoutingCoverage());
      assertFalse(r.withdrawn());
      assertNotNull(r.teamName());
    }
  }

  @Test
  void defaultSort_oprDescWithNullsLast() {
    when(teamTournamentService.findTeamNumbersForTournament(T_ID))
        .thenReturn(List.of(1310, 2056, 254, 9999));
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(tbaEventOprsRepo.findByTbaEventKey(TBA_KEY))
        .thenReturn(
            List.of(
                opr(1310, 40.0, 200),
                opr(2056, 55.0, 200),
                opr(254, 70.0, 200)
                // 9999 has no OPR row
                ));

    List<TeamCapabilityResponse> out = enricher.enrich(T_ID);
    assertEquals(254, out.get(0).teamNumber());
    assertEquals(2056, out.get(1).teamNumber());
    assertEquals(1310, out.get(2).teamNumber());
    assertEquals(9999, out.get(3).teamNumber());
    assertNull(out.get(3).opr());
  }

  @Test
  void tournamentWithoutTbaEventKey_returnsScoutingOnlyRows() {
    when(teamTournamentService.findTeamNumbersForTournament(T_ID)).thenReturn(List.of(1310));
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(null)));
    when(tournamentAggregatesService.getAggregatesForAllTeams(T_ID))
        .thenReturn(Map.of(1310, agg(1310, 0.5, 0.4, 2.0, 3, 0)));

    List<TeamCapabilityResponse> out = enricher.enrich(T_ID);
    assertEquals(1, out.size());
    TeamCapabilityResponse r = out.get(0);
    assertNull(r.opr());
    assertNull(r.epaTotal());
    assertFalse(r.oprStale());
    assertFalse(r.epaStale());
    assertFalse(r.withdrawn());
    assertEquals(0.5, r.autoAccuracy());
    // No TBA/Statbotics lookup was attempted.
    verifyNoInteractions(tbaEventOprsRepo);
    verifyNoInteractions(statboticsTeamEventRepo);
  }

  @Test
  void teamWithEpaButNoOprYet_oprNullStaleTrue() {
    when(teamTournamentService.findTeamNumbersForTournament(T_ID)).thenReturn(List.of(1310));
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(tbaEventOprsRepo.findByTbaEventKey(TBA_KEY)).thenReturn(List.of());
    when(statboticsTeamEventRepo.findByTbaEventKey(TBA_KEY))
        .thenReturn(List.of(epa(1310, 42.0, 12.0, 24.0, 6.0, 200)));

    List<TeamCapabilityResponse> out = enricher.enrich(T_ID);
    assertEquals(1, out.size());
    TeamCapabilityResponse r = out.get(0);
    assertNull(r.opr());
    assertTrue(r.oprStale());
    assertNotNull(r.epaTotal());
    assertFalse(r.epaStale());
    assertFalse(r.withdrawn());
  }

  // ---------- edge cases ----------

  @Test
  void rookieWithZeroEventLogs_coverageNone() {
    when(teamTournamentService.findTeamNumbersForTournament(T_ID)).thenReturn(List.of(9999));
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    // No OPR / Statbotics rows; aggregates empty.

    List<TeamCapabilityResponse> out = enricher.enrich(T_ID);
    assertEquals(1, out.size());
    TeamCapabilityResponse r = out.get(0);
    assertEquals("none", r.scoutingCoverage());
    assertEquals(0, r.quickCommentCount());
    assertEquals(0, r.robotAlertCount());
    assertNull(r.autoAccuracy());
    assertTrue(r.oprStale());
    assertTrue(r.epaStale());
  }

  @Test
  void teamInOprButNotStatbotics_epaNullStale() {
    when(teamTournamentService.findTeamNumbersForTournament(T_ID)).thenReturn(List.of(1310));
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(tbaEventOprsRepo.findByTbaEventKey(TBA_KEY)).thenReturn(List.of(opr(1310, 40.0, 200)));
    // Statbotics has zero rows — pre-first-sync; don't flag withdrawn.

    List<TeamCapabilityResponse> out = enricher.enrich(T_ID);
    assertEquals(1, out.size());
    TeamCapabilityResponse r = out.get(0);
    assertEquals(40.0, r.opr());
    assertFalse(r.oprStale());
    assertNull(r.epaTotal());
    assertTrue(r.epaStale());
    assertFalse(r.withdrawn());
  }

  @Test
  void teamInStatboticsButNotOpr_oprNullStale() {
    when(teamTournamentService.findTeamNumbersForTournament(T_ID)).thenReturn(List.of(1310));
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(statboticsTeamEventRepo.findByTbaEventKey(TBA_KEY))
        .thenReturn(List.of(epa(1310, 42.0, 12.0, 24.0, 6.0, 200)));

    List<TeamCapabilityResponse> out = enricher.enrich(T_ID);
    assertEquals(1, out.size());
    TeamCapabilityResponse r = out.get(0);
    assertNull(r.opr());
    assertTrue(r.oprStale());
    assertNotNull(r.epaTotal());
    assertFalse(r.epaStale());
  }

  @Test
  void teamMissingFromAllSourcesButStatboticsHasRows_withdrawnTrueAndSortedLast() {
    when(teamTournamentService.findTeamNumbersForTournament(T_ID))
        .thenReturn(List.of(1310, 9999));
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(tbaEventOprsRepo.findByTbaEventKey(TBA_KEY)).thenReturn(List.of(opr(1310, 40.0, 200)));
    when(statboticsTeamEventRepo.findByTbaEventKey(TBA_KEY))
        .thenReturn(List.of(epa(1310, 42.0, 12.0, 24.0, 6.0, 200)));

    List<TeamCapabilityResponse> out = enricher.enrich(T_ID);
    assertEquals(2, out.size());
    // 9999 is withdrawn and must be sorted last even though 1310 also has an OPR.
    assertEquals(1310, out.get(0).teamNumber());
    assertEquals(9999, out.get(1).teamNumber());
    assertTrue(out.get(1).withdrawn());
    assertFalse(out.get(0).withdrawn());
  }

  @Test
  void oprRowWithNon200Status_marksOprStale() {
    when(teamTournamentService.findTeamNumbersForTournament(T_ID)).thenReturn(List.of(1310));
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(tbaEventOprsRepo.findByTbaEventKey(TBA_KEY))
        .thenReturn(List.of(opr(1310, 40.0, 404)));

    List<TeamCapabilityResponse> out = enricher.enrich(T_ID);
    assertEquals(40.0, out.get(0).opr()); // prior value preserved
    assertTrue(out.get(0).oprStale());
  }

  @Test
  void epaRowWithNon200Status_marksEpaStale() {
    when(teamTournamentService.findTeamNumbersForTournament(T_ID)).thenReturn(List.of(1310));
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(statboticsTeamEventRepo.findByTbaEventKey(TBA_KEY))
        .thenReturn(List.of(epa(1310, 42.0, 12.0, 24.0, 6.0, 422)));

    List<TeamCapabilityResponse> out = enricher.enrich(T_ID);
    assertEquals(42.0, out.get(0).epaTotal()); // prior value preserved
    assertTrue(out.get(0).epaStale());
  }

  @Test
  void emptyRoster_returnsEmptyList() {
    when(teamTournamentService.findTeamNumbersForTournament(T_ID)).thenReturn(List.of());
    List<TeamCapabilityResponse> out = enricher.enrich(T_ID);
    assertTrue(out.isEmpty());
    // No source lookups were issued.
    verifyNoInteractions(tbaEventOprsRepo);
    verifyNoInteractions(statboticsTeamEventRepo);
    verifyNoInteractions(tournamentAggregatesService);
  }

  @Test
  void coverageThin_partialEventLogData() {
    when(teamTournamentService.findTeamNumbersForTournament(T_ID)).thenReturn(List.of(1310));
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(null)));
    when(tournamentAggregatesService.getAggregatesForAllTeams(T_ID))
        .thenReturn(Map.of(1310, agg(1310, 0.8, null, null, 0, 0)));

    List<TeamCapabilityResponse> out = enricher.enrich(T_ID);
    assertEquals("thin", out.get(0).scoutingCoverage());
  }

  @Test
  void coverageNoneIgnoresAlertAndCommentCounts_onlyEventLogDrivesClassification() {
    // Alerts and comments are non-zero but all three scoring averages are null → still "none"
    // only when counts are also zero. Here we have a comment — coverage flips to "thin" per
    // the plan's classifier (scouting signal present even though event-log is empty).
    when(teamTournamentService.findTeamNumbersForTournament(T_ID)).thenReturn(List.of(1310));
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(null)));
    when(tournamentAggregatesService.getAggregatesForAllTeams(T_ID))
        .thenReturn(Map.of(1310, agg(1310, null, null, null, 3, 1)));

    List<TeamCapabilityResponse> out = enricher.enrich(T_ID);
    assertEquals("thin", out.get(0).scoutingCoverage());
    assertEquals(3, out.get(0).quickCommentCount());
    assertEquals(1, out.get(0).robotAlertCount());
  }

  @Test
  void largeRoster_oneEnricherCallPerSource() {
    // 80-team roster — verify exactly one lookup per data source (not N+1).
    List<Integer> roster = new java.util.ArrayList<>();
    for (int i = 1; i <= 80; i++) roster.add(1000 + i);
    when(teamTournamentService.findTeamNumbersForTournament(T_ID)).thenReturn(roster);
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));

    enricher.enrich(T_ID);
    verify(teamTournamentService, times(1)).findTeamNumbersForTournament(T_ID);
    verify(teamTournamentService, times(1)).findTeamNamesForTournament(T_ID);
    verify(tbaEventOprsRepo, times(1)).findByTbaEventKey(TBA_KEY);
    verify(statboticsTeamEventRepo, times(1)).findByTbaEventKey(TBA_KEY);
    verify(tournamentAggregatesService, times(1)).getAggregatesForAllTeams(T_ID);
  }
}
