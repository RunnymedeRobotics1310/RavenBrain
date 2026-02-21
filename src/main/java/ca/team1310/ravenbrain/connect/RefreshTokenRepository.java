package ca.team1310.ravenbrain.connect;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.time.Instant;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.MYSQL)
interface RefreshTokenRepository extends CrudRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByRefreshToken(String refreshToken);

  void updateByUsername(String username, boolean revoked);

  void deleteByRefreshToken(String refreshToken);

  void deleteByDateCreatedBefore(Instant cutoff);
}
