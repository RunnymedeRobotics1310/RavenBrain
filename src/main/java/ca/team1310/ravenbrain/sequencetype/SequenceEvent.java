package ca.team1310.ravenbrain.sequencetype;

/**
 * @author Tony Field
 * @since 2026-01-07 17:33
 */
public record SequenceEvent(
    SequenceType sequencetype, String eventtype, boolean startOfSequence, boolean endOfSequence) {}
