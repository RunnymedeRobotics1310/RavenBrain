package ca.team1310.ravenbrain.tbaapi.service;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.tbaapi.model.TbaEvent;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that canned TBA Event JSON payloads round-trip through the real Micronaut Serde
 * ObjectMapper into the {@link TbaEvent} record, including the {@code webcasts} array and the
 * snake-cased {@code event_code} field. Exercising the actual JSON layer — rather than mocking —
 * keeps the model and its serde config honest.
 */
@MicronautTest
public class TbaClientServiceTest {

  @Inject ObjectMapper objectMapper;

  @Test
  void parsesFullEventWithWebcasts() throws Exception {
    String json =
        """
        {
          "key": "2026onto",
          "name": "Ontario Provincial Championship",
          "year": 2026,
          "event_code": "onto",
          "district": { "abbreviation": "ont" },
          "webcasts": [
            { "type": "twitch", "channel": "firstinspires" },
            { "type": "youtube", "channel": "abc123", "date": "2026-03-20" }
          ]
        }
        """;

    TbaEvent event = objectMapper.readValue(json, Argument.of(TbaEvent.class));

    assertEquals("2026onto", event.key());
    assertEquals("Ontario Provincial Championship", event.name());
    assertEquals(2026, event.year());
    assertEquals("onto", event.eventCode());
    assertNotNull(event.webcasts());
    assertEquals(2, event.webcasts().size());
    assertEquals("twitch", event.webcasts().get(0).type());
    assertEquals("firstinspires", event.webcasts().get(0).channel());
    assertEquals("2026-03-20", event.webcasts().get(1).date());
  }

  @Test
  void parsesEventWithoutWebcasts() throws Exception {
    // /event/{key}/simple drops webcasts entirely. Even though P0 uses only the full endpoint,
    // the model must tolerate a missing webcasts field so future calls do not break.
    String json =
        """
        { "key": "2026onto", "name": "Ontario", "year": 2026, "event_code": "onto" }
        """;

    TbaEvent event = objectMapper.readValue(json, Argument.of(TbaEvent.class));

    assertNull(event.webcasts());
  }

  @Test
  void parsesEventWithEmptyWebcastsArray() throws Exception {
    String json =
        """
        { "key": "2026onto", "name": "Ontario", "year": 2026, "event_code": "onto",
          "webcasts": [] }
        """;

    TbaEvent event = objectMapper.readValue(json, Argument.of(TbaEvent.class));

    assertEquals(List.of(), event.webcasts());
  }

  @Test
  void ignoresUnknownTbaFields() throws Exception {
    // TBA adds fields over time; the model must not choke on fields it does not model.
    String json =
        """
        { "key": "2026onto", "name": "Ontario", "year": 2026, "event_code": "onto",
          "venue": "X", "address": "Y", "timezone": "America/Toronto",
          "some_new_future_field": { "nested": true } }
        """;

    TbaEvent event = objectMapper.readValue(json, Argument.of(TbaEvent.class));

    assertEquals("2026onto", event.key());
  }
}
