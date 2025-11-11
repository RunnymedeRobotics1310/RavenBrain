package ca.team1310.ravenbrain.frcapi.fetch;

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
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Client wrapper for the FRC API. This class actually communicates directly with FRC
 *
 * @author Tony Field
 * @since 2025-09-21 12:38
 */
@Singleton
@Slf4j
class FrcClient {
  private static final String ENDPOINT = "https://frc-api.firstinspires.org/v3.0/";

  private final String authorizationHeader;

  FrcClient(FrcClientConfig cfg) {
    String ut = cfg.user + ':' + cfg.key;
    String token = Base64.getEncoder().encodeToString(ut.getBytes());
    this.authorizationHeader = "Basic " + token;
  }

  /**
   * Construct a HTTP request for the uri specified. If <code>lastModified</code> is included, an
   * If-Modified-Since header will be sent to cut down on excess traffic.
   *
   * @param uri the API path
   * @param lastModified the optional Last-Modified date of the request. If set, the value will be
   *     included in a If-Modified-Since header
   * @return a packaged response object
   * @throws FrcClientException on failure or a 500+ error code
   */
  FrcRawResponse get(String uri, Instant lastModified) {
    if (uri == null) uri = "";
    if (uri.startsWith("/")) uri = uri.substring(1);
    try {
      String fullyQualifiedUri = ENDPOINT + uri;
      log.debug("Requesting {}", fullyQualifiedUri);
      HttpRequest.Builder rb =
          HttpRequest.newBuilder()
              .uri(new URI(fullyQualifiedUri))
              .GET()
              .header("Authorization", authorizationHeader);

      // include if-modified-since header
      if (lastModified != null) {
        var olm = toIfModifiedSince(lastModified);
        if (olm.isPresent()) {
          rb.header("If-Modified-Since", olm.get());
          log.trace("Sending If-Modified-Since: {}", olm.orElse(null));
        }
      }
      HttpRequest request = rb.build();
      HttpClient client = HttpClient.newHttpClient();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      HttpHeaders headers = response.headers();
      Optional<String> lastModHdr = headers.firstValue("last-modified");
      Instant respLastModified = parseLastModified(lastModHdr.orElse(null)).orElse(Instant.now());

      int code = response.statusCode();
      log.debug("Status: {}", code);
      if (log.isDebugEnabled()) {
        for (String key : headers.map().keySet()) {
          log.debug("header: " + key + ": " + headers.map().get(key));
        }
      }

      switch (code) {
        case 200:
          String body = response.body();
          log.trace("Raw response from server: {}", body);
          return new FrcRawResponse(
              null, Instant.now(), respLastModified, false, code, uri, response.body());
        case 304:
          // not modified - do not need body
          return new FrcRawResponse(null, Instant.now(), respLastModified, false, code, uri, null);
        default:
          throw new FrcClientException("Response " + code + ": " + response.body());
      }

    } catch (Exception e) {
      throw new FrcClientException(
          "Exception retrieving response for " + uri + ": " + e.getMessage(), e);
    }
  }

  // https://stackoverflow.com/questions/7707555/getting-date-in-http-format-in-java
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
