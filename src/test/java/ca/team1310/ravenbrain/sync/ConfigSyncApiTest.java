package ca.team1310.ravenbrain.sync;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.connect.Config;
import ca.team1310.ravenbrain.connect.TestUserHelper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@MicronautTest
public class ConfigSyncApiTest {

  @Inject
  @Client("/")
  HttpClient client;

  @Inject Config config;

  @Inject TestUserHelper testUserHelper;

  @AfterEach
  void cleanup() {
    testUserHelper.deleteTestUsers();
  }

  @Test
  void testSyncAsSuperuser() {
    SyncRequest body = new SyncRequest("http://invalid-source:9999", "user", "pass");
    HttpRequest<?> request =
        HttpRequest.POST("/api/config-sync", body).basicAuth("superuser", config.superuser());
    // Expect a server error (source is unreachable), NOT a 401/403
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());
  }

  @Test
  void testSyncForbiddenForAdmin() {
    String adminLogin = "sync-admin-testuser-" + System.currentTimeMillis();
    testUserHelper.createTestUser(adminLogin, "password", "ROLE_ADMIN");

    SyncRequest body = new SyncRequest("http://invalid-source:9999", "user", "pass");
    HttpRequest<?> request =
        HttpRequest.POST("/api/config-sync", body).basicAuth(adminLogin, "password");
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
  }

  @Test
  void testSyncForbiddenForMember() {
    String memberLogin = "sync-member-testuser-" + System.currentTimeMillis();
    testUserHelper.createTestUser(memberLogin, "password", "ROLE_MEMBER");

    SyncRequest body = new SyncRequest("http://invalid-source:9999", "user", "pass");
    HttpRequest<?> request =
        HttpRequest.POST("/api/config-sync", body).basicAuth(memberLogin, "password");
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
  }

  @Test
  void testSyncUnauthorized() {
    SyncRequest body = new SyncRequest("http://invalid-source:9999", "user", "pass");
    HttpRequest<?> request = HttpRequest.POST("/api/config-sync", body);
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
    assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
  }
}
