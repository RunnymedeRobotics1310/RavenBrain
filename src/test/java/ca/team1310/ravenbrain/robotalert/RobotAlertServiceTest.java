package ca.team1310.ravenbrain.robotalert;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.connect.TestUserHelper;
import ca.team1310.ravenbrain.connect.User;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
public class RobotAlertServiceTest {

  private static final String TOURNAMENT_A = "TEST_ALERT_A";
  private static final String TOURNAMENT_B = "TEST_ALERT_B";

  @Inject RobotAlertService robotAlertService;
  @Inject TestUserHelper testUserHelper;

  private long testUserId;

  @BeforeEach
  void setup() {
    User user =
        testUserHelper.createTestUser("alert-service-testuser", "password", "ROLE_DATASCOUT");
    testUserId = user.id();
  }

  @AfterEach
  void tearDown() {
    robotAlertService
        .findAll()
        .forEach(
            alert -> {
              if (alert.tournamentId().startsWith("TEST_ALERT")) {
                robotAlertService.delete(alert);
              }
            });
    testUserHelper.deleteTestUsers();
  }

  @Test
  void testSaveAndFindByTournament() {
    RobotAlert alert =
        new RobotAlert(null, TOURNAMENT_A, 1310, testUserId, Instant.now(), "Drive train issue");
    robotAlertService.save(alert);

    List<RobotAlert> results =
        robotAlertService.findAllByTournamentIdOrderByTeamNumberAscCreatedAtDesc(TOURNAMENT_A);

    assertEquals(1, results.size());
    assertEquals(1310, results.getFirst().teamNumber());
    assertEquals("Drive train issue", results.getFirst().alert());
    assertNotNull(results.getFirst().id());
  }

  @Test
  void testFindByTournamentAndTeamNumber() {
    Instant now = Instant.now();
    robotAlertService.save(
        new RobotAlert(null, TOURNAMENT_A, 1310, testUserId, now, "Alert for 1310"));
    robotAlertService.save(
        new RobotAlert(
            null, TOURNAMENT_A, 2056, testUserId, now.plusMillis(1), "Alert for 2056"));

    List<RobotAlert> team1310 =
        robotAlertService.findAllByTournamentIdAndTeamNumberOrderByCreatedAtDesc(
            TOURNAMENT_A, 1310);
    List<RobotAlert> team2056 =
        robotAlertService.findAllByTournamentIdAndTeamNumberOrderByCreatedAtDesc(
            TOURNAMENT_A, 2056);

    assertEquals(1, team1310.size());
    assertEquals("Alert for 1310", team1310.getFirst().alert());
    assertEquals(1, team2056.size());
    assertEquals("Alert for 2056", team2056.getFirst().alert());
  }

  @Test
  void testFindByTournamentReturnsEmptyForUnknownTournament() {
    List<RobotAlert> results =
        robotAlertService.findAllByTournamentIdOrderByTeamNumberAscCreatedAtDesc(
            "NONEXISTENT_TOURN");

    assertTrue(results.isEmpty());
  }

  @Test
  void testOrderingByTeamNumberAscAndCreatedAtDesc() {
    Instant base = Instant.parse("2025-06-01T12:00:00Z");
    robotAlertService.save(
        new RobotAlert(null, TOURNAMENT_A, 2056, testUserId, base, "2056 older"));
    robotAlertService.save(
        new RobotAlert(
            null, TOURNAMENT_A, 2056, testUserId, base.plusSeconds(60), "2056 newer"));
    robotAlertService.save(
        new RobotAlert(
            null, TOURNAMENT_A, 1310, testUserId, base.plusSeconds(1), "1310 older"));
    robotAlertService.save(
        new RobotAlert(
            null, TOURNAMENT_A, 1310, testUserId, base.plusSeconds(61), "1310 newer"));

    List<RobotAlert> results =
        robotAlertService.findAllByTournamentIdOrderByTeamNumberAscCreatedAtDesc(TOURNAMENT_A);

    assertEquals(4, results.size());
    // Team 1310 first (ascending), newest first within team (descending)
    assertEquals(1310, results.get(0).teamNumber());
    assertEquals("1310 newer", results.get(0).alert());
    assertEquals(1310, results.get(1).teamNumber());
    assertEquals("1310 older", results.get(1).alert());
    // Team 2056 next
    assertEquals(2056, results.get(2).teamNumber());
    assertEquals("2056 newer", results.get(2).alert());
    assertEquals(2056, results.get(3).teamNumber());
    assertEquals("2056 older", results.get(3).alert());
  }

  @Test
  void testTournamentIsolation() {
    Instant now = Instant.now();
    robotAlertService.save(
        new RobotAlert(null, TOURNAMENT_A, 1310, testUserId, now, "Alert in A"));
    robotAlertService.save(
        new RobotAlert(
            null, TOURNAMENT_B, 1310, testUserId, now.plusMillis(1), "Alert in B"));

    List<RobotAlert> tournamentA =
        robotAlertService.findAllByTournamentIdOrderByTeamNumberAscCreatedAtDesc(TOURNAMENT_A);
    List<RobotAlert> tournamentB =
        robotAlertService.findAllByTournamentIdOrderByTeamNumberAscCreatedAtDesc(TOURNAMENT_B);

    assertEquals(1, tournamentA.size());
    assertEquals("Alert in A", tournamentA.getFirst().alert());
    assertEquals(1, tournamentB.size());
    assertEquals("Alert in B", tournamentB.getFirst().alert());
  }

  @Test
  void testDuplicateAlertThrows() {
    Instant now = Instant.parse("2025-06-01T12:00:00Z");
    robotAlertService.save(
        new RobotAlert(null, TOURNAMENT_A, 1310, testUserId, now, "Original alert"));

    // Same created_at + user_id violates unique constraint
    assertThrows(
        Exception.class,
        () ->
            robotAlertService.save(
                new RobotAlert(null, TOURNAMENT_A, 1310, testUserId, now, "Duplicate alert")));
  }
}