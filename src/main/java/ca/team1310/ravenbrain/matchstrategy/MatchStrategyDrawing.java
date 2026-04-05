package ca.team1310.ravenbrain.matchstrategy;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

@MappedEntity(value = "RB_MATCH_STRATEGY_DRAWING")
@Serdeable
public record MatchStrategyDrawing(
    @Id @GeneratedValue Long id,
    @MappedProperty("plan_id") long planId,
    @MappedProperty("label") String label,
    @MappedProperty("strokes") String strokes,
    @MappedProperty("created_by_user_id") long createdByUserId,
    @MappedProperty("created_by_display_name") String createdByDisplayName,
    @MappedProperty("updated_by_user_id") long updatedByUserId,
    @MappedProperty("updated_by_display_name") String updatedByDisplayName,
    @MappedProperty("created_at") Instant createdAt,
    @MappedProperty("updated_at") Instant updatedAt) {}
