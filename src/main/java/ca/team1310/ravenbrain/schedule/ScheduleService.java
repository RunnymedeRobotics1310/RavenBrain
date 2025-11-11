package ca.team1310.ravenbrain.schedule;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-23 22:59
 */
@Slf4j
@Singleton
@JdbcRepository(dialect = Dialect.MYSQL)
public abstract class ScheduleService implements CrudRepository<ScheduleRecord, Long> {

  public abstract List<ScheduleRecord> findAllByTournamentIdOrderByMatch(String tournamentId);

  public abstract Optional<ScheduleRecord> findByTournamentIdAndMatch(
      String tournamentId, int match);
}
