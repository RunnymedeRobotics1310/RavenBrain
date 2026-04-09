package ca.team1310.ravenbrain.telemetry;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface TelemetryEntryRepository extends CrudRepository<TelemetryEntry, Long> {}
