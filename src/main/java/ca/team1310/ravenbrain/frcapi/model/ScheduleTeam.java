package ca.team1310.ravenbrain.frcapi.model;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

/**
 * @author Tony Field
 * @since 2025-09-22 00:44
 */
@Serdeable
@Data
public class ScheduleTeam {
  private int teamNumber;
  private String station;
  private boolean surrogate;
}
