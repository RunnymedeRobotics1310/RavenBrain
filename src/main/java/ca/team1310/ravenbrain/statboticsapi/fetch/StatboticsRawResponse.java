package ca.team1310.ravenbrain.statboticsapi.fetch;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

/**
 * Cached HTTP response from the Statbotics API. Shape mirrors {@code TbaRawResponse} for
 * operational parity; {@link #etag} and {@link #lastmodified} are structurally preserved but will
 * always be NULL / current-instant for Statbotics rows since the upstream does not emit
 * conditional-request headers (no ETag, no Last-Modified).
 *
 * <p>Primitive {@code int} / {@code boolean} fields require NOT NULL columns in the migration so
 * record construction never NPE's on a NULL read.
 */
@MappedEntity(value = "RB_STATBOTICS_RESPONSES")
@Serdeable
public record StatboticsRawResponse(
    @Id @GeneratedValue Long id,
    Instant lastcheck,
    Instant lastmodified,
    @Nullable String etag,
    boolean processed,
    int statuscode,
    String url,
    @Nullable String body) {}
