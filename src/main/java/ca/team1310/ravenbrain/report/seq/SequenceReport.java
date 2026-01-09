package ca.team1310.ravenbrain.report.seq;

import java.util.List;

/**
 * @author Tony Field
 * @since 2026-01-08 07:10
 */
public record SequenceReport(
    List<SequenceInfo> sequences,
    long averageDuration,
    long fastestDuration,
    long slowestDuration,
    long durationStdDev,
    List<IntervalStats> intervalStats) {}
