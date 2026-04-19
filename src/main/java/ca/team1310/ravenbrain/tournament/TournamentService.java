package ca.team1310.ravenbrain.tournament;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
   * Find tournaments currently in the active window: started in the past AND ended no more than
   * {@code tailHours} ago. Callers typically use {@link TournamentWindow#findActive()} which reads
   * the tail from {@code raven-eye.sync.tournament-window-tail-hours}.
   */
  @Query(
      "SELECT * FROM RB_TOURNAMENT WHERE starttime < NOW() "
          + "AND DATE_ADD(endtime, INTERVAL :tailHours HOUR) > NOW()")
  public abstract List<TournamentRecord> findActiveTournaments(int tailHours);

  /**
   * Find tournaments in the upcoming-or-active window: starting within {@code leadHours} or ended
   * within {@code tailHours}. Callers typically use {@link TournamentWindow#findUpcomingAndActive()}
   * which reads both bounds from the unified sync-config block.
   */
  @Query(
      "SELECT * FROM RB_TOURNAMENT "
          + "WHERE DATE_SUB(starttime, INTERVAL :leadHours HOUR) < NOW() "
          + "AND DATE_ADD(endtime, INTERVAL :tailHours HOUR) > NOW()")
  public abstract List<TournamentRecord> findUpcomingAndActiveTournaments(
      int leadHours, int tailHours);

  /**
   * ETag version source for {@code /api/tournament} and its derivatives. Returns the greatest
   * {@code updated_at} across all rows, or {@code null} when the table is empty. Controllers wrap
   * this call and the body query in {@code @Transactional(readOnly = true,
   * isolation = TransactionIsolation.REPEATABLE_READ)} so the ETag is snapshot-consistent with the
   * body.
   */
  @Query("SELECT MAX(updated_at) FROM RB_TOURNAMENT")
  public abstract Optional<Instant> findMaxUpdatedAt();

  @Query("SELECT season FROM RB_TOURNAMENT WHERE id = :tournamentId")
  public abstract int findYearForTournament(String tournamentId);

  public abstract Optional<TournamentRecord> findByCodeAndSeason(String code, int season);

  @Query(
      "SELECT * FROM RB_TOURNAMENT WHERE starttime <= :at AND endtime >= :at ORDER BY starttime ASC")
  public abstract List<TournamentRecord> findCoveringInstant(Instant at);
}
