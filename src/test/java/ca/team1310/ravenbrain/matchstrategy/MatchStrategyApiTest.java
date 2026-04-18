package ca.team1310.ravenbrain.matchstrategy;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.connect.TestUserHelper;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
public class MatchStrategyApiTest {

  private static final String TOURNAMENT = "TEST_STRAT_API_2026";
  private static final String USER_EXPERT = "strat-expert-testuser";
  private static final String USER_EXPERT_2 = "strat-expert2-testuser";
  private static final String USER_DATASCOUT = "strat-datascout-testuser";

  @Inject
  @Client("/")
  HttpClient client;

  @Inject TournamentService tournamentService;
  @Inject MatchStrategyPlanService planService;
  @Inject MatchStrategyDrawingService drawingService;
  @Inject TestUserHelper testUserHelper;

  @BeforeEach
  void setup() {
    testUserHelper.createTestUser(USER_EXPERT, "password", "ROLE_EXPERTSCOUT");
    testUserHelper.createTestUser(USER_EXPERT_2, "password", "ROLE_EXPERTSCOUT");
    testUserHelper.createTestUser(USER_DATASCOUT, "password", "ROLE_DATASCOUT");
    // Clean up any leftover state from a previous aborted run
    cleanupMatchStrategyData();
    try {
      tournamentService.deleteById(TOURNAMENT);
    } catch (Exception ignored) {
    }
    tournamentService.save(
        new TournamentRecord(
            TOURNAMENT,
            "TESTCODE",
            2026,
            "Test Strategy Tournament",
            Instant.parse("2026-06-01T12:00:00Z"),
            Instant.parse("2026-06-03T12:00:00Z"),
            1,
            null,
            null));
  }

  @AfterEach
  void tearDown() {
    cleanupMatchStrategyData();
    try {
      tournamentService.deleteById(TOURNAMENT);
    } catch (Exception ignored) {
    }
    testUserHelper.deleteTestUsers();
  }

  private void cleanupMatchStrategyData() {
    // Drawings first (FK to plans)
    planService
        .findAll()
        .forEach(
            p -> {
              if (TOURNAMENT.equals(p.tournamentId())) {
                drawingService
                    .findAllByPlanIdOrderByCreatedAtAsc(p.id())
                    .forEach(
                        d -> {
                          try {
                            drawingService.delete(d);
                          } catch (Exception ignored) {
                          }
                        });
                try {
                  planService.delete(p);
                } catch (Exception ignored) {
                }
              }
            });
  }

  @Test
  void testUnauthenticatedReturns401() {
    assertThrows(
        HttpClientResponseException.class,
        () -> client.toBlocking().exchange(HttpRequest.GET("/api/match-strategy/" + TOURNAMENT)));
  }

  @Test
  void testDatascoutIsForbidden() {
    HttpRequest<?> req =
        HttpRequest.GET("/api/match-strategy/" + TOURNAMENT).basicAuth(USER_DATASCOUT, "password");
    HttpClientResponseException e =
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(req));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
  }

  @Test
  void testUpsertPlanCreatesThenUpdates() {
    MatchStrategyApi.PlanUpsertRequest create =
        new MatchStrategyApi.PlanUpsertRequest(
            TOURNAMENT, "Qualification", 1, "First plan", "Long strategy text");

    MatchStrategyPlan created =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.POST("/api/match-strategy", create).basicAuth(USER_EXPERT, "password"),
                MatchStrategyPlan.class);
    assertNotNull(created.id());
    assertEquals("First plan", created.shortSummary());
    assertEquals("Long strategy text", created.strategyText());
    assertEquals(USER_EXPERT, created.updatedByDisplayName().toLowerCase().contains("expert") ? created.updatedByDisplayName() : created.updatedByDisplayName());
    Long firstId = created.id();

    // Second POST with same natural key must update, not create
    MatchStrategyApi.PlanUpsertRequest update =
        new MatchStrategyApi.PlanUpsertRequest(
            TOURNAMENT, "Qualification", 1, "Updated summary", "Revised strategy");
    MatchStrategyPlan updated =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.POST("/api/match-strategy", update).basicAuth(USER_EXPERT_2, "password"),
                MatchStrategyPlan.class);
    assertEquals(firstId, updated.id());
    assertEquals("Updated summary", updated.shortSummary());
    assertEquals("Revised strategy", updated.strategyText());
  }

  @Test
  void testShortSummaryTruncatedTo32() {
    String tooLong = "123456789012345678901234567890ABCDEFGH"; // 38 chars
    MatchStrategyApi.PlanUpsertRequest req =
        new MatchStrategyApi.PlanUpsertRequest(TOURNAMENT, "Qualification", 2, tooLong, "note");
    MatchStrategyPlan plan =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.POST("/api/match-strategy", req).basicAuth(USER_EXPERT, "password"),
                MatchStrategyPlan.class);
    assertEquals(32, plan.shortSummary().length());
    assertEquals(tooLong.substring(0, 32), plan.shortSummary());
  }

  @Test
  void testDrawingCreateUpdateDeletePreservesCreator() {
    // Create a drawing (server auto-creates the plan row too)
    MatchStrategyApi.DrawingUpsertRequest createReq =
        new MatchStrategyApi.DrawingUpsertRequest(
            null, TOURNAMENT, "Qualification", 3, "Auto", "[]");
    MatchStrategyDrawing created =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.POST("/api/match-strategy/drawing", createReq)
                    .basicAuth(USER_EXPERT, "password"),
                MatchStrategyDrawing.class);
    assertNotNull(created.id());
    assertNotNull(created.planId());
    assertEquals("Auto", created.label());
    assertEquals("[]", created.strokes());
    String originalCreator = created.createdByDisplayName();
    assertNotNull(originalCreator);
    assertEquals(created.createdByUserId(), created.updatedByUserId());

    // Update same drawing as a different expert user — creator fields must be preserved
    MatchStrategyApi.DrawingUpsertRequest updateReq =
        new MatchStrategyApi.DrawingUpsertRequest(
            created.id(),
            TOURNAMENT,
            "Qualification",
            3,
            "Auto v2",
            "[{\"robotSlot\":\"R1\",\"colorIndex\":0,\"points\":[]}]");
    MatchStrategyDrawing updated =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.POST("/api/match-strategy/drawing", updateReq)
                    .basicAuth(USER_EXPERT_2, "password"),
                MatchStrategyDrawing.class);
    assertEquals(created.id(), updated.id());
    assertEquals("Auto v2", updated.label());
    assertTrue(updated.strokes().contains("robotSlot"));
    assertEquals(originalCreator, updated.createdByDisplayName());
    assertEquals(created.createdByUserId(), updated.createdByUserId());
    assertNotEquals(updated.createdByUserId(), updated.updatedByUserId());

    // Two concurrent creates on the same plan produce two distinct drawings
    MatchStrategyApi.DrawingUpsertRequest secondDrawing =
        new MatchStrategyApi.DrawingUpsertRequest(
            null, TOURNAMENT, "Qualification", 3, "Defence", "[]");
    MatchStrategyDrawing another =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.POST("/api/match-strategy/drawing", secondDrawing)
                    .basicAuth(USER_EXPERT_2, "password"),
                MatchStrategyDrawing.class);
    assertNotEquals(created.id(), another.id());
    assertEquals(updated.planId(), another.planId());

    // GET one returns both drawings
    HttpResponse<MatchStrategyApi.PlanWithDrawings> getResp =
        client
            .toBlocking()
            .exchange(
                HttpRequest.GET("/api/match-strategy/" + TOURNAMENT + "/Qualification/3")
                    .basicAuth(USER_EXPERT, "password"),
                MatchStrategyApi.PlanWithDrawings.class);
    assertEquals(HttpStatus.OK, getResp.getStatus());
    assertNotNull(getResp.body());
    assertEquals(2, getResp.body().drawings().size());

    // Delete one drawing
    HttpResponse<?> delResp =
        client
            .toBlocking()
            .exchange(
                HttpRequest.DELETE("/api/match-strategy/drawing/" + another.id())
                    .basicAuth(USER_EXPERT, "password"));
    assertEquals(HttpStatus.NO_CONTENT, delResp.getStatus());

    // GET one now shows just the remaining drawing
    HttpResponse<MatchStrategyApi.PlanWithDrawings> getResp2 =
        client
            .toBlocking()
            .exchange(
                HttpRequest.GET("/api/match-strategy/" + TOURNAMENT + "/Qualification/3")
                    .basicAuth(USER_EXPERT, "password"),
                MatchStrategyApi.PlanWithDrawings.class);
    assertEquals(1, getResp2.body().drawings().size());
  }

  @Test
  void testGetOneReturns404WhenMissing() {
    HttpRequest<?> req =
        HttpRequest.GET("/api/match-strategy/" + TOURNAMENT + "/Qualification/99")
            .basicAuth(USER_EXPERT, "password");
    HttpClientResponseException e =
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(req));
    assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
  }

  @Test
  void testListForTournamentReturnsEmbeddedDrawings() {
    // Create two plans with drawings
    client
        .toBlocking()
        .retrieve(
            HttpRequest.POST(
                    "/api/match-strategy/drawing",
                    new MatchStrategyApi.DrawingUpsertRequest(
                        null, TOURNAMENT, "Qualification", 10, "Auto", "[]"))
                .basicAuth(USER_EXPERT, "password"),
            MatchStrategyDrawing.class);
    client
        .toBlocking()
        .retrieve(
            HttpRequest.POST(
                    "/api/match-strategy/drawing",
                    new MatchStrategyApi.DrawingUpsertRequest(
                        null, TOURNAMENT, "Qualification", 11, "Auto", "[]"))
                .basicAuth(USER_EXPERT, "password"),
            MatchStrategyDrawing.class);

    List<MatchStrategyApi.PlanWithDrawings> list =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.GET("/api/match-strategy/" + TOURNAMENT)
                    .basicAuth(USER_EXPERT, "password"),
                Argument.listOf(MatchStrategyApi.PlanWithDrawings.class));
    assertEquals(2, list.size());
    assertTrue(list.stream().allMatch(pwd -> pwd.drawings().size() == 1));
  }
}
