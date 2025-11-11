package ca.team1310.ravenbrain.frcapi.fetch;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Http Response Cache. Raw responses from FRC API are stored here
 *
 * @author Tony Field
 * @since 2025-09-21 12:39
 */
@Slf4j
@Singleton
@JdbcRepository(dialect = Dialect.MYSQL)
abstract class FrcRawResponseRepo implements CrudRepository<FrcRawResponse, Long> {

  public abstract List<FrcRawResponse> findByUrlOrderByLastmodified(String uri);

  public FrcRawResponse find(String uri) {
    List<FrcRawResponse> results = findByUrlOrderByLastmodified(uri);
    if (results.isEmpty()) {
      return null;
    }
    return results.get(0);
  }
}
