package ca.team1310.ravenbrain.nexusapi;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record NexusDebugResponse(
    boolean enabled,
    String apiKey,
    long ttlSeconds,
    int cacheEntryCount,
    CacheEntryInfo cacheEntry,
    NexusQueueStatus queueStatus,
    int teamNumber,
    String nexusEventKey,
    String lastError,
    String lastErrorTime,
    LiveFetchResult liveFetch) {

  @Serdeable
  public record CacheEntryInfo(
      boolean present, String fetchedAt, long ageSeconds, boolean stale, String body) {}

  @Serdeable
  public record LiveFetchResult(boolean success, int statusCode, long latencyMs, String error) {}
}
