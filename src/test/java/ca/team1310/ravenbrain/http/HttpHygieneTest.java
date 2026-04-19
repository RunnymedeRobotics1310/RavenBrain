package ca.team1310.ravenbrain.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage for the HTTP-hygiene infrastructure introduced in Unit 1:
 *
 * <ul>
 *   <li>{@link ServerTimeFilter} adds {@code X-RavenBrain-Time} to every response (authenticated,
 *       anonymous, and error paths).
 *   <li>{@link ResponseEtags} emits weak ETags from controllers and short-circuits to {@code 304
 *       Not Modified} when the inbound {@code If-None-Match} matches.
 * </ul>
 *
 * <p>Uses {@code GET /api/tournament} as the canonical ETag-emitting endpoint (anonymous, so no
 * test-user setup needed), and {@code GET /api/ping} for cross-cutting filter coverage.
 */
@MicronautTest
public class HttpHygieneTest {

  @Inject
  @Client("/")
  HttpClient client;

  @Test
  void serverTimeHeaderPresentOnAnonymousResponse() {
    HttpResponse<?> response = client.toBlocking().exchange(HttpRequest.GET("/api/ping"));
    assertEquals(HttpStatus.OK, response.getStatus());
    assertNotNull(
        response.getHeaders().get(ServerTimeFilter.HEADER),
        "X-RavenBrain-Time should be present on every response, including anonymous /api/ping");

    String value = response.getHeaders().get(ServerTimeFilter.HEADER);
    long parsed = Long.parseLong(value);
    long now = System.currentTimeMillis();
    assertTrue(
        Math.abs(now - parsed) < 60_000,
        "X-RavenBrain-Time should be near the current millis (within 60s); got " + parsed);
  }

  @Test
  void serverTimeHeaderPresentOnAuthenticatedListResponse() {
    HttpResponse<?> response = client.toBlocking().exchange(HttpRequest.GET("/api/tournament"));
    assertEquals(HttpStatus.OK, response.getStatus());
    assertNotNull(response.getHeaders().get(ServerTimeFilter.HEADER));
  }

  @Test
  void tournamentListEmitsWeakEtag() {
    HttpResponse<?> response = client.toBlocking().exchange(HttpRequest.GET("/api/tournament"));
    assertEquals(HttpStatus.OK, response.getStatus());
    String etag = response.getHeaders().get("ETag");
    assertNotNull(etag, "/api/tournament should emit an ETag header");
    assertTrue(etag.startsWith("W/\""), "ETag should be a weak validator; got: " + etag);
  }

  @Test
  void conditionalGetReturns304WhenEtagMatches() {
    // First call: capture the ETag.
    HttpResponse<?> first = client.toBlocking().exchange(HttpRequest.GET("/api/tournament"));
    String etag = first.getHeaders().get("ETag");
    assertNotNull(etag);

    // Second call with matching If-None-Match: server should short-circuit to 304.
    HttpRequest<?> conditional =
        HttpRequest.GET("/api/tournament").header("If-None-Match", etag);
    try {
      HttpResponse<?> second = client.toBlocking().exchange(conditional);
      // Some Micronaut client configurations surface 304 as a normal response; others throw.
      // Either is acceptable so long as we got the not-modified outcome.
      assertEquals(HttpStatus.NOT_MODIFIED, second.getStatus());
      assertEquals(etag, second.getHeaders().get("ETag"));
      assertNull(second.getBody().orElse(null), "304 response must not carry a body");
    } catch (HttpClientResponseException ex) {
      assertEquals(HttpStatus.NOT_MODIFIED, ex.getStatus());
      assertEquals(etag, ex.getResponse().getHeaders().get("ETag"));
    }
  }

  @Test
  void conditionalGetReturns200WhenEtagDoesNotMatch() {
    HttpRequest<?> request =
        HttpRequest.GET("/api/tournament").header("If-None-Match", "W/\"definitely-not-matching\"");
    HttpResponse<?> response = client.toBlocking().exchange(request);
    assertEquals(HttpStatus.OK, response.getStatus());
    assertNotNull(response.getHeaders().get("ETag"));
  }

  @Test
  void weakTagFormat() {
    String tag = ResponseEtags.weakTag("42");
    assertEquals("W/\"42\"", tag);
  }
}
