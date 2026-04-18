package ca.team1310.ravenbrain.tbaapi.fetch;

import io.micronaut.context.annotation.Property;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Client wrapper for The Blue Alliance API v3. Mirrors {@code FrcClient} with two differences:
 *
 * <ul>
 *   <li>Auth uses a single {@code X-TBA-Auth-Key} header instead of Basic auth.
 *   <li>TBA supports both {@code If-Modified-Since} and {@code If-None-Match} (ETag), so a prior
 *       response's {@code etag} is sent back on the next conditional request for a sharper cache
 *       hit per TBA's documented guidance.
 * </ul>
 *
 * <p>Every caller passes the event-key path segment through {@link java.net.URLEncoder} before
 * concatenation so an admin-supplied string cannot break out of the path.
 */
@Singleton
@Slf4j
public class TbaClient {

  private final String baseUrl;
  private final String authKey;

  public TbaClient(
      @Property(name = "raven-eye.tba-api.key") String key,
      @Property(name = "raven-eye.tba-api.base-url",
          defaultValue = "https://www.thebluealliance.com/api/v3") String baseUrl) {
    this.authKey = key;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
  }

  @PostConstruct
  void logConfiguration() {
    // Log only whether the key is configured — never the value, prefix, or length.
    boolean configured = authKey != null && !authKey.isBlank() && !"tba_api_key".equals(authKey);
    log.info(
        "TBA API client configured: base-url={}, key={}",
        baseUrl,
        configured ? "configured" : "NOT configured (set TBA_KEY env var to enable TBA sync)");
  }

  /**
   * Fetch a TBA resource with optional conditional headers.
   *
   * @param uri relative path (e.g. {@code "event/2026onto"}); leading slash is tolerated
   * @param lastModified optional — when present, sent as {@code If-Modified-Since}
   * @param etag optional — when present, sent as {@code If-None-Match}
   * @return the raw response (no {@link TbaRawResponse#id}; caller persists it)
   * @throws TbaClientException on 5xx, network failure, or malformed response
   */
  TbaRawResponse get(String uri, Instant lastModified, String etag) {
    if (uri == null) uri = "";
    if (uri.startsWith("/")) uri = uri.substring(1);
    try {
      String fullyQualifiedUri = baseUrl + uri;
      HttpRequest.Builder rb =
          HttpRequest.newBuilder()
              .uri(new URI(fullyQualifiedUri))
              .GET()
              .header("X-TBA-Auth-Key", authKey == null ? "" : authKey);

      // Send any conditional header the caller provides — they are all captured from the same
      // prior 200 response, so sending them together never mixes generations.
      if (lastModified != null) {
        toIfModifiedSince(lastModified).ifPresent(s -> rb.header("If-Modified-Since", s));
      }
      if (etag != null && !etag.isBlank()) {
        rb.header("If-None-Match", etag);
      }

      HttpRequest request = rb.build();
      if (log.isDebugEnabled()) {
        log.debug("Request: GET {}", fullyQualifiedUri);
        for (var hdr : request.headers().map().entrySet()) {
          for (String value : hdr.getValue()) {
            if (hdr.getKey().equalsIgnoreCase("X-TBA-Auth-Key")) value = "[[REDACTED]]";
            log.debug("Request Header: {}: {}", hdr.getKey(), value);
          }
        }
      }

      HttpClient client = HttpClient.newHttpClient();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      HttpHeaders headers = response.headers();
      Instant respLastModified =
          parseLastModified(headers.firstValue("last-modified").orElse(null))
              .orElse(Instant.now());
      String respEtag = headers.firstValue("etag").orElse(null);
      int code = response.statusCode();

      log.debug("Response Status: {} for {}", code, uri);

      switch (code) {
        case 200:
          return new TbaRawResponse(
              null, Instant.now(), respLastModified, respEtag, false, code, uri, response.body());
        case 304:
          // Not modified — no body expected. Caller reuses cached body (unless body is NULL,
          // in which case TbaCachingClient re-requests unconditionally).
          return new TbaRawResponse(
              null, Instant.now(), respLastModified, respEtag, false, code, uri, null);
        case 404:
          return new TbaRawResponse(
              null, Instant.now(), respLastModified, respEtag, false, code, uri, response.body());
        default:
          throw new TbaClientException("Response " + code + ": " + response.body());
      }

    } catch (java.net.ConnectException e) {
      throw new TbaClientException(
          "Could not connect to TBA API for " + uri + " — is the network available?", e);
    } catch (TbaClientException e) {
      throw e;
    } catch (Exception e) {
      String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      throw new TbaClientException("Exception retrieving response for " + uri + ": " + detail, e);
    }
  }

  private static final DateTimeFormatter HTTP_LAST_MODIFIED_FORMAT =
      DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
          .withZone(ZoneId.of("GMT"));

  private static Optional<Instant> parseLastModified(String lastModifiedHeader) {
    if (lastModifiedHeader == null || lastModifiedHeader.isBlank()) return Optional.empty();
    try {
      return Optional.of(
          ZonedDateTime.parse(lastModifiedHeader, HTTP_LAST_MODIFIED_FORMAT).toInstant());
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  private static Optional<String> toIfModifiedSince(Instant lastModified) {
    if (lastModified == null) return Optional.empty();
    return Optional.of(
        ZonedDateTime.ofInstant(lastModified, ZoneId.of("GMT")).format(HTTP_LAST_MODIFIED_FORMAT));
  }
}
