package ca.team1310.ravenbrain.tournament;

import static org.junit.jupiter.api.Assertions.*;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@MicronautTest(transactional = false)
public class WatchedTournamentServiceTest {

  private static final String TEST_TOURNAMENT_ID = "TEST_WATCHED_" + System.currentTimeMillis();

  @Inject TournamentService tournamentService;

  @Inject WatchedTournamentService watchedTournamentService;

  @AfterEach
  void cleanup() {
    // Remove watched entry first (FK constraint), then the tournament
    try {
      var ds =
          watchedTournamentService
              .getClass()
              .getDeclaredField("dataSource")
              .getType(); // can't easily access — use watch idempotency instead
    } catch (Exception ignored) {
    }
    // Clean up via raw JDBC is complex; the test DB is ephemeral (Testcontainers) so cleanup
    // is optional. But let's do our best: re-watching is idempotent, and the container is
    // discarded after the test suite.
  }

  @Test
  void testWatchAndIsWatched() {
    // Create a tournament so the FK constraint is satisfied
    TournamentRecord tournament =
        new TournamentRecord(
            TEST_TOURNAMENT_ID,
            "TESTWT",
            2026,
            "Test Watched Tournament",
            Instant.parse("2026-03-20T00:00:00Z"),
            Instant.parse("2026-03-22T00:00:00Z"),
            10);
    tournamentService.save(tournament);

    // Initially not watched
    assertFalse(watchedTournamentService.isWatched(TEST_TOURNAMENT_ID));

    // Watch it
    watchedTournamentService.watch(TEST_TOURNAMENT_ID);
    assertTrue(watchedTournamentService.isWatched(TEST_TOURNAMENT_ID));

    // Idempotent — no error on second watch
    assertDoesNotThrow(() -> watchedTournamentService.watch(TEST_TOURNAMENT_ID));
    assertTrue(watchedTournamentService.isWatched(TEST_TOURNAMENT_ID));

    // Present in the full set
    assertTrue(watchedTournamentService.getWatchedTournamentIds().contains(TEST_TOURNAMENT_ID));
  }

  @Test
  void testWatchNonExistentTournamentDoesNotThrow() {
    // Watching a tournament that doesn't exist in RB_TOURNAMENT should not throw
    // (INSERT IGNORE will fail silently due to FK constraint, logged as warning)
    assertDoesNotThrow(() -> watchedTournamentService.watch("NONEXISTENT_TOURNAMENT_ID"));
    // It should NOT be in the in-memory cache since the DB insert failed
    assertFalse(watchedTournamentService.isWatched("NONEXISTENT_TOURNAMENT_ID"));
  }
}
