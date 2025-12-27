package ca.team1310.ravenbrain.schedule;

import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * @author Tony Field
 * @since 2025-03-23 22:59
 */
@Slf4j
@Singleton
@JdbcRepository(dialect = Dialect.MYSQL)
public abstract class ScheduleService implements CrudRepository<ScheduleRecord, Long> {

    public abstract List<ScheduleRecord> findAllByTournamentIdOrderByMatch(String tournamentId);

    public abstract Optional<ScheduleRecord> findByTournamentIdAndLevelAndMatch(
            String tournamentId, TournamentLevel level, int match);
}
