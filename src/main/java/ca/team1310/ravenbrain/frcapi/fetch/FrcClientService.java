/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.frcapi.fetch;

import ca.team1310.ravenbrain.frcapi.DistrictCode;
import ca.team1310.ravenbrain.frcapi.TournamentLevel;
import ca.team1310.ravenbrain.frcapi.model.EventResponse;
import ca.team1310.ravenbrain.frcapi.model.ScheduleResponse;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for accessing data from FRC. This service intelligently manages communication with the
 * FRC servers and returns processed results.
 *
 * @author Tony Field
 * @since 2025-09-21 18:50
 */
@Singleton
@Slf4j
public class FrcClientService {

  private final FrcCachingClient client;
  private final ObjectMapper objectMapper;

  public FrcClientService(FrcCachingClient client, ObjectMapper objectMapper) {
    this.client = client;
    this.objectMapper = objectMapper;
  }

  boolean ping() {
    return client.ping();
  }

  // todo: fixme: return object from model package not a raw string
  public String getSeasonSummary(int year) {
    FrcRawResponse response = client.fetch(Integer.toString(year));

    return response.body;
  }

  // todo: fixme: return object from model package not a raw string
  public String getDistrictListings(int season) {
    FrcRawResponse response = client.fetch(season + "/districts");
    return response.body;
  }

  public EventResponse getEventListingsForTeam(int season, int teamNumber) {
    FrcRawResponse response = client.fetch(season + "/events?teamNumber=" + teamNumber);
    try {
      return objectMapper.readValue(response.body, EventResponse.class);
    } catch (Exception e) {
      throw new FrcClientException("Failed to parse EventResponse", e);
    }
  }

  public EventResponse getEventListingsForDistrict(int season, DistrictCode districtCode) {
    FrcRawResponse response = client.fetch(season + "/events?districtCode=" + districtCode.name());
    try {
      return objectMapper.readValue(response.body, EventResponse.class);
    } catch (Exception e) {
      throw new FrcClientException("Failed to parse EventResponse", e);
    }
  }

  public ScheduleResponse getEventSchedule(
      int season, String eventCode, TournamentLevel tournamentLevel) {
    String path = season + "/schedule/" + eventCode + "?tournamentLevel=" + tournamentLevel.name();
    FrcRawResponse response = client.fetch(path);
    try {
      return objectMapper.readValue(response.body, ScheduleResponse.class);
    } catch (Exception e) {
      throw new FrcClientException("Failed to parse EventResponse", e);
    }
  }

  public ScheduleResponse getEventSchedule(int season, String eventCode, int team) {
    String path = season + "/schedule/" + eventCode + "?teamNumber=" + team;
    FrcRawResponse response = client.fetch(path);
    try {
      return objectMapper.readValue(response.body, ScheduleResponse.class);
    } catch (Exception e) {
      throw new FrcClientException("Failed to parse EventResponse", e);
    }
  }

  // todo: fixme: return object from model package not a raw string
  // https://frc-api.firstinspires.org/v3.0/:season/scores/:eventCode/:tournamentLevel?matchNumber=&start=&end=
  public String getScoreDetails(int season, String eventCode, TournamentLevel level) {
    String path = season + "/scores/" + eventCode + "/" + level.name();
    FrcRawResponse response = client.fetch(path);
    return response.body;
  }
}
