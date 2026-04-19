package ca.team1310.ravenbrain.tbaapi.service;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.inject.Singleton;
import java.util.List;

/** JDBC repository for {@code RB_TBA_MATCH_VIDEO}. */
@Singleton
@JdbcRepository(dialect = Dialect.MYSQL)
public abstract class TbaMatchVideoRepo implements CrudRepository<TbaMatchVideoRecord, String> {

  /** All rows for a single TBA event (drives read-time enrichment in one batched query). */
  public abstract List<TbaMatchVideoRecord> findByTbaEventKey(String tbaEventKey);
}
