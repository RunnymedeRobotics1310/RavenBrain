package ca.team1310.ravenbrain.tbaapi.fetch;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

/**
 * Cached HTTP response from TBA API v3. Mirrors FrcRawResponse with one addition: {@code etag},
 * since TBA supports both If-None-Match and If-Modified-Since conditional requests.
 *
 * <p>{@link #etag} and {@link #lastmodified} are always captured from the same 200 response, so
 * sending them together on the next conditional request never mixes generations.
 */
@MappedEntity(value = "RB_TBA_RESPONSES")
@Serdeable
public record TbaRawResponse(
    @Id @GeneratedValue Long id,
    Instant lastcheck,
    Instant lastmodified,
    @Nullable String etag,
    boolean processed,
    int statuscode,
    String url,
    @Nullable String body) {}
