/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.frcapi.model;

import io.micronaut.serde.annotation.Serdeable;
import java.time.LocalDateTime;
import java.util.Arrays;
import lombok.Data;

/**
 * @author Tony Field
 * @since 2025-09-21 23:35
 */
@Serdeable
@Data
public class Event {
  String allianceCount;
  int weekNumber;
  String code;
  String divisionCode;
  String name;
  String type;
  String districtCode;
  String venue;
  String city;
  String stateprov;
  String country;
  LocalDateTime dateStart;
  LocalDateTime dateEnd;
  String address;
  String website;
  String timezone;
  String[] announcements;
  String[] webcasts;

  @Override
  public String toString() {
    return "Event{"
        + "address='"
        + address
        + '\''
        + ", allianceCount='"
        + allianceCount
        + '\''
        + ", weekNumber="
        + weekNumber
        + ", code='"
        + code
        + '\''
        + ", divisionCode='"
        + divisionCode
        + '\''
        + ", name='"
        + name
        + '\''
        + ", type='"
        + type
        + '\''
        + ", districtCode='"
        + districtCode
        + '\''
        + ", venue='"
        + venue
        + '\''
        + ", city='"
        + city
        + '\''
        + ", stateprov='"
        + stateprov
        + '\''
        + ", country='"
        + country
        + '\''
        + ", dateStart="
        + dateStart
        + ", dateEnd="
        + dateEnd
        + ", website='"
        + website
        + '\''
        + ", timezone='"
        + timezone
        + '\''
        + ", announcements="
        + Arrays.toString(announcements)
        + ", webcasts="
        + Arrays.toString(webcasts)
        + '}';
  }
}
