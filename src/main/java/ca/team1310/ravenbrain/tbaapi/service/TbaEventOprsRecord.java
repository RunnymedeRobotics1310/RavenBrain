package ca.team1310.ravenbrain.tbaapi.service;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

/**
 * Persisted TBA OPR / DPR / CCWM snapshot, one row per {@code (tbaEventKey, teamNumber)}. Written
 * only by {@link TbaEventSyncService}; read by the team-capability enricher (Unit 5).
 *
 * <p>Each metric column is {@link Nullable} because TBA's {@code /event/{key}/oprs} may return a
 * partial response (e.g. {@code oprs} present but {@code dprs} absent in rare cases). The sync
 * path persists whatever was returned; missing maps → NULL columns.
 *
 * <p>Persistence uses raw JDBC via {@link TbaEventOprsRepo} because Micronaut Data's
 * {@code CrudRepository} does not cleanly support composite primary keys without
 * {@code @EmbeddedId} ceremony. Raw JDBC keeps the sync service code readable and mirrors the
 * {@code StatboticsTeamEventRepo} precedent established in Unit 2.
 */
@Serdeable
public record TbaEventOprsRecord(
    String tbaEventKey,
    int teamNumber,
    @Nullable Double opr,
    @Nullable Double dpr,
    @Nullable Double ccwm,
    @Nullable Instant lastSync,
    @Nullable Integer lastStatus) {}
