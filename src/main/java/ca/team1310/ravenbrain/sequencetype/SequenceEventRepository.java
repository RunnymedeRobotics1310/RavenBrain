package ca.team1310.ravenbrain.sequencetype;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

/**
 * @author Junie
 * @since 2026-01-07
 */
@JdbcRepository(dialect = Dialect.MYSQL)
public interface SequenceEventRepository extends CrudRepository<SequenceEvent, Long> {
  void deleteBySequencetype(SequenceType sequencetype);
}
