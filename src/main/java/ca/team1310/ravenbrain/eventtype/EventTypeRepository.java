package ca.team1310.ravenbrain.eventtype;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * @author Junie
 * @since 2026-01-04
 */
@JdbcRepository(dialect = Dialect.MYSQL)
public interface EventTypeRepository extends CrudRepository<EventType, String> {
  @Override
  List<EventType> findAll();

  List<EventType> findByFrcyear(int frcyear);

  boolean existsByStrategyareaId(Long strategyareaId);

  /** Weak-ETag version source for {@code GET /api/event-types} and its derivatives. */
  @Query("SELECT MAX(updated_at) FROM RB_EVENTTYPE")
  Optional<Instant> findMaxUpdatedAt();
}
