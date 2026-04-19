package ca.team1310.ravenbrain.report.cache;

import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for caching report responses in the database. Reports are stored as JSON strings keyed by
 * a composite cache key (e.g. "pmva:tournamentId"). Cache entries are invalidated when new scouting
 * events are posted for a tournament, or manually cleared by an admin.
 *
 * @author Tony Field
 * @since 2026-03-16
 */
@Slf4j
@Singleton
public class ReportCacheService {

  private final ReportCacheRepository repository;

  public ReportCacheService(ReportCacheRepository repository) {
    this.repository = repository;
  }

  /**
   * Retrieve a cached report body by cache key.
   *
   * @param cacheKey the cache key (e.g. "pmva:2025onham")
   * @return the cached JSON body, or empty if not cached
   */
  public Optional<String> get(String cacheKey) {
    return repository.findByCachekey(cacheKey).map(ReportCacheRecord::body);
  }

  /**
   * Store a report body in the cache. If an entry with the same key already exists, it is replaced.
   *
   * @param cacheKey the cache key
   * @param jsonBody the serialized report JSON
   */
  public void put(String cacheKey, String jsonBody) {
    repository.upsert(cacheKey, jsonBody, Instant.now());
    log.debug("Cached report for key: {}", cacheKey);
  }

  /**
   * Invalidate all cached reports for a given tournament. Called when new scouting events are
   * posted.
   *
   * @param tournamentId the tournament whose cached reports should be cleared
   */
  public void invalidateForTournament(String tournamentId) {
    repository.deleteByTournamentId(tournamentId);
    log.debug("Invalidated report cache for tournament: {}", tournamentId);
  }

  /**
   * Invalidate all cached reports matching a given prefix.
   *
   * @param prefix the cache key prefix (e.g. "team-summary:")
   */
  public void invalidateByPrefix(String prefix) {
    repository.deleteByPrefix(prefix);
    log.debug("Invalidated report cache for prefix: {}", prefix);
  }

  /** Clear all cached reports. Called by admin/superuser via the cache clear endpoint. */
  public void clearAll() {
    repository.deleteAll();
    log.info("Cleared all report cache entries");
  }

  /** All cache-key + created-timestamp tuples across the whole table. Backs Unit 6's metadata endpoint. */
  public List<CachekeyCreated> allMetadata() {
    return repository.findAllMetadata();
  }

  /** Weak-ETag version source for the metadata endpoint: the latest created across the cache. */
  public Instant maxCreated() {
    return repository.findMaxCreated().orElse(Instant.EPOCH);
  }
}
