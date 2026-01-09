package ca.team1310.ravenbrain.report.seq;

import ca.team1310.ravenbrain.eventtype.EventType;
import java.time.Instant;

/**
 * This represents a recorded event that is part of a sequence. It refers to the captured event
 * itself, copies the actual timestamp from the event into itself, and records the time since
 * preceding event and the first event in the sequence.
 *
 * @author Tony Field
 * @since 2026-01-08 06:50
 */
public record TimedSequenceEvent(
    EventType eventtype,
    Instant timestamp,
    long elapsedSincePrecedingEvent,
    long elapsedSinceStartOfSequence) {}
