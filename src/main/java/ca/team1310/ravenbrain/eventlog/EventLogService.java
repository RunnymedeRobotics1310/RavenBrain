/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.eventlog;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @author Tony Field
 * @since 2025-03-23 22:59
 */
@Slf4j
@Singleton
@JdbcRepository(dialect = Dialect.MYSQL)
public abstract class EventLogService implements CrudRepository<EventLogRecord, Long> {

  public abstract List<EventLogRecord> findAllByTournamentIdAndTeamNumber(
      String tournamentId, int teamNumber);
}
