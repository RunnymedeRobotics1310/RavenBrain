package ca.team1310.ravenbrain.strategyarea;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.time.Instant;
import java.util.Optional;

/**
 * @author Junie
 * @since 2026-01-04
 */
@JdbcRepository(dialect = Dialect.MYSQL)
interface StrategyAreaRepository extends CrudRepository<StrategyArea, Long> {

  /** Weak-ETag version source for {@code GET /api/strategy-areas}. */
  @Query("SELECT MAX(updated_at) FROM RB_STRATEGYAREA")
  Optional<Instant> findMaxUpdatedAt();
}
