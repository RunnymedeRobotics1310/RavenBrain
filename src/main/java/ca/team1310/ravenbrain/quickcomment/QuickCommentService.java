/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.quickcomment;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-31 00:28
 */
@Singleton
@Slf4j
@JdbcRepository(dialect = Dialect.MYSQL)
abstract class QuickCommentService implements CrudRepository<QuickComment, Long> {}
