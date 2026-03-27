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

  boolean existsByEventType(String eventType);

  void deleteByUserId(long userId);

  @Query(
      "SELECT * FROM 'RB_EVENT' e, RB_TOURNAMENT t WHERE teamnumber = :team "
          + "AND e.tournamentid = t.id"
          + "AND t.season = :year")
  List<EventLogRecord> findForSeason(int team, int year);

  @Query(
      "SELECT DISTINCT tournamentid FROM RB_EVENT WHERE tournamentid LIKE 'DRILL-%' ORDER BY tournamentid DESC")
  List<String> findDrillTournamentIds();

  @Query(
      "SELECT DISTINCT teamnumber FROM RB_EVENT WHERE tournamentid NOT LIKE 'DRILL-%' ORDER BY teamnumber ASC")
  List<Integer> findDistinctTeamNumbers();

  @Query(
      "SELECT DISTINCT tournamentid FROM RB_EVENT WHERE teamnumber = :team AND tournamentid NOT LIKE 'DRILL-%' ORDER BY tournamentid ASC")
  List<String> findDistinctTournamentIdsByTeamNumber(int team);

  @Query(
      "SELECT DISTINCT teamnumber FROM RB_EVENT WHERE tournamentid = :tournamentId ORDER BY teamnumber ASC")
  List<Integer> findDistinctTeamNumbersByTournamentId(String tournamentId);

  @Query(
      "SELECT DISTINCT tournamentid FROM RB_EVENT WHERE tournamentid NOT LIKE 'DRILL-%' ORDER BY tournamentid ASC")
  List<String> findDistinctNonDrillTournamentIds();

  @Query(
      "SELECT DISTINCT tournamentid, eventtype FROM RB_EVENT WHERE teamnumber = :team ORDER BY tournamentid")
  List<TournamentEventTypePair> findDistinctTournamentAndEventTypeByTeamNumber(int team);

  @Query(
      "SELECT DISTINCT tournamentid FROM RB_EVENT WHERE teamnumber = :team"
          + " AND eventtype LIKE 'pmva-%' AND tournamentid NOT LIKE 'DRILL-%'"
          + " ORDER BY tournamentid")
  List<String> findDistinctPmvaTournamentIds(int team);

  List<EventLogRecord> findAllByTeamNumberAndEventTypeAndNoteIsNotNullOrderByTimestampAsc(
      int teamNumber, String eventType);

  List<EventLogRecord> findAllByTeamNumberAndEventTypeOrderByTimestampAsc(
      int teamNumber, String eventType);

  @Query("SELECT DISTINCT eventtype FROM RB_EVENT")
  List<String> findDistinctEventTypes();
}
