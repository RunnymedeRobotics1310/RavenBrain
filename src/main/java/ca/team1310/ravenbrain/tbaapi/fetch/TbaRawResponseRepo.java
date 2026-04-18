package ca.team1310.ravenbrain.tbaapi.fetch;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** HTTP response cache for the TBA API. Mirrors FrcRawResponseRepo. */
@Slf4j
@Singleton
@JdbcRepository(dialect = Dialect.MYSQL)
abstract class TbaRawResponseRepo implements CrudRepository<TbaRawResponse, Long> {

  public abstract List<TbaRawResponse> findByUrlOrderByLastmodified(String uri);

  public TbaRawResponse find(String uri) {
    List<TbaRawResponse> results = findByUrlOrderByLastmodified(uri);
    if (results.isEmpty()) {
      return null;
    }
    return results.get(0);
  }
}
