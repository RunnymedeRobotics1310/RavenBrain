package ca.team1310.ravenbrain.eventtype;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;

/**
 * @author Junie
 * @since 2026-01-04
 */
@JdbcRepository(dialect = Dialect.MYSQL)
public interface EventTypeRepository extends CrudRepository<EventType, String> {
  @Override
  List<EventType> findAll();

  List<EventType> findByFrcyear(int frcyear);
}
