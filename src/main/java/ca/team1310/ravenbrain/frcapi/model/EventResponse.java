package ca.team1310.ravenbrain.frcapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/**
 * @author Tony Field
 * @since 2025-09-21 23:38
 */
@Serdeable
public class EventResponse {
  long id;
  boolean processed;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public boolean isProcessed() {
    return processed;
  }

  public void setProcessed(boolean processed) {
    this.processed = processed;
  }

  // API uses "Events" (capital E). We keep idiomatic Java field "events" and map it.
  @JsonProperty("Events")
  private List<Event> events;

  public List<Event> getEvents() {
    return events;
  }

  public void setEvents(List<Event> events) {
    this.events = events;
  }

  @Override
  public String toString() {
    return "EventResponse{" + "Events=" + events + '}';
  }
}
