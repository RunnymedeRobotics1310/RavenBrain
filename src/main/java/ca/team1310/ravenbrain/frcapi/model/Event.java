package ca.team1310.ravenbrain.frcapi.model;

import io.micronaut.serde.annotation.Serdeable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * @author Tony Field
 * @since 2025-09-21 23:35
 */
@Serdeable
@Data
public class Event {
  private String allianceCount;
  private int weekNumber;
  private String code;
  private String divisionCode;
  private String name;
  private String type;
  private String districtCode;
  private String venue;
  private String city;
  private String stateprov;
  private String country;
  private LocalDateTime dateStart;
  private LocalDateTime dateEnd;
  private String address;
  private String website;
  private String timezone;
  private String[] announcements;
  private String[] webcasts;
}
