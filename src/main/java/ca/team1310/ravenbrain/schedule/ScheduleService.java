package ca.team1310.ravenbrain.schedule;

import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import io.micronaut.data.annotation.Query;
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

  public abstract List<ScheduleRecord> findAllByTournamentIdInListOrderByTournamentId(
      List<String> tournamentIds);

  public abstract Optional<ScheduleRecord> findByTournamentIdAndLevelAndMatch(
      String tournamentId, TournamentLevel level, int match);

  /** Count schedule records for a tournament and level that have no score yet. */
  @Query(
      "SELECT COUNT(*) FROM RB_SCHEDULE WHERE tournamentid = :tournamentId AND level = :level AND redscore IS NULL")
  public abstract long countUnscoredByTournamentIdAndLevel(
      String tournamentId, TournamentLevel level);

  /** Check whether any scored qualification records exist for this tournament. */
  @Query(
      "SELECT COUNT(*) FROM RB_SCHEDULE WHERE tournamentid = :tournamentId AND level = 'Qualification' AND redscore IS NOT NULL")
  public abstract long countScoredQualificationByTournamentId(String tournamentId);

  /** Find the last two playoff matches by match number (descending), used for completion detection. */
  @Query(
      "SELECT * FROM RB_SCHEDULE WHERE tournamentid = :tournamentId AND level = 'Playoff' ORDER BY matchnum DESC LIMIT 2")
  public abstract List<ScheduleRecord> findLastTwoPlayoffMatches(String tournamentId);
}
