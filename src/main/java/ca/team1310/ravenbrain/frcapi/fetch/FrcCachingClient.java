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
public class FrcCachingClient {
  private final FrcClient frcClient;
  private final FrcRawResponseRepo repo;
  private final long maxAgeSeconds;

  FrcCachingClient(
      FrcClient frcClient,
      FrcRawResponseRepo repo,
      @Property(name = "raven-eye.frc-api.ttl-seconds") long maxAgeSeconds) {
    this.frcClient = frcClient;
    this.repo = repo;
    this.maxAgeSeconds = maxAgeSeconds;
  }

  public boolean ping() {
    FrcRawResponse response = frcClient.get(null, null);
    return response.statuscode() == 200;
  }

  public void markProcessed(long requestId) {
    Optional<FrcRawResponse> or = repo.findById(requestId);
    if (or.isPresent()) {
      FrcRawResponse response = or.get();
      FrcRawResponse updated =
          new FrcRawResponse(
              response.id(),
              response.lastcheck(),
              response.lastmodified(),
              true,
              response.statuscode(),
              response.url(),
              response.body());
      repo.update(updated);
    }
  }

  /**
   * Fetch content from cache, or if not found or found but old, fetch from FRC.
   *
   * @param uri the URI to fetch
   * @return the unprocessed response data
   */
  @Transactional
  public FrcRawResponse fetch(String uri) {
    FrcRawResponse db = repo.find(uri);
    if (db == null) {
      FrcRawResponse api = frcClient.get(uri, null);
      log.debug("Saving first request for: {}", uri);
      repo.save(api);
      db = repo.find(uri);
      return db;
    } else {
      if (Instant.now().minus(maxAgeSeconds, ChronoUnit.SECONDS).isAfter(db.lastcheck())) {
        // check again
        FrcRawResponse api = frcClient.get(uri, db.lastmodified());
        if (api.statuscode() == 304) /* i.e. NOT MODIFIED since db.lastmodified */ {
          // update cache
          log.debug("Not modified for: {}", uri);
          return repo.update(
              new FrcRawResponse(
                  db.id(),
                  Instant.now(),
                  db.lastmodified(),
                  db.processed(),
                  db.statuscode(),
                  db.url(),
                  db.body()));
        } else if ((db.statuscode() >= 200 && db.statuscode() <= 299)
            || db.statuscode() == 404) /* PROCESSABLE CODES */ {
          // new data - update it
          boolean same =
              db.lastmodified().equals(api.lastmodified())
                  && (db.statuscode() == api.statuscode())
                  && (db.body().equals(api.body()));

          FrcRawResponse updated;
          if (same) {
            // not sure why we didn't get a 304 for this - we should have
            log.debug("Did not get a 304 but data has not changed for {}", uri);
            updated =
                new FrcRawResponse(
                    db.id(),
                    Instant.now(),
                    db.lastmodified(),
                    db.processed(),
                    db.statuscode(),
                    db.url(),
                    db.body());
          } else {
            // changed data needs reprocessing
            log.debug("Updated cached response for: {}", uri);
            updated =
                new FrcRawResponse(
                    db.id(),
                    Instant.now(),
                    api.lastmodified(),
                    false,
                    api.statuscode(),
                    db.url(),
                    api.body());
          }
          return repo.update(updated);
        } else {
          log.debug("Unexpected response code for {}", uri);
          throw new FrcClientException(db);
        }
      } else {
        // too early to check again
        log.debug("Too early to check again for: {}", uri);
        return db;
      }
    }
  }

  /** Clear the processed flag for all items. For test use only. */
  void clearProcessed() {
    for (FrcRawResponse response : repo.findAll()) {
      repo.update(
          new FrcRawResponse(
              response.id(),
              response.lastcheck(),
              response.lastmodified(),
              false,
              response.statuscode(),
              response.url(),
              response.body()));
    }
  }
}
