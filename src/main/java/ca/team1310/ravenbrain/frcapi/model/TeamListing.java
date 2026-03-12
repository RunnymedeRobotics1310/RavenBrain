package ca.team1310.ravenbrain.frcapi.model;

import io.micronaut.serde.annotation.Serdeable;

/**
 * A team from the FRC API teams endpoint.
 *
 * @author Tony Field
 * @since 2026-03-12
 */
@Serdeable
public record TeamListing(
    int teamNumber,
    String nameFull,
    String nameShort,
    String city,
    String stateProv,
    String country,
    String website,
    int rookieYear,
    String robotName,
    String districtCode,
    String homeCMP,
    String schoolName) {}
