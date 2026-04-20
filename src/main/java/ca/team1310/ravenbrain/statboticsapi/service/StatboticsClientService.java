package ca.team1310.ravenbrain.statboticsapi.service;

import ca.team1310.ravenbrain.statboticsapi.fetch.StatboticsCachingClient;
import ca.team1310.ravenbrain.statboticsapi.fetch.StatboticsClient;
import ca.team1310.ravenbrain.statboticsapi.fetch.StatboticsRawResponse;
import ca.team1310.ravenbrain.statboticsapi.model.StatboticsTeamEvent;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Service wrapper around {@link StatboticsCachingClient} + Jackson/Serde JSON parsing. Mirrors
 * {@code TbaClientService} — the caching layer handles TTL, this layer handles JSON marshalling,
 * result/outcome wrapping, and already-processed / 404 / empty-body skip handling.
 *
 * <p>P1 exposes only the batch {@code /v3/team_events?event={key}} endpoint. Future phases can add
 * {@code /v3/team_year/{team}/{year}} and similar.
 */
@Singleton
@Slf4j
public class StatboticsClientService {

  private final StatboticsCachingClient client;
  private final ObjectMapper objectMapper;

  StatboticsClientService(StatboticsCachingClient client, ObjectMapper objectMapper) {
    this.client = client;
    this.objectMapper = objectMapper;
  }

  /** Mark the specified cached response as processed so the next sync skips it. */
  public void markProcessed(long responseId) {
    client.markProcessed(responseId);
  }

  /** Clear the processed flag on all cached Statbotics responses (forces re-processing). */
  public void clearProcessed() {
    client.clearProcessed();
  }

  /**
   * Result of a Statbotics {@code /v3/team_events?event={key}} fetch. Carries the cache row id
   * (for {@link #markProcessed}) and the parsed list. Unlike TBA's event result, there is no
   * "raw body" field because the per-event batch body is never persisted — per-team {@code
   * breakdown_json} rows on {@code RB_STATBOTICS_TEAM_EVENT} carry the structured data instead.
   */
  public record TeamEventsFetchResult(long responseId, List<StatboticsTeamEvent> teamEvents) {}

  /** Skipped-fetch outcomes the sync loop cares about besides a successful fetch. */
  public enum TeamEventsFetchOutcome {
    NOT_FOUND, // Statbotics returned 404 — event key is wrong or the event does not exist
    ALREADY_PROCESSED, // cached response was already marked processed in a prior sync
    EMPTY_BODY // cached response had no usable body (should not happen in practice)
  }

  /** Holder — exactly one of the two fields is non-null. */
  public record TeamEventsFetch(
      @Nullable TeamEventsFetchResult result, @Nullable TeamEventsFetchOutcome outcome) {
    public static TeamEventsFetch ok(TeamEventsFetchResult r) {
      return new TeamEventsFetch(r, null);
    }

    public static TeamEventsFetch skipped(TeamEventsFetchOutcome o) {
      return new TeamEventsFetch(null, o);
    }
  }

  /**
   * Fetch and parse the Statbotics per-team-per-event batch for a single event. One HTTP call
   * covers every team present at the event (Statbotics caps this endpoint at ~80-100 teams per
   * event; {@code limit=1000} is defensive).
   *
   * <p>Admin-supplied event keys are URL-encoded via
   * {@link StatboticsClient#encodePathSegment(String)} before concatenation so a stray {@code ?}
   * or {@code &} cannot break out of the query string.
   *
   * @throws StatboticsClientServiceException on JSON parse errors
   */
  @NonNull
  public TeamEventsFetch getTeamEventsByEvent(String eventKey) {
    if (eventKey == null || eventKey.isBlank()) {
      throw new StatboticsClientServiceException(
          "Statbotics event key must not be null or blank");
    }
    String encoded = StatboticsClient.encodePathSegment(eventKey);
    String path = "v3/team_events?event=" + encoded + "&limit=1000";
    StatboticsRawResponse response = client.fetch(path);
    if (response == null) {
      log.error("Unexpected null response from Statbotics for {}", eventKey);
      return TeamEventsFetch.skipped(TeamEventsFetchOutcome.EMPTY_BODY);
    }
    if (response.processed()) {
      log.debug("Statbotics team_events work already processed for {}", eventKey);
      return TeamEventsFetch.skipped(TeamEventsFetchOutcome.ALREADY_PROCESSED);
    }
    if (response.statuscode() == 404) {
      log.debug("Statbotics team_events not found for {}", eventKey);
      return TeamEventsFetch.skipped(TeamEventsFetchOutcome.NOT_FOUND);
    }
    if (response.body() == null || response.body().isBlank()) {
      log.debug("No body for Statbotics team_events response {}", eventKey);
      return TeamEventsFetch.skipped(TeamEventsFetchOutcome.EMPTY_BODY);
    }
    List<StatboticsTeamEvent> parsed = parseList(response.body(), StatboticsTeamEvent.class);
    return TeamEventsFetch.ok(new TeamEventsFetchResult(response.id(), parsed));
  }

  private <T> List<T> parseList(@NonNull String string, @NonNull Class<T> type) {
    Objects.requireNonNull(type, "Type cannot be null");
    try {
      return objectMapper.readValue(string, Argument.listOf(type));
    } catch (IOException e) {
      throw new StatboticsClientServiceException(
          "Failed to parse Statbotics JSON list response (" + e.getMessage() + "): " + string, e);
    }
  }
}
