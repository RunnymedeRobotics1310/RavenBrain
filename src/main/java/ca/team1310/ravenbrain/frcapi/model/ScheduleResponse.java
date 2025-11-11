package ca.team1310.ravenbrain.frcapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/**
 * @author Tony Field
 * @since 2025-09-22 00:49
 */
@Serdeable
public class ScheduleResponse {
  long id;
  boolean processed;

  @JsonProperty("Schedule")
  List<Schedule> schedule;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public boolean isProcessed() {
    return processed;
  }

  public void setProcessed(boolean processed) {
    this.processed = processed;
  }

  public List<Schedule> getSchedule() {
    return schedule;
  }

  public void setSchedule(List<Schedule> schedule) {
    this.schedule = schedule;
  }
}
