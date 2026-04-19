package ca.team1310.ravenbrain.tbaapi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ca.team1310.ravenbrain.matchvideo.MatchVideoRecord;
import ca.team1310.ravenbrain.matchvideo.MatchVideoService;
import ca.team1310.ravenbrain.tbaapi.fetch.TbaClientException;
import ca.team1310.ravenbrain.tbaapi.model.TbaMatch;
import ca.team1310.ravenbrain.tbaapi.model.TbaMatchAlliance;
import ca.team1310.ravenbrain.tbaapi.model.TbaMatchVideo;
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
 * End-to-end match-sync behaviour. {@link TbaClientService} is mocked at the service boundary so
 * the real {@link TbaMatchSyncService}, persistence via {@link TbaMatchVideoRepo}, the alliance
 * canonicalization, and cross-table disjoint-write contract (admin-owned {@code RB_MATCH_VIDEO}
 * never touched) all run against Testcontainers MySQL.
 *
 * <p>Most {@code syncOne} tests exercise the service directly with a mock TBA key — no RB_TOURNAMENT
 * or RB_WATCHED_TOURNAMENT rows are required because {@code RB_TBA_MATCH_VIDEO} has no FK to
 * either. Only the {@code syncAllActiveTournaments_*} tests exercise the filter and need those
 * tables populated; those tests use a fresh unique tournament id per invocation to avoid cross-
 * test FK cleanup headaches in the shared Testcontainers DB.
 */
@MicronautTest(transactional = false)
public class TbaMatchSyncServiceTest {

  private static final TbaClientService SERVICE_MOCK = mock(TbaClientService.class);

  @Inject TbaMatchSyncService syncService;
  @Inject TbaMatchVideoRepo tbaMatchVideoRepo;
  @Inject TournamentService tournamentService;
  @Inject WatchedTournamentService watchedTournamentService;
  @Inject MatchVideoService matchVideoService;

  @MockBean(TbaClientService.class)
  TbaClientService mockTbaClientService() {
    return SERVICE_MOCK;
  }

  @BeforeEach
  void setUp() {
    reset(SERVICE_MOCK);
    // Default: any TBA call from a leftover active tournament returns an empty match list so the
    // test under scrutiny isn't affected by orphaned tournaments in the shared test DB.
    when(SERVICE_MOCK.getEventMatches(anyString()))
        .thenReturn(
            TbaClientService.EventMatchesFetch.ok(
                new TbaClientService.EventMatchesFetchResult(0L, "[]", List.of())));
  }

  @AfterEach
  void tearDown() {
    // Wipe any RB_TBA_MATCH_VIDEO rows seeded or written by these tests (no FK, safe to nuke all).
    for (TbaMatchVideoRecord r : tbaMatchVideoRepo.findAll()) {
      tbaMatchVideoRepo.deleteById(r.tbaMatchKey());
    }
    // Wipe any admin rows written for ownership-contract tests.
    for (MatchVideoRecord r : matchVideoService.findAll()) {
      if (r.tournamentId() != null && r.tournamentId().startsWith("TBAMSYNC_")) {
        matchVideoService.deleteById(r.id());
      }
    }
    // RB_TOURNAMENT / RB_WATCHED_TOURNAMENT rows are intentionally not cleaned between tests — the
    // default empty-list mock above means their presence does not corrupt subsequent verifications.
  }

  private static TbaMatch match(
      String key,
      String compLevel,
      int number,
      List<String> red,
      List<String> blue,
      List<TbaMatchVideo> videos) {
    return new TbaMatch(
        key,
        compLevel,
        1,
        number,
        new TbaMatch.TbaMatchAlliances(new TbaMatchAlliance(red), new TbaMatchAlliance(blue)),
        videos);
  }

  private TbaClientService.EventMatchesFetch okFetch(long responseId, List<TbaMatch> matches) {
    return TbaClientService.EventMatchesFetch.ok(
        new TbaClientService.EventMatchesFetchResult(responseId, "[]", matches));
  }

  // ---------- Happy paths (direct syncOne — no tournament setup needed) ----------

  @Test
  void syncOne_persistsTwoQmMatchesWithCanonicalTuples() {
    String key = "2026tbamsa";
    List<TbaMatch> matches =
        List.of(
            match(
                "2026tbamsa_qm1",
                "qm",
                1,
                List.of("frc1310", "frc2056", "frc4917"),
                List.of("frc1114", "frc2713", "frc5406"),
                List.of(new TbaMatchVideo("youtube", "abc123"))),
            match(
                "2026tbamsa_qm2",
                "qm",
                2,
                List.of("frc9999", "frc8888", "frc7777"),
                List.of("frc6666", "frc5555", "frc4444"),
                List.of(new TbaMatchVideo("tba", "ignored"))));
    when(SERVICE_MOCK.getEventMatches(key)).thenReturn(okFetch(10L, matches));

    int written = syncService.syncOne(key);

    assertEquals(2, written);
    var a = tbaMatchVideoRepo.findById("2026tbamsa_qm1").orElseThrow();
    assertEquals("1310,2056,4917", a.redTeams());
    assertEquals("1114,2713,5406", a.blueTeams());
    assertEquals("qm", a.compLevel());
    assertEquals(200, a.lastStatus());
    assertEquals("[\"https://www.youtube.com/watch?v=abc123\"]", a.videosJson());
    assertNotNull(a.lastSync());

    var b = tbaMatchVideoRepo.findById("2026tbamsa_qm2").orElseThrow();
    assertEquals(
        "[\"https://www.thebluealliance.com/match/2026tbamsa_qm2\"]", b.videosJson());
  }

  @Test
  void syncOne_isIdempotent() {
    String key = "2026tbamsidem";
    List<TbaMatch> matches =
        List.of(
            match(
                "2026tbamsidem_qm1",
                "qm",
                1,
                List.of("frc1310", "frc2056", "frc4917"),
                List.of("frc1114", "frc2713", "frc5406"),
                List.of(new TbaMatchVideo("youtube", "xyz"))));
    when(SERVICE_MOCK.getEventMatches(key)).thenReturn(okFetch(20L, matches), okFetch(21L, matches));

    syncService.syncOne(key);
    syncService.syncOne(key);

    assertEquals(1, tbaMatchVideoRepo.findByTbaEventKey(key).size(),
        "repeated syncs must upsert on tba_match_key PK — no duplicates");
  }

  @Test
  void syncOne_persistsPlayoffMatchWithCompLevelQf() {
    String key = "2026tbamsqf";
    List<TbaMatch> matches =
        List.of(
            match(
                "2026tbamsqf_qf1m2",
                "qf",
                2,
                List.of("frc1310", "frc2056", "frc4917"),
                List.of("frc1114", "frc2713", "frc5406"),
                List.of()));
    when(SERVICE_MOCK.getEventMatches(key)).thenReturn(okFetch(30L, matches));

    assertEquals(1, syncService.syncOne(key));

    var row = tbaMatchVideoRepo.findById("2026tbamsqf_qf1m2").orElseThrow();
    assertEquals("qf", row.compLevel());
    assertEquals("1310,2056,4917", row.redTeams());
    assertEquals("1114,2713,5406", row.blueTeams());
  }

  @Test
  void syncOne_sortsAllianceOrderCanonically() {
    String key = "2026tbamssort";
    List<TbaMatch> matches =
        List.of(
            match(
                "2026tbamssort_qm1",
                "qm",
                1,
                List.of("frc4917", "frc1310", "frc2056"), // unsorted
                List.of("frc5406", "frc1114", "frc2713"),
                List.of()));
    when(SERVICE_MOCK.getEventMatches(key)).thenReturn(okFetch(40L, matches));

    assertEquals(1, syncService.syncOne(key));

    var row = tbaMatchVideoRepo.findById("2026tbamssort_qm1").orElseThrow();
    assertEquals("1310,2056,4917", row.redTeams());
    assertEquals("1114,2713,5406", row.blueTeams());
  }

  @Test
  void syncOne_persistsEmptyVideoListAsEmptyJsonArray() {
    String key = "2026tbamsempty";
    List<TbaMatch> matches =
        List.of(
            match(
                "2026tbamsempty_qm1",
                "qm",
                1,
                List.of("frc1310", "frc2056", "frc4917"),
                List.of("frc1114", "frc2713", "frc5406"),
                List.of()));
    when(SERVICE_MOCK.getEventMatches(key)).thenReturn(okFetch(50L, matches));

    assertEquals(1, syncService.syncOne(key));
    assertEquals("[]", tbaMatchVideoRepo.findById("2026tbamsempty_qm1").orElseThrow().videosJson());
  }

  @Test
  void syncOne_dropsUnsupportedVideoTypes() {
    String key = "2026tbamsunsupp";
    List<TbaMatch> matches =
        List.of(
            match(
                "2026tbamsunsupp_qm1",
                "qm",
                1,
                List.of("frc1310", "frc2056", "frc4917"),
                List.of("frc1114", "frc2713", "frc5406"),
                List.of(new TbaMatchVideo("iframe", "<html>"), new TbaMatchVideo("html5", "r"))));
    when(SERVICE_MOCK.getEventMatches(key)).thenReturn(okFetch(60L, matches));

    assertEquals(1, syncService.syncOne(key));
    var row = tbaMatchVideoRepo.findById("2026tbamsunsupp_qm1").orElseThrow();
    assertEquals("[]", row.videosJson());
    assertEquals(200, row.lastStatus());
  }

  @Test
  void syncOne_persistsCompLevelEf() {
    // Eighthfinal (ef) is eligible per R2 — match level scope covers all playoffs.
    String key = "2026tbamsef";
    List<TbaMatch> matches =
        List.of(
            match(
                "2026tbamsef_ef1m1",
                "ef",
                1,
                List.of("frc1310", "frc2056", "frc4917"),
                List.of("frc1114", "frc2713", "frc5406"),
                List.of()));
    when(SERVICE_MOCK.getEventMatches(key)).thenReturn(okFetch(70L, matches));

    assertEquals(1, syncService.syncOne(key));
    assertEquals("ef", tbaMatchVideoRepo.findById("2026tbamsef_ef1m1").orElseThrow().compLevel());
  }

  // ---------- Edge cases ----------

  @Test
  void syncOne_skipsMatchWithMalformedTeamKey() {
    String key = "2026tbamsbad";
    List<TbaMatch> matches =
        List.of(
            match(
                "2026tbamsbad_qm1",
                "qm",
                1,
                List.of("frcABC", "frc2056", "frc4917"), // malformed
                List.of("frc1114", "frc2713", "frc5406"),
                List.of()),
            match(
                "2026tbamsbad_qm2",
                "qm",
                2,
                List.of("frc1310", "frc2056", "frc4917"),
                List.of("frc1114", "frc2713", "frc5406"),
                List.of()));
    when(SERVICE_MOCK.getEventMatches(key)).thenReturn(okFetch(80L, matches));

    int written = syncService.syncOne(key);

    assertEquals(1, written, "malformed alliance should skip that one match, not abort the batch");
    assertTrue(tbaMatchVideoRepo.findById("2026tbamsbad_qm1").isEmpty());
    assertTrue(tbaMatchVideoRepo.findById("2026tbamsbad_qm2").isPresent());
  }

  // ---------- Error paths ----------

  @Test
  void syncOne_404BumpsStatusOnExistingRows() {
    String key = "2026tbams404";
    // Seed a previously-synced row so bumpStatusOnExistingRows has something to update.
    tbaMatchVideoRepo.save(
        new TbaMatchVideoRecord(
            key + "_qm1",
            key,
            "qm",
            1,
            "1310,2056,4917",
            "1114,2713,5406",
            "[\"https://www.youtube.com/watch?v=prior\"]",
            Instant.parse("2026-04-01T12:00:00Z"),
            200));
    when(SERVICE_MOCK.getEventMatches(key))
        .thenReturn(
            TbaClientService.EventMatchesFetch.skipped(
                TbaClientService.EventFetchOutcome.NOT_FOUND));

    int result = syncService.syncOne(key);

    assertEquals(-1, result);
    var saved = tbaMatchVideoRepo.findById(key + "_qm1").orElseThrow();
    assertEquals(404, saved.lastStatus());
    assertEquals(
        "[\"https://www.youtube.com/watch?v=prior\"]",
        saved.videosJson(),
        "previous videos_json must be preserved so read layer serves stale");
  }

  @Test
  void syncOne_transportExceptionRecordsTransportError() {
    String key = "2026tbamsboom";
    tbaMatchVideoRepo.save(
        new TbaMatchVideoRecord(
            key + "_qm1",
            key,
            "qm",
            1,
            "1310,2056,4917",
            "1114,2713,5406",
            "[]",
            Instant.parse("2026-04-01T12:00:00Z"),
            200));
    when(SERVICE_MOCK.getEventMatches(key)).thenThrow(new TbaClientException("boom"));

    assertEquals(-1, syncService.syncOne(key));
    assertEquals(-1, tbaMatchVideoRepo.findById(key + "_qm1").orElseThrow().lastStatus());
  }

  // ---------- Filter path (exercises the full sync-all loop) ----------

  @Test
  void syncAllActiveTournaments_skipsMalformedTbaEventKey() {
    // Create a watched tournament with an invalid tba_event_key and verify the regex guard blocks
    // the outbound call. Other leftover active tournaments in the test DB will have their mock
    // return an empty list (see setUp default), so they do not pollute this assertion.
    String tid = "TBAMSYNC_BADKEY_" + System.currentTimeMillis();
    Instant now = Instant.now();
    tournamentService.save(
        new TournamentRecord(
            tid,
            "TBAMS",
            2026,
            "BadKey",
            now.minus(1, ChronoUnit.HOURS),
            now.plus(2, ChronoUnit.HOURS),
            3,
            null,
            "NOT-A-KEY"));
    watchedTournamentService.watch(tid);

    syncService.syncAllActiveTournaments();

    verify(SERVICE_MOCK, never()).getEventMatches(eq("NOT-A-KEY"));
  }

  @Test
  void syncAllActiveTournaments_skipsNullTbaEventKey() {
    // A watched tournament with tba_event_key = NULL must not produce an outbound call. Any
    // leftover tournaments with valid keys will be called but return empty (see setUp), so their
    // presence does not affect the assertion — we only verify no call with a NULL key, which the
    // filter guarantees.
    String tid = "TBAMSYNC_NULLKEY_" + System.currentTimeMillis();
    Instant now = Instant.now();
    tournamentService.save(
        new TournamentRecord(
            tid,
            "TBAMS",
            2026,
            "NullKey",
            now.minus(1, ChronoUnit.HOURS),
            now.plus(2, ChronoUnit.HOURS),
            3,
            null,
            null));
    watchedTournamentService.watch(tid);

    syncService.syncAllActiveTournaments();

    // Null is filtered out before outbound — verify no call with a null argument.
    verify(SERVICE_MOCK, never()).getEventMatches((String) null);
  }

  @Test
  void syncAllActiveTournaments_continuesAfterIndividualFailure() {
    String okKey = "2026tbamsok" + (System.currentTimeMillis() % 1000);
    String badKey = "2026tbamsbd" + (System.currentTimeMillis() % 1000);
    Instant now = Instant.now();
    String okId = "TBAMSYNC_OK2_" + System.currentTimeMillis();
    String badId = "TBAMSYNC_BAD2_" + (System.currentTimeMillis() + 1);
    tournamentService.save(
        new TournamentRecord(
            okId, "TBAMS", 2026, "OK",
            now.minus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS), 3, null, okKey));
    tournamentService.save(
        new TournamentRecord(
            badId, "TBAMS", 2026, "BAD",
            now.minus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS), 3, null, badKey));
    watchedTournamentService.watch(okId);
    watchedTournamentService.watch(badId);

    when(SERVICE_MOCK.getEventMatches(okKey))
        .thenReturn(
            okFetch(
                100L,
                List.of(
                    match(
                        okKey + "_qm1",
                        "qm",
                        1,
                        List.of("frc1310", "frc2056", "frc4917"),
                        List.of("frc1114", "frc2713", "frc5406"),
                        List.of()))));
    when(SERVICE_MOCK.getEventMatches(badKey)).thenThrow(new TbaClientException("explode"));

    syncService.syncAllActiveTournaments();

    assertTrue(
        tbaMatchVideoRepo.findById(okKey + "_qm1").isPresent(),
        "individual tournament failure must not abort the overall loop");
  }

  // ---------- Ownership contract ----------

  @Test
  void syncOne_doesNotTouchAdminMatchVideoTable() {
    // RB_MATCH_VIDEO has an FK to RB_TOURNAMENT, so a tournament row must exist to seed an admin
    // entry. Created with unique id per run so we don't collide with leftover state.
    String fakeTournamentId = "TBAMSYNC_OWN_" + System.currentTimeMillis();
    Instant now = Instant.now();
    tournamentService.save(
        new TournamentRecord(
            fakeTournamentId,
            "TBAMS",
            2026,
            "Own",
            now.minus(1, ChronoUnit.HOURS),
            now.plus(2, ChronoUnit.HOURS),
            3,
            null,
            null));
    MatchVideoRecord seeded =
        matchVideoService.save(
            new MatchVideoRecord(
                null,
                fakeTournamentId,
                "Qualification",
                1,
                "Driver Station",
                "https://admin.example/x"));

    String key = "2026tbamsown";
    List<TbaMatch> matches =
        List.of(
            match(
                key + "_qm1",
                "qm",
                1,
                List.of("frc1310", "frc2056", "frc4917"),
                List.of("frc1114", "frc2713", "frc5406"),
                List.of(new TbaMatchVideo("youtube", "abc"))));
    when(SERVICE_MOCK.getEventMatches(key)).thenReturn(okFetch(200L, matches));

    syncService.syncOne(key);

    MatchVideoRecord reloaded = matchVideoService.findById(seeded.id()).orElseThrow();
    assertEquals("https://admin.example/x", reloaded.videoUrl());
    assertEquals("Driver Station", reloaded.label());
    // And the TBA row lives in its own table.
    assertTrue(tbaMatchVideoRepo.findById(key + "_qm1").isPresent());
  }
}
