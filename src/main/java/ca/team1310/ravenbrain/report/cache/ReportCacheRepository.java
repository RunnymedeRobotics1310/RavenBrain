package ca.team1310.ravenbrain.report.cache;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for report response cache entries.
 *
 * @author Tony Field
 * @since 2026-03-16
 */
@JdbcRepository(dialect = Dialect.MYSQL)
public interface ReportCacheRepository extends CrudRepository<ReportCacheRecord, Long> {

  Optional<ReportCacheRecord> findByCachekey(String cachekey);

  @Query(
      value =
          "INSERT INTO RB_REPORT_CACHE (cachekey, body, created) VALUES (:cachekey, :body, :created)"
              + " ON DUPLICATE KEY UPDATE body = :body, created = :created",
      nativeQuery = true)
  void upsert(String cachekey, String body, Instant created);

  @Query("DELETE FROM RB_REPORT_CACHE WHERE cachekey LIKE CONCAT('%:', :tournamentId)")
  void deleteByTournamentId(String tournamentId);

  @Query("DELETE FROM RB_REPORT_CACHE WHERE cachekey LIKE CONCAT(:prefix, '%')")
  void deleteByPrefix(String prefix);

  /**
   * Returns every cachekey and its last-rebuild timestamp. Used by /api/report/metadata to
   * publish the client-side version signal for reports-in-IndexedDB (Unit 6). Season-wide; no
   * tournament filter because several cache keys (team-summary, robot-perf, custom-stats) are
   * not tournament-scoped.
   */
  @Query("SELECT cachekey, created FROM RB_REPORT_CACHE ORDER BY created DESC")
  List<CachekeyCreated> findAllMetadata();

  /** Maximum created timestamp across the cache. Null (empty Optional) when the cache is empty. */
  @Query("SELECT MAX(created) FROM RB_REPORT_CACHE")
  Optional<Instant> findMaxCreated();
}
