package ca.team1310.ravenbrain.statboticsapi.service;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

/**
 * Persisted Statbotics per-team-per-event snapshot. Written only by
 * {@link StatboticsTeamEventSyncService}; read by the team-capability enricher (Unit 4).
 *
 * <p>Composite primary key is {@code (tbaEventKey, teamNumber)}. {@code breakdownJson} holds the
 * raw {@code epa.breakdown} sub-object as JSON TEXT for season-specific drill-down; flat
 * {@code epaAuto} / {@code epaTeleop} / {@code epaEndgame} / {@code epaTotal} columns extract
 * cross-season-stable point totals for direct SQL reads.
 *
 * <p>{@code tournamentId} is a denormalized convenience column — written whenever a mapped
 * {@link ca.team1310.ravenbrain.tournament.TournamentRecord} with a matching
 * {@code tbaEventKey} exists — so read-side enrichment can scope by tournament without a join to
 * {@code RB_TOURNAMENT}.
 *
 * <p>Persistence uses raw JDBC via {@link StatboticsTeamEventRepo} because Micronaut Data's
 * convenience {@code CrudRepository} does not cleanly support composite primary keys without
 * dragging in {@code @EmbeddedId} ceremony. Raw JDBC keeps the sync service code readable and
 * mirrors the {@code TeamTournamentService} precedent already in this codebase.
 */
@Serdeable
public record StatboticsTeamEventRecord(
    String tbaEventKey,
    int teamNumber,
    @Nullable String tournamentId,
    @Nullable Double epaTotal,
    @Nullable Double epaAuto,
    @Nullable Double epaTeleop,
    @Nullable Double epaEndgame,
    @Nullable Double epaUnitless,
    @Nullable Double epaNorm,
    @Nullable String breakdownJson,
    @Nullable Instant lastSync,
    @Nullable Integer lastStatus) {}
