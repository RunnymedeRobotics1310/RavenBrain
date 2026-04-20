package ca.team1310.ravenbrain.teamcapability;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.connect.TestUserHelper;
import ca.team1310.ravenbrain.statboticsapi.service.StatboticsTeamEventRecord;
import ca.team1310.ravenbrain.statboticsapi.service.StatboticsTeamEventRepo;
import ca.team1310.ravenbrain.tbaapi.service.TbaEventOprsRecord;
import ca.team1310.ravenbrain.tbaapi.service.TbaEventOprsRepo;
import ca.team1310.ravenbrain.tournament.TeamTournamentService;
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

/**
 * Integration test for {@link TeamCapabilityApi}. Exercises the full stack — controller, cache,
 * enricher, repositories — against the Testcontainers-provisioned MySQL. Verifies cache hit, ETag
 * round-trip, and 404 on invalid tournament.
 */
@MicronautTest
public class TeamCapabilityApiTest {

  private static final String TOURNAMENT_ID = "TEST_CAP_API_2026";
  private static final String TBA_KEY = "2026capapitest";
  private static final String USER_MEMBER = "cap-member-testuser";

  @Inject
  @Client("/")
  HttpClient client;

  @Inject TournamentService tournamentService;
  @Inject TeamTournamentService teamTournamentService;
  @Inject TbaEventOprsRepo tbaEventOprsRepo;
  @Inject StatboticsTeamEventRepo statboticsTeamEventRepo;
  @Inject TeamCapabilityCache cache;
  @Inject TestUserHelper testUserHelper;

  @BeforeEach
  void setup() {
    testUserHelper.createTestUser(USER_MEMBER, "password", "ROLE_MEMBER");
    cleanup();
    tournamentService.save(
        new TournamentRecord(
            TOURNAMENT_ID,
            "CAPAPI",
            2026,
            "Capability API Test",
            Instant.parse("2026-06-01T12:00:00Z"),
            Instant.parse("2026-06-03T12:00:00Z"),
            1,
            null,
            TBA_KEY));
    teamTournamentService.replaceTeamsForTournament(TOURNAMENT_ID, List.of(1310, 2056, 254));
    tbaEventOprsRepo.save(
        new TbaEventOprsRecord(TBA_KEY, 1310, 40.0, null, null, Instant.now(), 200));
    tbaEventOprsRepo.save(
        new TbaEventOprsRecord(TBA_KEY, 2056, 55.0, null, null, Instant.now(), 200));
    tbaEventOprsRepo.save(
        new TbaEventOprsRecord(TBA_KEY, 254, 70.0, null, null, Instant.now(), 200));
    statboticsTeamEventRepo.save(
        new StatboticsTeamEventRecord(
            TBA_KEY,
            1310,
            TOURNAMENT_ID,
            42.0,
            12.0,
            24.0,
            6.0,
            null,
            null,
            null,
            Instant.now(),
            200));
    statboticsTeamEventRepo.save(
        new StatboticsTeamEventRecord(
            TBA_KEY,
            2056,
            TOURNAMENT_ID,
            58.0,
            16.0,
            32.0,
            10.0,
            null,
            null,
            null,
            Instant.now(),
            200));
    statboticsTeamEventRepo.save(
        new StatboticsTeamEventRecord(
            TBA_KEY,
            254,
            TOURNAMENT_ID,
            72.0,
            20.0,
            40.0,
            12.0,
            null,
            null,
            null,
            Instant.now(),
            200));
    cache.invalidate(TOURNAMENT_ID);
  }

  @AfterEach
  void tearDown() {
    cleanup();
    testUserHelper.deleteTestUsers();
  }

  private void cleanup() {
    try {
      tbaEventOprsRepo.deleteByTbaEventKey(TBA_KEY);
    } catch (Exception ignored) {
    }
    try {
      statboticsTeamEventRepo.deleteByTbaEventKey(TBA_KEY);
    } catch (Exception ignored) {
    }
    try {
      teamTournamentService.replaceTeamsForTournament(TOURNAMENT_ID, List.of());
    } catch (Exception ignored) {
    }
    try {
      tournamentService.deleteById(TOURNAMENT_ID);
    } catch (Exception ignored) {
    }
    cache.invalidateAll();
  }

  @Test
  void unauthenticatedReturns401() {
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class,
            () ->
                client
                    .toBlocking()
                    .exchange(HttpRequest.GET("/api/team-capability/" + TOURNAMENT_ID)));
    assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
  }

  @Test
  void getByTournament_returnsEnrichedRowsWithEtag() {
    HttpRequest<?> req =
        HttpRequest.GET("/api/team-capability/" + TOURNAMENT_ID)
            .basicAuth(USER_MEMBER, "password");
    HttpResponse<List<TeamCapabilityResponse>> response =
        client.toBlocking().exchange(req, Argument.listOf(TeamCapabilityResponse.class));
    assertEquals(HttpStatus.OK, response.getStatus());

    List<TeamCapabilityResponse> body = response.body();
    assertNotNull(body);
    assertEquals(3, body.size());
    // Default sort: OPR desc.
    assertEquals(254, body.get(0).teamNumber());
    assertEquals(70.0, body.get(0).opr());
    assertEquals(72.0, body.get(0).epaTotal());
    assertFalse(body.get(0).oprStale());
    assertFalse(body.get(0).epaStale());
    assertFalse(body.get(0).withdrawn());

    String etag = response.getHeaders().get("ETag");
    assertNotNull(etag);
    assertTrue(etag.startsWith("W/\""), "expected weak ETag, got: " + etag);
  }

  @Test
  void invalidTournamentId_returns404() {
    HttpRequest<?> req =
        HttpRequest.GET("/api/team-capability/DOES_NOT_EXIST").basicAuth(USER_MEMBER, "password");
    HttpClientResponseException e =
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(req));
    assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
  }

  @Test
  void ifNoneMatchMatches_returns304() {
    HttpRequest<?> first =
        HttpRequest.GET("/api/team-capability/" + TOURNAMENT_ID)
            .basicAuth(USER_MEMBER, "password");
    HttpResponse<?> firstResponse = client.toBlocking().exchange(first);
    String etag = firstResponse.getHeaders().get("ETag");
    assertNotNull(etag);

    HttpRequest<?> conditional =
        HttpRequest.GET("/api/team-capability/" + TOURNAMENT_ID)
            .basicAuth(USER_MEMBER, "password")
            .header("If-None-Match", etag);
    try {
      HttpResponse<?> second = client.toBlocking().exchange(conditional);
      assertEquals(HttpStatus.NOT_MODIFIED, second.getStatus());
    } catch (HttpClientResponseException ex) {
      // Some Micronaut clients surface 304 as an exception; either is acceptable.
      assertEquals(HttpStatus.NOT_MODIFIED, ex.getStatus());
    }
  }

  @Test
  void cacheHit_returnsSameEtagOnConsecutiveCalls() {
    HttpRequest<?> req =
        HttpRequest.GET("/api/team-capability/" + TOURNAMENT_ID)
            .basicAuth(USER_MEMBER, "password");
    HttpResponse<?> first = client.toBlocking().exchange(req);
    HttpResponse<?> second = client.toBlocking().exchange(req);
    assertEquals(first.getHeaders().get("ETag"), second.getHeaders().get("ETag"));
  }
}
