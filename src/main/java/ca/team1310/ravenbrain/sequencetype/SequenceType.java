package ca.team1310.ravenbrain.sequencetype;

import java.util.List;

/**
 * @author Tony Field
 * @since 2026-01-07 17:29
 */
public record SequenceType(String name, String description, List<SequenceEvent> eventtypes) {}
