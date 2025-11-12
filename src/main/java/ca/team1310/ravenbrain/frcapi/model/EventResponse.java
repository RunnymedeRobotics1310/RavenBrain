package ca.team1310.ravenbrain.frcapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

import java.util.List;

/**
 * @author Tony Field
 * @since 2025-09-21 23:38
 */
@Serdeable
@Data
public class EventResponse {
  // API uses "Events" (capital E). We keep idiomatic Java field "events" and map it.
  @JsonProperty("Events")
  private List<Event> events;
}
