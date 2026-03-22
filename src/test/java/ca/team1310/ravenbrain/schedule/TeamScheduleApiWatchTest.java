package ca.team1310.ravenbrain.schedule;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import ca.team1310.ravenbrain.tournament.WatchedTournamentService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.Test;

@MicronautTest(transactional = false)
public class TeamScheduleApiWatchTest {

  @Inject
  @Client("/")
  HttpClient client;

  @Inject TournamentService tournamentService;

  @Inject WatchedTournamentService watchedTournamentService;

  @Test
  void viewingScheduleRegistersWatch() {
    String tid = "TEST_SCHED_WATCH_" + System.currentTimeMillis();

    // Create a tournament
    TournamentRecord tournament =
        new TournamentRecord(
            tid,
            "TESTSW",
            2026,
            "Test Schedule Watch Tournament",
            Instant.parse("2026-03-20T00:00:00Z"),
            Instant.parse("2026-03-22T00:00:00Z"),
            10);
    tournamentService.save(tournament);

    // Not watched initially
    assertFalse(watchedTournamentService.isWatched(tid));

    // GET the team schedule (anonymous access) — should return 200
    HttpRequest<?> request = HttpRequest.GET("/api/schedule/team-schedule/" + tid);
    HttpResponse<String> response =
        client.toBlocking().exchange(request, String.class);
    assertEquals(200, response.code());

    // Tournament should now be watched
    assertTrue(
        watchedTournamentService.isWatched(tid),
        "Tournament " + tid + " should be watched after viewing schedule");
  }

  @Test
  void viewingNonExistentTournamentReturns200() {
    // Request a tournament that doesn't exist in RB_TOURNAMENT
    HttpRequest<?> request =
        HttpRequest.GET("/api/schedule/team-schedule/NONEXISTENT_" + System.currentTimeMillis());
    HttpResponse<String> response =
        client.toBlocking().exchange(request, String.class);

    // Should return 200 (graceful handling), not 404/500
    assertEquals(200, response.code());
  }
}
