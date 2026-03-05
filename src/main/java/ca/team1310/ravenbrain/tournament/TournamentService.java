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

  /**
   * Find tournaments that are currently active or ended within the last 36 hours. This captures
   * tournaments still in progress as well as those that recently concluded, allowing background
   * tasks and schedule syncs to continue processing shortly after the event ends.
   */
  @Query(
      "SELECT * FROM RB_TOURNAMENT WHERE starttime < NOW() AND DATE_ADD(endtime, INTERVAL 36 HOUR) > NOW()")
  public abstract List<TournamentRecord> findActiveTournaments();

  @Query("SELECT season FROM RB_TOURNAMENT WHERE id = :tournamentId")
  public abstract int findYearForTournament(String tournamentId);
}
