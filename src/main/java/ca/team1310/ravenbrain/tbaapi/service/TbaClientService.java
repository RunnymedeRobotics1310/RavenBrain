package ca.team1310.ravenbrain.tbaapi.service;

import ca.team1310.ravenbrain.frcapi.service.ServiceResponse;
import ca.team1310.ravenbrain.tbaapi.fetch.TbaCachingClient;
import ca.team1310.ravenbrain.tbaapi.fetch.TbaRawResponse;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import ca.team1310.ravenbrain.tbaapi.model.TbaEvent;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
   * Fetch and parse a TBA event (full, not /simple — /simple drops the webcasts field).
   *
   * @param eventKey TBA event key, e.g. {@code "2026onto"}. The caller is responsible for
   *     validating the key format before calling this method; here we URL-encode the path segment
   *     defensively so an admin-supplied value cannot break the outbound URI.
   * @return a {@code ServiceResponse} wrapping the parsed event, or {@code null} if the response
   *     is already processed, returned 404, or has no usable body.
   */
  public ServiceResponse<TbaEvent> getEvent(String eventKey) {
    if (eventKey == null || eventKey.isBlank()) {
      throw new TbaClientServiceException("TBA event key must not be null or blank");
    }
    String encoded = URLEncoder.encode(eventKey, StandardCharsets.UTF_8);
    TbaRawResponse response = fetchWork("event/" + encoded);
    if (response == null) return null;
    TbaEvent parsed = parse(response.body(), TbaEvent.class);
    return new ServiceResponse<>(response.id(), parsed);
  }

  private TbaRawResponse fetchWork(String uri) {
    TbaRawResponse response = client.fetch(uri);
    if (response == null) {
      log.error("Unexpected null response from TBA for {}", uri);
      return null;
    }
    if (response.processed()) {
      log.debug("TBA work already processed {}", uri);
      return null;
    }
    if (response.statuscode() == 404) {
      log.debug("TBA event not found {}", uri);
      return null;
    }
    if (response.body() == null || response.body().isBlank()) {
      log.debug("No body for TBA response {}", uri);
      return null;
    }
    return response;
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
}
