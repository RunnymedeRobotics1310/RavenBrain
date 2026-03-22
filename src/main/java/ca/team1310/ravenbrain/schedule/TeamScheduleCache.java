package ca.team1310.ravenbrain.schedule;

import ca.team1310.ravenbrain.schedule.TeamScheduleService.TeamScheduleResponse;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for team schedule responses. Supports multiple tournaments simultaneously since
 * watched tournaments may be queried concurrently.
 */
@Singleton
public class TeamScheduleCache {
  private final ConcurrentHashMap<String, TeamScheduleResponse> cache = new ConcurrentHashMap<>();

  public @Nullable TeamScheduleResponse get(String tournamentId) {
    return cache.get(tournamentId);
  }

  public void put(String tournamentId, TeamScheduleResponse response) {
    cache.put(tournamentId, response);
  }

  public void invalidate() {
    cache.clear();
  }
}
