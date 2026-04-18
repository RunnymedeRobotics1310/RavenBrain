package ca.team1310.ravenbrain.telemetry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import ca.team1310.ravenbrain.tournament.WatchedTournamentService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatchIdentityEnricherTest {

  private TournamentService tournaments;
  private WatchedTournamentService watched;
  private MatchIdentityEnricher enricher;

  private static final Instant SESSION_START = Instant.parse("2026-03-05T15:00:00Z");

  @BeforeEach
  void setUp() {
    tournaments = mock(TournamentService.class);
    watched = mock(WatchedTournamentService.class);
    when(tournaments.findByCodeAndSeason(anyString(), anyInt())).thenReturn(Optional.empty());
    when(tournaments.findCoveringInstant(any())).thenReturn(List.of());
    enricher = new MatchIdentityEnricher(tournaments, watched);
  }

  private static TelemetryApi.TelemetryEntryRequest nt(Instant ts, String key, String value) {
    return new TelemetryApi.TelemetryEntryRequest(ts, "nt_update", key, "string", value, null);
  }

  @Test
  void extractsMatchLabelFromSingleEntry() {
    List<TelemetryApi.TelemetryEntryRequest> entries =
        List.of(nt(SESSION_START.plusSeconds(1), "/FMSInfo/MatchNumber", "Q14"));

    MatchIdentityEnricher.Result r = enricher.enrich(entries, SESSION_START);

    assertEquals("Q14", r.parsedLabel().rawLabel());
    assertEquals(TournamentLevel.Qualification, r.parsedLabel().level());
    assertEquals(14, r.parsedLabel().number());
    assertNull(r.tournamentId());
  }

  @Test
  void resolvesTournamentByFmsEventName() {
    TournamentRecord t =
        new TournamentRecord(
            "2026ONWAT",
            "ONWAT",
            2026,
            "Waterloo",
            Instant.parse("2026-03-01T00:00:00Z"),
            Instant.parse("2026-03-10T00:00:00Z"),
            10,
            null,
            null);
    when(tournaments.findByCodeAndSeason("ONWAT", 2026)).thenReturn(Optional.of(t));

    List<TelemetryApi.TelemetryEntryRequest> entries =
        List.of(
            nt(SESSION_START.plusSeconds(1), "/FMSInfo/MatchNumber", "Q14"),
            nt(SESSION_START.plusSeconds(2), "/FMSInfo/EventName", "ONWAT"));

    MatchIdentityEnricher.Result r = enricher.enrich(entries, SESSION_START);

    assertEquals("2026ONWAT", r.tournamentId());
    assertEquals("ONWAT", r.fmsEventName());
  }

  @Test
  void fallsBackToWatchedTournamentCoveringSessionStart() {
    TournamentRecord t =
        new TournamentRecord(
            "2026ONWAT",
            "ONWAT",
            2026,
            "Waterloo",
            Instant.parse("2026-03-01T00:00:00Z"),
            Instant.parse("2026-03-10T00:00:00Z"),
            10,
            null,
            null);
    when(tournaments.findCoveringInstant(SESSION_START)).thenReturn(List.of(t));
    when(watched.isWatched("2026ONWAT")).thenReturn(true);

    List<TelemetryApi.TelemetryEntryRequest> entries =
        List.of(nt(SESSION_START.plusSeconds(1), "/FMSInfo/MatchNumber", "Q14"));

    MatchIdentityEnricher.Result r = enricher.enrich(entries, SESSION_START);
    assertEquals("2026ONWAT", r.tournamentId());
  }

  @Test
  void ambiguousWatchedTournamentsReturnsNull() {
    TournamentRecord a =
        new TournamentRecord(
            "2026ONWAT", "ONWAT", 2026, "A", SESSION_START.minusSeconds(60),
            SESSION_START.plusSeconds(60), 10, null, null);
    TournamentRecord b =
        new TournamentRecord(
            "2026ONTOR", "ONTOR", 2026, "B", SESSION_START.minusSeconds(60),
            SESSION_START.plusSeconds(60), 10, null, null);
    when(tournaments.findCoveringInstant(SESSION_START)).thenReturn(List.of(a, b));
    when(watched.isWatched(anyString())).thenReturn(true);

    List<TelemetryApi.TelemetryEntryRequest> entries =
        List.of(nt(SESSION_START.plusSeconds(1), "/FMSInfo/MatchNumber", "Q14"));

    MatchIdentityEnricher.Result r = enricher.enrich(entries, SESSION_START);
    assertNull(r.tournamentId());
  }

  @Test
  void emptyEntriesReturnsEmptyResult() {
    MatchIdentityEnricher.Result r = enricher.enrich(List.of(), SESSION_START);
    assertFalse(r.hasAny());
  }

  @Test
  void ignoresBlankMatchNumber() {
    List<TelemetryApi.TelemetryEntryRequest> entries =
        List.of(nt(SESSION_START.plusSeconds(1), "/FMSInfo/MatchNumber", ""));
    MatchIdentityEnricher.Result r = enricher.enrich(entries, SESSION_START);
    assertNull(r.parsedLabel());
  }

  @Test
  void picksEarliestMatchNumber() {
    List<TelemetryApi.TelemetryEntryRequest> entries =
        List.of(
            nt(SESSION_START.plusSeconds(5), "/FMSInfo/MatchNumber", "Q15"),
            nt(SESSION_START.plusSeconds(1), "/FMSInfo/MatchNumber", "Q14"));
    MatchIdentityEnricher.Result r = enricher.enrich(entries, SESSION_START);
    assertEquals("Q14", r.parsedLabel().rawLabel());
  }

  @Test
  void picksLatestEventName() {
    List<TelemetryApi.TelemetryEntryRequest> entries =
        List.of(
            nt(SESSION_START.plusSeconds(1), "/FMSInfo/EventName", "ONWAT"),
            nt(SESSION_START.plusSeconds(5), "/FMSInfo/EventName", "ONTOR"));
    MatchIdentityEnricher.Result r = enricher.enrich(entries, SESSION_START);
    assertEquals("ONTOR", r.fmsEventName());
  }

  @Test
  void unknownLabelPrefixStillProducesRawLabel() {
    List<TelemetryApi.TelemetryEntryRequest> entries =
        List.of(nt(SESSION_START.plusSeconds(1), "/FMSInfo/MatchNumber", "X99"));
    MatchIdentityEnricher.Result r = enricher.enrich(entries, SESSION_START);
    assertEquals("X99", r.parsedLabel().rawLabel());
    assertNull(r.parsedLabel().level());
    assertNull(r.parsedLabel().number());
  }
}
