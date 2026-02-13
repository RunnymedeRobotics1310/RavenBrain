package ca.team1310.ravenbrain.eventlog;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;

/**
 * @author Tony Field
 * @since 2026-01-04
 */
@JdbcRepository(dialect = Dialect.MYSQL)
public interface EventLogRepository extends CrudRepository<EventLogRecord, Long> {
  @Override
  List<EventLogRecord> findAll();

  List<EventLogRecord> findAllByTeamNumberAndTournamentIdOrderByTimestampAsc(
      int teamNumber, String tournamentId);

  boolean existsByUserId(long userId);

  @Query(
      "SELECT * FROM 'RB_EVENT' e, RB_TOURNAMENT t WHERE teamnumber = :team "
          + "AND e.tournamentid = t.id"
          + "AND t.season = :year")
  List<EventLogRecord> findForSeason(int team, int year);
}
