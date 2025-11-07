/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
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
  @JsonProperty("Schedule")
  List<Schedule> schedule;

  public List<Schedule> getSchedule() {
    return schedule;
  }

  public void setSchedule(List<Schedule> schedule) {
    this.schedule = schedule;
  }
}
