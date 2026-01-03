package ca.team1310.ravenbrain.connect;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.MYSQL)
interface UserRepository extends CrudRepository<User, Long> {
  Optional<User> findByLogin(String login);
}
