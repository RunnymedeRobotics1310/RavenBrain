package ca.team1310.ravenbrain.frcapi.fetch;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

/**
 * Holder for raw response data. This is not meant to be consumed by business logic, but instead
 * just holds cached HTTP responses, in order to cut down on excess HTTP traffic.
 *
 * @author Tony Field
 * @since 2025-09-21 19:04
 */
@MappedEntity(value = "RB_FRC_RESPONSES")
@Serdeable
public record FrcRawResponse(
    @Id @GeneratedValue Long id,
    Instant lastcheck,
    Instant lastmodified,
    boolean processed,
    int statuscode,
    String url,
    String body) {}
