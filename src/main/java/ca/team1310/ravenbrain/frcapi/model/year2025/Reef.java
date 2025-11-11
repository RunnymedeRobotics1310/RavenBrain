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
  ReefRow topRow;
  ReefRow midRow;
  ReefRow botRow;
  int trough;
}
