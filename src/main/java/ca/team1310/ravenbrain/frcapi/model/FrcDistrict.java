package ca.team1310.ravenbrain.frcapi.model;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

/**
 * @author Tony Field
 * @since 2025-11-10 21:48
 */
@Serdeable
@Data
public class FrcDistrict {
  private String code;
  private String name;
}
