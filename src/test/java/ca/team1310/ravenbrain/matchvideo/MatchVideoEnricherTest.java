package ca.team1310.ravenbrain.matchvideo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import ca.team1310.ravenbrain.schedule.ScheduleRecord;
import ca.team1310.ravenbrain.schedule.ScheduleService;
import ca.team1310.ravenbrain.tbaapi.service.TbaMatchVideoRecord;
import ca.team1310.ravenbrain.tbaapi.service.TbaMatchVideoRepo;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit test of {@link MatchVideoEnricher} with all dependencies mocked — exercises the merge
 * algorithm, alliance-composition join, canonicalize-dedup, collision tiebreaker, and staleness
 * flag without touching the DB.
 */
class MatchVideoEnricherTest {

  private static final String T_ID = "2026TEST";
  private static final String TBA_KEY = "2026test";

  private MatchVideoService matchVideoService;
  private ScheduleService scheduleService;
  private TournamentService tournamentService;
  private TbaMatchVideoRepo tbaMatchVideoRepo;
  private MatchVideoEnricher enricher;

  @BeforeEach
  void setUp() {
    matchVideoService = mock(MatchVideoService.class);
    scheduleService = mock(ScheduleService.class);
    tournamentService = mock(TournamentService.class);
    tbaMatchVideoRepo = mock(TbaMatchVideoRepo.class);
    when(matchVideoService.findAllByTournamentId(anyString())).thenReturn(List.of());
    when(scheduleService.findAllByTournamentIdOrderByMatch(anyString())).thenReturn(List.of());
    when(tournamentService.findById(anyString())).thenReturn(Optional.empty());
    when(tbaMatchVideoRepo.findByTbaEventKey(anyString())).thenReturn(List.of());
    enricher =
        new MatchVideoEnricher(
            matchVideoService, scheduleService, tournamentService, tbaMatchVideoRepo);
  }

  private static TournamentRecord tournamentWith(String tbaKey) {
    return new TournamentRecord(
        T_ID,
        "TEST",
        2026,
        "Test",
        Instant.parse("2026-03-20T00:00:00Z"),
        Instant.parse("2026-03-22T00:00:00Z"),
        3,
        null,
        tbaKey);
  }

  private static ScheduleRecord schedule(
      TournamentLevel level, int match, int[] red, int[] blue) {
    return new ScheduleRecord(
        1L, T_ID, level, match, null,
        red[0], red[1], red[2], red.length > 3 ? red[3] : 0,
        blue[0], blue[1], blue[2], blue.length > 3 ? blue[3] : 0,
        null, null, null, null, 0);
  }

  private static TbaMatchVideoRecord tbaRow(
      String matchKey, String compLevel, int matchNum, String red, String blue, String json, int status) {
    return new TbaMatchVideoRecord(
        matchKey, TBA_KEY, compLevel, matchNum, red, blue, json, Instant.now(), status);
  }

  // ---------- Happy paths ----------

  @Test
  void singleTbaEntry_matchedByAllianceComposition() {
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(scheduleService.findAllByTournamentIdOrderByMatch(T_ID))
        .thenReturn(
            List.of(
                schedule(
                    TournamentLevel.Qualification,
                    14,
                    new int[] {1310, 2056, 4917},
                    new int[] {1114, 2713, 5406})));
    when(tbaMatchVideoRepo.findByTbaEventKey(TBA_KEY))
        .thenReturn(
            List.of(
                tbaRow(
                    "2026test_qm14",
                    "qm",
                    14,
                    "1310,2056,4917",
                    "1114,2713,5406",
                    "[\"https://www.youtube.com/watch?v=abc\"]",
                    200)));

    List<MatchVideoResponse> out = enricher.enrich(T_ID);

    assertEquals(1, out.size());
    MatchVideoResponse r = out.get(0);
    assertNull(r.id());
    assertEquals("Qualification", r.matchLevel());
    assertEquals(14, r.matchNumber());
    assertEquals("TBA", r.label());
    assertEquals("https://www.youtube.com/watch?v=abc", r.videoUrl());
    assertEquals("tba", r.source());
    assertFalse(r.stale());
  }

  @Test
  void adminEntriesListedBeforeTba() {
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(matchVideoService.findAllByTournamentId(T_ID))
        .thenReturn(
            List.of(
                new MatchVideoRecord(
                    10L, T_ID, "Qualification", 14, "Driver Station", "https://example.com/ds"),
                new MatchVideoRecord(
                    11L, T_ID, "Qualification", 14, "Full Field", "https://example.com/ff")));
    when(scheduleService.findAllByTournamentIdOrderByMatch(T_ID))
        .thenReturn(
            List.of(
                schedule(
                    TournamentLevel.Qualification,
                    14,
                    new int[] {1310, 2056, 4917},
                    new int[] {1114, 2713, 5406})));
    when(tbaMatchVideoRepo.findByTbaEventKey(TBA_KEY))
        .thenReturn(
            List.of(
                tbaRow(
                    "2026test_qm14",
                    "qm",
                    14,
                    "1310,2056,4917",
                    "1114,2713,5406",
                    "[\"https://www.youtube.com/watch?v=abc\"]",
                    200)));

    List<MatchVideoResponse> out = enricher.enrich(T_ID);

    assertEquals(3, out.size());
    assertEquals("manual", out.get(0).source());
    assertEquals("Driver Station", out.get(0).label());
    assertEquals("manual", out.get(1).source());
    assertEquals("Full Field", out.get(1).label());
    assertEquals("tba", out.get(2).source());
  }

  @Test
  void canonicalizationCollapsesAdminAndTbaDuplicates_adminWins() {
    // Admin URL differs from TBA in scheme case and host case — canonicalize() normalizes both to
    // the same string, so the entry collapses to one with the admin label kept.
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(matchVideoService.findAllByTournamentId(T_ID))
        .thenReturn(
            List.of(
                new MatchVideoRecord(
                    10L,
                    T_ID,
                    "Qualification",
                    14,
                    "Full Field",
                    "HTTPS://WWW.YOUTUBE.COM/watch?v=abc")));
    when(scheduleService.findAllByTournamentIdOrderByMatch(T_ID))
        .thenReturn(
            List.of(
                schedule(
                    TournamentLevel.Qualification,
                    14,
                    new int[] {1310, 2056, 4917},
                    new int[] {1114, 2713, 5406})));
    when(tbaMatchVideoRepo.findByTbaEventKey(TBA_KEY))
        .thenReturn(
            List.of(
                tbaRow(
                    "2026test_qm14",
                    "qm",
                    14,
                    "1310,2056,4917",
                    "1114,2713,5406",
                    "[\"https://www.youtube.com/watch?v=abc\"]",
                    200)));

    List<MatchVideoResponse> out = enricher.enrich(T_ID);

    assertEquals(1, out.size());
    assertEquals("manual", out.get(0).source());
    assertEquals("Full Field", out.get(0).label()); // admin label preserved
  }

  @Test
  void playoffMatchJoinedByAllianceComposition() {
    // RB_SCHEDULE has a Playoff with matchNum 1; TBA has qf1m2 — match numbers differ, but alliance
    // composition is the join key, not the match number.
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(scheduleService.findAllByTournamentIdOrderByMatch(T_ID))
        .thenReturn(
            List.of(
                schedule(
                    TournamentLevel.Playoff,
                    1,
                    new int[] {1310, 2056, 4917},
                    new int[] {254, 1678, 118})));
    when(tbaMatchVideoRepo.findByTbaEventKey(TBA_KEY))
        .thenReturn(
            List.of(
                tbaRow(
                    "2026test_qf1m2",
                    "qf",
                    2,
                    "1310,2056,4917",
                    "118,254,1678",
                    "[\"https://www.youtube.com/watch?v=playoff\"]",
                    200)));

    List<MatchVideoResponse> out = enricher.enrich(T_ID);
    assertEquals(1, out.size());
    assertEquals("Playoff", out.get(0).matchLevel());
    assertEquals(1, out.get(0).matchNumber());
  }

  @Test
  void midEventReschedulingJoinsByAlliancesNotMatchNumber() {
    // RB_SCHEDULE says this is qm14; TBA says qm15 with the same alliances → joins correctly.
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(scheduleService.findAllByTournamentIdOrderByMatch(T_ID))
        .thenReturn(
            List.of(
                schedule(
                    TournamentLevel.Qualification,
                    14,
                    new int[] {1310, 2056, 4917},
                    new int[] {1114, 2713, 5406})));
    when(tbaMatchVideoRepo.findByTbaEventKey(TBA_KEY))
        .thenReturn(
            List.of(
                tbaRow(
                    "2026test_qm15",
                    "qm",
                    15,
                    "1310,2056,4917",
                    "1114,2713,5406",
                    "[\"https://www.youtube.com/watch?v=resched\"]",
                    200)));

    List<MatchVideoResponse> out = enricher.enrich(T_ID);
    assertEquals(1, out.size());
    assertEquals(14, out.get(0).matchNumber());
  }

  @Test
  void collisionTiebreakerChoosesClosestMatchNumber() {
    // Two different TBA matches have identical alliance composition (rare: surrogate / replay).
    // Tie-breaker picks the one whose match number is closest to the schedule row's.
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(scheduleService.findAllByTournamentIdOrderByMatch(T_ID))
        .thenReturn(
            List.of(
                schedule(
                    TournamentLevel.Qualification,
                    14,
                    new int[] {1310, 2056, 4917},
                    new int[] {1114, 2713, 5406})));
    when(tbaMatchVideoRepo.findByTbaEventKey(TBA_KEY))
        .thenReturn(
            List.of(
                tbaRow(
                    "2026test_qm8",
                    "qm",
                    8,
                    "1310,2056,4917",
                    "1114,2713,5406",
                    "[\"https://www.youtube.com/watch?v=far\"]",
                    200),
                tbaRow(
                    "2026test_qm15",
                    "qm",
                    15,
                    "1310,2056,4917",
                    "1114,2713,5406",
                    "[\"https://www.youtube.com/watch?v=close\"]",
                    200)));

    List<MatchVideoResponse> out = enricher.enrich(T_ID);
    assertEquals(1, out.size());
    assertEquals("https://www.youtube.com/watch?v=close", out.get(0).videoUrl());
  }

  // ---------- Edge cases ----------

  @Test
  void tbaEventKeySetButNoRowsYet_returnsAdminOnly() {
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(matchVideoService.findAllByTournamentId(T_ID))
        .thenReturn(
            List.of(
                new MatchVideoRecord(
                    10L, T_ID, "Qualification", 14, "Driver Station", "https://example.com/ds")));
    when(tbaMatchVideoRepo.findByTbaEventKey(TBA_KEY)).thenReturn(List.of());
    when(scheduleService.findAllByTournamentIdOrderByMatch(T_ID))
        .thenReturn(
            List.of(
                schedule(
                    TournamentLevel.Qualification,
                    14,
                    new int[] {1310, 2056, 4917},
                    new int[] {1114, 2713, 5406})));

    List<MatchVideoResponse> out = enricher.enrich(T_ID);
    assertEquals(1, out.size());
    assertEquals("manual", out.get(0).source());
  }

  @Test
  void tbaRowWith404StatusMarksEntryStale() {
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(scheduleService.findAllByTournamentIdOrderByMatch(T_ID))
        .thenReturn(
            List.of(
                schedule(
                    TournamentLevel.Qualification,
                    14,
                    new int[] {1310, 2056, 4917},
                    new int[] {1114, 2713, 5406})));
    when(tbaMatchVideoRepo.findByTbaEventKey(TBA_KEY))
        .thenReturn(
            List.of(
                tbaRow(
                    "2026test_qm14",
                    "qm",
                    14,
                    "1310,2056,4917",
                    "1114,2713,5406",
                    "[\"https://www.youtube.com/watch?v=prior\"]",
                    404)));

    List<MatchVideoResponse> out = enricher.enrich(T_ID);
    assertEquals(1, out.size());
    assertTrue(out.get(0).stale());
  }

  @Test
  void allZeroAllianceSkipsTbaLookup() {
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(scheduleService.findAllByTournamentIdOrderByMatch(T_ID))
        .thenReturn(
            List.of(
                schedule(
                    TournamentLevel.Qualification, 14, new int[] {0, 0, 0}, new int[] {0, 0, 0})));
    when(tbaMatchVideoRepo.findByTbaEventKey(TBA_KEY))
        .thenReturn(
            List.of(
                tbaRow(
                    "2026test_qm14",
                    "qm",
                    14,
                    "1310,2056,4917",
                    "1114,2713,5406",
                    "[\"https://www.youtube.com/watch?v=abc\"]",
                    200)));

    List<MatchVideoResponse> out = enricher.enrich(T_ID);
    assertTrue(out.isEmpty());
  }

  @Test
  void practiceMatchRowsGetNoTbaVideos() {
    // TBA never returns Practice matches, so a Practice schedule row produces no TBA entries —
    // admin can still add manual entries for Practice if wanted.
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(scheduleService.findAllByTournamentIdOrderByMatch(T_ID))
        .thenReturn(
            List.of(
                schedule(
                    TournamentLevel.Practice,
                    1,
                    new int[] {1310, 2056, 4917},
                    new int[] {1114, 2713, 5406})));
    when(tbaMatchVideoRepo.findByTbaEventKey(TBA_KEY)).thenReturn(List.of());

    List<MatchVideoResponse> out = enricher.enrich(T_ID);
    assertTrue(out.isEmpty());
  }

  @Test
  void adminOnlyMatchNotInScheduleStillSurfaces() {
    // Backward compat: admin row for a (level, match) not in RB_SCHEDULE still appears in GET.
    when(tournamentService.findById(T_ID)).thenReturn(Optional.empty());
    when(matchVideoService.findAllByTournamentId(T_ID))
        .thenReturn(
            List.of(
                new MatchVideoRecord(
                    10L, T_ID, "Qualification", 99, "Notes", "https://example.com/notes")));

    List<MatchVideoResponse> out = enricher.enrich(T_ID);
    assertEquals(1, out.size());
    assertEquals("manual", out.get(0).source());
    assertEquals(99, out.get(0).matchNumber());
  }

  @Test
  void enrichByMatchFiltersOutput() {
    when(tournamentService.findById(T_ID)).thenReturn(Optional.of(tournamentWith(TBA_KEY)));
    when(scheduleService.findAllByTournamentIdOrderByMatch(T_ID))
        .thenReturn(
            List.of(
                schedule(
                    TournamentLevel.Qualification,
                    1,
                    new int[] {1310, 2056, 4917},
                    new int[] {1114, 2713, 5406}),
                schedule(
                    TournamentLevel.Qualification,
                    2,
                    new int[] {111, 222, 333},
                    new int[] {444, 555, 666})));
    when(tbaMatchVideoRepo.findByTbaEventKey(TBA_KEY))
        .thenReturn(
            List.of(
                tbaRow(
                    "2026test_qm1",
                    "qm",
                    1,
                    "1310,2056,4917",
                    "1114,2713,5406",
                    "[\"https://www.youtube.com/watch?v=one\"]",
                    200),
                tbaRow(
                    "2026test_qm2",
                    "qm",
                    2,
                    "111,222,333",
                    "444,555,666",
                    "[\"https://www.youtube.com/watch?v=two\"]",
                    200)));

    List<MatchVideoResponse> out = enricher.enrich(T_ID, "Qualification", 2);
    assertEquals(1, out.size());
    assertEquals("https://www.youtube.com/watch?v=two", out.get(0).videoUrl());
  }
}
