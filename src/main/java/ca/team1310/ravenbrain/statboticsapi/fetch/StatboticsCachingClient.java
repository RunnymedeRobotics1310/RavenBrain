package ca.team1310.ravenbrain.statboticsapi.fetch;

import io.micronaut.context.annotation.Property;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * TTL-aware caching wrapper around {@link StatboticsClient}, persisting responses in {@code
 * RB_STATBOTICS_RESPONSES} so subsequent fetches within the TTL hit the cache.
 *
 * <p>Parallel to {@code TbaCachingClient} with one important simplification: Statbotics does not
 * emit {@code ETag} or {@code Last-Modified}, so there is no conditional-request branch. Every
 * fetch past the TTL issues a fresh unconditional GET.
 *
 * <p>The {@code etag} and {@code lastmodified} columns remain in the schema for structural parity
 * with {@code RB_TBA_RESPONSES}, but they are always populated with synthesized values (NULL etag;
 * {@code lastmodified = lastcheck}) so code that later inspects either table uniformly will not
 * choke on NULL reads of primitive fields.
 */
@Singleton
@Slf4j
public class StatboticsCachingClient {

  private final StatboticsClient statboticsClient;
  private final StatboticsRawResponseRepo repo;
  private final long maxAgeSeconds;

  StatboticsCachingClient(
      StatboticsClient statboticsClient,
      StatboticsRawResponseRepo repo,
      @Property(name = "raven-eye.statbotics-api.ttl-seconds", defaultValue = "60")
          long maxAgeSeconds) {
    this.statboticsClient = statboticsClient;
    this.repo = repo;
    this.maxAgeSeconds = maxAgeSeconds;
  }

  public void markProcessed(long requestId) {
    Optional<StatboticsRawResponse> or = repo.findById(requestId);
    if (or.isPresent()) {
      StatboticsRawResponse response = or.get();
      StatboticsRawResponse updated =
          new StatboticsRawResponse(
              response.id(),
              response.lastcheck(),
              response.lastmodified(),
              response.etag(),
              true,
              response.statuscode(),
              response.url(),
              response.body());
      repo.update(updated);
    }
  }

  /**
   * Fetch a Statbotics URI, hitting the cache when fresh and issuing an unconditional GET when
   * stale. Callers are expected to pass a URL-encoded relative path — see
   * {@link StatboticsClient#encodePathSegment(String)} for path-segment encoding.
   */
  @Transactional
  public StatboticsRawResponse fetch(String uri) {
    StatboticsRawResponse db = repo.find(uri);
    if (db == null) {
      // First-ever request for this URI — network + persist.
      StatboticsRawResponse api = statboticsClient.get(uri);
      log.debug("Saving first Statbotics request for: {}", uri);
      repo.save(api);
      return repo.find(uri);
    }

    if (Instant.now().minus(maxAgeSeconds, ChronoUnit.SECONDS).isBefore(db.lastcheck())) {
      // Within TTL window — return cached response without hitting Statbotics.
      log.debug("Too early to check Statbotics again for: {}", uri);
      return db;
    }

    // Past TTL — unconditional GET. Any thrown StatboticsClientException propagates and the
    // prior cache row is preserved untouched (no write on the failure path).
    StatboticsRawResponse api = statboticsClient.get(uri);

    log.debug("Updated cached Statbotics response for: {}", uri);
    StatboticsRawResponse updated =
        new StatboticsRawResponse(
            db.id(),
            Instant.now(),
            api.lastmodified(),
            api.etag(),
            false,
            api.statuscode(),
            db.url(),
            api.body());
    return repo.update(updated);
  }

  /** Reset the processed flag for all cached Statbotics responses (force re-processing). */
  public void clearProcessed() {
    for (StatboticsRawResponse response : repo.findAll()) {
      repo.update(
          new StatboticsRawResponse(
              response.id(),
              response.lastcheck(),
              response.lastmodified(),
              response.etag(),
              false,
              response.statuscode(),
              response.url(),
              response.body()));
    }
  }
}
