package ca.team1310.ravenbrain.tournament;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.connect.TestUserHelper;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

@MicronautTest
public class TournamentApiTest {

  private static final String USER_MEMBER = "tournament-member-testuser";
  private static final String USER_ADMIN = "tournament-admin-testuser";
  private static final String USER_SUPERUSER = "tournament-superuser-testuser";
  private static final String USER_EXPERTSCOUT = "tournament-expertscout-testuser";
  private static final String USER_DATASCOUT = "tournament-datascout-testuser";
  private static final String USER_DRIVE_TEAM = "tournament-driveteam-testuser";

  @Inject
  @Client("/")
  HttpClient client;

  @Inject TournamentService tournamentService;

  @Inject TestUserHelper testUserHelper;

  @org.junit.jupiter.api.BeforeEach
  void setup() {
    testUserHelper.createTestUser(USER_MEMBER, "password", "ROLE_MEMBER");
    testUserHelper.createTestUser(USER_ADMIN, "password", "ROLE_ADMIN");
    testUserHelper.createTestUser(USER_SUPERUSER, "password", "ROLE_SUPERUSER");
    testUserHelper.createTestUser(USER_EXPERTSCOUT, "password", "ROLE_EXPERTSCOUT");
    testUserHelper.createTestUser(USER_DATASCOUT, "password", "ROLE_DATASCOUT");
    testUserHelper.createTestUser(USER_DRIVE_TEAM, "password", "ROLE_DRIVE_TEAM");
  }

  @org.junit.jupiter.api.AfterEach
  void tearDown() {
    testUserHelper.deleteTestUsers();
    // Cleanup tournaments created in tests
    tournamentService
        .findAll()
        .forEach(
            tournament -> {
              if (tournament.id().contains("TEST_TOURN")
                  || tournament.id().contains("GET_TOURN_TEST")
                  || tournament.id().contains("DUPLICATE_ID")
                  || tournament.id().startsWith("WEBCAST_GATE_")) {
                tournamentService.delete(tournament);
              }
            });
  }

  @Test
  void testCreateTournament() {
    String tournId = "TEST_TOURN_" + System.currentTimeMillis();
    TournamentApi.TournamentDTO dto =
        new TournamentApi.TournamentDTO(
            tournId,
            2025,
            "TEST",
            "Test Tournament",
            LocalDateTime.of(2025, 3, 26, 8, 0),
            LocalDateTime.of(2025, 3, 26, 17, 0),
            1);

    HttpRequest<TournamentApi.TournamentDTO> request =
        HttpRequest.POST("/api/tournament", dto).basicAuth(USER_MEMBER, "password");

    HttpResponse<Void> response = client.toBlocking().exchange(request);

    assertEquals(HttpStatus.OK, response.getStatus());

    // Verify it was saved
    TournamentRecord saved = tournamentService.findById(tournId).orElse(null);
    assertNotNull(saved);
    assertEquals("TEST", saved.code());
    assertEquals("Test Tournament", saved.name());
  }

  @Test
  void testGetTournaments() {
    String tournId = "GET_TOURN_TEST_" + System.currentTimeMillis();
    TournamentApi.TournamentDTO dto =
        new TournamentApi.TournamentDTO(
            tournId,
            2025,
            "GETTEST",
            "Get Test Tournament",
            LocalDateTime.of(2025, 4, 1, 8, 0),
            LocalDateTime.of(2025, 4, 1, 17, 0),
            1);

    // Save via API
    client
        .toBlocking()
        .exchange(HttpRequest.POST("/api/tournament", dto).basicAuth(USER_MEMBER, "password"));

    HttpRequest<?> request = HttpRequest.GET("/api/tournament").basicAuth(USER_MEMBER, "password");

    List<TournamentRecord> response =
        client.toBlocking().retrieve(request, Argument.listOf(TournamentRecord.class));

    assertNotNull(response);
    assertTrue(response.stream().anyMatch(t -> t.id().equals(tournId)));
  }

  @Test
  void testAnonymousGetTournaments() {
    HttpRequest<?> request = HttpRequest.GET("/api/tournament");
    HttpResponse<?> response = client.toBlocking().exchange(request);
    assertEquals(HttpStatus.OK, response.getStatus());
  }

  @Test
  void testAnonymousCreateTournamentUnauthorized() {
    TournamentApi.TournamentDTO dto =
        new TournamentApi.TournamentDTO(
            "anon-test", 2025, "ANONTEST", "Anon Test",
            LocalDateTime.of(2025, 3, 26, 8, 0),
            LocalDateTime.of(2025, 3, 26, 17, 0), 1);
    HttpRequest<?> request = HttpRequest.POST("/api/tournament", dto);
    assertThrows(Exception.class, () -> client.toBlocking().exchange(request));
  }

  @Test
  void testCreateTournamentInvalidNullId() {
    TournamentApi.TournamentDTO dto =
        new TournamentApi.TournamentDTO(
            null,
            2025,
            "TEST",
            "Test Tournament",
            LocalDateTime.of(2025, 3, 26, 8, 0),
            LocalDateTime.of(2025, 3, 26, 17, 0),
            1);

    HttpRequest<TournamentApi.TournamentDTO> request =
        HttpRequest.POST("/api/tournament", dto).basicAuth(USER_MEMBER, "password");

    assertThrows(Exception.class, () -> client.toBlocking().exchange(request));
  }

  @Test
  void testCreateTournamentInvalidNullName() {
    TournamentApi.TournamentDTO dto =
        new TournamentApi.TournamentDTO(
            "INVALID_NAME",
            2025,
            "TEST",
            null,
            LocalDateTime.of(2025, 3, 26, 8, 0),
            LocalDateTime.of(2025, 3, 26, 17, 0),
            1);

    HttpRequest<TournamentApi.TournamentDTO> request =
        HttpRequest.POST("/api/tournament", dto).basicAuth(USER_MEMBER, "password");

    assertThrows(Exception.class, () -> client.toBlocking().exchange(request));
  }

  @Test
  void testCreateTournamentDuplicateId() {
    String tournId = "DUPLICATE_ID_" + System.currentTimeMillis();
    TournamentApi.TournamentDTO dto =
        new TournamentApi.TournamentDTO(
            tournId,
            2025,
            "TEST",
            "Test Tournament",
            LocalDateTime.of(2025, 3, 26, 8, 0),
            LocalDateTime.of(2025, 3, 26, 17, 0),
            1);

    HttpRequest<TournamentApi.TournamentDTO> request =
        HttpRequest.POST("/api/tournament", dto).basicAuth(USER_MEMBER, "password");

    // First time should work
    client.toBlocking().exchange(request);

    // Second time with same ID should fail
    assertThrows(Exception.class, () -> client.toBlocking().exchange(request));
  }

  @Test
  void testCreateTournamentInvalidNullDates() {
    TournamentApi.TournamentDTO dto =
        new TournamentApi.TournamentDTO(
            "INVALID_DATES", 2025, "TEST", "Test Tournament", null, null, 1);

    HttpRequest<TournamentApi.TournamentDTO> request =
        HttpRequest.POST("/api/tournament", dto).basicAuth(USER_MEMBER, "password");

    assertThrows(Exception.class, () -> client.toBlocking().exchange(request));
  }

  /** Seeds a tournament for the webcast role-gating tests (via POST so it survives the transaction boundary). */
  private String seedWebcastTournament() {
    String id = "WEBCAST_GATE_" + System.nanoTime();
    TournamentApi.TournamentDTO dto =
        new TournamentApi.TournamentDTO(
            id,
            2026,
            "TESTWG",
            "Webcast Gate Test",
            LocalDateTime.of(2026, 3, 20, 0, 0),
            LocalDateTime.of(2026, 3, 22, 0, 0),
            3);
    client
        .toBlocking()
        .exchange(HttpRequest.POST("/api/tournament", dto).basicAuth(USER_MEMBER, "password"));
    return id;
  }

  @Test
  void addWebcast_memberIsForbidden() {
    String id = seedWebcastTournament();
    HttpRequest<?> request =
        HttpRequest.PUT(
                "/api/tournament/" + id + "/webcast",
                new TournamentApi.WebcastRequest("https://twitch.tv/tryme"))
            .basicAuth(USER_MEMBER, "password");

    HttpClientResponseException thrown =
        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
    assertEquals(HttpStatus.FORBIDDEN, thrown.getStatus());
  }

  @Test
  void addWebcast_expertScoutIsForbidden() {
    String id = seedWebcastTournament();
    HttpRequest<?> request =
        HttpRequest.PUT(
                "/api/tournament/" + id + "/webcast",
                new TournamentApi.WebcastRequest("https://twitch.tv/tryme"))
            .basicAuth(USER_EXPERTSCOUT, "password");

    HttpClientResponseException thrown =
        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
    assertEquals(HttpStatus.FORBIDDEN, thrown.getStatus());
  }

  @Test
  void addWebcast_dataScoutIsForbidden() {
    String id = seedWebcastTournament();
    HttpRequest<?> request =
        HttpRequest.PUT(
                "/api/tournament/" + id + "/webcast",
                new TournamentApi.WebcastRequest("https://twitch.tv/tryme"))
            .basicAuth(USER_DATASCOUT, "password");

    HttpClientResponseException thrown =
        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
    assertEquals(HttpStatus.FORBIDDEN, thrown.getStatus());
  }

  @Test
  void addWebcast_driveTeamIsForbidden() {
    String id = seedWebcastTournament();
    HttpRequest<?> request =
        HttpRequest.PUT(
                "/api/tournament/" + id + "/webcast",
                new TournamentApi.WebcastRequest("https://twitch.tv/tryme"))
            .basicAuth(USER_DRIVE_TEAM, "password");

    HttpClientResponseException thrown =
        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
    assertEquals(HttpStatus.FORBIDDEN, thrown.getStatus());
  }

  @Test
  void addWebcast_adminSucceeds() {
    String id = seedWebcastTournament();
    HttpRequest<?> request =
        HttpRequest.PUT(
                "/api/tournament/" + id + "/webcast",
                new TournamentApi.WebcastRequest("https://twitch.tv/tryme"))
            .basicAuth(USER_ADMIN, "password");

    HttpResponse<?> response = client.toBlocking().exchange(request);
    assertEquals(HttpStatus.OK, response.getStatus());

    TournamentRecord saved = tournamentService.findById(id).orElseThrow();
    assertNotNull(saved.manualWebcasts());
    assertTrue(saved.manualWebcasts().contains("https://twitch.tv/tryme"));
  }

  @Test
  void addWebcast_superuserSucceeds() {
    String id = seedWebcastTournament();
    HttpRequest<?> request =
        HttpRequest.PUT(
                "/api/tournament/" + id + "/webcast",
                new TournamentApi.WebcastRequest("https://twitch.tv/tryme"))
            .basicAuth(USER_SUPERUSER, "password");

    HttpResponse<?> response = client.toBlocking().exchange(request);
    assertEquals(HttpStatus.OK, response.getStatus());
  }

  @Test
  void removeWebcast_memberIsForbidden() {
    String id = seedWebcastTournament();
    // Seed a URL as admin first so there's something to remove.
    client
        .toBlocking()
        .exchange(
            HttpRequest.PUT(
                    "/api/tournament/" + id + "/webcast",
                    new TournamentApi.WebcastRequest("https://twitch.tv/tryme"))
                .basicAuth(USER_ADMIN, "password"));

    HttpRequest<?> request =
        HttpRequest.DELETE(
                "/api/tournament/" + id + "/webcast",
                new TournamentApi.WebcastRequest("https://twitch.tv/tryme"))
            .basicAuth(USER_MEMBER, "password");

    HttpClientResponseException thrown =
        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
    assertEquals(HttpStatus.FORBIDDEN, thrown.getStatus());
  }

  @Test
  void removeWebcast_adminSucceeds() {
    String id = seedWebcastTournament();
    client
        .toBlocking()
        .exchange(
            HttpRequest.PUT(
                    "/api/tournament/" + id + "/webcast",
                    new TournamentApi.WebcastRequest("https://twitch.tv/tryme"))
                .basicAuth(USER_ADMIN, "password"));

    HttpRequest<?> request =
        HttpRequest.DELETE(
                "/api/tournament/" + id + "/webcast",
                new TournamentApi.WebcastRequest("https://twitch.tv/tryme"))
            .basicAuth(USER_ADMIN, "password");

    HttpResponse<?> response = client.toBlocking().exchange(request);
    assertEquals(HttpStatus.OK, response.getStatus());
  }
}
