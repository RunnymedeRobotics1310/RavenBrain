package ca.team1310.ravenbrain.report.seq;

import ca.team1310.ravenbrain.eventtype.EventType;

/**
 * This record captures the interval between two events in a sequence.
 *
 * @author Tony Field
 * @since 2026-01-08 06:53
 */
public record IntervalDuration(EventType start, EventType end, long duration) {}
