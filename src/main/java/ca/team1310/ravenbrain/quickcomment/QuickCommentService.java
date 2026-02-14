package ca.team1310.ravenbrain.quickcomment;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-31 00:28
 */
@Singleton
@Slf4j
@JdbcRepository(dialect = Dialect.MYSQL)
public abstract class QuickCommentService implements CrudRepository<QuickComment, Long> {
  public abstract List<QuickComment> findAllByTeamOrderByTimestamp(int teamNumber);

  public abstract List<QuickComment> findAllOrderByTeamAndTimestamp();

  public abstract boolean existsByUserId(long userId);

  public abstract void deleteByUserId(long userId);
}
