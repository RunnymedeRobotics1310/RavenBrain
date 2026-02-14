package ca.team1310.ravenbrain.frcapi.service;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.connect.Config;
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
import org.junit.jupiter.api.Test;

@MicronautTest
public class FrcSyncApiTest {

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
  void testForceSyncAsSuperuser() {
    HttpRequest<?> request =
        HttpRequest.POST("/api/frc-sync", "").basicAuth("superuser", config.superuser());
    HttpResponse<?> response = client.toBlocking().exchange(request);
    assertEquals(HttpStatus.OK, response.getStatus());
  }

  @Test
  void testForceSyncForbiddenForMember() {
    String memberLogin = "sync-member-testuser-" + System.currentTimeMillis();
    testUserHelper.createTestUser(memberLogin, "password", "ROLE_MEMBER");

    HttpRequest<?> request =
        HttpRequest.POST("/api/frc-sync", "").basicAuth(memberLogin, "password");
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
  }

  @Test
  void testForceSyncForbiddenForAdmin() {
    String adminLogin = "sync-admin-testuser-" + System.currentTimeMillis();
    testUserHelper.createTestUser(adminLogin, "password", "ROLE_ADMIN");

    HttpRequest<?> request =
        HttpRequest.POST("/api/frc-sync", "").basicAuth(adminLogin, "password");
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
  }

  @Test
  void testForceSyncUnauthorized() {
    HttpRequest<?> request = HttpRequest.POST("/api/frc-sync", "");
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
    assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
  }
}