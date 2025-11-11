package ca.team1310.ravenbrain.frcapi.fetch;

import ca.team1310.ravenbrain.frcapi.DistrictCode;
import ca.team1310.ravenbrain.frcapi.TournamentLevel;
import ca.team1310.ravenbrain.frcapi.model.EventResponse;
import ca.team1310.ravenbrain.frcapi.model.FrcDistrictsResponse;
import ca.team1310.ravenbrain.frcapi.model.ScheduleResponse;
import ca.team1310.ravenbrain.frcapi.model.SeasonSummaryResponse;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;

/**
 * Service for accessing data from FRC. This service intelligently manages communication with the
 * FRC servers and returns processed results.
 *
 * @author Tony Field
 * @since 2025-09-21 18:50
 */
@Singleton
public class FrcClientService {

  private final FrcCachingClient client;
  private final ObjectMapper objectMapper;

  FrcClientService(FrcCachingClient client, ObjectMapper objectMapper) {
    this.client = client;
    this.objectMapper = objectMapper;
  }

  boolean ping() {
    return client.ping();
  }

  public SeasonSummaryResponse getSeasonSummary(int year) {
    FrcRawResponse response = client.fetch(Integer.toString(year));
    String body = response.body;
    if (body == null || body.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.readValue(body, SeasonSummaryResponse.class);
    } catch (Exception e) {
      throw new FrcClientException("Failed to parse SeasonSummaryResponse: '" + body + "'", e);
    }
  }

  public FrcDistrictsResponse getDistrictListings(int season) {
    FrcRawResponse response = client.fetch(season + "/districts");
    String body = response.body;
    if (body == null || body.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.readValue(body, FrcDistrictsResponse.class);
    } catch (Exception e) {
      throw new FrcClientException("Failed to parse FrcDistrictsResponse: '" + body + "'", e);
    }
  }

  public EventResponse getEventListingsForTeam(int season, int teamNumber) {
    FrcRawResponse response = client.fetch(season + "/events?teamNumber=" + teamNumber);
    String body = response.body;
    if (body == null || body.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.readValue(body, EventResponse.class);
    } catch (Exception e) {
      throw new FrcClientException("Failed to parse EventResponse: '" + body + "'", e);
    }
  }

  public EventResponse getEventListingsForDistrict(int season, DistrictCode districtCode) {
    FrcRawResponse response = client.fetch(season + "/events?districtCode=" + districtCode.name());
    String body = response.body;
    if (body == null || body.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.readValue(body, EventResponse.class);
    } catch (Exception e) {
      throw new FrcClientException("Failed to parse EventResponse: '" + body + "'", e);
    }
  }

  public ScheduleResponse getEventSchedule(
      int season, String eventCode, TournamentLevel tournamentLevel) {
    String path = season + "/schedule/" + eventCode + "?tournamentLevel=" + tournamentLevel.name();
    FrcRawResponse response = client.fetch(path);
    String body = response.body;
    if (body == null || body.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.readValue(body, ScheduleResponse.class);
    } catch (Exception e) {
      throw new FrcClientException("Failed to parse ScheduleResponse: '" + body + "'", e);
    }
  }

  public ScheduleResponse getEventSchedule(int season, String eventCode, int team) {
    String path = season + "/schedule/" + eventCode + "?teamNumber=" + team;
    FrcRawResponse response = client.fetch(path);
    String body = response.body;
    if (body == null || body.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.readValue(body, ScheduleResponse.class);
    } catch (Exception e) {
      throw new FrcClientException("Failed to parse ScheduleResponse: '" + body + "'", e);
    }
  }

  /**
   * Return a stringified array of match scores for the season specified. The resultant object will
   * need to be parsed into a season-specific result object. <a
   * href="https://frc-api.firstinspires.org/v3.0/:season/scores/:eventCode/:tournamentLevel?matchNumber=&start=&end=">API</a>
   */
  public String getScoreDetails(int season, String eventCode, TournamentLevel level) {
    String path = season + "/scores/" + eventCode + "/" + level.name();
    FrcRawResponse response = client.fetch(path);
    return response.body;
  }
}
