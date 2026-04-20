package ca.team1310.ravenbrain.tbaapi.service;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.tbaapi.model.TbaEventOprs;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Verifies that canned TBA {@code /event/{key}/oprs} payloads round-trip through the real
 * Micronaut Serde {@link ObjectMapper} into the {@link TbaEventOprs} record. Mirrors
 * {@link TbaClientServiceTest} / {@link TbaClientServiceMatchesTest}: JSON parsing without network
 * or DB. End-to-end sync behaviour is exercised in {@link TbaEventSyncServiceTest}.
 */
@MicronautTest
public class TbaClientServiceOprsTest {

  @Inject ObjectMapper objectMapper;

  @Test
  void parsesFullOprsDprsCcwmsResponse() throws Exception {
    String json =
        """
        {
          "oprs":  { "frc1310": 42.5, "frc2056": 55.1, "frc4917": 30.0 },
          "dprs":  { "frc1310": 12.0, "frc2056": 14.2, "frc4917": 18.7 },
          "ccwms": { "frc1310": 30.5, "frc2056": 40.9, "frc4917": 11.3 }
        }
        """;

    TbaEventOprs parsed = objectMapper.readValue(json, Argument.of(TbaEventOprs.class));

    assertNotNull(parsed.oprs());
    assertNotNull(parsed.dprs());
    assertNotNull(parsed.ccwms());
    assertEquals(3, parsed.oprs().size());
    assertEquals(42.5, parsed.oprs().get("frc1310"), 0.0001);
    assertEquals(55.1, parsed.oprs().get("frc2056"), 0.0001);
    assertEquals(12.0, parsed.dprs().get("frc1310"), 0.0001);
    assertEquals(30.5, parsed.ccwms().get("frc1310"), 0.0001);
  }

  @Test
  void parsesResponseMissingDprsBlock() throws Exception {
    // TBA may omit a block entirely when computation is incomplete. Missing maps must deserialize
    // as null without failure so the sync layer can treat them as "no rows for this metric".
    String json =
        """
        {
          "oprs":  { "frc1310": 42.5 },
          "ccwms": { "frc1310": 30.5 }
        }
        """;

    TbaEventOprs parsed = objectMapper.readValue(json, Argument.of(TbaEventOprs.class));

    assertNotNull(parsed.oprs());
    assertNull(parsed.dprs());
    assertNotNull(parsed.ccwms());
    assertEquals(42.5, parsed.oprs().get("frc1310"), 0.0001);
  }

  @Test
  void parsesEmptyMapsObject() throws Exception {
    // All three blocks present but empty (computed but no teams yet — theoretical pre-quals state).
    String json = "{ \"oprs\": {}, \"dprs\": {}, \"ccwms\": {} }";

    TbaEventOprs parsed = objectMapper.readValue(json, Argument.of(TbaEventOprs.class));

    assertNotNull(parsed.oprs());
    assertNotNull(parsed.dprs());
    assertNotNull(parsed.ccwms());
    assertTrue(parsed.oprs().isEmpty());
    assertTrue(parsed.dprs().isEmpty());
    assertTrue(parsed.ccwms().isEmpty());
  }

  @Test
  void ignoresUnknownTopLevelFields() throws Exception {
    // TBA has historically added fields (e.g. prediction metadata) to the OPR endpoint. The model
    // must not choke on fields it does not consume.
    String json =
        """
        {
          "oprs":  { "frc1310": 42.5 },
          "dprs":  { "frc1310": 12.0 },
          "ccwms": { "frc1310": 30.5 },
          "some_future_field": { "nested": true },
          "version": "1.2"
        }
        """;

    TbaEventOprs parsed = objectMapper.readValue(json, Argument.of(TbaEventOprs.class));

    assertEquals(42.5, parsed.oprs().get("frc1310"), 0.0001);
  }

  @Test
  void parsesResponseWithMalformedTeamKeyPreservingRawString() throws Exception {
    // A malformed "frcABC" key must round-trip through JSON — transformation happens in the sync
    // layer via parseTeamNumber, not at parse time. Other teams in the same map must be preserved.
    String json =
        """
        {
          "oprs":  { "frcABC": 1.0, "frc1310": 42.5 },
          "dprs":  { "frc1310": 12.0 },
          "ccwms": { "frc1310": 30.5 }
        }
        """;

    TbaEventOprs parsed = objectMapper.readValue(json, Argument.of(TbaEventOprs.class));

    assertEquals(1.0, parsed.oprs().get("frcABC"), 0.0001);
    assertEquals(42.5, parsed.oprs().get("frc1310"), 0.0001);
  }
}
