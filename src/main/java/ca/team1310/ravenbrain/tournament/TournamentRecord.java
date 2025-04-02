/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.tournament;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import lombok.Data;

/**
 * @author Tony Field
 * @since 2025-03-23 13:58
 */
@MappedEntity(value = "RB_TOURNAMENT")
@Serdeable
@Data
public class TournamentRecord {
  @Id private String id;

  @MappedProperty("tournamentname")
  private String name;

  @MappedProperty("starttime")
  private Instant startTime;

  @MappedProperty("endtime")
  private Instant endTime;
}
