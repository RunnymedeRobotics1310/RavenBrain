package ca.team1310.ravenbrain.tbaapi.model;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * A single webcast entry from TBA's Event.webcasts list.
 *
 * <p>TBA returns {@code type} + {@code channel} rather than a ready-to-use URL. {@link
 * ca.team1310.ravenbrain.tbaapi.service.WebcastUrlReconstructor} turns these tuples into https://
 * URLs, dropping types that cannot be expressed as a simple URL (iframe, html5, rtmp, etc).
 *
 * <p>{@code date} constrains a webcast to a single competition day (yyyy-mm-dd). P0 ignores it --
 * day-filtering is future work.
 */
@Serdeable
public record TbaWebcast(
    String type, String channel, @Nullable String date, @Nullable String file) {}
