package ca.team1310.ravenbrain.sequencetype;

import ca.team1310.ravenbrain.eventtype.EventType;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;

/**
 * @author Tony Field
 * @since 2026-01-07 17:33
 */
@MappedEntity("RB_SEQUENCEEVENT")
@Serdeable
public record SequenceEvent(
    @Id @GeneratedValue Long id,
    @Relation(Relation.Kind.MANY_TO_ONE) @MappedProperty("sequencetype_id") @Nullable
        SequenceType sequencetype,
    @Relation(Relation.Kind.MANY_TO_ONE) @MappedProperty("eventtype_id") EventType eventtype,
    @MappedProperty("startOfSequence") boolean startOfSequence,
    @MappedProperty("endOfSequence") boolean endOfSequence) {}
