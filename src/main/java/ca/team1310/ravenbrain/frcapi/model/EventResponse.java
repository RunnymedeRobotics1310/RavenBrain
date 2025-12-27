package ca.team1310.ravenbrain.frcapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/**
 * @author Tony Field
 * @since 2025-09-21 23:38
 */ 
@Serdeable
public record EventResponse(
        // API uses "Events" (capital E). We keep idiomatic Java field "events" and map it.
        @JsonProperty("Events")
        List<Event> events
) {
}
