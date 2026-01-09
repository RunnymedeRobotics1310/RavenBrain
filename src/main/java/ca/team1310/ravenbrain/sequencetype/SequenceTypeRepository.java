package ca.team1310.ravenbrain.sequencetype;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;
import java.util.Optional;

/**
 * @author Junie
 * @since 2026-01-07
 */
@JdbcRepository(dialect = Dialect.MYSQL)
public interface SequenceTypeRepository extends CrudRepository<SequenceType, Long> {
  @Override
  @Join(value = "events", type = Join.Type.LEFT_FETCH)
  @Join(value = "events.eventtype", type = Join.Type.LEFT_FETCH)
  List<SequenceType> findAll();

  @Override
  @Join(value = "events", type = Join.Type.LEFT_FETCH)
  @Join(value = "events.eventtype", type = Join.Type.LEFT_FETCH)
  Optional<SequenceType> findById(Long id);

  @Join(value = "events", type = Join.Type.LEFT_FETCH)
  @Join(value = "events.eventtype", type = Join.Type.LEFT_FETCH)
  List<SequenceType> findByFrcyear(int frcyear);
}
