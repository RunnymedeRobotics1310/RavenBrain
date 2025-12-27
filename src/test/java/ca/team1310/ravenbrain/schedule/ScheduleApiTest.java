package ca.team1310.ravenbrain.schedule;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

@MicronautTest
public class ScheduleApiTest {

  @Inject
  @Client("/")
  HttpClient client;

  @Inject ScheduleService scheduleService;

  @Test
  void testCreateScheduleItem() {
    String tournId = "TEST_TOURN_1_" + System.currentTimeMillis();
    ScheduleRecord record =
        new ScheduleRecord(0, tournId, TournamentLevel.Qualification, 1, 1310, 1, 2, 3, 4, 5);

    HttpRequest<ScheduleRecord> request =
        HttpRequest.POST("/api/schedule", record).basicAuth("user", "team1310IsTheBest");

    HttpResponse<Void> response = client.toBlocking().exchange(request);

    assertEquals(HttpStatus.OK, response.getStatus());

    // Verify it was saved
    List<ScheduleRecord> saved = scheduleService.findAllByTournamentIdOrderByMatch(tournId);
    assertFalse(saved.isEmpty());
    assertEquals(1, saved.get(0).match());
    assertEquals(1310, saved.get(0).red1());
  }

  @Test
  void testCreateScheduleItemInvalid() {
    ScheduleRecord record =
        new ScheduleRecord(0, null, TournamentLevel.Qualification, 2, 0, 0, 0, 0, 0, 0);

    HttpRequest<ScheduleRecord> request =
        HttpRequest.POST("/api/schedule", record).basicAuth("user", "team1310IsTheBest");

    // This should fail because of database constraints
    assertThrows(
        HttpClientResponseException.class,
        () -> {
          client.toBlocking().exchange(request);
        });
  }

  @Test
  void testGetScheduleForTournament() {
    String tournId = "TEST_TOURN_2_" + System.currentTimeMillis();
    ScheduleRecord record =
        new ScheduleRecord(
            0, tournId, TournamentLevel.Qualification, 1, 100, 101, 102, 103, 104, 105);

    // Save via API so it's committed and visible to subsequent GET
    client
        .toBlocking()
        .exchange(HttpRequest.POST("/api/schedule", record).basicAuth("user", "team1310IsTheBest"));

    HttpRequest<?> request =
        HttpRequest.GET("/api/schedule/" + tournId).basicAuth("user", "team1310IsTheBest");

    List<ScheduleRecord> response =
        client.toBlocking().retrieve(request, Argument.listOf(ScheduleRecord.class));

    assertNotNull(response);
    assertEquals(1, response.size());
    assertEquals(tournId, response.get(0).tournamentId());
  }

  @Test
  void testGetTeamsForTournament() {
    String tournId = "TEST_TOURN_3_" + System.currentTimeMillis();
    ScheduleRecord record =
        new ScheduleRecord(0, tournId, TournamentLevel.Qualification, 1, 10, 11, 12, 13, 14, 15);

    // Save via API
    client
        .toBlocking()
        .exchange(HttpRequest.POST("/api/schedule", record).basicAuth("user", "team1310IsTheBest"));

    // Test with ROLE_EXPERTSCOUT
    HttpRequest<?> request =
        HttpRequest.GET("/api/schedule/teams-for-tournament/" + tournId)
            .basicAuth("user", "default_expert_scout_password_876");

    List<Integer> teams = client.toBlocking().retrieve(request, Argument.listOf(Integer.class));

    assertNotNull(teams);
    assertEquals(6, teams.size());
    assertTrue(teams.contains(10));
    assertTrue(teams.contains(15));

    // Test with ROLE_MEMBER (should be unauthorized for this specific endpoint)
    HttpRequest<?> memberRequest =
        HttpRequest.GET("/api/schedule/teams-for-tournament/" + tournId)
            .basicAuth("user", "team1310IsTheBest");

    assertThrows(
        HttpClientResponseException.class,
        () -> {
          client.toBlocking().exchange(memberRequest);
        });
  }

  @Test
  void testGetTournamentReport() {
    String tournId = "TEST_TOURN_4_" + System.currentTimeMillis();
    int teamId = 1310;

    // Just testing endpoint accessibility and basic response structure
    // Since it requires ROLE_EXPERTSCOUT
    HttpRequest<?> request =
        HttpRequest.GET("/api/schedule/tournament/" + tournId + "/" + teamId)
            .basicAuth("user", "default_expert_scout_password_876");

    HttpResponse<Object> response = client.toBlocking().exchange(request, Object.class);

    assertEquals(HttpStatus.OK, response.getStatus());
    assertNotNull(response.body());
  }

  @Test
  void testUnauthorized() {
    HttpRequest<?> request = HttpRequest.GET("/api/schedule/ANY");
    assertThrows(
        HttpClientResponseException.class,
        () -> {
          client.toBlocking().exchange(request);
        });
  }
}
