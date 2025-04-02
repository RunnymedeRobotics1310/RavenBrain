/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.eventlog;

import ca.team1310.ravenbrain.schedule.ScheduleRecord;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-23 22:59
 */
@Slf4j
@Singleton
@JdbcRepository(dialect = Dialect.MYSQL)
abstract class EventLogService implements CrudRepository<EventLogRecord, Long> {}
