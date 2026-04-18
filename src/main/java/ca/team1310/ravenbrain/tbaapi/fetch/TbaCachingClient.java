package ca.team1310.ravenbrain.tbaapi.fetch;

import io.micronaut.context.annotation.Property;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * TTL-aware caching wrapper around {@link TbaClient}, persisting responses in {@code
 * RB_TBA_RESPONSES} so subsequent fetches within the TTL hit the cache and fetches past the TTL
 * issue conditional requests (If-Modified-Since + If-None-Match).
 *
 * <p>Mirrors {@code FrcCachingClient} with two additions aligned to the plan's safety rules:
 *
 * <ol>
 *   <li>If TBA returns {@code 304} but the matched cache row has a NULL body, the matched row is
 *       treated as a miss and the request is re-issued unconditionally. This prevents a stale
 *       partial write (body dropped separately) from silently serving ambiguous data.
 *   <li>On a {@code 200} update, {@code body}, {@code etag}, {@code lastmodified}, and {@code
 *       statuscode} are written together so the conditional-request tuple stays coherent for the
 *       next fetch.
 * </ol>
 */
@Singleton
@Slf4j
public class TbaCachingClient {

  private final TbaClient tbaClient;
  private final TbaRawResponseRepo repo;
  private final long maxAgeSeconds;

  TbaCachingClient(
      TbaClient tbaClient,
      TbaRawResponseRepo repo,
      @Property(name = "raven-eye.tba-api.ttl-seconds") long maxAgeSeconds) {
    this.tbaClient = tbaClient;
    this.repo = repo;
    this.maxAgeSeconds = maxAgeSeconds;
  }

  public void markProcessed(long requestId) {
    Optional<TbaRawResponse> or = repo.findById(requestId);
    if (or.isPresent()) {
      TbaRawResponse response = or.get();
      TbaRawResponse updated =
          new TbaRawResponse(
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

  /** Fetch a TBA URI, hitting the cache when fresh and issuing a conditional request when stale. */
  @Transactional
  public TbaRawResponse fetch(String uri) {
    TbaRawResponse db = repo.find(uri);
    if (db == null) {
      TbaRawResponse api = tbaClient.get(uri, null, null);
      log.debug("Saving first TBA request for: {}", uri);
      repo.save(api);
      return repo.find(uri);
    }

    if (Instant.now().minus(maxAgeSeconds, ChronoUnit.SECONDS).isBefore(db.lastcheck())) {
      // Within TTL window — return cached response without hitting TBA.
      log.debug("Too early to check TBA again for: {}", uri);
      return db;
    }

    // Past TTL — issue a conditional request using whatever validators we captured previously.
    TbaRawResponse api = tbaClient.get(uri, db.lastmodified(), db.etag());

    if (api.statuscode() == 304) {
      if (db.body() == null) {
        // Safety rule: a 304 against a cache row with no body is ambiguous. Re-request
        // unconditionally so we end up with a coherent (body, etag, lastmodified) tuple.
        log.debug("304 on empty-body cache row; re-requesting unconditionally for: {}", uri);
        TbaRawResponse fresh = tbaClient.get(uri, null, null);
        return repo.update(
            new TbaRawResponse(
                db.id(),
                Instant.now(),
                fresh.lastmodified(),
                fresh.etag(),
                false,
                fresh.statuscode(),
                db.url(),
                fresh.body()));
      }
      log.debug("TBA not modified for: {}", uri);
      return repo.update(
          new TbaRawResponse(
              db.id(),
              Instant.now(),
              db.lastmodified(),
              db.etag(),
              db.processed(),
              db.statuscode(),
              db.url(),
              db.body()));
    }

    if ((db.statuscode() >= 200 && db.statuscode() <= 299) || db.statuscode() == 404) {
      // Compare bodies — sometimes the server returns 200 with unchanged content.
      boolean same =
          db.lastmodified().equals(api.lastmodified())
              && (db.statuscode() == api.statuscode())
              && (db.body() != null && db.body().equals(api.body()));

      TbaRawResponse updated;
      if (same) {
        log.debug("TBA returned 200 but data unchanged for {}", uri);
        updated =
            new TbaRawResponse(
                db.id(),
                Instant.now(),
                db.lastmodified(),
                db.etag(),
                db.processed(),
                db.statuscode(),
                db.url(),
                db.body());
      } else {
        log.debug("Updated cached TBA response for: {}", uri);
        updated =
            new TbaRawResponse(
                db.id(),
                Instant.now(),
                api.lastmodified(),
                api.etag(),
                false,
                api.statuscode(),
                db.url(),
                api.body());
      }
      return repo.update(updated);
    }

    log.debug("Unexpected TBA response code for {}", uri);
    throw new TbaClientException(db);
  }

  /** Reset the processed flag for all cached TBA responses (force re-processing on next sync). */
  public void clearProcessed() {
    for (TbaRawResponse response : repo.findAll()) {
      repo.update(
          new TbaRawResponse(
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
