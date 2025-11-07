/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.frcapi.model;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

/**
 * @author Tony Field
 * @since 2025-09-22 00:44
 */
@Data
@Serdeable
public class ScheduleTeam {

  int teamNumber;
  String station;
  boolean surrogate;
}
