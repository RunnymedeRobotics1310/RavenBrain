package ca.team1310.ravenbrain.tbaapi.service;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

/**
 * Persisted TBA per-match snapshot, keyed by TBA match key. Written only by {@link
 * TbaMatchSyncService}; read by {@code MatchVideoEnricher} to merge with admin-owned
 * {@code RB_MATCH_VIDEO} rows at request time.
 *
 * <p>{@code redTeams} / {@code blueTeams} are canonical sorted comma-separated team numbers
 * (e.g. {@code "1310,2056,4917"}) computed at write time so read-time joins against RB_SCHEDULE
 * are pure string equality. {@code videosJson} is the canonicalized + deduplicated URL list
 * produced by {@code WebcastUrlReconstructor.reconstructAndDedupMatchVideos}.
 */
@MappedEntity(value = "RB_TBA_MATCH_VIDEO")
@Serdeable
public record TbaMatchVideoRecord(
    @Id @MappedProperty("tba_match_key") String tbaMatchKey,
    @MappedProperty("tba_event_key") String tbaEventKey,
    @MappedProperty("comp_level") String compLevel,
    @MappedProperty("tba_match_number") int tbaMatchNumber,
    @MappedProperty("red_teams") String redTeams,
    @MappedProperty("blue_teams") String blueTeams,
    @Nullable @MappedProperty("videos_json") String videosJson,
    @Nullable @MappedProperty("last_sync") Instant lastSync,
    @Nullable @MappedProperty("last_status") Integer lastStatus) {}
