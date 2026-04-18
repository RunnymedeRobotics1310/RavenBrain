package ca.team1310.ravenbrain.frcapi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ca.team1310.ravenbrain.frcapi.model.Event;
import ca.team1310.ravenbrain.frcapi.model.EventResponse;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the ownership contract introduced by the P0 TBA Data Foundation plan:
 *
 * <ul>
 *   <li>FRC sync no longer writes webcasts — {@code manual_webcasts} is admin-owned.
 *   <li>{@code tba_event_key} is auto-derived on insert as {@code year + code.toLowerCase()}.
 *   <li>Admin-edited {@code tba_event_key} values are preserved across subsequent FRC syncs.
 *   <li>Admin-added manual webcasts are preserved across subsequent FRC syncs.
 * </ul>
 */
@MicronautTest(transactional = false)
public class EventSyncTournamentsTest {

  @Inject EventSyncService eventSyncService;
  @Inject FrcClientService frcClientService;
  @Inject TournamentService tournamentService;

  @MockBean(FrcClientService.class)
  FrcClientService mockFrcClientService() {
    return mock(FrcClientService.class);
  }

  private Event sampleEvent(String code, String name) {
    return new Event(
        "4",
        3,
        code,
        null,
        name,
        "DistrictEvent",
        "ONT",
        "Test Venue",
        "Toronto",
        "Ontario",
        "Canada",
        LocalDateTime.parse("2026-03-20T00:00:00"),
        LocalDateTime.parse("2026-03-22T00:00:00"),
        "123 Test Ave",
        "http://example.com",
        "America/New_York",
        new String[] {},
        new String[] {"https://youtube.com/ignored-by-frc-writer"});
  }

  @AfterEach
  void tearDown() {
    // Best-effort cleanup; tests build their own fixtures.
    try {
      tournamentService.deleteById("2026TESTA");
    } catch (Exception ignored) {
    }
    try {
      tournamentService.deleteById("2026TESTB");
    } catch (Exception ignored) {
    }
  }

  @Test
  void saveEvents_insertsAutoDerivedTbaEventKey() {
    var resp = new ServiceResponse<>(100L, new EventResponse(List.of(sampleEvent("TESTA", "Test A"))));

    eventSyncService.saveEvents(2026, resp);

    var saved = tournamentService.findById("2026TESTA").orElseThrow();
    assertEquals("2026testa", saved.tbaEventKey(), "tba_event_key must be auto-derived on insert");
    assertNull(saved.manualWebcasts(), "FRC sync must not populate manual_webcasts on insert");
    verify(frcClientService).markProcessed(100L);
  }

  @Test
  void saveEvents_preservesAdminEditedTbaEventKeyOnUpdate() {
    // Seed tournament with an admin-corrected tba_event_key (e.g., divisional key).
    Instant start = Instant.parse("2026-03-20T00:00:00Z");
    Instant end = Instant.parse("2026-03-22T00:00:00Z");
    tournamentService.save(
        new TournamentRecord(
            "2026TESTA", "TESTA", 2026, "Test A", start, end, 3, null, "2026onsci"));

    var resp = new ServiceResponse<>(101L, new EventResponse(List.of(sampleEvent("TESTA", "Test A"))));

    eventSyncService.saveEvents(2026, resp);

    var saved = tournamentService.findById("2026TESTA").orElseThrow();
    assertEquals(
        "2026onsci",
        saved.tbaEventKey(),
        "admin-edited tba_event_key must not be overwritten by auto-derivation on FRC sync");
  }

  @Test
  void saveEvents_preservesManualWebcastsOnUpdate() {
    // Seed tournament with admin-added manual webcasts.
    Instant start = Instant.parse("2026-03-20T00:00:00Z");
    Instant end = Instant.parse("2026-03-22T00:00:00Z");
    String adminEntry = "[\"https://twitch.tv/team1310\"]";
    tournamentService.save(
        new TournamentRecord(
            "2026TESTB", "TESTB", 2026, "Test B", start, end, 3, adminEntry, "2026testb"));

    var resp = new ServiceResponse<>(102L, new EventResponse(List.of(sampleEvent("TESTB", "Test B"))));

    eventSyncService.saveEvents(2026, resp);

    var saved = tournamentService.findById("2026TESTB").orElseThrow();
    assertEquals(
        adminEntry,
        saved.manualWebcasts(),
        "admin-added manual_webcasts must not be overwritten by FRC sync");
  }

  @Test
  void saveEvents_autoDerivesWhenExistingTbaEventKeyIsNull() {
    // Seed a row created before V30 migration — tba_event_key is NULL.
    Instant start = Instant.parse("2026-03-20T00:00:00Z");
    Instant end = Instant.parse("2026-03-22T00:00:00Z");
    tournamentService.save(
        new TournamentRecord(
            "2026TESTA", "TESTA", 2026, "Test A", start, end, 3, null, null));

    var resp = new ServiceResponse<>(103L, new EventResponse(List.of(sampleEvent("TESTA", "Test A"))));

    eventSyncService.saveEvents(2026, resp);

    var saved = tournamentService.findById("2026TESTA").orElseThrow();
    assertEquals(
        "2026testa",
        saved.tbaEventKey(),
        "NULL tba_event_key on an existing row must be filled in by the next FRC sync");
  }
}
