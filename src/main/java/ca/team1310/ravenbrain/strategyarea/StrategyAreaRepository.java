package ca.team1310.ravenbrain.strategyarea;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

/**
 * @author Junie
 * @since 2026-01-04
 */
@JdbcRepository(dialect = Dialect.MYSQL)
interface StrategyAreaRepository extends CrudRepository<StrategyArea, Long> {}
