package ca.team1310.ravenbrain.sequencetype;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;

/**
 * @author Junie
 * @since 2026-01-07
 */
@JdbcRepository(dialect = Dialect.MYSQL)
public interface SequenceTypeRepository extends CrudRepository<SequenceType, Long> {
  @Override
  List<SequenceType> findAll();
}
