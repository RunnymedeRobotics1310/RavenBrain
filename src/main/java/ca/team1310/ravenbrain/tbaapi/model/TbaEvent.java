package ca.team1310.ravenbrain.tbaapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/**
 * Subset of the TBA Event response used by P0 — only the fields RavenBrain consumes are modelled.
 * TBA returns many more fields (venue, address, district, etc.); they are ignored so adding new
 * TBA fields later does not break deserialization.
 *
 * <p>{@code key} is TBA's event identifier (e.g. {@code "2026onto"}); {@code event_code} is the
 * code part without the year prefix (e.g. {@code "onto"}). {@code webcasts} is only populated on
 * the full {@code /event/{key}} response — {@code /event/{key}/simple} drops it.
 */
@Serdeable
public record TbaEvent(
    String key,
    String name,
    int year,
    @JsonProperty("event_code") String eventCode,
    @Nullable List<TbaWebcast> webcasts) {}
