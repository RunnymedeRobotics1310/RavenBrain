/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.eventlog;

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
@MappedEntity(value = "RB_EVENT")
@Serdeable
@Data
public class EventLogRecord {
  @Id private long id;

  @MappedProperty("eventtimestamp")
  private Instant timestamp;

  @MappedProperty("scoutname")
  private String scoutName;

  @MappedProperty("tournamentid")
  private String tournamentId;

  @MappedProperty("matchid")
  private int matchId;

  private String alliance;

  @MappedProperty("teamnumber")
  private int teamNumber;

  @MappedProperty("eventtype")
  private String eventType;

  private double amount;
  private String note;
}
