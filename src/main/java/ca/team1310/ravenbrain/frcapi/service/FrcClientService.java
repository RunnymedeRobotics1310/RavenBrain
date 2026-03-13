package ca.team1310.ravenbrain.frcapi.service;

import ca.team1310.ravenbrain.frcapi.fetch.FrcCachingClient;
import ca.team1310.ravenbrain.frcapi.fetch.FrcRawResponse;
import ca.team1310.ravenbrain.frcapi.model.*;
import ca.team1310.ravenbrain.frcapi.model.year2025.MatchScores2025;
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
    if (response.processed()) {
      log.debug("Work already processed {}", uri);
      return null;
    }
    if (response.statuscode() == 404) {
      log.debug("Not found {}", uri);
      return null;
    }
    if (response.body() == null) {
      log.debug("No body found for {}", uri);
      return null;
    }
    if (response.body().isBlank()) {
      log.debug("Blank body found for {}", uri);
      return null;
    }
    return response;
  }

  /** Mark the specified response ID as processed */
  public void markProcessed(long responseId) {
    client.markProcessed(responseId);
  }

  /** Clear the processed flag for all cached responses, forcing re-processing on next sync. */
  public void clearProcessed() {
    client.clearProcessed();
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
    SeasonSummaryResponse parsedResponse = parse(response.body(), SeasonSummaryResponse.class);
    return new ServiceResponse<>(response.id(), parsedResponse);
  }

  public ServiceResponse<FrcDistrictsResponse> getDistrictListings(int season) {
    FrcRawResponse response = fetchWork(season + "/districts");
    if (response == null) return null;
    FrcDistrictsResponse parsedResponse = parse(response.body(), FrcDistrictsResponse.class);
    return new ServiceResponse<>(response.id(), parsedResponse);
  }

  public ServiceResponse<EventResponse> getEventListingsForTeam(int season, int teamNumber) {
    FrcRawResponse response = fetchWork(season + "/events?teamNumber=" + teamNumber);
    if (response == null) return null;
    EventResponse parsedResponse = parse(response.body(), EventResponse.class);
    return new ServiceResponse<>(response.id(), parsedResponse);
  }

  /**
   * Read the district code for a team from the cached FRC response, without requiring the response
   * to be unprocessed. Returns null if no cached data exists or no district is found.
   */
  public @Nullable String peekDistrictForTeam(int season, int teamNumber) {
    String uri = season + "/events?teamNumber=" + teamNumber;
    FrcRawResponse response = client.fetch(uri);
    if (response == null || response.body() == null || response.body().isBlank()) {
      return null;
    }
    try {
      EventResponse eventResponse = parse(response.body(), EventResponse.class);
      if (eventResponse != null && eventResponse.events() != null) {
        for (Event event : eventResponse.events()) {
          if (event.districtCode() != null && !event.districtCode().isBlank()) {
            return event.districtCode();
          }
        }
      }
    } catch (Exception e) {
      log.warn("Failed to peek district for team {} season {}: {}", teamNumber, season, e.getMessage());
    }
    return null;
  }

  public ServiceResponse<EventResponse> getEventListings(int season) {
    FrcRawResponse response = fetchWork(season + "/events");
    if (response == null) return null;
    EventResponse parsedResponse = parse(response.body(), EventResponse.class);
    return new ServiceResponse<>(response.id(), parsedResponse);
  }

  /**
   * Read event codes for a team from the cached FRC response, without requiring the response to be
   * unprocessed. Returns null if no cached data exists.
   */
  public @Nullable java.util.List<String> peekTeamEventCodes(int season, int teamNumber) {
    String uri = season + "/events?teamNumber=" + teamNumber;
    FrcRawResponse response = client.fetch(uri);
    if (response == null || response.body() == null || response.body().isBlank()) {
      return null;
    }
    try {
      EventResponse eventResponse = parse(response.body(), EventResponse.class);
      if (eventResponse != null && eventResponse.events() != null) {
        return eventResponse.events().stream().map(Event::code).toList();
      }
    } catch (Exception e) {
      log.warn(
          "Failed to peek event codes for team {} season {}: {}",
          teamNumber,
          season,
          e.getMessage());
    }
    return null;
  }

  public ServiceResponse<EventResponse> getEventListingsForDistrict(
      int season, DistrictCode districtCode) {
    FrcRawResponse response = fetchWork(season + "/events?districtCode=" + districtCode.name());
    if (response == null) return null;
    EventResponse parsedResponse = parse(response.body(), EventResponse.class);
    return new ServiceResponse<>(response.id(), parsedResponse);
  }

  public ServiceResponse<ScheduleResponse> getEventSchedule(
      int season, String eventCode, TournamentLevel tournamentLevel) {
    String path = season + "/schedule/" + eventCode + "?tournamentLevel=" + tournamentLevel.name();
    FrcRawResponse response = fetchWork(path);
    if (response == null) return null;
    ScheduleResponse parsedResponse = parse(response.body(), ScheduleResponse.class);
    return new ServiceResponse<>(response.id(), parsedResponse);
  }

  public ServiceResponse<ScheduleResponse> getEventSchedule(
      int season, String eventCode, int team) {
    FrcRawResponse response = fetchWork(season + "/schedule/" + eventCode + "?teamNumber=" + team);
    if (response == null) return null;
    ScheduleResponse parsedResponse = parse(response.body(), ScheduleResponse.class);
    return new ServiceResponse<>(response.id(), parsedResponse);
  }

  /**
   * Return a stringified array of match scores for the season specified. The resultant object will
   * need to be parsed into a season-specific result object. <a
   * href="https://frc-api.firstinspires.org/v3.0/:season/scores/:eventCode/:tournamentLevel?matchNumber=&start=&end=">API</a>
   */
  private FrcRawResponse getScoreDetails(int season, String eventCode, TournamentLevel level) {
    return fetchWork(season + "/scores/" + eventCode + "/" + level.name());
  }

  public ServiceResponse<MatchScores2025> get2025Scores(String eventCode, TournamentLevel level) {
    FrcRawResponse response = getScoreDetails(2025, eventCode, level);
    if (response == null) return null;
    MatchScores2025 parsedResponse = parse(response.body(), MatchScores2025.class);
    return new ServiceResponse<>(response.id(), parsedResponse);
  }

  /**
   * Fetch the list of teams registered for a specific event.
   *
   * @param season the FRC season year
   * @param eventCode the event code (e.g. "ONHAM")
   * @return the teams response, or null if no work to do
   */
  public ServiceResponse<TeamListingResponse> getTeamListingsForEvent(
      int season, String eventCode) {
    FrcRawResponse response = fetchWork(season + "/teams?eventCode=" + eventCode);
    if (response == null) return null;
    TeamListingResponse parsedResponse = parse(response.body(), TeamListingResponse.class);
    return new ServiceResponse<>(response.id(), parsedResponse);
  }

  // todo: Implement get2026Scores in a manner that is consistent with get2025Scores including defining model types

  /**
   * Peek at cached schedule data for a tournament level without affecting the processed flag. Will
   * refresh from FRC API if cache TTL has expired.
   */
  public @Nullable ScheduleResponse peekSchedule(
      int season, String eventCode, TournamentLevel tournamentLevel) {
    String path = season + "/schedule/" + eventCode + "?tournamentLevel=" + tournamentLevel.name();
    try {
      FrcRawResponse response = client.fetch(path);
      if (response == null
          || response.body() == null
          || response.body().isBlank()
          || response.statuscode() == 404) {
        return null;
      }
      return parse(response.body(), ScheduleResponse.class);
    } catch (Exception e) {
      log.warn("Failed to peek schedule for {} {}: {}", eventCode, tournamentLevel, e.getMessage());
      return null;
    }
  }

  /**
   * Peek at cached score data for a tournament level without affecting the processed flag. Will
   * refresh from FRC API if cache TTL has expired. Parses using the 2025 model which contains the
   * common fields (totalPoints, rp, winningAlliance) needed across all seasons.
   */
  public @Nullable MatchScores2025 peekScores(int season, String eventCode, TournamentLevel level) {
    String uri = season + "/scores/" + eventCode + "/" + level.name();
    try {
      FrcRawResponse response = client.fetch(uri);
      if (response == null
          || response.body() == null
          || response.body().isBlank()
          || response.statuscode() == 404) {
        return null;
      }
      return parse(response.body(), MatchScores2025.class);
    } catch (Exception e) {
      log.warn("Failed to peek 2025 scores for {} {}: {}", eventCode, level, e.getMessage());
      return null;
    }
  }
}
