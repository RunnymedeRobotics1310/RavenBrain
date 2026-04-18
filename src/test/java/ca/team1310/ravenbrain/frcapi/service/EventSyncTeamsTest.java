package ca.team1310.ravenbrain.frcapi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ca.team1310.ravenbrain.frcapi.model.TeamListing;
import ca.team1310.ravenbrain.frcapi.model.TeamListingResponse;
import ca.team1310.ravenbrain.tournament.TeamTournamentService;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

@MicronautTest(transactional = false)
public class EventSyncTeamsTest {

  @Inject EventSyncService eventSyncService;
  @Inject FrcClientService frcClientService;
  @Inject TeamTournamentService teamTournamentService;
  @Inject TournamentService tournamentService;

  @MockBean(FrcClientService.class)
  FrcClientService mockFrcClientService() {
    return mock(FrcClientService.class);
  }

  @Test
  void testLoadTeamsForTournament() {
    // Set up a tournament in the database
    Instant start = Instant.parse("2026-03-20T00:00:00Z");
    Instant end = Instant.parse("2026-03-22T00:00:00Z");
    TournamentRecord tournament =
        new TournamentRecord(
            "2026ONHAM", "ONHAM", 2026, "Hamilton District", start, end, 3, null, null);
    tournamentService.save(tournament);

    // Mock the FRC API teams response
    List<TeamListing> teams =
        List.of(
            new TeamListing(
                1310, "Runnymede Robotics Full", "Runnymede Robotics", "Toronto", "Ontario",
                "Canada", "http://team1310.ca", 2004, "Robot", "ONT", null, "Runnymede CI"),
            new TeamListing(
                2056, "OP Robotics Full", "OP Robotics", "Toronto", "Ontario", "Canada",
                "http://team2056.ca", 2007, "Robot2", "ONT", null, "Some School"),
            new TeamListing(
                4917, "Scorpions Full", "Scorpions", "Hamilton", "Ontario", "Canada",
                null, 2014, null, "ONT", null, null));
    TeamListingResponse teamsResponse = new TeamListingResponse(teams, 3, 3, 1, 1);
    when(frcClientService.getTeamListingsForEvent(2026, "ONHAM"))
        .thenReturn(new ServiceResponse<>(1L, teamsResponse));

    // Execute
    eventSyncService.loadTeamsForTournament(tournament);

    // Verify the teams were saved
    List<Integer> savedTeams = teamTournamentService.findTeamNumbersForTournament("2026ONHAM");
    assertEquals(3, savedTeams.size());
    assertTrue(savedTeams.contains(1310));
    assertTrue(savedTeams.contains(2056));
    assertTrue(savedTeams.contains(4917));

    // Verify response was marked processed
    verify(frcClientService).markProcessed(1L);

    // Clean up
    teamTournamentService.replaceTeamsForTournament("2026ONHAM", List.of());
    tournamentService.deleteById("2026ONHAM");
  }

  @Test
  void testLoadTeamsForTournamentReplacesExistingTeams() {
    // Set up a tournament
    Instant start = Instant.parse("2026-03-20T00:00:00Z");
    Instant end = Instant.parse("2026-03-22T00:00:00Z");
    TournamentRecord tournament =
        new TournamentRecord(
            "2026ONHAM2", "ONHAM2", 2026, "Hamilton District 2", start, end, 3, null, null);
    tournamentService.save(tournament);

    // Pre-populate with old team data
    teamTournamentService.replaceTeamsForTournament("2026ONHAM2", List.of(9999, 8888));
    assertEquals(2, teamTournamentService.findTeamNumbersForTournament("2026ONHAM2").size());

    // Mock a new teams response with different teams
    List<TeamListing> newTeams =
        List.of(
            new TeamListing(
                1310, null, "Runnymede", null, null, null, null, 2004, null, null, null, null),
            new TeamListing(
                1114, null, "Simbotics", null, null, null, null, 2003, null, null, null, null));
    TeamListingResponse teamsResponse = new TeamListingResponse(newTeams, 2, 2, 1, 1);
    when(frcClientService.getTeamListingsForEvent(2026, "ONHAM2"))
        .thenReturn(new ServiceResponse<>(2L, teamsResponse));

    // Execute
    eventSyncService.loadTeamsForTournament(tournament);

    // Verify old teams were replaced
    List<Integer> savedTeams = teamTournamentService.findTeamNumbersForTournament("2026ONHAM2");
    assertEquals(2, savedTeams.size());
    assertTrue(savedTeams.contains(1310));
    assertTrue(savedTeams.contains(1114));
    assertFalse(savedTeams.contains(9999));
    assertFalse(savedTeams.contains(8888));

    // Clean up
    teamTournamentService.replaceTeamsForTournament("2026ONHAM2", List.of());
    tournamentService.deleteById("2026ONHAM2");
  }

  @Test
  void testLoadTeamsForTournamentNoDataFromApi() {
    Instant start = Instant.parse("2026-03-20T00:00:00Z");
    Instant end = Instant.parse("2026-03-22T00:00:00Z");
    TournamentRecord tournament =
        new TournamentRecord(
            "2026ONHAM3", "ONHAM3", 2026, "Hamilton District 3", start, end, 3, null, null);
    tournamentService.save(tournament);

    // Pre-populate existing data
    teamTournamentService.replaceTeamsForTournament("2026ONHAM3", List.of(1310));

    // Mock null response (no work to do)
    when(frcClientService.getTeamListingsForEvent(2026, "ONHAM3")).thenReturn(null);

    // Execute
    eventSyncService.loadTeamsForTournament(tournament);

    // Verify existing data is preserved when API returns null
    List<Integer> savedTeams = teamTournamentService.findTeamNumbersForTournament("2026ONHAM3");
    assertEquals(1, savedTeams.size());
    assertTrue(savedTeams.contains(1310));

    // Clean up
    teamTournamentService.replaceTeamsForTournament("2026ONHAM3", List.of());
    tournamentService.deleteById("2026ONHAM3");
  }

  @Test
  void testLoadTeamsForTournamentEmptyTeamsList() {
    Instant start = Instant.parse("2026-03-20T00:00:00Z");
    Instant end = Instant.parse("2026-03-22T00:00:00Z");
    TournamentRecord tournament =
        new TournamentRecord(
            "2026ONHAM4", "ONHAM4", 2026, "Hamilton District 4", start, end, 3, null, null);
    tournamentService.save(tournament);

    // Mock response with empty teams list
    TeamListingResponse teamsResponse = new TeamListingResponse(List.of(), 0, 0, 1, 1);
    when(frcClientService.getTeamListingsForEvent(2026, "ONHAM4"))
        .thenReturn(new ServiceResponse<>(3L, teamsResponse));

    // Execute
    eventSyncService.loadTeamsForTournament(tournament);

    // Verify no teams saved
    List<Integer> savedTeams = teamTournamentService.findTeamNumbersForTournament("2026ONHAM4");
    assertTrue(savedTeams.isEmpty());

    verify(frcClientService).markProcessed(3L);

    // Clean up
    tournamentService.deleteById("2026ONHAM4");
  }
}
