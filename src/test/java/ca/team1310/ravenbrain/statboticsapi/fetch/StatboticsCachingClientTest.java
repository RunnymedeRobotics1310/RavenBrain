package ca.team1310.ravenbrain.statboticsapi.fetch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/**
 * Pure-unit behaviour tests for {@link StatboticsCachingClient}. Both collaborators are mocked so
 * these tests do not depend on the {@code RB_STATBOTICS_RESPONSES} table (which Unit 2 creates via
 * V35 migration). When V35 lands, Unit 2 will add integration tests that exercise the full DB
 * stack.
 *
 * <p>Covers the cache-hit / TTL-expiry / error-path contract that downstream sync services depend
 * on, matching the test scenarios called out in the P1 plan for Unit 1.
 */
public class StatboticsCachingClientTest {

  private static final long TTL_SECONDS = 60L;

  private StatboticsClient client;
  private StatboticsRawResponseRepo repo;
  private StatboticsCachingClient caching;

  private StatboticsCachingClient build() {
    client = mock(StatboticsClient.class);
    repo = mock(StatboticsRawResponseRepo.class);
    caching = new StatboticsCachingClient(client, repo, TTL_SECONDS);
    return caching;
  }

  /** Build a cached row as if previously saved: non-null id, given lastcheck, 200 status. */
  private static StatboticsRawResponse cachedRow(long id, String uri, Instant lastcheck, String body) {
    return new StatboticsRawResponse(id, lastcheck, lastcheck, null, false, 200, uri, body);
  }

  /** Build a fresh-from-network response: id null (pre-persist), current lastcheck. */
  private static StatboticsRawResponse networkRow(String uri, int status, String body) {
    return new StatboticsRawResponse(
        null, Instant.now(), Instant.now(), null, false, status, uri, body);
  }

  @Test
  void firstCallHitsNetworkAndPersists() {
    build();
    String uri = "v3/team_events?event=2026onto";
    StatboticsRawResponse fresh = networkRow(uri, 200, "[{\"team\":1310}]");
    StatboticsRawResponse persisted = cachedRow(42L, uri, Instant.now(), "[{\"team\":1310}]");

    when(repo.find(uri)).thenReturn(null, persisted);
    when(client.get(uri)).thenReturn(fresh);

    StatboticsRawResponse result = caching.fetch(uri);

    assertSame(persisted, result);
    assertEquals(42L, result.id());
    verify(client, times(1)).get(uri);
    verify(repo, times(1)).save(fresh);
    verify(repo, never()).update(any());
  }

  @Test
  void secondCallWithinTtlServesCacheWithoutNetwork() {
    build();
    String uri = "v3/team_events?event=2026onto";
    // Cached 10s ago — well within the 60s TTL.
    StatboticsRawResponse cached =
        cachedRow(42L, uri, Instant.now().minus(10, ChronoUnit.SECONDS), "[{\"team\":1310}]");
    when(repo.find(uri)).thenReturn(cached);

    StatboticsRawResponse result = caching.fetch(uri);

    assertSame(cached, result);
    verify(client, never()).get(anyString());
    verify(repo, never()).save(any());
    verify(repo, never()).update(any());
  }

  @Test
  void callAfterTtlExpiryRefetchesAndUpdatesLastcheck() {
    build();
    String uri = "v3/team_events?event=2026onto";
    Instant staleMoment = Instant.now().minus(TTL_SECONDS + 30, ChronoUnit.SECONDS);
    StatboticsRawResponse stale = cachedRow(42L, uri, staleMoment, "[{\"team\":1310,\"old\":true}]");
    StatboticsRawResponse fresh = networkRow(uri, 200, "[{\"team\":1310,\"new\":true}]");

    when(repo.find(uri)).thenReturn(stale);
    when(client.get(uri)).thenReturn(fresh);
    when(repo.update(any(StatboticsRawResponse.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    StatboticsRawResponse result = caching.fetch(uri);

    verify(client, times(1)).get(uri);
    // The update preserves the cache row's id but carries forward fresh body + status and a new
    // lastcheck at ~now — proves the TTL window restarts on a successful refetch.
    assertEquals(42L, result.id());
    assertEquals("[{\"team\":1310,\"new\":true}]", result.body());
    assertTrue(
        result.lastcheck().isAfter(staleMoment),
        "lastcheck must advance past the previous stale moment");
    assertEquals(200, result.statuscode());
    verify(repo, never()).save(any());
  }

  @Test
  void urlWithHyphensAndUnusualCharactersFlowsThroughUnchanged() {
    build();
    // The caching client itself doesn't re-encode — it trusts the caller. This test locks in that
    // an already-encoded URI round-trips through the lookup key without corruption, and that
    // StatboticsClient.encodePathSegment produces the expected shape for a hyphenated event key.
    assertEquals("2026-onto-fsc", StatboticsClient.encodePathSegment("2026-onto-fsc"));

    String uri = "v3/team_event/2026-onto-fsc/1310";
    StatboticsRawResponse fresh = networkRow(uri, 200, "{\"team\":1310}");
    StatboticsRawResponse persisted = cachedRow(99L, uri, Instant.now(), "{\"team\":1310}");
    when(repo.find(uri)).thenReturn(null, persisted);
    when(client.get(uri)).thenReturn(fresh);

    StatboticsRawResponse result = caching.fetch(uri);

    assertSame(persisted, result);
    verify(client).get(uri);
  }

  @Test
  void fiveHundredResponsePropagatesExceptionAndCachesNothing() {
    build();
    String uri = "v3/team_events?event=boom";
    when(repo.find(uri)).thenReturn(null);
    when(client.get(uri)).thenThrow(new StatboticsClientException("Response 503: gateway-down"));

    StatboticsClientException ex =
        assertThrows(StatboticsClientException.class, () -> caching.fetch(uri));
    assertTrue(ex.getMessage().contains("503"), "exception detail should carry the status");
    verify(repo, never()).save(any());
    verify(repo, never()).update(any());
  }

  @Test
  void networkFailureAfterTtlPreservesPriorCacheRow() {
    build();
    // Prior successful cache entry is past the TTL; upstream is now down. The client-level
    // exception propagates, but the cache row is left exactly as it was — no write happens on
    // the failure path, so the next successful call still has something to compare / return.
    String uri = "v3/team_events?event=2026onto";
    StatboticsRawResponse stale =
        cachedRow(
            42L, uri, Instant.now().minus(TTL_SECONDS + 30, ChronoUnit.SECONDS), "[{\"prior\":true}]");
    when(repo.find(uri)).thenReturn(stale);
    when(client.get(uri))
        .thenThrow(new StatboticsClientException("Could not connect to Statbotics API for " + uri));

    assertThrows(StatboticsClientException.class, () -> caching.fetch(uri));

    // Critically — no update/save on the failure path. Whatever was in RB_STATBOTICS_RESPONSES
    // for this URI stays put.
    verify(repo, never()).update(any());
    verify(repo, never()).save(any());
  }

  @Test
  void fourOhFourRefreshesCacheButStoresStatusAndBody() {
    // A 404 from Statbotics is a legitimate response shape — cache it so subsequent callers get
    // the same verdict within the TTL instead of re-hammering the API.
    build();
    String uri = "v3/team_events?event=2026ghost";
    StatboticsRawResponse fresh = networkRow(uri, 404, "{\"error\":\"unknown event\"}");
    StatboticsRawResponse persisted = cachedRow(7L, uri, Instant.now(), "{\"error\":\"unknown event\"}");

    when(repo.find(uri)).thenReturn(null, persisted);
    when(client.get(uri)).thenReturn(fresh);

    StatboticsRawResponse result = caching.fetch(uri);

    assertEquals(7L, result.id());
    verify(repo).save(fresh);
  }

  @Test
  void markProcessedUpdatesFlag() {
    build();
    StatboticsRawResponse row = cachedRow(55L, "v3/x", Instant.now(), "{}");
    when(repo.findById(55L)).thenReturn(java.util.Optional.of(row));

    caching.markProcessed(55L);

    verify(repo)
        .update(
            argThat(
                r ->
                    r.id() == 55L
                        && r.processed()
                        && r.statuscode() == 200
                        && "v3/x".equals(r.url())));
  }

  @Test
  void markProcessedNoOpOnMissingRow() {
    build();
    when(repo.findById(eq(999L))).thenReturn(java.util.Optional.empty());

    caching.markProcessed(999L);

    verify(repo, never()).update(any());
  }

  @Test
  void clearProcessedFlipsFlagForAllRows() {
    build();
    StatboticsRawResponse a =
        new StatboticsRawResponse(
            1L, Instant.now(), Instant.now(), null, true, 200, "v3/a", "{\"a\":1}");
    StatboticsRawResponse b =
        new StatboticsRawResponse(
            2L, Instant.now(), Instant.now(), null, true, 200, "v3/b", "{\"b\":2}");
    when(repo.findAll()).thenReturn(java.util.List.of(a, b));

    caching.clearProcessed();

    verify(repo).update(argThat(r -> r.id() == 1L && !r.processed()));
    verify(repo).update(argThat(r -> r.id() == 2L && !r.processed()));
  }
}
