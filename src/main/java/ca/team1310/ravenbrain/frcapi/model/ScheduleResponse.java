package ca.team1310.ravenbrain.frcapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import lombok.Data;

/**
 * @author Tony Field
 * @since 2025-09-22 00:49
 */
@Serdeable
@Data
public class ScheduleResponse {

  @JsonProperty("Schedule")
  private List<Schedule> schedule;
}
