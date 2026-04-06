package ca.team1310.ravenbrain.matchstrategy;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

@MappedEntity(value = "RB_MATCH_STRATEGY_PLAN")
@Serdeable
public record MatchStrategyPlan(
    @Id @GeneratedValue Long id,
    @MappedProperty("tournament_id") String tournamentId,
    @MappedProperty("match_level") String matchLevel,
    @MappedProperty("match_number") int matchNumber,
    @MappedProperty("short_summary") String shortSummary,
    @Nullable @MappedProperty("strategy_text") String strategyText,
    @MappedProperty("updated_by_user_id") long updatedByUserId,
    @MappedProperty("updated_by_display_name") String updatedByDisplayName,
    @MappedProperty("updated_at") Instant updatedAt) {}
