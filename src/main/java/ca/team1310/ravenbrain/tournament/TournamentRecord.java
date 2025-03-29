/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.tournament;

import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.util.Objects;

/**
 * @author Tony Field
 * @since 2025-03-23 13:58
 */
@Serdeable
public class TournamentRecord {
  private String id;
  private String name;
  private Instant startTime;
  private Instant endTime;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Instant getStartTime() {
    return startTime;
  }

  public void setStartTime(Instant startTime) {
    this.startTime = startTime;
  }

  public Instant getEndTime() {
    return endTime;
  }

  public void setEndTime(Instant endTime) {
    this.endTime = endTime;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;

    TournamentRecord that = (TournamentRecord) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(startTime, that.startTime)
        && Objects.equals(endTime, that.endTime);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(id);
    result = 31 * result + Objects.hashCode(name);
    result = 31 * result + Objects.hashCode(startTime);
    result = 31 * result + Objects.hashCode(endTime);
    return result;
  }
}
