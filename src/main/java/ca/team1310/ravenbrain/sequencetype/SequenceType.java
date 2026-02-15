package ca.team1310.ravenbrain.sequencetype;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/**
 * @author Tony Field
 * @since 2026-01-07 17:29
 */
@MappedEntity("RB_SEQUENCETYPE")
@Serdeable
public record SequenceType(
    @Id @GeneratedValue Long id,
    String code,
    String name,
    String description,
    int frcyear,
    boolean disabled,
    @MappedProperty("strategyarea_id") Long strategyareaId,
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "sequencetype")
        List<SequenceEvent> events) {}
