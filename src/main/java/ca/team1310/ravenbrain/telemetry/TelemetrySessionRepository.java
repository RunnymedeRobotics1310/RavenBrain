package ca.team1310.ravenbrain.telemetry;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface TelemetrySessionRepository extends CrudRepository<TelemetrySession, Long> {
  Optional<TelemetrySession> findBySessionId(String sessionId);
}
