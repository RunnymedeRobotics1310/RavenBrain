/*
 * Copyright 2026 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.strategyarea;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;

/**
 * @author Junie
 * @since 2026-01-04
 */
@MappedEntity("RB_STRATEGYAREA")
@Serdeable
public record StrategyArea(
    @Id @GeneratedValue long id, int frcyear, String name, String description) {}
