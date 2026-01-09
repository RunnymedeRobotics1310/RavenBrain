package ca.team1310.ravenbrain.report.seq;

import java.util.List;

/**
 * @author Tony Field
 * @since 2026-01-08 06:55
 */
public record SequenceInfo(
    long team,
    int frcYear,
    List<TimedSequenceEvent> events,
    List<IntervalDuration> intervals,
    long duration) {}
