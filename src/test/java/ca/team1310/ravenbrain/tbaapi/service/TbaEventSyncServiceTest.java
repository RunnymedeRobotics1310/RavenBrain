package ca.team1310.ravenbrain.tbaapi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ca.team1310.ravenbrain.tbaapi.fetch.TbaClientException;
import ca.team1310.ravenbrain.tbaapi.model.TbaEvent;
import ca.team1310.ravenbrain.tbaapi.model.TbaEventOprs;
import ca.team1310.ravenbrain.tbaapi.model.TbaWebcast;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end sync service behaviour: we mock {@link TbaClientService} at the service boundary so
 * the real {@link TbaEventSyncService}, persistence via {@link TbaEventRepo}, and cross-service
 * interactions (including the ownership contract — RB_TOURNAMENT is never written) all run
 * against Testcontainers MySQL.
 */
@MicronautTest(transactional = false)
public class TbaEventSyncServiceTest {

  private static final TbaClientService SERVICE_MOCK = mock(TbaClientService.class);

  @Inject TbaEventSyncService syncService;
  @Inject TbaEventRepo tbaEventRepo;
  @Inject TbaEventOprsRepo tbaEventOprsRepo;
  @Inject TournamentService tournamentService;

  @MockBean(TbaClientService.class)
  TbaClientService mockTbaClientService() {
    return SERVICE_MOCK;
  }

  @BeforeEach
  void setUp() {
    reset(SERVICE_MOCK);
  }

  @AfterEach
  void tearDown() {
    for (TbaEventRecord r : tbaEventRepo.findAll()) {
      tbaEventRepo.deleteById(r.eventKey());
    }
    for (TbaEventOprsRecord r : tbaEventOprsRepo.findAll()) {
      tbaEventOprsRepo.deleteByTbaEventKey(r.tbaEventKey());
    }
    tournamentService
        .findAll()
        .forEach(
            t -> {
              if (t.id().startsWith("TBASYNC_")) tournamentService.deleteById(t.id());
            });
  }

  private TournamentRecord mappedTournament(String id, String tbaKey) {
    var t =
        new TournamentRecord(
            id,
            "TBASYNC",
            2026,
            "TBA Sync Test",
            Instant.parse("2026-03-20T00:00:00Z"),
            Instant.parse("2026-03-22T00:00:00Z"),
            3,
            null,
            tbaKey);
    tournamentService.save(t);
    return t;
  }

  private TbaClientService.EventFetch okFetch(long responseId, TbaEvent event, String rawBody) {
    return TbaClientService.EventFetch.ok(
        new TbaClientService.EventFetchResult(responseId, rawBody, event));
  }

  private TbaClientService.EventOprsFetch okOprsFetch(
      long responseId, TbaEventOprs oprs, String rawBody) {
    return TbaClientService.EventOprsFetch.ok(
        new TbaClientService.EventOprsFetchResult(responseId, rawBody, oprs));
  }

  private TbaClientService.EventOprsFetch skippedOprsFetch(TbaClientService.EventFetchOutcome o) {
    return TbaClientService.EventOprsFetch.skipped(o);
  }

  private static TbaEventOprs oprsResponse(
      Map<String, Double> oprs, Map<String, Double> dprs, Map<String, Double> ccwms) {
    return new TbaEventOprs(oprs, dprs, ccwms);
  }

  @Test
  void syncOne_persistsCanonicalWebcastListAndMarksProcessed() {
    String key = "2026tbasync";
    mappedTournament("TBASYNC_A", key);
    TbaEvent event =
        new TbaEvent(
            key,
            "TBA Sync Test",
            2026,
            "tbasync",
            List.of(
                new TbaWebcast("youtube", "abc123", null, null),
                new TbaWebcast("twitch", "firstinspires", null, null)));
    when(SERVICE_MOCK.getEvent(key)).thenReturn(okFetch(55L, event, "{\"raw\":true}"));

    boolean result = syncService.syncOne(key);

    assertTrue(result);
    TbaEventRecord saved = tbaEventRepo.findById(key).orElseThrow();
    assertEquals(200, saved.lastStatus());
    assertEquals(
        "[\"https://www.youtube.com/watch?v=abc123\",\"https://www.twitch.tv/firstinspires\"]",
        saved.webcastsJson());
    assertEquals("{\"raw\":true}", saved.rawEventJson());
    assertNotNull(saved.lastSync());
    verify(SERVICE_MOCK).markProcessed(55L);

    // Ownership contract: TBA sync never touches RB_TOURNAMENT.
    var tournament = tournamentService.findById("TBASYNC_A").orElseThrow();
    assertNull(tournament.manualWebcasts(), "manual_webcasts must not be written by TBA sync");
  }

  @Test
  void syncOne_emptyTbaWebcastsPersistsEmptyArray() {
    String key = "2026tbaempty";
    mappedTournament("TBASYNC_B", key);
    TbaEvent event = new TbaEvent(key, "Empty", 2026, "tbaempty", List.of());
    when(SERVICE_MOCK.getEvent(key)).thenReturn(okFetch(56L, event, "{}"));

    assertTrue(syncService.syncOne(key));

    TbaEventRecord saved = tbaEventRepo.findById(key).orElseThrow();
    assertEquals("[]", saved.webcastsJson());
    assertEquals(200, saved.lastStatus());
  }

  @Test
  void syncOne_notFoundRecordsStatusAndPreservesPreviousWebcasts() {
    String key = "2026tbamissing";
    // Seed a prior successful sync.
    tbaEventRepo.save(
        new TbaEventRecord(
            key,
            "[\"https://twitch.tv/prior\"]",
            "{\"prior\":true}",
            Instant.parse("2026-04-01T12:00:00Z"),
            200));
    when(SERVICE_MOCK.getEvent(key))
        .thenReturn(TbaClientService.EventFetch.skipped(TbaClientService.EventFetchOutcome.NOT_FOUND));

    assertFalse(syncService.syncOne(key));

    TbaEventRecord saved = tbaEventRepo.findById(key).orElseThrow();
    assertEquals(404, saved.lastStatus());
    assertEquals(
        "[\"https://twitch.tv/prior\"]",
        saved.webcastsJson(),
        "previous successful webcasts_json must be preserved so the read layer can serve stale");
    assertEquals("{\"prior\":true}", saved.rawEventJson());
  }

  @Test
  void syncOne_clientExceptionRecordsTransportError() {
    String key = "2026tbabroken";
    when(SERVICE_MOCK.getEvent(key)).thenThrow(new TbaClientException("boom"));

    assertFalse(syncService.syncOne(key));

    TbaEventRecord saved = tbaEventRepo.findById(key).orElseThrow();
    assertEquals(-1, saved.lastStatus());
  }

  @Test
  void syncAllMappedTournaments_skipsNullTbaEventKey() {
    // Tournament exists but tba_event_key is NULL — should be ignored by the sync loop.
    var unmapped =
        new TournamentRecord(
            "TBASYNC_UNMAPPED",
            "UNMAP",
            2026,
            "Unmapped",
            Instant.parse("2026-03-20T00:00:00Z"),
            Instant.parse("2026-03-22T00:00:00Z"),
            3,
            null,
            null);
    tournamentService.save(unmapped);

    syncService.syncAllMappedTournaments();

    verify(SERVICE_MOCK, never()).getEvent(anyString());
    assertTrue(tbaEventRepo.findAll().iterator().hasNext() == false);
  }

  @Test
  void syncAllMappedTournaments_continuesAfterIndividualFailure() {
    String ok = "2026tbaok";
    String bad = "2026tbabad";
    mappedTournament("TBASYNC_OK", ok);
    mappedTournament("TBASYNC_BAD", bad);

    when(SERVICE_MOCK.getEvent(ok))
        .thenReturn(
            okFetch(
                1L,
                new TbaEvent(
                    ok,
                    "OK",
                    2026,
                    "tbaok",
                    List.of(new TbaWebcast("youtube", "ok", null, null))),
                "{}"));
    when(SERVICE_MOCK.getEvent(bad)).thenThrow(new TbaClientException("explode"));
    // The scheduled loop now also invokes getEventOprs for every tournament; stub empty skips.
    when(SERVICE_MOCK.getEventOprs(ok))
        .thenReturn(skippedOprsFetch(TbaClientService.EventFetchOutcome.NOT_FOUND));
    when(SERVICE_MOCK.getEventOprs(bad))
        .thenReturn(skippedOprsFetch(TbaClientService.EventFetchOutcome.NOT_FOUND));

    syncService.syncAllMappedTournaments();

    assertEquals(200, tbaEventRepo.findById(ok).orElseThrow().lastStatus());
    assertEquals(-1, tbaEventRepo.findById(bad).orElseThrow().lastStatus());
  }

  @Test
  void syncOne_isIdempotentOnRepeatedSuccess() {
    String key = "2026tbaidem";
    mappedTournament("TBASYNC_IDEM", key);
    TbaEvent event =
        new TbaEvent(
            key,
            "Idem",
            2026,
            "tbaidem",
            List.of(new TbaWebcast("youtube", "xyz", null, null)));
    when(SERVICE_MOCK.getEvent(key))
        .thenReturn(okFetch(10L, event, "{}"), okFetch(11L, event, "{}"));

    syncService.syncOne(key);
    syncService.syncOne(key);

    long rowCount = 0;
    for (TbaEventRecord ignored : tbaEventRepo.findAll()) rowCount++;
    assertEquals(1L, rowCount, "repeated syncs must not create duplicate rows (upsert by PK)");
    verify(SERVICE_MOCK, times(2)).getEvent(key);
    verify(SERVICE_MOCK).markProcessed(10L);
    verify(SERVICE_MOCK).markProcessed(11L);
  }

  // -------------------------------------------------------------------------------------------
  // OPR sync tests (Unit 3)
  // -------------------------------------------------------------------------------------------

  @Test
  void syncOneOprs_persistsSixTeamsFromFullResponse() {
    String key = "2026tbaoprs";
    Map<String, Double> oprs = new HashMap<>();
    Map<String, Double> dprs = new HashMap<>();
    Map<String, Double> ccwms = new HashMap<>();
    for (int i = 1; i <= 6; i++) {
      oprs.put("frc" + (1310 + i), 40.0 + i);
      dprs.put("frc" + (1310 + i), 10.0 + i);
      ccwms.put("frc" + (1310 + i), 30.0 + i);
    }
    when(SERVICE_MOCK.getEventOprs(key))
        .thenReturn(okOprsFetch(77L, oprsResponse(oprs, dprs, ccwms), "{}"));

    boolean result = syncService.syncOneOprs(key);

    assertTrue(result);
    List<TbaEventOprsRecord> rows = tbaEventOprsRepo.findByTbaEventKey(key);
    assertEquals(6, rows.size());
    for (TbaEventOprsRecord row : rows) {
      assertEquals(200, row.lastStatus());
      assertNotNull(row.opr());
      assertNotNull(row.dpr());
      assertNotNull(row.ccwm());
      assertNotNull(row.lastSync());
    }
    TbaEventOprsRecord team1311 = tbaEventOprsRepo.find(key, 1311);
    assertNotNull(team1311);
    assertEquals(41.0, team1311.opr(), 0.0001);
    assertEquals(11.0, team1311.dpr(), 0.0001);
    assertEquals(31.0, team1311.ccwm(), 0.0001);
    verify(SERVICE_MOCK).markProcessed(77L);
  }

  @Test
  void syncOneOprs_upsertIsIdempotentOnUnchangedValues() {
    String key = "2026tbaoprsidem";
    TbaEventOprs payload =
        oprsResponse(
            Map.of("frc1310", 42.5), Map.of("frc1310", 12.0), Map.of("frc1310", 30.5));
    when(SERVICE_MOCK.getEventOprs(key))
        .thenReturn(
            okOprsFetch(1L, payload, "{}"), okOprsFetch(2L, payload, "{}"));

    syncService.syncOneOprs(key);
    syncService.syncOneOprs(key);

    List<TbaEventOprsRecord> rows = tbaEventOprsRepo.findByTbaEventKey(key);
    assertEquals(1, rows.size(), "repeated syncs must not create duplicate rows (upsert by PK)");
    assertEquals(42.5, rows.get(0).opr(), 0.0001);
    verify(SERVICE_MOCK).markProcessed(1L);
    verify(SERVICE_MOCK).markProcessed(2L);
  }

  @Test
  void syncOneOprs_missingDprsBlockLeavesDprColumnNull() {
    String key = "2026tbaoprspartial";
    // dprs block absent entirely — partial upstream state.
    TbaEventOprs payload =
        oprsResponse(Map.of("frc1310", 42.5), null, Map.of("frc1310", 30.5));
    when(SERVICE_MOCK.getEventOprs(key)).thenReturn(okOprsFetch(3L, payload, "{}"));

    assertTrue(syncService.syncOneOprs(key));

    TbaEventOprsRecord row = tbaEventOprsRepo.find(key, 1310);
    assertNotNull(row);
    assertEquals(42.5, row.opr(), 0.0001);
    assertNull(row.dpr());
    assertEquals(30.5, row.ccwm(), 0.0001);
    assertEquals(200, row.lastStatus());
  }

  @Test
  void syncOneOprs_malformedTeamKeyIsSkippedAndOthersPersist() {
    String key = "2026tbaoprsbadkey";
    Map<String, Double> oprs = new HashMap<>();
    oprs.put("frcABC", 1.0); // malformed — must be skipped
    oprs.put("frc1310", 42.5);
    Map<String, Double> dprs = Map.of("frc1310", 12.0);
    Map<String, Double> ccwms = Map.of("frc1310", 30.5);
    when(SERVICE_MOCK.getEventOprs(key))
        .thenReturn(okOprsFetch(4L, oprsResponse(oprs, dprs, ccwms), "{}"));

    assertTrue(syncService.syncOneOprs(key));

    List<TbaEventOprsRecord> rows = tbaEventOprsRepo.findByTbaEventKey(key);
    assertEquals(1, rows.size(), "only the valid team survives — malformed key logged + skipped");
    assertEquals(1310, rows.get(0).teamNumber());
    assertEquals(42.5, rows.get(0).opr(), 0.0001);
  }

  @Test
  void syncOneOprs_notFoundPreservesPriorRowsWithStatus404() {
    String key = "2026tbaoprs404";
    // Seed two prior rows from a hypothetical earlier success.
    tbaEventOprsRepo.save(
        new TbaEventOprsRecord(key, 1310, 42.5, 12.0, 30.5, Instant.parse("2026-04-01T00:00:00Z"), 200));
    tbaEventOprsRepo.save(
        new TbaEventOprsRecord(key, 2056, 55.1, 14.2, 40.9, Instant.parse("2026-04-01T00:00:00Z"), 200));
    when(SERVICE_MOCK.getEventOprs(key))
        .thenReturn(skippedOprsFetch(TbaClientService.EventFetchOutcome.NOT_FOUND));

    assertFalse(syncService.syncOneOprs(key));

    List<TbaEventOprsRecord> rows = tbaEventOprsRepo.findByTbaEventKey(key);
    assertEquals(2, rows.size());
    for (TbaEventOprsRecord row : rows) {
      assertEquals(404, row.lastStatus(), "prior row gets status bumped to 404");
      assertNotNull(row.opr(), "prior opr value must be preserved");
      assertNotNull(row.dpr(), "prior dpr value must be preserved");
      assertNotNull(row.ccwm(), "prior ccwm value must be preserved");
    }
  }

  @Test
  void syncOneOprs_clientExceptionRecordsTransportError() {
    String key = "2026tbaoprsboom";
    tbaEventOprsRepo.save(
        new TbaEventOprsRecord(key, 1310, 42.5, 12.0, 30.5, Instant.parse("2026-04-01T00:00:00Z"), 200));
    when(SERVICE_MOCK.getEventOprs(key)).thenThrow(new TbaClientException("boom"));

    assertFalse(syncService.syncOneOprs(key));

    TbaEventOprsRecord row = tbaEventOprsRepo.find(key, 1310);
    assertNotNull(row);
    assertEquals(-1, row.lastStatus());
    assertEquals(42.5, row.opr(), 0.0001, "prior opr preserved through transport failure");
  }

  @Test
  void syncAllMappedTournaments_oprFetchFailureDoesNotBlockOtherTournaments() {
    String ok = "2026tbaoprmixok";
    String bad = "2026tbaoprmixbad";
    mappedTournament("TBASYNC_OPR_OK", ok);
    mappedTournament("TBASYNC_OPR_BAD", bad);

    // Both event-level syncs succeed so the loop definitely reaches the OPR block.
    TbaEvent okEvent =
        new TbaEvent(
            ok, "OK", 2026, "tbaoprmixok", List.of(new TbaWebcast("youtube", "ok", null, null)));
    TbaEvent badEvent =
        new TbaEvent(
            bad,
            "BAD",
            2026,
            "tbaoprmixbad",
            List.of(new TbaWebcast("youtube", "bad", null, null)));
    when(SERVICE_MOCK.getEvent(ok)).thenReturn(okFetch(100L, okEvent, "{}"));
    when(SERVICE_MOCK.getEvent(bad)).thenReturn(okFetch(101L, badEvent, "{}"));
    // ok tournament: OPR fetch succeeds.
    when(SERVICE_MOCK.getEventOprs(ok))
        .thenReturn(
            okOprsFetch(
                200L,
                oprsResponse(Map.of("frc1310", 42.5), Map.of("frc1310", 12.0), Map.of("frc1310", 30.5)),
                "{}"));
    // bad tournament: OPR fetch throws — the per-tournament try/catch must isolate it.
    when(SERVICE_MOCK.getEventOprs(bad)).thenThrow(new TbaClientException("opr explode"));

    syncService.syncAllMappedTournaments();

    // ok tournament persists a row; bad tournament has no prior rows, persistStatusOnly is a no-op.
    TbaEventOprsRecord okRow = tbaEventOprsRepo.find(ok, 1310);
    assertNotNull(okRow, "ok tournament OPR must persist even when another tournament's OPR fails");
    assertEquals(200, okRow.lastStatus());
    assertEquals(42.5, okRow.opr(), 0.0001);
    assertTrue(
        tbaEventOprsRepo.findByTbaEventKey(bad).isEmpty(),
        "bad tournament has no prior rows — persistStatusOnly is a no-op");
    // Both event-level syncs still landed.
    assertEquals(200, tbaEventRepo.findById(ok).orElseThrow().lastStatus());
    assertEquals(200, tbaEventRepo.findById(bad).orElseThrow().lastStatus());
  }

  @Test
  void syncAllMappedTournaments_oprRidesAlongWithEventSync() {
    // Integration: after the scheduled loop runs, TbaEventOprsRepo.findByTbaEventKey(key) returns
    // the populated rows. This proves OPR sync is wired into the same pass as event sync.
    String key = "2026tbaoprintegration";
    mappedTournament("TBASYNC_OPR_INT", key);
    TbaEvent event =
        new TbaEvent(
            key,
            "Integration",
            2026,
            "tbaoprintegration",
            List.of(new TbaWebcast("youtube", "vid", null, null)));
    when(SERVICE_MOCK.getEvent(key)).thenReturn(okFetch(300L, event, "{}"));
    when(SERVICE_MOCK.getEventOprs(key))
        .thenReturn(
            okOprsFetch(
                301L,
                oprsResponse(
                    Map.of("frc1310", 42.5, "frc2056", 55.1),
                    Map.of("frc1310", 12.0, "frc2056", 14.2),
                    Map.of("frc1310", 30.5, "frc2056", 40.9)),
                "{}"));

    syncService.syncAllMappedTournaments();

    List<TbaEventOprsRecord> rows = tbaEventOprsRepo.findByTbaEventKey(key);
    assertEquals(2, rows.size());
  }
}
