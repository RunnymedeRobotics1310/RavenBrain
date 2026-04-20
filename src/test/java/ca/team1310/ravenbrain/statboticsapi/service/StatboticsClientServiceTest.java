package ca.team1310.ravenbrain.statboticsapi.service;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.statboticsapi.model.StatboticsTeamEvent;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Round-trips canned {@code /v3/team_events} JSON through the real Micronaut Serde
 * {@link ObjectMapper} to keep the {@link StatboticsTeamEvent} model + nested records honest.
 * Mirrors {@code TbaClientServiceTest}: JSON parsing without network or DB.
 */
@MicronautTest
public class StatboticsClientServiceTest {

  @Inject ObjectMapper objectMapper;

  @Test
  void parsesFullTeamEventWithEpaBreakdown() throws Exception {
    String json =
        """
        [
          {
            "team": 1310,
            "event": "2026onto",
            "epa": {
              "unitless": 1712.4,
              "norm": 1680.0,
              "breakdown": {
                "total_points": 72.3,
                "auto_points": 18.1,
                "teleop_points": 44.2,
                "endgame_points": 10.0
              }
            }
          }
        ]
        """;

    List<StatboticsTeamEvent> parsed =
        objectMapper.readValue(json, Argument.listOf(StatboticsTeamEvent.class));

    assertEquals(1, parsed.size());
    StatboticsTeamEvent te = parsed.get(0);
    assertEquals(1310, te.team());
    assertEquals("2026onto", te.event());
    assertNotNull(te.epa());
    assertEquals(1712.4, te.epa().unitless(), 0.0001);
    assertEquals(1680.0, te.epa().norm(), 0.0001);
    assertNotNull(te.epa().breakdown());
    assertEquals(72.3, te.epa().breakdown().totalPoints(), 0.0001);
    assertEquals(18.1, te.epa().breakdown().autoPoints(), 0.0001);
    assertEquals(44.2, te.epa().breakdown().teleopPoints(), 0.0001);
    assertEquals(10.0, te.epa().breakdown().endgamePoints(), 0.0001);
  }

  @Test
  void parsesTeamEventWithNullBreakdownAutoPoints() throws Exception {
    // Statbotics returns null for auto_points when the team has not played any qualification
    // matches yet. Parsing must not blow up and the flat column must land as null downstream.
    String json =
        """
        [{
          "team": 9999,
          "event": "2026onto",
          "epa": { "breakdown": { "auto_points": null, "teleop_points": 12.0 } }
        }]
        """;

    List<StatboticsTeamEvent> parsed =
        objectMapper.readValue(json, Argument.listOf(StatboticsTeamEvent.class));
    assertEquals(1, parsed.size());
    assertNull(parsed.get(0).epa().breakdown().autoPoints());
    assertEquals(12.0, parsed.get(0).epa().breakdown().teleopPoints(), 0.0001);
  }

  @Test
  void ignoresUnknownTopLevelField() throws Exception {
    // Statbotics adds season-specific top-level fields; the model must tolerate them.
    String json =
        """
        [{
          "team": 1310,
          "event": "2026onto",
          "some_new_field": { "nested": true },
          "record": { "wins": 5, "losses": 2 },
          "epa": { "unitless": 1700.0 }
        }]
        """;

    List<StatboticsTeamEvent> parsed =
        objectMapper.readValue(json, Argument.listOf(StatboticsTeamEvent.class));
    assertEquals(1, parsed.size());
    assertEquals(1310, parsed.get(0).team());
    assertEquals(1700.0, parsed.get(0).epa().unitless(), 0.0001);
  }

  @Test
  void ignoresUnknownBreakdownFieldSuchAsTraversalRp() throws Exception {
    // traversal_rp is polymorphic: scalar in one record, array in another. The model does not
    // read it, so either shape must round-trip cleanly.
    String jsonScalar =
        """
        [{
          "team": 1,
          "event": "2026onto",
          "epa": { "breakdown": { "total_points": 40.0, "traversal_rp": 0.9 } }
        }]
        """;
    String jsonArray =
        """
        [{
          "team": 2,
          "event": "2026onto",
          "epa": { "breakdown": { "total_points": 41.0, "traversal_rp": [0.9, 0.8] } }
        }]
        """;

    List<StatboticsTeamEvent> a =
        objectMapper.readValue(jsonScalar, Argument.listOf(StatboticsTeamEvent.class));
    List<StatboticsTeamEvent> b =
        objectMapper.readValue(jsonArray, Argument.listOf(StatboticsTeamEvent.class));
    assertEquals(40.0, a.get(0).epa().breakdown().totalPoints(), 0.0001);
    assertEquals(41.0, b.get(0).epa().breakdown().totalPoints(), 0.0001);
  }

  @Test
  void parsesEmptyArray() throws Exception {
    List<StatboticsTeamEvent> parsed =
        objectMapper.readValue("[]", Argument.listOf(StatboticsTeamEvent.class));
    assertTrue(parsed.isEmpty());
  }

  @Test
  void parsesTeamEventWithNoEpa() throws Exception {
    // Defensive — extremely sparse Statbotics row. Flat columns must land as null downstream.
    String json = "[{\"team\": 1310, \"event\": \"2026onto\"}]";

    List<StatboticsTeamEvent> parsed =
        objectMapper.readValue(json, Argument.listOf(StatboticsTeamEvent.class));
    assertEquals(1, parsed.size());
    assertNull(parsed.get(0).epa());
  }
}
