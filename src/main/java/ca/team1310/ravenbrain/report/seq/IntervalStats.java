package ca.team1310.ravenbrain.report.seq;

import ca.team1310.ravenbrain.eventtype.EventType;
import io.micronaut.serde.annotation.Serdeable;

/**
 * @author Tony Field
 * @since 2026-01-08 07:07
 */
@Serdeable
public record IntervalStats(
    EventType start, EventType end, long average, long fastest, long slowest, long stddev) {}
