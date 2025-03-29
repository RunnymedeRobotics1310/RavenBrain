/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.schedule;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Objects;

/**
 * @author Tony Field
 * @since 2025-03-23 13:59
 */
@Serdeable
public class ScheduleRecord {
  String tournamentId;
  int match;
  int red1;
  int red2;
  int red3;
  int blue1;
  int blue2;
  int blue3;

  public String getTournamentId() {
    return tournamentId;
  }

  public void setTournamentId(String tournamentId) {
    this.tournamentId = tournamentId;
  }

  public int getMatch() {
    return match;
  }

  public void setMatch(int match) {
    this.match = match;
  }

  public int getRed1() {
    return red1;
  }

  public void setRed1(int red1) {
    this.red1 = red1;
  }

  public int getRed2() {
    return red2;
  }

  public void setRed2(int red2) {
    this.red2 = red2;
  }

  public int getRed3() {
    return red3;
  }

  public void setRed3(int red3) {
    this.red3 = red3;
  }

  public int getBlue1() {
    return blue1;
  }

  public void setBlue1(int blue1) {
    this.blue1 = blue1;
  }

  public int getBlue2() {
    return blue2;
  }

  public void setBlue2(int blue2) {
    this.blue2 = blue2;
  }

  public int getBlue3() {
    return blue3;
  }

  public void setBlue3(int blue3) {
    this.blue3 = blue3;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    ScheduleRecord that = (ScheduleRecord) o;
    return match == that.match && red1 == that.red1 && red2 == that.red2 && red3 == that.red3 && blue1 == that.blue1 && blue2 == that.blue2 && blue3 == that.blue3 && Objects.equals(tournamentId, that.tournamentId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tournamentId, match, red1, red2, red3, blue1, blue2, blue3);
  }

  @Override
  public String toString() {
    return "ScheduleRecord{" +
            "tournamentId='" + tournamentId + '\'' +
            ", match=" + match +
            ", red1=" + red1 +
            ", red2=" + red2 +
            ", red3=" + red3 +
            ", blue1=" + blue1 +
            ", blue2=" + blue2 +
            ", blue3=" + blue3 +
            '}';
  }
}
