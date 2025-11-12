package ca.team1310.ravenbrain.frcapi.service;

import ca.team1310.ravenbrain.frcapi.DistrictCode;
import ca.team1310.ravenbrain.frcapi.TournamentLevel;
import ca.team1310.ravenbrain.frcapi.fetch.FrcCachingClient;
import ca.team1310.ravenbrain.frcapi.fetch.FrcRawResponse;
import ca.team1310.ravenbrain.frcapi.model.EventResponse;
import ca.team1310.ravenbrain.frcapi.model.FrcDistrictsResponse;
import ca.team1310.ravenbrain.frcapi.model.ScheduleResponse;
import ca.team1310.ravenbrain.frcapi.model.SeasonSummaryResponse;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Objects;
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

  FrcClientService(FrcCachingClient client, ObjectMapper objectMapper) {
    this.client = client;
    this.objectMapper = objectMapper;
  }

  public boolean ping() {
    return client.ping();
  }

  /**
   * Fetch work to do from FRC. This method returns null when there is no work to do. To find out
   * why no work is to be done for a given request, enable DEBUG logging.
   *
   * @param uri the URI to request
   * @return the FrcRawResponse if work is to be done, or null if none is to be done.
   */
  private FrcRawResponse fetchWork(String uri) {
    FrcRawResponse response = client.fetch(uri);
    if (response == null) {
      log.error("Unexpected null response from FRC API for {}", uri);
      return null;
    }
    if (response.isProcessed()) {
      log.debug("Work already processed {}", uri);
      return null;
    }
    if (response.getStatuscode() == 404) {
      log.debug("Not found {}", uri);
      return null;
    }
    if (response.getBody() == null) {
      log.debug("No body found for {}", uri);
      return null;
    }
    if (response.getBody().isBlank()) {
      log.debug("Blank body found for {}", uri);
      return null;
    }
    return response;
  }

  /** Mark the specified response ID as processed */
  public void markProcessed(long responseId) {
    client.markProcessed(responseId);
  }

  private @Nullable <T> T parse(@NonNull String string, @NonNull Class<T> type)
      throws FrcClientServiceException {
    Objects.requireNonNull(type, "Type cannot be null");
    try {
      return objectMapper.readValue(string, Argument.of(type));
    } catch (IOException e) {
      throw new FrcClientServiceException(
          "Failed to parse JSON Response (" + e.getMessage() + "): " + string, e);
    }
  }

  public ServiceResponse<SeasonSummaryResponse> getSeasonSummary(int year) {
    FrcRawResponse response = fetchWork(Integer.toString(year));
    if (response == null) return null;
    SeasonSummaryResponse parsedResponse = parse(response.getBody(), SeasonSummaryResponse.class);
    return new ServiceResponse<>(response.getId(), parsedResponse);
  }

  public ServiceResponse<FrcDistrictsResponse> getDistrictListings(int season) {
    FrcRawResponse response = fetchWork(season + "/districts");
    if (response == null) return null;
    FrcDistrictsResponse parsedResponse = parse(response.getBody(), FrcDistrictsResponse.class);
    return new ServiceResponse<>(response.getId(), parsedResponse);
  }

  public ServiceResponse<EventResponse> getEventListingsForTeam(int season, int teamNumber) {
    FrcRawResponse response = fetchWork(season + "/events?teamNumber=" + teamNumber);
    if (response == null) return null;
    EventResponse parsedResponse = parse(response.getBody(), EventResponse.class);
    return new ServiceResponse<>(response.getId(), parsedResponse);
  }

  public ServiceResponse<EventResponse> getEventListingsForDistrict(
      int season, DistrictCode districtCode) {
    FrcRawResponse response = fetchWork(season + "/events?districtCode=" + districtCode.name());
    if (response == null) return null;
    EventResponse parsedResponse = parse(response.getBody(), EventResponse.class);
    return new ServiceResponse<>(response.getId(), parsedResponse);
  }

  public ServiceResponse<ScheduleResponse> getEventSchedule(
      int season, String eventCode, TournamentLevel tournamentLevel) {
    String path = season + "/schedule/" + eventCode + "?tournamentLevel=" + tournamentLevel.name();
    FrcRawResponse response = fetchWork(path);
    if (response == null) return null;
    ScheduleResponse parsedResponse = parse(response.getBody(), ScheduleResponse.class);
    return new ServiceResponse<>(response.getId(), parsedResponse);
  }

  public ServiceResponse<ScheduleResponse> getEventSchedule(
      int season, String eventCode, int team) {
    FrcRawResponse response = fetchWork(season + "/schedule/" + eventCode + "?teamNumber=" + team);
    if (response == null) return null;
    ScheduleResponse parsedResponse = parse(response.getBody(), ScheduleResponse.class);
    return new ServiceResponse<>(response.getId(), parsedResponse);
  }

  /**
   * Return a stringified array of match scores for the season specified. The resultant object will
   * need to be parsed into a season-specific result object. <a
   * href="https://frc-api.firstinspires.org/v3.0/:season/scores/:eventCode/:tournamentLevel?matchNumber=&start=&end=">API</a>
   */
  public String getScoreDetails(int season, String eventCode, TournamentLevel level) {
    String path = season + "/scores/" + eventCode + "/" + level.name();
    FrcRawResponse response = client.fetch(path);
    return response.getBody();
  }
}
