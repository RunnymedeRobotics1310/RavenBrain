package ca.team1310.ravenbrain.eventlog;

import static org.junit.jupiter.api.Assertions.*;

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

  private static final String AUTH_USER = "user";
  private static final String AUTH_PASS = "default_data_scout_password__1";

  @Test
  void testPostEventLogsSuccess() {
    EventLogRecord record = new EventLogRecord();
    record.setTimestamp(Instant.now());
    record.setScoutName("Test Scout");
    record.setTournamentId("TEST_TOURN");
    record.setLevel("Qualification");
    record.setMatchId(1);
    record.setAlliance("Red");
    record.setTeamNumber(1310);
    record.setEventType("SCORE");
    record.setAmount(1.0);
    record.setNote("Good job");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, AUTH_PASS);

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertEquals(1, results.size());
    assertTrue(results.get(0).success());
    assertNull(results.get(0).reason());

    // Verify it was saved
    List<EventLogRecord> saved =
        eventLogService.findAllByTournamentIdAndTeamNumberOrderByMatchId("TEST_TOURN", 1310);
    assertTrue(
        saved.stream()
            .anyMatch(
                r ->
                    r.getScoutName().equals("Test Scout")
                        && r.getEventType().equals("SCORE")
                        && r.getTournamentId().equals("TEST_TOURN")
                        && r.getLevel().equals("Qualification")
                        && r.getMatchId() == 1
                        && r.getAlliance().equals("Red")
                        && r.getTeamNumber() == 1310
                        && r.getAmount() == 1.0
                        && r.getNote().equals("Good job")
                        && r.getTimestamp() != null));
  }

  @Test
  void testPostEventLogsDuplicate() {
    Instant now = Instant.now();
    EventLogRecord record = new EventLogRecord();
    record.setTimestamp(now);
    record.setScoutName("Duplicate Scout");
    record.setTournamentId("TEST_TOURN");
    record.setLevel("Qualification");
    record.setMatchId(1);
    record.setAlliance("Blue");
    record.setTeamNumber(1310);
    record.setEventType("CLIMB");
    record.setAmount(1.0);
    record.setNote("Testing duplicate");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, AUTH_PASS);

    // First time
    client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    // Second time - should still return success=true as per EventApi logic
    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertEquals(1, results.size());
    assertTrue(results.get(0).success());
  }

  @Test
  void testPostEventLogsInvalidEventTypeLength() {
    EventLogRecord record = new EventLogRecord();
    record.setTimestamp(Instant.now());
    record.setScoutName("Test Scout");
    record.setTournamentId("TEST_TOURN");
    record.setMatchId(1);
    record.setAlliance("Red");
    record.setTeamNumber(1310);
    record.setEventType("A".repeat(256)); // Max is 255
    record.setAmount(1.0);
    record.setNote("Long event type");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, AUTH_PASS);

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertFalse(results.get(0).success());
    assertNotNull(results.get(0).reason());
  }

  @Test
  void testPostEventLogsInvalidNoteLength() {
    EventLogRecord record = new EventLogRecord();
    record.setTimestamp(Instant.now());
    record.setScoutName("Test Scout");
    record.setTournamentId("TEST_TOURN");
    record.setMatchId(1);
    record.setAlliance("Red");
    record.setTeamNumber(1310);
    record.setEventType("SCORE");
    record.setAmount(1.0);
    record.setNote("N".repeat(1025)); // Max is 1024

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, AUTH_PASS);

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertFalse(results.get(0).success());
    assertNotNull(results.get(0).reason());
  }

  @Test
  void testPostEventLogsMissingRequiredFields() {
    EventLogRecord record = new EventLogRecord();
    // Missing timestamp, scoutName, etc. which are NOT NULL in DB

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, AUTH_PASS);

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertFalse(results.get(0).success());
    assertNotNull(results.get(0).reason());
  }

  @Test
  void testPostEventLogsInvalidAllianceLength() {
    EventLogRecord record = new EventLogRecord();
    record.setTimestamp(Instant.now());
    record.setScoutName("Test Scout");
    record.setTournamentId("TEST_TOURN");
    record.setMatchId(1);
    record.setAlliance("A".repeat(65)); // Max is 64
    record.setTeamNumber(1310);
    record.setEventType("SCORE");
    record.setAmount(1.0);
    record.setNote("Long alliance");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, AUTH_PASS);

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertFalse(results.get(0).success());
    assertNotNull(results.get(0).reason());
  }

  @Test
  void testPostEventLogsInvalidScoutNameLength() {
    EventLogRecord record = new EventLogRecord();
    record.setTimestamp(Instant.now());
    record.setScoutName("S".repeat(256)); // Max is 255
    record.setTournamentId("TEST_TOURN");
    record.setMatchId(1);
    record.setAlliance("Red");
    record.setTeamNumber(1310);
    record.setEventType("SCORE");
    record.setAmount(1.0);
    record.setNote("Long scout name");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, AUTH_PASS);

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertFalse(results.get(0).success());
    assertNotNull(results.get(0).reason());
  }

  @Test
  void testPostEventLogsInvalidTournamentIdLength() {
    EventLogRecord record = new EventLogRecord();
    record.setTimestamp(Instant.now());
    record.setScoutName("Test Scout");
    record.setTournamentId("T".repeat(128)); // Max is 127
    record.setLevel("Qualification");
    record.setMatchId(1);
    record.setAlliance("Red");
    record.setTeamNumber(1310);
    record.setEventType("SCORE");
    record.setAmount(1.0);
    record.setNote("Long tournament ID");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, AUTH_PASS);

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertFalse(results.get(0).success());
    assertNotNull(results.get(0).reason());
  }

  @Test
  void testPostEventLogsInvalidLevelLength() {
    EventLogRecord record = new EventLogRecord();
    record.setTimestamp(Instant.now());
    record.setScoutName("Test Scout");
    record.setTournamentId("TEST_TOURN");
    record.setLevel("L".repeat(128)); // Max is 127
    record.setMatchId(1);
    record.setAlliance("Red");
    record.setTeamNumber(1310);
    record.setEventType("SCORE");
    record.setAmount(1.0);
    record.setNote("Long level");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, AUTH_PASS);

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertFalse(results.get(0).success());
    assertNotNull(results.get(0).reason());
  }
}
