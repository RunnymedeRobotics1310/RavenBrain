package ca.team1310.ravenbrain.tbaapi.service;

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

/** Security surface of POST /api/tba-sync — SUPERUSER only. */
@MicronautTest
public class TbaSyncApiTest {

  private static final String USER_MEMBER = "tbasync-member-testuser";
  private static final String USER_ADMIN = "tbasync-admin-testuser";
  private static final String USER_SUPERUSER = "tbasync-superuser-testuser";

  @Inject
  @Client("/")
  HttpClient client;

  @Inject TestUserHelper testUserHelper;

  @BeforeEach
  void setUp() {
    testUserHelper.createTestUser(USER_MEMBER, "password", "ROLE_MEMBER");
    testUserHelper.createTestUser(USER_ADMIN, "password", "ROLE_ADMIN");
    testUserHelper.createTestUser(USER_SUPERUSER, "password", "ROLE_SUPERUSER");
  }

  @AfterEach
  void tearDown() {
    testUserHelper.deleteTestUsers();
  }

  @Test
  void forceSync_memberIsForbidden() {
    HttpRequest<?> request =
        HttpRequest.POST("/api/tba-sync", "").basicAuth(USER_MEMBER, "password");
    HttpClientResponseException e =
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
  }

  @Test
  void forceSync_adminIsForbidden() {
    // ADMIN can edit webcasts (Unit 1) but NOT trigger a TBA sync — that is a superuser-only op.
    HttpRequest<?> request =
        HttpRequest.POST("/api/tba-sync", "").basicAuth(USER_ADMIN, "password");
    HttpClientResponseException e =
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
  }

  @Test
  void forceSync_superuserReturnsAccepted() {
    HttpRequest<?> request =
        HttpRequest.POST("/api/tba-sync", "").basicAuth(USER_SUPERUSER, "password");
    HttpResponse<?> response = client.toBlocking().exchange(request);
    assertEquals(HttpStatus.ACCEPTED, response.getStatus());
  }

  @Test
  void forceSync_anonymousIsUnauthorized() {
    HttpRequest<?> request = HttpRequest.POST("/api/tba-sync", "");
    HttpClientResponseException e =
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
    assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
  }
}
