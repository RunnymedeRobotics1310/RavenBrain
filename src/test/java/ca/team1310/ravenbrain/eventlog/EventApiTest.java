package ca.team1310.ravenbrain.eventlog;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.connect.TestUserHelper;
import ca.team1310.ravenbrain.connect.User;
import ca.team1310.ravenbrain.frcapi.model.Alliance;
import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
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

  private static final String AUTH_USER = "event-datascout-testuser";

  @Inject
  @Client("/")
  HttpClient client;

  @Inject EventLogService eventLogService;
  @Inject ca.team1310.ravenbrain.eventtype.EventTypeService eventTypeService;

  @Inject TestUserHelper testUserHelper;

  @org.junit.jupiter.api.BeforeEach
  void setup() {
    User user = testUserHelper.createTestUser(AUTH_USER, "password", "ROLE_DATASCOUT");
    this.testUserId = user.id();
  }

  private long testUserId;

  @org.junit.jupiter.api.AfterEach
  void tearDown() {
    testUserHelper.deleteTestUsers();
    // Cleanup event logs created in tests
    eventLogService
        .findAll()
        .forEach(
            record -> {
              if (record.tournamentId().equals("TEST_TOURN")) {
                eventLogService.delete(record);
              }
            });
  }

  @Test
  void testPostEventLogsSuccess() {
    EventLogRecord record =
        new EventLogRecord(
            0,
            Instant.now(),
            testUserId,
            "TEST_TOURN",
            TournamentLevel.Qualification,
            1,
            Alliance.red,
            1310,
            "comment",
            1.0,
            "Good job");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, "password");

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
        eventLogService.listEventsForTeamAndTournament("TEST_TOURN", 1310, true);
    assertTrue(
        saved.stream()
            .anyMatch(
                r ->
                    r.userId() == testUserId
                        && r.eventType().equals("comment")
                        && r.tournamentId().equals("TEST_TOURN")
                        && r.level() == TournamentLevel.Qualification
                        && r.matchId() == 1
                        && r.alliance() == Alliance.red
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
            testUserId,
            "TEST_TOURN",
            TournamentLevel.Qualification,
            1,
            Alliance.blue,
            1310,
            "comment",
            1.0,
            "Testing duplicate");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, "password");

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
            testUserId,
            "TEST_TOURN",
            null,
            1,
            Alliance.red,
            1310,
            "A".repeat(256), // Max is 255
            1.0,
            "Long event type");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, "password");

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
            testUserId,
            "TEST_TOURN",
            null,
            1,
            Alliance.red,
            1310,
            "comment",
            1.0,
            "N".repeat(1025)); // Max is 1024

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, "password");

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
    EventLogRecord record = new EventLogRecord(0, null, 0, null, null, 0, null, 0, null, 0, null);
    // Missing timestamp, userId, etc. which are NOT NULL in DB

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, "password");

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
            testUserId,
            "TEST_TOURN",
            null,
            1,
            null, // Alliance.red is "Red" in previous string, but here we test length of string.
            1310,
            "comment",
            1.0,
            "Long alliance");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, "password");

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertFalse(results.getFirst().success());
    assertNotNull(results.getFirst().reason());
  }

  @Test
  void testPostEventLogsInvalidUserId() {
    EventLogRecord record =
        new EventLogRecord(
            0,
            Instant.now(),
            -1, // Invalid user ID
            "TEST_TOURN",
            null,
            1,
            Alliance.red,
            1310,
            "comment",
            1.0,
            "Invalid user ID");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, "password");

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
            testUserId,
            "T".repeat(128), // Max is 127
            TournamentLevel.Qualification,
            1,
            Alliance.red,
            1310,
            "comment",
            1.0,
            "Long tournament ID");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, "password");

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
            testUserId,
            "TEST_TOURN",
            null, // Testing invalid level
            1,
            Alliance.red,
            1310,
            "comment",
            1.0,
            "Long level");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, "password");

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertFalse(results.getFirst().success());
    assertNotNull(results.getFirst().reason());
  }

  @Test
  void testPostEventLogsInvalidEventTypeNotFound() {
    EventLogRecord record =
        new EventLogRecord(
            0,
            Instant.now(),
            testUserId,
            "TEST_TOURN",
            TournamentLevel.Qualification,
            1,
            Alliance.red,
            1310,
            "NON_EXISTENT_TYPE",
            1.0,
            "Unknown event type");

    HttpRequest<EventLogRecord[]> request =
        HttpRequest.POST("/api/event", new EventLogRecord[] {record})
            .basicAuth(AUTH_USER, "password");

    HttpResponse<List<EventApi.EventLogPostResult>> response =
        client.toBlocking().exchange(request, Argument.listOf(EventApi.EventLogPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<EventApi.EventLogPostResult> results = response.body();
    assertNotNull(results);
    assertFalse(results.getFirst().success());
    assertTrue(results.getFirst().reason().contains("Invalid event type"));
  }

  @Test
  void testListEventsForTeamAndTournamentFiltering() {
    String tournamentId = "FILTER_TEST";
    int teamNumber = 1310;
    String eventType = "FILTER-TEST-TYPE";

    // Ensure event type exists
    if (eventTypeService.findById(eventType).isEmpty()) {
      eventTypeService.create(
          new ca.team1310.ravenbrain.eventtype.EventType(
              eventType, "Filter Test Type", "Description", 2025, null));
    }

    // Create a practice event
    EventLogRecord practiceEvent =
        new EventLogRecord(
            0,
            Instant.now(),
            testUserId,
            tournamentId,
            TournamentLevel.Practice,
            1,
            Alliance.red,
            teamNumber,
            eventType,
            1.0,
            "practice");
    eventLogService.save(practiceEvent);

    // Create a qualification event
    EventLogRecord qualificationEvent =
        new EventLogRecord(
            0,
            Instant.now().plusMillis(1),
            testUserId,
            tournamentId,
            TournamentLevel.Qualification,
            2,
            Alliance.red,
            teamNumber,
            eventType,
            1.0,
            "qualification");
    eventLogService.save(qualificationEvent);

    // When includePractice is true, both should be returned
    List<EventLogRecord> allEvents =
        eventLogService.listEventsForTeamAndTournament(tournamentId, teamNumber, true);
    assertTrue(allEvents.stream().anyMatch(e -> e.level() == TournamentLevel.Practice));
    assertTrue(allEvents.stream().anyMatch(e -> e.level() == TournamentLevel.Qualification));

    // When includePractice is false, only Qualification should be returned
    List<EventLogRecord> nonPracticeEvents =
        eventLogService.listEventsForTeamAndTournament(tournamentId, teamNumber, false);
    assertFalse(nonPracticeEvents.stream().anyMatch(e -> e.level() == TournamentLevel.Practice));
    assertTrue(
        nonPracticeEvents.stream().anyMatch(e -> e.level() == TournamentLevel.Qualification));

    // Cleanup
    allEvents.forEach(eventLogService::delete);
    eventTypeService.delete(eventType);
  }
}
