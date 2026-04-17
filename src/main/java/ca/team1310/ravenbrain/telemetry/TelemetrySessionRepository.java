package ca.team1310.ravenbrain.telemetry;

import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface TelemetrySessionRepository extends CrudRepository<TelemetrySession, Long> {
  Optional<TelemetrySession> findBySessionId(String sessionId);

  List<TelemetrySession> findAllByTournamentIdAndMatchLevelAndMatchNumberOrderByStartedAtAsc(
      String tournamentId, TournamentLevel matchLevel, Integer matchNumber);

  List<TelemetrySession> findAllByMatchLabelIsNullOrderByIdAsc();
}
