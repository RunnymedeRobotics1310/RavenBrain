package ca.team1310.ravenbrain.tournament;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-26 07:17
 */
@Slf4j
@Singleton
@JdbcRepository(dialect = Dialect.MYSQL)
public abstract class TournamentService implements CrudRepository<TournamentRecord, String> {
  public abstract List<TournamentRecord> findAllSortByStartTime();

  @Query("SELECT * FROM RB_TOURNAMENT WHERE starttime > NOW() and endtime < NOW()")
  public abstract List<TournamentRecord> findCurrentTournaments();
}
