package ca.team1310.ravenbrain.tbaapi.service;

import ca.team1310.ravenbrain.tbaapi.fetch.TbaCachingClient;
import ca.team1310.ravenbrain.tbaapi.fetch.TbaRawResponse;
import ca.team1310.ravenbrain.tbaapi.model.TbaEvent;
import ca.team1310.ravenbrain.tbaapi.model.TbaEventOprs;
import ca.team1310.ravenbrain.tbaapi.model.TbaMatch;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Service wrapper around {@link TbaCachingClient} + Jackson/Serde JSON parsing. Mirrors
 * {@code FrcClientService} — fetchWork() filters out already-processed, 404, and empty-body
 * responses; parse() is the one place JSON errors become {@link TbaClientServiceException}.
 *
 * <p>P0 only exposes {@link #getEvent(String)}. Later phases will add /teams, /matches, etc.
 */
@Singleton
@Slf4j
public class TbaClientService {

  private final TbaCachingClient client;
  private final ObjectMapper objectMapper;

  TbaClientService(TbaCachingClient client, ObjectMapper objectMapper) {
    this.client = client;
    this.objectMapper = objectMapper;
  }

  /** Mark the specified cached response as processed so the next sync skips it. */
  public void markProcessed(long responseId) {
    client.markProcessed(responseId);
  }

  /** Clear the processed flag on all cached TBA responses (forces re-processing on next sync). */
  public void clearProcessed() {
    client.clearProcessed();
  }

  /**
   * Result of a TBA event fetch. Carries the cache row id (for {@link #markProcessed}), the raw
   * response body (for {@code RB_TBA_EVENT.raw_event_json} forensics) and the parsed model.
   */
  public record EventFetchResult(long responseId, String rawBody, TbaEvent event) {}

  /**
   * Possible outcomes a sync loop cares about, besides a successful fetch. These correspond to
   * HTTP status codes the caching layer considers "processable" without throwing.
   */
  public enum EventFetchOutcome {
    NOT_FOUND, // TBA returned 404 — event key is wrong or the event does not exist
    ALREADY_PROCESSED, // cached response was already marked processed in a prior sync
    EMPTY_BODY // cached response had no usable body (should not happen in practice)
  }

  /** Holder returned by {@link #getEvent(String)} — exactly one of the two fields is non-null. */
  public record EventFetch(@Nullable EventFetchResult result, @Nullable EventFetchOutcome outcome) {
    public static EventFetch ok(EventFetchResult r) {
      return new EventFetch(r, null);
    }

    public static EventFetch skipped(EventFetchOutcome o) {
      return new EventFetch(null, o);
    }
  }

  /**
   * Fetch and parse a TBA event (full, not /simple — /simple drops the webcasts field).
   *
   * <p>The caller is responsible for validating the event key format before calling; here we
   * URL-encode the path segment defensively so an admin-supplied value cannot break the outbound
   * URI.
   *
   * @throws TbaClientServiceException on JSON parse errors
   */
  @NonNull
  public EventFetch getEvent(String eventKey) {
    if (eventKey == null || eventKey.isBlank()) {
      throw new TbaClientServiceException("TBA event key must not be null or blank");
    }
    String encoded = URLEncoder.encode(eventKey, StandardCharsets.UTF_8);
    TbaRawResponse response = client.fetch("event/" + encoded);
    if (response == null) {
      log.error("Unexpected null response from TBA for {}", eventKey);
      return EventFetch.skipped(EventFetchOutcome.EMPTY_BODY);
    }
    if (response.processed()) {
      log.debug("TBA work already processed for {}", eventKey);
      return EventFetch.skipped(EventFetchOutcome.ALREADY_PROCESSED);
    }
    if (response.statuscode() == 404) {
      log.debug("TBA event not found {}", eventKey);
      return EventFetch.skipped(EventFetchOutcome.NOT_FOUND);
    }
    if (response.body() == null || response.body().isBlank()) {
      log.debug("No body for TBA response {}", eventKey);
      return EventFetch.skipped(EventFetchOutcome.EMPTY_BODY);
    }
    TbaEvent parsed = parse(response.body(), TbaEvent.class);
    return EventFetch.ok(new EventFetchResult(response.id(), response.body(), parsed));
  }

  /**
   * Result of a TBA {@code /event/{key}/matches} fetch. Carries the cache row id, the raw body
   * (for forensics; not currently persisted per-match because the full batch lives in
   * {@code RB_TBA_RESPONSES}), and the parsed match list.
   */
  public record EventMatchesFetchResult(long responseId, String rawBody, List<TbaMatch> matches) {}

  /** Holder mirroring {@link EventFetch} — exactly one of the two fields is non-null. */
  public record EventMatchesFetch(
      @Nullable EventMatchesFetchResult result, @Nullable EventFetchOutcome outcome) {
    public static EventMatchesFetch ok(EventMatchesFetchResult r) {
      return new EventMatchesFetch(r, null);
    }

    public static EventMatchesFetch skipped(EventFetchOutcome o) {
      return new EventMatchesFetch(null, o);
    }
  }

  /**
   * Fetch and parse the full match list for a TBA event (one batch call covers every qm / qf / sf /
   * f / ef match, including videos and alliance team keys). Mirrors {@link #getEvent(String)}.
   *
   * @throws TbaClientServiceException on JSON parse errors
   */
  @NonNull
  public EventMatchesFetch getEventMatches(String eventKey) {
    if (eventKey == null || eventKey.isBlank()) {
      throw new TbaClientServiceException("TBA event key must not be null or blank");
    }
    String encoded = URLEncoder.encode(eventKey, StandardCharsets.UTF_8);
    TbaRawResponse response = client.fetch("event/" + encoded + "/matches");
    if (response == null) {
      log.error("Unexpected null response from TBA for {} matches", eventKey);
      return EventMatchesFetch.skipped(EventFetchOutcome.EMPTY_BODY);
    }
    if (response.processed()) {
      log.debug("TBA match work already processed for {}", eventKey);
      return EventMatchesFetch.skipped(EventFetchOutcome.ALREADY_PROCESSED);
    }
    if (response.statuscode() == 404) {
      log.debug("TBA matches not found for {}", eventKey);
      return EventMatchesFetch.skipped(EventFetchOutcome.NOT_FOUND);
    }
    if (response.body() == null || response.body().isBlank()) {
      log.debug("No body for TBA matches response {}", eventKey);
      return EventMatchesFetch.skipped(EventFetchOutcome.EMPTY_BODY);
    }
    List<TbaMatch> parsed = parseList(response.body(), TbaMatch.class);
    return EventMatchesFetch.ok(new EventMatchesFetchResult(response.id(), response.body(), parsed));
  }

  /**
   * Result of a TBA {@code /event/{key}/oprs} fetch. Carries the cache row id, the raw body (for
   * forensics — not persisted, but useful for log-based debugging of partial-map responses), and
   * the parsed OPR / DPR / CCWM maps.
   */
  public record EventOprsFetchResult(long responseId, String rawBody, TbaEventOprs oprs) {}

  /** Holder mirroring {@link EventFetch} — exactly one of the two fields is non-null. */
  public record EventOprsFetch(
      @Nullable EventOprsFetchResult result, @Nullable EventFetchOutcome outcome) {
    public static EventOprsFetch ok(EventOprsFetchResult r) {
      return new EventOprsFetch(r, null);
    }

    public static EventOprsFetch skipped(EventFetchOutcome o) {
      return new EventOprsFetch(null, o);
    }
  }

  /**
   * Fetch and parse the OPR / DPR / CCWM maps for a TBA event. Mirrors {@link #getEvent(String)}.
   * Each metric block may be absent on partial data; the caller (sync service) transforms the
   * three parallel maps into per-team rows and handles malformed team keys.
   *
   * @throws TbaClientServiceException on JSON parse errors
   */
  @NonNull
  public EventOprsFetch getEventOprs(String eventKey) {
    if (eventKey == null || eventKey.isBlank()) {
      throw new TbaClientServiceException("TBA event key must not be null or blank");
    }
    String encoded = URLEncoder.encode(eventKey, StandardCharsets.UTF_8);
    TbaRawResponse response = client.fetch("event/" + encoded + "/oprs");
    if (response == null) {
      log.error("Unexpected null response from TBA for {} oprs", eventKey);
      return EventOprsFetch.skipped(EventFetchOutcome.EMPTY_BODY);
    }
    if (response.processed()) {
      log.debug("TBA OPR work already processed for {}", eventKey);
      return EventOprsFetch.skipped(EventFetchOutcome.ALREADY_PROCESSED);
    }
    if (response.statuscode() == 404) {
      log.debug("TBA OPRs not found for {}", eventKey);
      return EventOprsFetch.skipped(EventFetchOutcome.NOT_FOUND);
    }
    if (response.body() == null || response.body().isBlank()) {
      log.debug("No body for TBA OPR response {}", eventKey);
      return EventOprsFetch.skipped(EventFetchOutcome.EMPTY_BODY);
    }
    TbaEventOprs parsed = parse(response.body(), TbaEventOprs.class);
    return EventOprsFetch.ok(new EventOprsFetchResult(response.id(), response.body(), parsed));
  }

  private @Nullable <T> T parse(@NonNull String string, @NonNull Class<T> type) {
    Objects.requireNonNull(type, "Type cannot be null");
    try {
      return objectMapper.readValue(string, Argument.of(type));
    } catch (IOException e) {
      throw new TbaClientServiceException(
          "Failed to parse TBA JSON Response (" + e.getMessage() + "): " + string, e);
    }
  }

  private <T> List<T> parseList(@NonNull String string, @NonNull Class<T> type) {
    Objects.requireNonNull(type, "Type cannot be null");
    try {
      return objectMapper.readValue(string, Argument.listOf(type));
    } catch (IOException e) {
      throw new TbaClientServiceException(
          "Failed to parse TBA JSON List Response (" + e.getMessage() + "): " + string, e);
    }
  }
}
