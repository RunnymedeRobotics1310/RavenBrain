package ca.team1310.ravenbrain.matchstrategy;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;

@Singleton
@JdbcRepository(dialect = Dialect.MYSQL)
public abstract class MatchStrategyPlanService implements CrudRepository<MatchStrategyPlan, Long> {

  public abstract Optional<MatchStrategyPlan> findByTournamentIdAndMatchLevelAndMatchNumber(
      String tournamentId, String matchLevel, int matchNumber);

  @Query(
      "SELECT * FROM RB_MATCH_STRATEGY_PLAN WHERE tournament_id = :tournamentId "
          + "ORDER BY match_level ASC, match_number ASC")
  public abstract List<MatchStrategyPlan> findAllForTournament(String tournamentId);
}
