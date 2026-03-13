package ca.team1310.ravenbrain.schedule;

import ca.team1310.ravenbrain.schedule.TeamScheduleService.TeamScheduleResponse;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

/**
 * Simple in-memory cache for the team schedule response. Only one tournament is cached at a time
 * since the team is only at one tournament at a time.
 */
@Singleton
public class TeamScheduleCache {
  private volatile TeamScheduleResponse cachedResponse;
  private volatile String cachedTournamentId;

  public @Nullable TeamScheduleResponse get(String tournamentId) {
    if (tournamentId != null && tournamentId.equals(cachedTournamentId)) {
      return cachedResponse;
    }
    return null;
  }

  public void put(String tournamentId, TeamScheduleResponse response) {
    this.cachedTournamentId = tournamentId;
    this.cachedResponse = response;
  }

  public void invalidate() {
    this.cachedResponse = null;
    this.cachedTournamentId = null;
  }
}
