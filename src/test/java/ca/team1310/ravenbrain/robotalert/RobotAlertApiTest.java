package ca.team1310.ravenbrain.robotalert;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.connect.TestUserHelper;
import ca.team1310.ravenbrain.connect.User;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
public class RobotAlertApiTest {

  private static final String TOURNAMENT = "TEST_ALERT_API";
  private static final String USER_MEMBER = "alert-member-testuser";
  private static final String USER_DATASCOUT = "alert-datascout-testuser";

  @Inject
  @Client("/")
  HttpClient client;

  @Inject RobotAlertService robotAlertService;
  @Inject TestUserHelper testUserHelper;

  private long memberUserId;
  private long datascoutUserId;

  @BeforeEach
  void setup() {
    User member = testUserHelper.createTestUser(USER_MEMBER, "password", "ROLE_MEMBER");
    memberUserId = member.id();
    User datascout = testUserHelper.createTestUser(USER_DATASCOUT, "password", "ROLE_DATASCOUT");
    datascoutUserId = datascout.id();
  }

  @AfterEach
  void tearDown() {
    robotAlertService
        .findAll()
        .forEach(
            alert -> {
              if (alert.tournamentId().startsWith("TEST_ALERT")) {
                robotAlertService.delete(alert);
              }
            });
    testUserHelper.deleteTestUsers();
  }

  @Test
  void testPostAndGetAlerts() {
    RobotAlert alert =
        new RobotAlert(
            null, TOURNAMENT, 1310, memberUserId, Instant.now(), "Broken intake mechanism");

    HttpRequest<List<RobotAlert>> postRequest =
        HttpRequest.POST("/api/robot-alert", Collections.singletonList(alert))
            .basicAuth(USER_MEMBER, "password");

    HttpResponse<List<RobotAlertApi.RobotAlertPostResult>> postResponse =
        client
            .toBlocking()
            .exchange(postRequest, Argument.listOf(RobotAlertApi.RobotAlertPostResult.class));

    assertEquals(HttpStatus.OK, postResponse.getStatus());
    List<RobotAlertApi.RobotAlertPostResult> postBody = postResponse.body();
    assertNotNull(postBody);
    assertEquals(1, postBody.size());
    assertTrue(postBody.getFirst().success());
    assertNotNull(postBody.getFirst().alert().id());

    // GET the alerts back
    HttpRequest<?> getRequest =
        HttpRequest.GET("/api/robot-alert/" + TOURNAMENT)
            .basicAuth(USER_DATASCOUT, "password");

    HttpResponse<List<RobotAlert>> getResponse =
        client.toBlocking().exchange(getRequest, Argument.listOf(RobotAlert.class));

    assertEquals(HttpStatus.OK, getResponse.getStatus());
    List<RobotAlert> alerts = getResponse.body();
    assertNotNull(alerts);
    assertEquals(1, alerts.size());
    assertEquals(1310, alerts.getFirst().teamNumber());
    assertEquals("Broken intake mechanism", alerts.getFirst().alert());
  }

  @Test
  void testPostDuplicateAlertReturnsSuccess() {
    Instant now = Instant.parse("2025-06-01T12:00:00Z");
    RobotAlert alert =
        new RobotAlert(null, TOURNAMENT, 1310, memberUserId, now, "Duplicate test alert");

    List<RobotAlert> alerts = Collections.singletonList(alert);
    HttpRequest<List<RobotAlert>> request =
        HttpRequest.POST("/api/robot-alert", alerts).basicAuth(USER_MEMBER, "password");

    // First post
    client
        .toBlocking()
        .exchange(request, Argument.listOf(RobotAlertApi.RobotAlertPostResult.class));

    // Second post — should still return success (duplicate handled gracefully)
    HttpResponse<List<RobotAlertApi.RobotAlertPostResult>> response =
        client
            .toBlocking()
            .exchange(request, Argument.listOf(RobotAlertApi.RobotAlertPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<RobotAlertApi.RobotAlertPostResult> body = response.body();
    assertNotNull(body);
    assertEquals(1, body.size());
    assertTrue(body.getFirst().success());
  }

  @Test
  void testGetReturns401WhenUnauthenticated() {
    HttpRequest<?> request = HttpRequest.GET("/api/robot-alert/" + TOURNAMENT);
    assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
  }

  @Test
  void testPostReturns401WhenUnauthenticated() {
    RobotAlert alert =
        new RobotAlert(null, TOURNAMENT, 1310, 1, Instant.now(), "Should not save");
    HttpRequest<List<RobotAlert>> request =
        HttpRequest.POST("/api/robot-alert", Collections.singletonList(alert));
    assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
  }

  @Test
  void testGetEmptyTournamentReturnsEmptyList() {
    HttpRequest<?> request =
        HttpRequest.GET("/api/robot-alert/NONEXISTENT_TOURN")
            .basicAuth(USER_MEMBER, "password");

    HttpResponse<List<RobotAlert>> response =
        client.toBlocking().exchange(request, Argument.listOf(RobotAlert.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    assertNotNull(response.body());
    assertTrue(response.body().isEmpty());
  }
}