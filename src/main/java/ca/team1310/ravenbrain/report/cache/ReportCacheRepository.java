package ca.team1310.ravenbrain.report.cache;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
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

  @Query("DELETE FROM RB_REPORT_CACHE WHERE cachekey LIKE CONCAT('%:', :tournamentId)")
  void deleteByTournamentId(String tournamentId);
}
