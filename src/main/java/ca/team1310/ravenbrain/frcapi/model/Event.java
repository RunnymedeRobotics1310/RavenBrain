package ca.team1310.ravenbrain.frcapi.model;

import io.micronaut.serde.annotation.Serdeable;

import java.time.LocalDateTime;

/**
 * @author Tony Field
 * @since 2025-09-21 23:35
 */
@Serdeable
public record Event(
    String allianceCount,
    int weekNumber,
    String code,
    String divisionCode,
    String name,
    String type,
    String districtCode,
    String venue,
    String city,
    String stateprov,
    String country,
    LocalDateTime dateStart,
    LocalDateTime dateEnd,
    String address,
    String website,
    String timezone,
    String[] announcements,
    String[] webcasts) {}
