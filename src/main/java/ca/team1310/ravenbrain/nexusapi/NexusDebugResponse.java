package ca.team1310.ravenbrain.nexusapi;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record NexusDebugResponse(
    boolean enabled,
    int apiKeyLength,
    long ttlSeconds,
    int cacheEntryCount,
    CacheEntryInfo cacheEntry,
    NexusQueueStatus queueStatus,
    int teamNumber,
    String nexusEventKey) {

  @Serdeable
  public record CacheEntryInfo(
      boolean present, String fetchedAt, long ageSeconds, boolean stale, String body) {}
}
