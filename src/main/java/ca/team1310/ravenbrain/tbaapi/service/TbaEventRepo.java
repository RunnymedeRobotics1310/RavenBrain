package ca.team1310.ravenbrain.tbaapi.service;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.inject.Singleton;
import java.util.List;

/** JDBC repository for {@code RB_TBA_EVENT}. */
@Singleton
@JdbcRepository(dialect = Dialect.MYSQL)
public abstract class TbaEventRepo implements CrudRepository<TbaEventRecord, String> {
  public abstract List<TbaEventRecord> findByEventKeyIn(List<String> eventKeys);
}
