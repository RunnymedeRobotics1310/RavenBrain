package ca.team1310.ravenbrain.statboticsapi.fetch;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** HTTP response cache for the Statbotics API. Mirrors {@code TbaRawResponseRepo}. */
@Slf4j
@Singleton
@JdbcRepository(dialect = Dialect.MYSQL)
abstract class StatboticsRawResponseRepo implements CrudRepository<StatboticsRawResponse, Long> {

  public abstract List<StatboticsRawResponse> findByUrlOrderByLastmodified(String uri);

  public StatboticsRawResponse find(String uri) {
    List<StatboticsRawResponse> results = findByUrlOrderByLastmodified(uri);
    if (results.isEmpty()) {
      return null;
    }
    return results.get(0);
  }
}
