package ca.team1310.ravenbrain.tournament;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ca.team1310.ravenbrain.tbaapi.service.TbaEventRecord;
import ca.team1310.ravenbrain.tbaapi.service.TbaEventRepo;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the read-time merge logic. Uses a real {@link TournamentEnricher} with a mocked
 * {@link TbaEventRepo} — no Micronaut context needed since the enricher's dependencies are
 * lightweight.
 */
public class TournamentEnricherTest {

  private TbaEventRepo tbaEventRepo;
  private TournamentEnricher enricher;

  @BeforeEach
  void setUp() throws Exception {
    tbaEventRepo = mock(TbaEventRepo.class);
    when(tbaEventRepo.findByEventKeyIn(any())).thenReturn(List.of());
    Constructor<TournamentEnricher> ctor =
        TournamentEnricher.class.getDeclaredConstructor(
            TbaEventRepo.class, long.class, long.class, long.class);
    ctor.setAccessible(true);
    // staleThresholdMinutes=90, windowLeadHours=12, windowTailHours=10 (Unit 4 defaults)
    enricher = ctor.newInstance(tbaEventRepo, 90L, 12L, 10L);
  }

  private TournamentRecord record(String manualJson, String tbaKey) {
    return new TournamentRecord(
        "2026ONX",
        "ONX",
        2026,
        "Test Event",
        Instant.parse("2026-03-20T00:00:00Z"),
        Instant.parse("2026-03-22T00:00:00Z"),
        3,
        manualJson,
        tbaKey);
  }

  private TbaEventRecord tba(String webcastsJson, Integer status, Instant lastSync) {
    return new TbaEventRecord("2026onx", webcastsJson, "{}", lastSync, status);
  }

  @Test
  void unmappedTournament_manualOnly_noStaleness() {
    TournamentRecord t = record("[\"https://twitch.tv/manual\"]", null);

    TournamentResponse r = enricher.enrich(t);

    assertEquals(List.of("https://twitch.tv/manual"), r.webcasts());
    assertEquals(List.of(), r.webcastsFromTba());
    assertFalse(r.webcastsStale());
    assertNull(r.webcastsLastSync());
  }

  @Test
  void mappedWithFreshTba_emptyManual_returnsTbaUrls() {
    TournamentRecord t = record(null, "2026onx");
    when(tbaEventRepo.findById("2026onx"))
        .thenReturn(
            Optional.of(
                tba(
                    "[\"https://www.twitch.tv/first\"]",
                    200,
                    Instant.now().minusSeconds(30))));

    TournamentResponse r = enricher.enrich(t);

    assertEquals(List.of("https://www.twitch.tv/first"), r.webcasts());
    assertEquals(List.of("https://www.twitch.tv/first"), r.webcastsFromTba());
    assertFalse(r.webcastsStale());
    assertNotNull(r.webcastsLastSync());
  }

  @Test
  void mergeCanonicalizesAndDeduplicates() {
    TournamentRecord t =
        record(
            "[\"https://Twitch.tv/shared/\",\"https://www.twitch.tv/manual-only\"]", "2026onx");
    when(tbaEventRepo.findById("2026onx"))
        .thenReturn(
            Optional.of(
                tba(
                    "[\"https://twitch.tv/shared\",\"https://www.twitch.tv/tba-only\"]",
                    200,
                    Instant.now().minusSeconds(30))));

    TournamentResponse r = enricher.enrich(t);

    // Three distinct URLs after canonicalization — "https://twitch.tv/shared" appears once
    // (case + trailing-slash collapsed), manual-only and tba-only round out the list.
    assertEquals(
        List.of(
            "https://twitch.tv/shared",
            "https://www.twitch.tv/manual-only",
            "https://www.twitch.tv/tba-only"),
        r.webcasts());
    // webcastsFromTba is the canonicalized TBA list, even though one entry is also in manual.
    assertEquals(
        List.of("https://twitch.tv/shared", "https://www.twitch.tv/tba-only"),
        r.webcastsFromTba());
    // Invariant: every webcastsFromTba entry is in webcasts.
    assertTrue(r.webcasts().containsAll(r.webcastsFromTba()));
  }

  @Test
  void staleWhenLastSyncOlderThanThreshold() {
    TournamentRecord t = record(null, "2026onx");
    when(tbaEventRepo.findById("2026onx"))
        .thenReturn(
            Optional.of(
                tba(
                    "[\"https://twitch.tv/older\"]",
                    200,
                    Instant.now().minusSeconds(3600 * 2)))); // 2h, past 90min threshold

    TournamentResponse r = enricher.enrich(t);

    assertTrue(r.webcastsStale());
    assertEquals(List.of("https://twitch.tv/older"), r.webcasts(), "stale data must still be served");
  }

  @Test
  void staleWhenLastStatusNot200() {
    TournamentRecord t = record(null, "2026onx");
    when(tbaEventRepo.findById("2026onx"))
        .thenReturn(
            Optional.of(tba("[\"https://twitch.tv/prior\"]", 404, Instant.now().minusSeconds(60))));

    TournamentResponse r = enricher.enrich(t);

    assertTrue(r.webcastsStale());
    assertNull(r.webcastsLastSync(), "lastSync is only set when the last attempt succeeded");
    assertEquals(
        List.of("https://twitch.tv/prior"),
        r.webcasts(),
        "previous successful TBA data should still be served on a failed refresh");
  }

  @Test
  void staleWhenTbaKeySetButNoRow() {
    TournamentRecord t = record("[\"https://twitch.tv/manual\"]", "2026onx");
    when(tbaEventRepo.findById("2026onx")).thenReturn(Optional.empty());

    TournamentResponse r = enricher.enrich(t);

    assertTrue(r.webcastsStale());
    assertEquals(List.of("https://twitch.tv/manual"), r.webcasts());
    assertEquals(List.of(), r.webcastsFromTba());
  }

  @Test
  void batchEnrich_usesSingleRepoCall() {
    TournamentRecord a = record(null, "2026ona");
    TournamentRecord b = record(null, "2026onb");
    TournamentRecord c = record("[\"https://twitch.tv/cman\"]", null);
    when(tbaEventRepo.findByEventKeyIn(List.of("2026ona", "2026onb")))
        .thenReturn(
            List.of(
                new TbaEventRecord(
                    "2026ona", "[\"https://twitch.tv/a\"]", "{}", Instant.now(), 200),
                new TbaEventRecord(
                    "2026onb", "[\"https://twitch.tv/b\"]", "{}", Instant.now(), 200)));

    List<TournamentResponse> results = enricher.enrich(List.of(a, b, c));

    assertEquals(3, results.size());
    assertEquals(List.of("https://twitch.tv/a"), results.get(0).webcasts());
    assertEquals(List.of("https://twitch.tv/b"), results.get(1).webcasts());
    assertEquals(List.of("https://twitch.tv/cman"), results.get(2).webcasts());
    verify(tbaEventRepo, times(1)).findByEventKeyIn(any());
    verify(tbaEventRepo, never()).findById(any());
  }

  @Test
  void emptyWebcastsFromBothSources() {
    TournamentRecord t = record(null, null);
    TournamentResponse r = enricher.enrich(t);
    assertEquals(List.of(), r.webcasts());
    assertEquals(List.of(), r.webcastsFromTba());
    assertFalse(r.webcastsStale());
  }

  @Test
  void malformedManualJson_treatedAsEmpty() {
    TournamentRecord t = record("this is not JSON", null);
    TournamentResponse r = enricher.enrich(t);
    assertEquals(List.of(), r.webcasts());
    assertFalse(r.webcastsStale(), "malformed manual data should not be blamed on TBA staleness");
  }
}
