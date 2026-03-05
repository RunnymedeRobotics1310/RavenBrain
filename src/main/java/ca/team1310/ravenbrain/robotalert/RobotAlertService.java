package ca.team1310.ravenbrain.robotalert;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@JdbcRepository(dialect = Dialect.MYSQL)
public abstract class RobotAlertService implements CrudRepository<RobotAlert, Long> {
  @Query(
      "SELECT * FROM RB_ROBOT_ALERT WHERE tournament_id = :tournamentId ORDER BY team_number ASC, created_at DESC")
  public abstract List<RobotAlert> findAllByTournamentIdOrderByTeamNumberAscCreatedAtDesc(
      String tournamentId);

  public abstract List<RobotAlert> findAllByTournamentIdAndTeamNumberOrderByCreatedAtDesc(
      String tournamentId, int teamNumber);
}
