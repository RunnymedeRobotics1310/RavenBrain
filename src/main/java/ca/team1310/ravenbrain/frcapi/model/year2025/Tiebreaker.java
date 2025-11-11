package ca.team1310.ravenbrain.frcapi.model.year2025;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

/**
 * @author Tony Field
 * @since 2025-11-10 22:59
 */
@Serdeable
@Data
public class Tiebreaker {
  int item1;
  String item2;
}
