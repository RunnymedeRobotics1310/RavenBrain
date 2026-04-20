package ca.team1310.ravenbrain.statboticsapi.fetch;

import ca.team1310.ravenbrain.Application;
import io.micronaut.context.annotation.Property;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

/**
 * Client wrapper for the Statbotics v3 API. Parallel in shape to {@code TbaClient}, but narrower:
 *
 * <ul>
 *   <li>Unauthenticated — Statbotics has no API key. The startup log uses a mask-safe provider
 *       identifier ("unauthenticated") so operators can still verify the provider is wired without
 *       leaking anything that isn't there.
 *   <li>No conditional requests — Statbotics does not emit {@code ETag} or {@code Last-Modified}
 *       headers, so this client never sends {@code If-None-Match} / {@code If-Modified-Since}.
 *       Freshness is enforced purely via the TTL window in {@link StatboticsCachingClient}.
 * </ul>
 *
 * <p>Every caller passes admin-supplied path segments (event keys, team numbers) through {@link
 * java.net.URLEncoder} before concatenation so a stray {@code ?} or {@code #} cannot break out of
 * the path.
 */
@Singleton
@Slf4j
public class StatboticsClient {

  private final String baseUrl;
  private final long ttlSeconds;
  private final String userAgent;

  public StatboticsClient(
      @Property(name = "raven-eye.statbotics-api.base-url",
          defaultValue = "https://api.statbotics.io") String baseUrl,
      @Property(name = "raven-eye.statbotics-api.ttl-seconds", defaultValue = "60")
          long ttlSeconds,
      @Property(name = "raven-eye.statbotics-api.user-agent",
          defaultValue = "StratApp/{version} (+https://raveneye.team1310.ca)")
          String userAgent) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    this.ttlSeconds = ttlSeconds;
    this.userAgent = resolveUserAgent(userAgent);
  }

  /**
   * Resolve the configured user-agent template, substituting the literal token {@code {version}}
   * with the running RavenBrain version. Falls back to a sensible default when the template is
   * blank. {@link Application#getVersion()} itself falls back to {@code "Development Build"} when
   * the manifest is missing (e.g. unpacked dev runs), so this method never returns an empty UA.
   */
  private static String resolveUserAgent(String template) {
    if (template == null || template.isBlank()) {
      return "StratApp/" + Application.getVersion() + " (+https://raveneye.team1310.ca)";
    }
    return template.replace("{version}", Application.getVersion());
  }

  @PostConstruct
  void logConfiguration() {
    log.info(
        "Statbotics API: unauthenticated, base URL {}, TTL {}s",
        baseUrl,
        ttlSeconds);
  }

  /**
   * URL-encode an admin-supplied path segment so characters like {@code ?}, {@code &}, or {@code
   * #} cannot break out of the path when concatenated.
   *
   * @param segment raw path segment (e.g. event key, team number)
   * @return URL-encoded segment suitable for direct concatenation into a URI path
   */
  public static String encodePathSegment(String segment) {
    if (segment == null) return "";
    // URLEncoder targets form bodies (encodes space as "+"); path segments want %20.
    return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /**
   * Fetch a Statbotics resource. Unconditional GET — no conditional headers are sent because
   * Statbotics does not return them.
   *
   * @param uri relative path (e.g. {@code "v3/team_events?event=2026onto"}); leading slash tolerated
   * @return the raw response ({@code id} is null; caller persists it)
   * @throws StatboticsClientException on 5xx, network failure, or malformed response
   */
  StatboticsRawResponse get(String uri) {
    if (uri == null) uri = "";
    if (uri.startsWith("/")) uri = uri.substring(1);
    String fullyQualifiedUri = baseUrl + uri;
    try {
      HttpRequest.Builder rb =
          HttpRequest.newBuilder()
              .uri(new URI(fullyQualifiedUri))
              .GET()
              .header("User-Agent", userAgent)
              .header("Accept-Encoding", "gzip")
              .header("Accept", "application/json");

      HttpRequest request = rb.build();
      if (log.isDebugEnabled()) {
        log.debug("Request: GET {}", fullyQualifiedUri);
      }

      HttpClient client = HttpClient.newHttpClient();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      int code = response.statusCode();
      log.debug("Response Status: {} for {}", code, uri);

      switch (code) {
        case 200:
          return new StatboticsRawResponse(
              null, Instant.now(), Instant.now(), null, false, code, uri, response.body());
        case 404:
          return new StatboticsRawResponse(
              null, Instant.now(), Instant.now(), null, false, code, uri, response.body());
        default:
          throw new StatboticsClientException("Response " + code + ": " + response.body());
      }

    } catch (java.net.ConnectException e) {
      throw new StatboticsClientException(
          "Could not connect to Statbotics API for " + uri + " — is the network available?", e);
    } catch (StatboticsClientException e) {
      throw e;
    } catch (Exception e) {
      String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      throw new StatboticsClientException(
          "Exception retrieving response for " + uri + ": " + detail, e);
    }
  }

  /** Expose base URL for startup diagnostics / debug endpoints. */
  public String getBaseUrl() {
    return baseUrl;
  }

  /** Expose TTL for startup diagnostics / debug endpoints. */
  public long getTtlSeconds() {
    return ttlSeconds;
  }

  /** Expose the resolved User-Agent for diagnostics. */
  public String getUserAgent() {
    return userAgent;
  }
}
