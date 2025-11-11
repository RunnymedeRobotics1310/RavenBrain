package ca.team1310.ravenbrain.frcapi.fetch;

import io.micronaut.context.annotation.Property;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-11-07 10:24
 */
@Singleton
@Slf4j
class FrcCachingClient {
  private final FrcClient frcClient;
  private final FrcRawResponseRepo repo;
  private final long maxAgeSeconds;

  public FrcCachingClient(
      FrcClient frcClient,
      FrcRawResponseRepo repo,
      @Property(name = "raven-eye.frc-api.request-cache-seconds") long maxAgeSeconds) {
    this.frcClient = frcClient;
    this.repo = repo;
    this.maxAgeSeconds = maxAgeSeconds;
  }

  boolean ping() {
    FrcRawResponse response = frcClient.get(null, null);
    return response.statuscode == 200;
  }

  void markProcessed(long requestId) {
    Optional<FrcRawResponse> or = repo.findById(requestId);
    if (or.isPresent()) {
      FrcRawResponse response = or.get();
      response.setProcessed(true);
      repo.update(response);
    }
  }

  /**
   * Fetch content from cache, or if not found or found but old, fetch from FRC.
   *
   * @param uri the URI to fetch
   * @return the unprocessed response data
   */
  @Transactional
  FrcRawResponse fetch(String uri) {
    FrcRawResponse db = repo.find(uri);
    if (db == null) {
      FrcRawResponse api = frcClient.get(uri, null);
      log.trace("Saving first request for: {}", uri);
      return repo.save(api);
    } else {
      if (Instant.now().minus(maxAgeSeconds, ChronoUnit.SECONDS).isAfter(db.lastcheck)) {
        // check again
        FrcRawResponse api = frcClient.get(uri, db.lastmodified);
        if (api.statuscode == 304) /* i.e. NOT MODIFIED since db.lastmodified */ {
          // update cache
          db.lastcheck = Instant.now();
          log.trace("Not modified for: {}", uri);
          return repo.update(db);
        } else if ((db.statuscode >= 200 && db.statuscode <= 299)
            || db.statuscode == 404) /* PROCESSABLE CODES */ {
          // new data - update it
          db.lastcheck = Instant.now();
          boolean same =
              db.lastmodified.equals(api.lastmodified)
                  && (db.statuscode == api.statuscode)
                  && (db.body.equals(api.body));
          if (same) {
            // not sure why we didn't get a 304 for this - we should have
            log.trace("Did not get a 304 but data has not changed for {}", uri);
          } else {
            // changed data needs reprocessing
            db.processed = false;
            db.lastmodified = api.lastmodified;
            db.statuscode = api.statuscode;
            db.body = api.body;
            log.trace("Updated cached response for: {}", uri);
          }
          return repo.update(db);
        } else {
          log.trace("Unexpected response code for {}", uri);
          throw new FrcClientException(db);
        }
      } else {
        // too early to check again
        log.trace("Too early to check again for: {}", uri);
        return db;
      }
    }
  }
}
