package ca.team1310.ravenbrain.frcapi.model;

import io.micronaut.serde.annotation.Serdeable;

/**
 * @author Tony Field
 * @since 2025-11-10 21:48
 */
@Serdeable
public record FrcDistrict(String code, String name) {}
