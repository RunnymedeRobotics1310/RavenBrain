package ca.team1310.ravenbrain.frcapi.model.year2025;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

/**
 * @author Tony Field
 * @since 2025-11-10 23:11
 */
@Serdeable
@Data
public class Reef {
  private ReefRow topRow;
  private ReefRow midRow;
  private ReefRow botRow;
  private int trough;
}
