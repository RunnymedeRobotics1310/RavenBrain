/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.schedule;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

/**
 * @author Tony Field
 * @since 2025-03-23 13:59
 */
@MappedEntity(value = "RB_SCHEDULE")
@Serdeable
@Data
public class ScheduleRecord {
  @Id long id;

  @MappedProperty("tournamentid")
  String tournamentId;

  @MappedProperty("matchnum")
  int match;

  int red1;
  int red2;
  int red3;
  int blue1;
  int blue2;
  int blue3;
}
