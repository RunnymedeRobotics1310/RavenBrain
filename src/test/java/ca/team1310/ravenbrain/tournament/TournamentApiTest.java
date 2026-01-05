package ca.team1310.ravenbrain.tournament;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.connect.TestUserHelper;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

@MicronautTest
public class TournamentApiTest {

  private static final String USER_MEMBER = "tournament-member-testuser";

  @Inject
  @Client("/")
  HttpClient client;

  @Inject TournamentService tournamentService;

  @Inject TestUserHelper testUserHelper;

  @org.junit.jupiter.api.BeforeEach
  void setup() {
    testUserHelper.createTestUser(USER_MEMBER, "password", "ROLE_MEMBER");
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
                  || tournament.id().contains("DUPLICATE_ID")) {
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
            LocalDateTime.of(2025, 3, 26, 17, 0));

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
            LocalDateTime.of(2025, 4, 1, 17, 0));

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
  void testUnauthorized() {
    HttpRequest<?> request = HttpRequest.GET("/api/tournament");
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
            LocalDateTime.of(2025, 3, 26, 17, 0));

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
            LocalDateTime.of(2025, 3, 26, 17, 0));

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
            LocalDateTime.of(2025, 3, 26, 17, 0));

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
            "INVALID_DATES", 2025, "TEST", "Test Tournament", null, null);

    HttpRequest<TournamentApi.TournamentDTO> request =
        HttpRequest.POST("/api/tournament", dto).basicAuth(USER_MEMBER, "password");

    assertThrows(Exception.class, () -> client.toBlocking().exchange(request));
  }
}
