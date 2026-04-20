package ca.team1310.ravenbrain.statboticsapi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ca.team1310.ravenbrain.statboticsapi.fetch.StatboticsClientException;
import ca.team1310.ravenbrain.statboticsapi.model.StatboticsTeamEvent;
import ca.team1310.ravenbrain.statboticsapi.model.StatboticsTeamEventBreakdown;
import ca.team1310.ravenbrain.statboticsapi.model.StatboticsTeamEventEpa;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import ca.team1310.ravenbrain.tournament.WatchedTournamentService;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end sync behaviour for Statbotics team-event ingestion. {@link StatboticsClientService}
 * is mocked at the service boundary so the real sync service, the real JDBC
 * {@link StatboticsTeamEventRepo}, and the 8 KB breakdown cap all exercise against the
 * Testcontainers MySQL. Mirrors {@code TbaMatchSyncServiceTest}.
 *
 * <p>Most {@code syncOne} tests exercise the service directly with a synthetic key — no tournament
 * rows needed because {@code RB_STATBOTICS_TEAM_EVENT} has no foreign key on
 * {@code RB_TOURNAMENT}. Only the {@code syncAllActiveTournaments_*} tests need tournament +
 * watched rows.
 */
@MicronautTest(transactional = false)
public class StatboticsTeamEventSyncServiceTest {

  private static final StatboticsClientService CLIENT_MOCK = mock(StatboticsClientService.class);

  @Inject StatboticsTeamEventSyncService syncService;
  @Inject StatboticsTeamEventRepo repo;
  @Inject TournamentService tournamentService;
  @Inject WatchedTournamentService watchedTournamentService;

  @MockBean(StatboticsClientService.class)
  StatboticsClientService mockClient() {
    return CLIENT_MOCK;
  }

  @BeforeEach
  void setUp() {
    reset(CLIENT_MOCK);
    // Default: any leftover active tournament's Statbotics call returns empty, so stray state in
    // the shared Testcontainers DB doesn't pollute the assertions.
    when(CLIENT_MOCK.getTeamEventsByEvent(anyString()))
        .thenReturn(
            StatboticsClientService.TeamEventsFetch.ok(
                new StatboticsClientService.TeamEventsFetchResult(0L, List.of())));
  }

  @AfterEach
  void tearDown() {
    for (StatboticsTeamEventRecord r : repo.findAll()) {
      repo.deleteByTbaEventKey(r.tbaEventKey());
    }
    // RB_TOURNAMENT + RB_WATCHED_TOURNAMENT rows are intentionally not cleaned between tests
    // (they carry FKs and the watched-cache singleton keeps state). The default empty-list mock
    // in setUp() means their persistence does not corrupt subsequent verifications.
  }

  private static StatboticsTeamEvent teamEvent(
      int team, String event, Double total, Double auto, Double teleop, Double endgame) {
    return new StatboticsTeamEvent(
        team,
        event,
        new StatboticsTeamEventEpa(
            1600.0, 1580.0, new StatboticsTeamEventBreakdown(total, auto, teleop, endgame)));
  }

  private StatboticsClientService.TeamEventsFetch okFetch(long id, List<StatboticsTeamEvent> tes) {
    return StatboticsClientService.TeamEventsFetch.ok(
        new StatboticsClientService.TeamEventsFetchResult(id, tes));
  }

  // ---------- Happy path ----------

  @Test
  void syncOne_persistsAllTeamRowsWithFlatEpaAndBreakdownJson() {
    String key = "2026sbhappy";
    List<StatboticsTeamEvent> tes = new ArrayList<>();
    tes.add(teamEvent(1310, key, 72.3, 18.1, 44.2, 10.0));
    tes.add(teamEvent(2056, key, 80.5, 20.0, 48.5, 12.0));

    when(CLIENT_MOCK.getTeamEventsByEvent(key)).thenReturn(okFetch(55L, tes));

    int written = syncService.syncOne(key, "SBSYNC_TOURNAMENT_ID");

    assertEquals(2, written);
    var a = repo.find(key, 1310);
    assertNotNull(a);
    assertEquals("SBSYNC_TOURNAMENT_ID", a.tournamentId());
    assertEquals(200, a.lastStatus());
    assertEquals(72.3, a.epaTotal(), 0.0001);
    assertEquals(18.1, a.epaAuto(), 0.0001);
    assertEquals(44.2, a.epaTeleop(), 0.0001);
    assertEquals(10.0, a.epaEndgame(), 0.0001);
    assertEquals(1600.0, a.epaUnitless(), 0.0001);
    assertEquals(1580.0, a.epaNorm(), 0.0001);
    assertNotNull(a.breakdownJson());
    assertTrue(a.breakdownJson().contains("auto_points"));
    assertNotNull(a.lastSync());
    verify(CLIENT_MOCK).markProcessed(55L);

    // The other row landed too.
    var b = repo.find(key, 2056);
    assertNotNull(b);
    assertEquals(80.5, b.epaTotal(), 0.0001);
  }

  @Test
  void syncOne_isIdempotentOnUnchangedPayload() {
    String key = "2026sbidem";
    List<StatboticsTeamEvent> tes = List.of(teamEvent(1310, key, 72.3, 18.1, 44.2, 10.0));
    when(CLIENT_MOCK.getTeamEventsByEvent(key))
        .thenReturn(okFetch(10L, tes), okFetch(11L, tes));

    syncService.syncOne(key, null);
    syncService.syncOne(key, null);

    List<StatboticsTeamEventRecord> rows = repo.findByTbaEventKey(key);
    assertEquals(1, rows.size(), "repeated syncs must not duplicate on composite PK");
    verify(CLIENT_MOCK, times(2)).getTeamEventsByEvent(key);
    verify(CLIENT_MOCK).markProcessed(10L);
    verify(CLIENT_MOCK).markProcessed(11L);
  }

  @Test
  void syncOne_teamWithNullAutoPointsLandsNullFlatColumn() {
    String key = "2026sbnullauto";
    StatboticsTeamEvent te =
        new StatboticsTeamEvent(
            9999,
            key,
            new StatboticsTeamEventEpa(
                1500.0,
                1480.0,
                new StatboticsTeamEventBreakdown(40.0, null, 30.0, 10.0)));
    when(CLIENT_MOCK.getTeamEventsByEvent(key)).thenReturn(okFetch(20L, List.of(te)));

    assertEquals(1, syncService.syncOne(key, null));

    StatboticsTeamEventRecord row = repo.find(key, 9999);
    assertNull(row.epaAuto(), "flat column must be NULL when upstream is null");
    assertEquals(30.0, row.epaTeleop(), 0.0001);
    assertNotNull(row.breakdownJson(), "breakdown_json must preserve the raw shape");
  }

  // ---------- Error paths ----------

  @Test
  void syncOne_notFoundBumpsStatusAndPreservesPriorData() {
    String key = "2026sb404";
    // Seed a prior successful sync row so bumpStatusOnExistingRows has work to do.
    repo.save(
        new StatboticsTeamEventRecord(
            key,
            1310,
            "SBSYNC_A",
            72.3,
            18.1,
            44.2,
            10.0,
            1600.0,
            1580.0,
            "{\"prior\":true}",
            Instant.parse("2026-04-01T12:00:00Z"),
            200));

    when(CLIENT_MOCK.getTeamEventsByEvent(key))
        .thenReturn(
            StatboticsClientService.TeamEventsFetch.skipped(
                StatboticsClientService.TeamEventsFetchOutcome.NOT_FOUND));

    int result = syncService.syncOne(key, "SBSYNC_A");

    assertEquals(-1, result);
    StatboticsTeamEventRecord saved = repo.find(key, 1310);
    assertEquals(404, saved.lastStatus());
    assertEquals(72.3, saved.epaTotal(), 0.0001, "flat EPA columns must be preserved");
    assertEquals(
        "{\"prior\":true}",
        saved.breakdownJson(),
        "prior breakdown_json must survive a failed fetch");
  }

  @Test
  void syncOne_clientExceptionMarksTransportError() {
    String key = "2026sbboom";
    repo.save(
        new StatboticsTeamEventRecord(
            key,
            1310,
            null,
            1.0,
            null,
            null,
            null,
            null,
            null,
            "{}",
            Instant.parse("2026-04-01T12:00:00Z"),
            200));
    when(CLIENT_MOCK.getTeamEventsByEvent(key))
        .thenThrow(new StatboticsClientException("boom"));

    assertEquals(-1, syncService.syncOne(key, null));
    assertEquals(-1, repo.find(key, 1310).lastStatus());
  }

  @Test
  void syncAllActiveTournaments_continuesAfterIndividualFailure() {
    String okKey = "2026sbok" + (System.currentTimeMillis() % 1000);
    String badKey = "2026sbbd" + (System.currentTimeMillis() % 1000);
    Instant now = Instant.now();
    String okId = "SBSYNC_OK_" + System.currentTimeMillis();
    String badId = "SBSYNC_BAD_" + (System.currentTimeMillis() + 1);
    tournamentService.save(
        new TournamentRecord(
            okId,
            "SBS",
            2026,
            "OK",
            now.minus(1, ChronoUnit.HOURS),
            now.plus(2, ChronoUnit.HOURS),
            3,
            null,
            okKey));
    tournamentService.save(
        new TournamentRecord(
            badId,
            "SBS",
            2026,
            "BAD",
            now.minus(1, ChronoUnit.HOURS),
            now.plus(2, ChronoUnit.HOURS),
            3,
            null,
            badKey));
    watchedTournamentService.watch(okId);
    watchedTournamentService.watch(badId);

    when(CLIENT_MOCK.getTeamEventsByEvent(okKey))
        .thenReturn(okFetch(100L, List.of(teamEvent(1310, okKey, 70.0, 18.0, 42.0, 10.0))));
    when(CLIENT_MOCK.getTeamEventsByEvent(badKey))
        .thenThrow(new StatboticsClientException("explode"));

    syncService.syncAllActiveTournaments();

    assertNotNull(
        repo.find(okKey, 1310),
        "individual tournament failure must not abort the overall sync loop");
  }

  // ---------- Edge cases ----------

  @Test
  void syncOne_realisticBreakdownIsUnderCapAndPersistsNormally() {
    // Negative-case for the 8 KB cap: a realistic 4-field breakdown serializes to <100 bytes,
    // which must NOT trigger the guard. The positive 422-trigger case is covered in
    // StatboticsTeamEventSyncServiceCapTest (which lowers the cap via @Property for easy proof).
    String key = "2026sbunder";
    StatboticsTeamEvent te = teamEvent(1310, key, 73.0, 19.0, 44.0, 10.0);
    when(CLIENT_MOCK.getTeamEventsByEvent(key)).thenReturn(okFetch(30L, List.of(te)));

    assertEquals(1, syncService.syncOne(key, null));

    StatboticsTeamEventRecord saved = repo.find(key, 1310);
    assertEquals(200, saved.lastStatus());
    assertEquals(73.0, saved.epaTotal(), 0.0001);
    assertTrue(
        saved.breakdownJson().length() < StatboticsTeamEventSyncService.DEFAULT_BREAKDOWN_JSON_MAX_BYTES,
        "realistic breakdown must serialize well under the default 8 KB cap");
  }

  @Test
  void syncOne_skipsRowWithNullTeam() {
    // Statbotics should never return a row without a team number, but be defensive.
    String key = "2026sbnoteam";
    StatboticsTeamEvent te =
        new StatboticsTeamEvent(
            null,
            key,
            new StatboticsTeamEventEpa(
                1500.0, 1480.0, new StatboticsTeamEventBreakdown(40.0, 10.0, 25.0, 5.0)));
    StatboticsTeamEvent ok = teamEvent(1310, key, 70.0, 18.0, 42.0, 10.0);
    when(CLIENT_MOCK.getTeamEventsByEvent(key)).thenReturn(okFetch(40L, List.of(te, ok)));

    int written = syncService.syncOne(key, null);

    assertEquals(1, written, "null-team row is skipped, valid row persists");
    assertNotNull(repo.find(key, 1310));
  }

  @Test
  void syncAllActiveTournaments_skipsNullTbaEventKey() {
    Instant now = Instant.now();
    String id = "SBSYNC_NULL_" + System.currentTimeMillis();
    tournamentService.save(
        new TournamentRecord(
            id,
            "SBS",
            2026,
            "NullKey",
            now.minus(1, ChronoUnit.HOURS),
            now.plus(2, ChronoUnit.HOURS),
            3,
            null,
            null));
    watchedTournamentService.watch(id);

    syncService.syncAllActiveTournaments();

    verify(CLIENT_MOCK, never()).getTeamEventsByEvent((String) null);
  }

  @Test
  void syncAllActiveTournaments_skipsBlankTbaEventKey() {
    Instant now = Instant.now();
    String id = "SBSYNC_BLANK_" + System.currentTimeMillis();
    tournamentService.save(
        new TournamentRecord(
            id,
            "SBS",
            2026,
            "BlankKey",
            now.minus(1, ChronoUnit.HOURS),
            now.plus(2, ChronoUnit.HOURS),
            3,
            null,
            "   "));
    watchedTournamentService.watch(id);

    syncService.syncAllActiveTournaments();

    verify(CLIENT_MOCK, never()).getTeamEventsByEvent(eq("   "));
  }
}
