package ca.team1310.ravenbrain.tbaapi.model;

import io.micronaut.serde.annotation.Serdeable;

/**
 * A single video reference on a TBA Match. TBA returns {@code type} + {@code key} rather than a
 * ready-to-use URL, so {@code WebcastUrlReconstructor.reconstructMatchVideo} turns these tuples
 * into https:// URLs. Unsupported types (iframe, html5, rtmp, etc) are dropped with a debug log.
 */
@Serdeable
public record TbaMatchVideo(String type, String key) {}
