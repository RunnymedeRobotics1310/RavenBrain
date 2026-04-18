package ca.team1310.ravenbrain.tbaapi.service;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

/**
 * Persisted TBA event snapshot, keyed by TBA event key. Written only by {@link
 * TbaEventSyncService}; read by the tournament read-time merge (Unit 5).
 *
 * <p>{@code webcastsJson} is the canonicalized + deduplicated URL list (JSON array of strings)
 * produced by {@link WebcastUrlReconstructor#reconstructAndDedup}. {@code rawEventJson} holds the
 * full TBA payload for forensic lookup on tournament day; no P0 code reads it.
 */
@MappedEntity(value = "RB_TBA_EVENT")
@Serdeable
public record TbaEventRecord(
    @Id @MappedProperty("event_key") String eventKey,
    @Nullable @MappedProperty("webcasts_json") String webcastsJson,
    @Nullable @MappedProperty("raw_event_json") String rawEventJson,
    @Nullable @MappedProperty("last_sync") Instant lastSync,
    @Nullable @MappedProperty("last_status") Integer lastStatus) {}
