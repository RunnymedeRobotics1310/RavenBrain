package ca.team1310.ravenbrain.matchstrategy;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
@JdbcRepository(dialect = Dialect.MYSQL)
public abstract class MatchStrategyDrawingService
    implements CrudRepository<MatchStrategyDrawing, Long> {

  public abstract List<MatchStrategyDrawing> findAllByPlanIdOrderByCreatedAtAsc(long planId);

  public abstract List<MatchStrategyDrawing> findAllByPlanIdInListOrderByCreatedAtAsc(
      List<Long> planIds);
}
