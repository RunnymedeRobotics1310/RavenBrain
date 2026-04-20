package ca.team1310.ravenbrain.teamcapability;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-tournament cache for {@link TeamCapabilityResponse} lists. Invalidated by any
 * write that can affect a team's capability row: event-log / quick-comment / robot-alert writes
 * (wired in the corresponding controllers alongside {@code TournamentAggregatesService}), and TBA
 * OPR / Statbotics team-event syncs (wired via observer hooks invoked from the sync services'
 * success paths).
 *
 * <p>Mirrors the shape of {@link ca.team1310.ravenbrain.schedule.TeamScheduleCache} — small enough
 * that the whole thing lives in this one class.
 */
@Singleton
public class TeamCapabilityCache {

  private final ConcurrentHashMap<String, List<TeamCapabilityResponse>> byTournament =
      new ConcurrentHashMap<>();

  public @Nullable List<TeamCapabilityResponse> get(String tournamentId) {
    return byTournament.get(tournamentId);
  }

  public void put(String tournamentId, List<TeamCapabilityResponse> rows) {
    byTournament.put(tournamentId, rows);
  }

  /** Drop the cached list for one tournament id. */
  public void invalidate(String tournamentId) {
    byTournament.remove(tournamentId);
  }

  /**
   * Drop every cached tournament result. Used by sync services that do not carry a tournamentId
   * (they carry a {@code tba_event_key}) — invalidating the whole cache is cheaper than joining
   * to {@code RB_TOURNAMENT} to resolve the id on every sync, and the cache repopulates on the
   * next read.
   */
  public void invalidateAll() {
    byTournament.clear();
  }
}
