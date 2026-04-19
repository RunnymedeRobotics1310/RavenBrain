package ca.team1310.ravenbrain.tbaapi.service;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.tbaapi.model.TbaMatch;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Canned TBA {@code /event/{key}/matches} payloads round-trip through Micronaut Serde into the
 * {@link TbaMatch} record, including the nested alliances object and the videos array. Mirrors
 * {@link TbaClientServiceTest}'s JSON-only approach — the end-to-end fetch path is exercised by
 * the sync service integration tests in Unit 4.
 */
@MicronautTest
public class TbaClientServiceMatchesTest {

  @Inject ObjectMapper objectMapper;

  @Test
  void parsesMixedLevelMatchList() throws Exception {
    String json =
        """
        [
          {
            "key": "2026onto_qm1",
            "comp_level": "qm",
            "set_number": 1,
            "match_number": 1,
            "alliances": {
              "red":  { "team_keys": ["frc1310", "frc2056", "frc4917"] },
              "blue": { "team_keys": ["frc1114", "frc2713", "frc5406"] }
            },
            "videos": [
              { "type": "youtube", "key": "abc123" },
              { "type": "tba",     "key": "ignored" }
            ]
          },
          {
            "key": "2026onto_qf1m2",
            "comp_level": "qf",
            "set_number": 1,
            "match_number": 2,
            "alliances": {
              "red":  { "team_keys": ["frc1310", "frc2056", "frc4917"] },
              "blue": { "team_keys": ["frc254",  "frc1678", "frc118"] }
            },
            "videos": []
          },
          {
            "key": "2026onto_f1m1",
            "comp_level": "f",
            "set_number": 1,
            "match_number": 1,
            "alliances": {
              "red":  { "team_keys": ["frc1310", "frc2056", "frc4917"] },
              "blue": { "team_keys": ["frc254",  "frc1678", "frc118"] }
            }
          }
        ]
        """;

    List<TbaMatch> matches = objectMapper.readValue(json, Argument.listOf(TbaMatch.class));

    assertEquals(3, matches.size());

    TbaMatch qm1 = matches.get(0);
    assertEquals("2026onto_qm1", qm1.key());
    assertEquals("qm", qm1.compLevel());
    assertEquals(1, qm1.matchNumber());
    assertNotNull(qm1.alliances());
    assertNotNull(qm1.alliances().red());
    assertEquals(List.of("frc1310", "frc2056", "frc4917"), qm1.alliances().red().teamKeys());
    assertEquals(List.of("frc1114", "frc2713", "frc5406"), qm1.alliances().blue().teamKeys());
    assertNotNull(qm1.videos());
    assertEquals(2, qm1.videos().size());
    assertEquals("youtube", qm1.videos().get(0).type());
    assertEquals("abc123", qm1.videos().get(0).key());
    assertEquals("tba", qm1.videos().get(1).type());

    TbaMatch qf = matches.get(1);
    assertEquals("qf", qf.compLevel());
    assertNotNull(qf.videos());
    assertTrue(qf.videos().isEmpty());

    TbaMatch finalMatch = matches.get(2);
    assertEquals("f", finalMatch.compLevel());
    assertNull(finalMatch.videos()); // field omitted entirely
  }

  @Test
  void parsesFourTeamAlliance() throws Exception {
    // Older-season alliances had 4 team members. The model must tolerate any size.
    String json =
        """
        [
          {
            "key": "2024abc_qm1",
            "comp_level": "qm",
            "set_number": 1,
            "match_number": 1,
            "alliances": {
              "red":  { "team_keys": ["frc1310", "frc2056", "frc4917", "frc9999"] },
              "blue": { "team_keys": ["frc1114", "frc2713", "frc5406", "frc8888"] }
            },
            "videos": []
          }
        ]
        """;

    List<TbaMatch> matches = objectMapper.readValue(json, Argument.listOf(TbaMatch.class));

    assertEquals(1, matches.size());
    assertEquals(4, matches.get(0).alliances().red().teamKeys().size());
  }

  @Test
  void ignoresUnknownFields() throws Exception {
    // TBA adds fields (score_breakdown, time, actual_time, post_result_time, ...) — they must not
    // break deserialization.
    String json =
        """
        [
          {
            "key": "2026onto_qm1",
            "comp_level": "qm",
            "set_number": 1,
            "match_number": 1,
            "score_breakdown": { "red": { "totalPoints": 120 } },
            "time": 1710960000,
            "actual_time": 1710960042,
            "post_result_time": 1710960180,
            "winning_alliance": "red",
            "alliances": {
              "red":  { "team_keys": ["frc1310"] },
              "blue": { "team_keys": ["frc1114"] }
            },
            "videos": [{ "type": "youtube", "key": "xyz" }]
          }
        ]
        """;

    List<TbaMatch> matches = objectMapper.readValue(json, Argument.listOf(TbaMatch.class));

    assertEquals("2026onto_qm1", matches.get(0).key());
    assertEquals("xyz", matches.get(0).videos().get(0).key());
  }

  @Test
  void parsesEmptyMatchList() throws Exception {
    List<TbaMatch> matches = objectMapper.readValue("[]", Argument.listOf(TbaMatch.class));
    assertTrue(matches.isEmpty());
  }

  @Test
  void parsesMalformedTeamKeyWithoutThrowing() throws Exception {
    // Malformed "frcABC" — parseInt happens downstream in the sync service, not at JSON-parse time.
    // The model should preserve the raw string so the sync can log + skip.
    String json =
        """
        [
          {
            "key": "2026onto_qm1",
            "comp_level": "qm",
            "set_number": 1,
            "match_number": 1,
            "alliances": {
              "red":  { "team_keys": ["frcABC", "frc2056", "frc4917"] },
              "blue": { "team_keys": ["frc1114", "frc2713", "frc5406"] }
            },
            "videos": []
          }
        ]
        """;

    List<TbaMatch> matches = objectMapper.readValue(json, Argument.listOf(TbaMatch.class));
    assertEquals("frcABC", matches.get(0).alliances().red().teamKeys().get(0));
  }
}
