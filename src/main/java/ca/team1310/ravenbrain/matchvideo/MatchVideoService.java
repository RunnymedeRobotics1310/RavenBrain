package ca.team1310.ravenbrain.matchvideo;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@JdbcRepository(dialect = Dialect.MYSQL)
public abstract class MatchVideoService implements CrudRepository<MatchVideoRecord, Long> {

  @Query(
      "SELECT * FROM RB_MATCH_VIDEO WHERE tournament_id = :tournamentId ORDER BY match_level, match_number, label")
  public abstract List<MatchVideoRecord> findAllByTournamentId(String tournamentId);

  @Query(
      "SELECT * FROM RB_MATCH_VIDEO WHERE tournament_id = :tournamentId AND match_level = :matchLevel AND match_number = :matchNumber ORDER BY label")
  public abstract List<MatchVideoRecord> findByMatch(
      String tournamentId, String matchLevel, int matchNumber);
}
