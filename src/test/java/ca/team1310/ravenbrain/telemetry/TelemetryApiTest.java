package ca.team1310.ravenbrain.telemetry;

import static org.junit.jupiter.api.Assertions.*;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@MicronautTest
public class TelemetryApiTest {

  private static final String API_KEY = "default_telemetry_key_change_me";
  private static final String API_KEY_HEADER = "X-Telemetry-Key";

  @Inject
  @Client("/")
  HttpClient client;

  @Inject TelemetryService telemetryService;

  @AfterEach
  void tearDown() {
    // Cleanup sessions created during tests by looking them up via known sessionIds
    // The service does not expose a findAll, so cleanup is best-effort via individual lookups.
  }

  @Test
  void testCreateSessionPostDataAndComplete() {
    String sessionId = "test-" + UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-01-15T10:00:00Z");

    // Step 1: Create session
    TelemetryApi.CreateSessionRequest createRequest =
        new TelemetryApi.CreateSessionRequest(sessionId, 1310, "10.13.10.2", startedAt);

    HttpResponse<TelemetrySession> createResponse =
        client
            .toBlocking()
            .exchange(
                HttpRequest.POST("/api/telemetry/session", createRequest)
                    .header(API_KEY_HEADER, API_KEY),
                TelemetrySession.class);

    assertEquals(HttpStatus.OK, createResponse.getStatus());
    TelemetrySession session = createResponse.body();
    assertNotNull(session);
    assertNotNull(session.id());
    assertEquals(sessionId, session.sessionId());
    assertEquals(1310, session.teamNumber());
    assertEquals("10.13.10.2", session.robotIp());
    assertEquals(startedAt, session.startedAt());
    assertNull(session.endedAt());
    assertEquals(0, session.entryCount());

    // Step 2: Post batch data
    Instant ts1 = Instant.parse("2026-01-15T10:00:01Z");
    Instant ts2 = Instant.parse("2026-01-15T10:00:02Z");
    Instant ts3 = Instant.parse("2026-01-15T10:00:03Z");

    List<TelemetryApi.TelemetryEntryRequest> entries =
        List.of(
            new TelemetryApi.TelemetryEntryRequest(
                ts1, "nt_update", "/SmartDashboard/speed", "double", "3.14", null),
            new TelemetryApi.TelemetryEntryRequest(
                ts2, "nt_update", "/SmartDashboard/enabled", "boolean", "true", null),
            new TelemetryApi.TelemetryEntryRequest(ts3, "fms", null, null, null, 7));

    HttpResponse<TelemetryApi.BatchInsertResult> dataResponse =
        client
            .toBlocking()
            .exchange(
                HttpRequest.POST("/api/telemetry/session/" + sessionId + "/data", entries)
                    .header(API_KEY_HEADER, API_KEY),
                TelemetryApi.BatchInsertResult.class);

    assertEquals(HttpStatus.OK, dataResponse.getStatus());
    TelemetryApi.BatchInsertResult batchResult = dataResponse.body();
    assertNotNull(batchResult);
    assertEquals(3, batchResult.count());

    // Step 3: Complete session
    Instant endedAt = Instant.parse("2026-01-15T10:05:00Z");
    TelemetryApi.CompleteSessionRequest completeRequest =
        new TelemetryApi.CompleteSessionRequest(endedAt, 3);

    HttpResponse<TelemetrySession> completeResponse =
        client
            .toBlocking()
            .exchange(
                HttpRequest.POST("/api/telemetry/session/" + sessionId + "/complete", completeRequest)
                    .header(API_KEY_HEADER, API_KEY),
                TelemetrySession.class);

    assertEquals(HttpStatus.OK, completeResponse.getStatus());
    TelemetrySession completed = completeResponse.body();
    assertNotNull(completed);
    assertEquals(sessionId, completed.sessionId());
    assertEquals(endedAt, completed.endedAt());
    assertEquals(3, completed.entryCount());
  }

  @Test
  void testCreateSessionReturns401WithoutApiKey() {
    TelemetryApi.CreateSessionRequest request =
        new TelemetryApi.CreateSessionRequest(
            "no-key-session", 1310, "10.13.10.2", Instant.now());

    HttpClientResponseException exception =
        assertThrows(
            HttpClientResponseException.class,
            () ->
                client
                    .toBlocking()
                    .exchange(HttpRequest.POST("/api/telemetry/session", request)));

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
  }

  @Test
  void testCreateSessionReturns401WithWrongApiKey() {
    TelemetryApi.CreateSessionRequest request =
        new TelemetryApi.CreateSessionRequest(
            "wrong-key-session", 1310, "10.13.10.2", Instant.now());

    HttpClientResponseException exception =
        assertThrows(
            HttpClientResponseException.class,
            () ->
                client
                    .toBlocking()
                    .exchange(
                        HttpRequest.POST("/api/telemetry/session", request)
                            .header(API_KEY_HEADER, "wrong-api-key")));

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
  }

  @Test
  void testPostDataReturns401WithoutApiKey() {
    List<TelemetryApi.TelemetryEntryRequest> entries =
        List.of(
            new TelemetryApi.TelemetryEntryRequest(
                Instant.now(), "nt_update", "/key", "double", "1.0", null));

    HttpClientResponseException exception =
        assertThrows(
            HttpClientResponseException.class,
            () ->
                client
                    .toBlocking()
                    .exchange(
                        HttpRequest.POST("/api/telemetry/session/any-session/data", entries)));

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
  }

  @Test
  void testCompleteSessionReturns401WithoutApiKey() {
    TelemetryApi.CompleteSessionRequest request =
        new TelemetryApi.CompleteSessionRequest(Instant.now(), 0);

    HttpClientResponseException exception =
        assertThrows(
            HttpClientResponseException.class,
            () ->
                client
                    .toBlocking()
                    .exchange(
                        HttpRequest.POST(
                            "/api/telemetry/session/any-session/complete", request)));

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
  }

  @Test
  void testPostDataReturns404ForNonExistentSession() {
    List<TelemetryApi.TelemetryEntryRequest> entries =
        List.of(
            new TelemetryApi.TelemetryEntryRequest(
                Instant.now(), "nt_update", "/key", "double", "1.0", null));

    HttpClientResponseException exception =
        assertThrows(
            HttpClientResponseException.class,
            () ->
                client
                    .toBlocking()
                    .exchange(
                        HttpRequest.POST(
                                "/api/telemetry/session/nonexistent-session-id/data", entries)
                            .header(API_KEY_HEADER, API_KEY),
                        TelemetryApi.BatchInsertResult.class));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
  }

  @Test
  void testCompleteSessionReturns404ForNonExistentSession() {
    TelemetryApi.CompleteSessionRequest request =
        new TelemetryApi.CompleteSessionRequest(Instant.now(), 0);

    HttpClientResponseException exception =
        assertThrows(
            HttpClientResponseException.class,
            () ->
                client
                    .toBlocking()
                    .exchange(
                        HttpRequest.POST(
                                "/api/telemetry/session/nonexistent-session-id/complete", request)
                            .header(API_KEY_HEADER, API_KEY),
                        TelemetrySession.class));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
  }

  @Test
  void testBatchDataInsertWithMultipleEntries() {
    String sessionId = "test-batch-" + UUID.randomUUID();
    Instant startedAt = Instant.now();

    // Create session first
    TelemetryApi.CreateSessionRequest createRequest =
        new TelemetryApi.CreateSessionRequest(sessionId, 254, "10.2.54.2", startedAt);

    client
        .toBlocking()
        .exchange(
            HttpRequest.POST("/api/telemetry/session", createRequest)
                .header(API_KEY_HEADER, API_KEY),
            TelemetrySession.class);

    // Post a batch of 5 entries with mixed types
    Instant baseTs = Instant.parse("2026-01-15T10:00:00Z");
    List<TelemetryApi.TelemetryEntryRequest> entries =
        List.of(
            new TelemetryApi.TelemetryEntryRequest(
                baseTs, "nt_update", "/robot/speed", "double", "2.5", null),
            new TelemetryApi.TelemetryEntryRequest(
                baseTs.plusMillis(100), "nt_update", "/robot/heading", "double", "90.0", null),
            new TelemetryApi.TelemetryEntryRequest(
                baseTs.plusMillis(200), "nt_update", "/robot/enabled", "boolean", "true", null),
            new TelemetryApi.TelemetryEntryRequest(
                baseTs.plusMillis(300), "fms", null, null, null, 15),
            new TelemetryApi.TelemetryEntryRequest(
                baseTs.plusMillis(400), "nt_update", "/robot/mode", "string", "auto", null));

    HttpResponse<TelemetryApi.BatchInsertResult> response =
        client
            .toBlocking()
            .exchange(
                HttpRequest.POST("/api/telemetry/session/" + sessionId + "/data", entries)
                    .header(API_KEY_HEADER, API_KEY),
                TelemetryApi.BatchInsertResult.class);

    assertEquals(HttpStatus.OK, response.getStatus());
    TelemetryApi.BatchInsertResult result = response.body();
    assertNotNull(result);
    assertEquals(5, result.count());
  }

  @Test
  void testCreateSessionIdempotent() {
    String sessionId = "test-idempotent-" + UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-01-15T10:00:00Z");

    TelemetryApi.CreateSessionRequest request =
        new TelemetryApi.CreateSessionRequest(sessionId, 1310, "10.13.10.2", startedAt);

    // First POST — creates the session
    HttpResponse<TelemetrySession> firstResponse =
        client
            .toBlocking()
            .exchange(
                HttpRequest.POST("/api/telemetry/session", request)
                    .header(API_KEY_HEADER, API_KEY),
                TelemetrySession.class);

    assertEquals(HttpStatus.OK, firstResponse.getStatus());
    TelemetrySession first = firstResponse.body();
    assertNotNull(first);
    assertEquals(sessionId, first.sessionId());

    // Second POST with same sessionId — should return 200 with the same session
    HttpResponse<TelemetrySession> secondResponse =
        client
            .toBlocking()
            .exchange(
                HttpRequest.POST("/api/telemetry/session", request)
                    .header(API_KEY_HEADER, API_KEY),
                TelemetrySession.class);

    assertEquals(HttpStatus.OK, secondResponse.getStatus());
    TelemetrySession second = secondResponse.body();
    assertNotNull(second);
    assertEquals(first.id(), second.id());
    assertEquals(first.sessionId(), second.sessionId());
    assertEquals(first.teamNumber(), second.teamNumber());
  }

  @Test
  void testGetSessionReturnsUploadedCount() {
    String sessionId = "test-get-" + UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-01-15T10:00:00Z");

    // Create session
    TelemetryApi.CreateSessionRequest createRequest =
        new TelemetryApi.CreateSessionRequest(sessionId, 1310, "10.13.10.2", startedAt);

    client
        .toBlocking()
        .exchange(
            HttpRequest.POST("/api/telemetry/session", createRequest)
                .header(API_KEY_HEADER, API_KEY),
            TelemetrySession.class);

    // GET session — uploadedCount should be 0 initially
    HttpResponse<TelemetrySession> getResponse =
        client
            .toBlocking()
            .exchange(
                HttpRequest.GET("/api/telemetry/session/" + sessionId),
                TelemetrySession.class);

    assertEquals(HttpStatus.OK, getResponse.getStatus());
    TelemetrySession session = getResponse.body();
    assertNotNull(session);
    assertEquals(sessionId, session.sessionId());
    assertEquals(0, session.uploadedCount());
  }

  @Test
  void testGetSessionUploadedCountMatchesBatchSize() {
    String sessionId = "test-count-" + UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-01-15T10:00:00Z");

    // Create session
    TelemetryApi.CreateSessionRequest createRequest =
        new TelemetryApi.CreateSessionRequest(sessionId, 1310, "10.13.10.2", startedAt);

    client
        .toBlocking()
        .exchange(
            HttpRequest.POST("/api/telemetry/session", createRequest)
                .header(API_KEY_HEADER, API_KEY),
            TelemetrySession.class);

    // Post a batch of 3 entries
    Instant baseTs = Instant.parse("2026-01-15T10:00:01Z");
    List<TelemetryApi.TelemetryEntryRequest> entries =
        List.of(
            new TelemetryApi.TelemetryEntryRequest(
                baseTs, "nt_update", "/robot/speed", "double", "1.0", null),
            new TelemetryApi.TelemetryEntryRequest(
                baseTs.plusMillis(100), "nt_update", "/robot/heading", "double", "45.0", null),
            new TelemetryApi.TelemetryEntryRequest(
                baseTs.plusMillis(200), "nt_update", "/robot/mode", "string", "teleop", null));

    client
        .toBlocking()
        .exchange(
            HttpRequest.POST("/api/telemetry/session/" + sessionId + "/data", entries)
                .header(API_KEY_HEADER, API_KEY),
            TelemetryApi.BatchInsertResult.class);

    // GET session — uploadedCount should now be 3
    HttpResponse<TelemetrySession> getResponse =
        client
            .toBlocking()
            .exchange(
                HttpRequest.GET("/api/telemetry/session/" + sessionId),
                TelemetrySession.class);

    assertEquals(HttpStatus.OK, getResponse.getStatus());
    TelemetrySession session = getResponse.body();
    assertNotNull(session);
    assertEquals(3, session.uploadedCount());
  }

  @Test
  void testGetSessionReturns404ForNonExistent() {
    HttpClientResponseException exception =
        assertThrows(
            HttpClientResponseException.class,
            () ->
                client
                    .toBlocking()
                    .exchange(
                        HttpRequest.GET("/api/telemetry/session/nonexistent-session-id"),
                        TelemetrySession.class));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
  }
}
