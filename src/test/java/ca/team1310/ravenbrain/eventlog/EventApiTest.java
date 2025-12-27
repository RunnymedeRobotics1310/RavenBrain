package ca.team1310.ravenbrain.eventlog;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.connect.Config;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

@MicronautTest
public class EventApiTest {

  @Inject
  @Client("/")
  HttpClient client;

  @Inject EventLogService eventLogService;

  @Inject Config config;

  private static final String AUTH_USER = "user";

  @Test
  void testPostEventLogsSuccess() {
    EventLogRecord record =
        new EventLogRecord(
            0,
            Instant.now(),
            "Test Scout",
            "TEST_TOURN",
            "Qualification",
            1,
            "Red",
            1310,
            "SCORE",
            1.0,
            "Good job");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, config.getDatascout());

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertEquals(1, results.size());
    assertTrue(results.getFirst().success());
    assertNull(results.getFirst().reason());

    // Verify it was saved
    List<EventLogRecord> saved =
        eventLogService.findAllByTournamentIdAndTeamNumberOrderByMatchId("TEST_TOURN", 1310);
    assertTrue(
        saved.stream()
            .anyMatch(
                r ->
                    r.scoutName().equals("Test Scout")
                        && r.eventType().equals("SCORE")
                        && r.tournamentId().equals("TEST_TOURN")
                        && r.level().equals("Qualification")
                        && r.matchId() == 1
                        && r.alliance().equals("Red")
                        && r.teamNumber() == 1310
                        && r.amount() == 1.0
                        && r.note().equals("Good job")
                        && r.timestamp() != null));
  }

  @Test
  void testPostEventLogsDuplicate() {
    Instant now = Instant.now();
    EventLogRecord record =
        new EventLogRecord(
            0,
            now,
            "Duplicate Scout",
            "TEST_TOURN",
            "Qualification",
            1,
            "Blue",
            1310,
            "CLIMB",
            1.0,
            "Testing duplicate");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, config.getDatascout());

    // First time
    client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    // Second time - should still return success=true as per EventApi logic
    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertEquals(1, results.size());
    assertTrue(results.getFirst().success());
  }

  @Test
  void testPostEventLogsInvalidEventTypeLength() {
    EventLogRecord record =
        new EventLogRecord(
            0,
            Instant.now(),
            "Test Scout",
            "TEST_TOURN",
            null,
            1,
            "Red",
            1310,
            "A".repeat(256), // Max is 255
            1.0,
            "Long event type");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, config.getDatascout());

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertFalse(results.getFirst().success());
    assertNotNull(results.getFirst().reason());
  }

  @Test
  void testPostEventLogsInvalidNoteLength() {
    EventLogRecord record =
        new EventLogRecord(
            0,
            Instant.now(),
            "Test Scout",
            "TEST_TOURN",
            null,
            1,
            "Red",
            1310,
            "SCORE",
            1.0,
            "N".repeat(1025)); // Max is 1024

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, config.getDatascout());

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertFalse(results.getFirst().success());
    assertNotNull(results.getFirst().reason());
  }

  @Test
  void testPostEventLogsMissingRequiredFields() {
    EventLogRecord record =
        new EventLogRecord(0, null, null, null, null, 0, null, 0, null, 0, null);
    // Missing timestamp, scoutName, etc. which are NOT NULL in DB

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, config.getDatascout());

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertFalse(results.getFirst().success());
    assertNotNull(results.getFirst().reason());
  }

  @Test
  void testPostEventLogsInvalidAllianceLength() {
    EventLogRecord record =
        new EventLogRecord(
            0,
            Instant.now(),
            "Test Scout",
            "TEST_TOURN",
            null,
            1,
            "A".repeat(65), // Max is 64
            1310,
            "SCORE",
            1.0,
            "Long alliance");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, config.getDatascout());

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertFalse(results.getFirst().success());
    assertNotNull(results.getFirst().reason());
  }

  @Test
  void testPostEventLogsInvalidScoutNameLength() {
    EventLogRecord record =
        new EventLogRecord(
            0,
            Instant.now(),
            "S".repeat(256), // Max is 255
            "TEST_TOURN",
            null,
            1,
            "Red",
            1310,
            "SCORE",
            1.0,
            "Long scout name");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, config.getDatascout());

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertFalse(results.getFirst().success());
    assertNotNull(results.getFirst().reason());
  }

  @Test
  void testPostEventLogsInvalidTournamentIdLength() {
    EventLogRecord record =
        new EventLogRecord(
            0,
            Instant.now(),
            "Test Scout",
            "T".repeat(128), // Max is 127
            "Qualification",
            1,
            "Red",
            1310,
            "SCORE",
            1.0,
            "Long tournament ID");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, config.getDatascout());

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertFalse(results.getFirst().success());
    assertNotNull(results.getFirst().reason());
  }

  @Test
  void testPostEventLogsInvalidLevelLength() {
    EventLogRecord record =
        new EventLogRecord(
            0,
            Instant.now(),
            "Test Scout",
            "TEST_TOURN",
            "L".repeat(128), // Max is 127
            1,
            "Red",
            1310,
            "SCORE",
            1.0,
            "Long level");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, config.getDatascout());

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertFalse(results.getFirst().success());
    assertNotNull(results.getFirst().reason());
  }
}
