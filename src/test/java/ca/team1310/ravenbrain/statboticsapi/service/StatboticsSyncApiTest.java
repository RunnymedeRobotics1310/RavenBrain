package ca.team1310.ravenbrain.statboticsapi.service;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.connect.TestUserHelper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Security + contention surface of {@code POST /api/statbotics-sync}. Covers ROLE_SUPERUSER-only
 * access and the 202 / 409 / 429 contract. The async-task body is exercised against the real sync
 * service; per-tournament network behaviour lives in
 * {@link StatboticsTeamEventSyncServiceTest}.
 */
@MicronautTest
public class StatboticsSyncApiTest {

  private static final String USER_MEMBER = "sbsync-member-testuser";
  private static final String USER_ADMIN = "sbsync-admin-testuser";
  private static final String USER_SUPERUSER = "sbsync-superuser-testuser";

  @Inject
  @Client("/")
  HttpClient client;

  @Inject TestUserHelper testUserHelper;
  @Inject StatboticsSyncApi syncApi;

  @BeforeEach
  void setUp() throws Exception {
    testUserHelper.createTestUser(USER_MEMBER, "password", "ROLE_MEMBER");
    testUserHelper.createTestUser(USER_ADMIN, "password", "ROLE_ADMIN");
    testUserHelper.createTestUser(USER_SUPERUSER, "password", "ROLE_SUPERUSER");
    // Reset the singleton's interval guard + gate between tests so test order cannot induce
    // false 429 / 409 responses. Reflection here is contained to test code and mirrors the
    // reset-between-tests posture used elsewhere in the suite.
    resetApiState();
  }

  @AfterEach
  void tearDown() {
    testUserHelper.deleteTestUsers();
  }

  @SuppressWarnings("unchecked")
  private void resetApiState() throws Exception {
    var lastField = StatboticsSyncApi.class.getDeclaredField("lastSuccessfulSyncAt");
    lastField.setAccessible(true);
    ((java.util.concurrent.atomic.AtomicReference<java.time.Instant>) lastField.get(syncApi))
        .set(null);
    var gateField = StatboticsSyncApi.class.getDeclaredField("syncInProgress");
    gateField.setAccessible(true);
    ((java.util.concurrent.atomic.AtomicBoolean) gateField.get(syncApi)).set(false);
  }

  @Test
  void forceSync_memberIsForbidden() {
    HttpRequest<?> request =
        HttpRequest.POST("/api/statbotics-sync", "").basicAuth(USER_MEMBER, "password");
    HttpClientResponseException e =
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
  }

  @Test
  void forceSync_adminIsForbidden() {
    // Statbotics sync is a tournament-day superuser utility — admin can CRUD tournaments but
    // cannot force-trigger an external-API fetch.
    HttpRequest<?> request =
        HttpRequest.POST("/api/statbotics-sync", "").basicAuth(USER_ADMIN, "password");
    HttpClientResponseException e =
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
  }

  @Test
  void forceSync_anonymousIsUnauthorized() {
    HttpRequest<?> request = HttpRequest.POST("/api/statbotics-sync", "");
    HttpClientResponseException e =
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
    assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
  }

  @Test
  void forceSync_superuserReturnsAccepted() {
    HttpRequest<?> request =
        HttpRequest.POST("/api/statbotics-sync", "").basicAuth(USER_SUPERUSER, "password");
    HttpResponse<?> response = client.toBlocking().exchange(request);
    assertEquals(HttpStatus.ACCEPTED, response.getStatus());
  }

  @Test
  void forceSync_secondCallWithinFiveMinutesReturns429() throws Exception {
    // Fire one sync, wait for the async task to finish (status flips back to idle), then fire a
    // second. The 5-minute interval guard must reject the second call.
    HttpRequest<?> first =
        HttpRequest.POST("/api/statbotics-sync", "").basicAuth(USER_SUPERUSER, "password");
    HttpResponse<?> firstResp = client.toBlocking().exchange(first);
    assertEquals(HttpStatus.ACCEPTED, firstResp.getStatus());

    // Poll /status until the async sync finishes. Abort after ~10s to fail loudly if the async
    // task hangs — in practice it completes in well under a second because the default setup
    // has zero active tournaments in the Testcontainers DB.
    long deadline = System.currentTimeMillis() + 10_000L;
    HttpRequest<?> statusReq =
        HttpRequest.GET("/api/statbotics-sync/status").basicAuth(USER_SUPERUSER, "password");
    String status = "running";
    while (System.currentTimeMillis() < deadline) {
      status = client.toBlocking().retrieve(statusReq);
      if ("idle".equals(status)) break;
      Thread.sleep(50);
    }
    assertEquals("idle", status, "async sync must finish before the 429 assertion");

    HttpRequest<?> second =
        HttpRequest.POST("/api/statbotics-sync", "").basicAuth(USER_SUPERUSER, "password");
    HttpClientResponseException ex =
        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(second));
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
  }
}
