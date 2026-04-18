package ca.team1310.ravenbrain.tbaapi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ca.team1310.ravenbrain.tbaapi.fetch.TbaClientException;
import ca.team1310.ravenbrain.tbaapi.model.TbaEvent;
import ca.team1310.ravenbrain.tbaapi.model.TbaWebcast;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
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
}
