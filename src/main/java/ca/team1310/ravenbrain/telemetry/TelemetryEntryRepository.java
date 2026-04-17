package ca.team1310.ravenbrain.telemetry;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface TelemetryEntryRepository extends CrudRepository<TelemetryEntry, Long> {

  @Query(
      "SELECT DISTINCT nt_key FROM RB_TELEMETRY_ENTRY WHERE nt_key IS NOT NULL ORDER BY nt_key ASC")
  List<String> findDistinctNtKeys();
}
