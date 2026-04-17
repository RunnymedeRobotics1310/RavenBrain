package ca.team1310.ravenbrain.fieldcalibration;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.MYSQL)
interface FieldCalibrationRepository extends CrudRepository<FieldCalibration, Long> {

  Optional<FieldCalibration> findByYear(int year);
}
