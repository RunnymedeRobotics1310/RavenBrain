package ca.team1310.ravenbrain.eventtype;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

/**
 * @author Junie
 * @since 2026-01-04
 */
@MappedEntity("RB_EVENTTYPE")
@Serdeable
public record EventType(
    @Id String eventtype,
    String name,
    String description,
    int frcyear,
    @MappedProperty("strategyarea_id") Long strategyareaId,
    @MappedProperty("showNote") boolean showNote,
    @MappedProperty("showQuantity") boolean showQuantity) {}
