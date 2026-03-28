package ca.team1310.ravenbrain.nexusapi.fetch;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class NexusCachingClient {
  private final NexusClient client;
  private final long ttlSeconds;
  private final ConcurrentHashMap<String, CachedResponse> cache = new ConcurrentHashMap<>();
  private volatile String lastError;
  private volatile Instant lastErrorTime;

  record CachedResponse(String body, Instant fetchedAt) {}

  NexusCachingClient(
      NexusClient client,
      @Property(name = "raven-eye.nexus-api.ttl-seconds") long ttlSeconds) {
    this.client = client;
    this.ttlSeconds = ttlSeconds;
  }

  public boolean isEnabled() {
    return client.isEnabled();
  }

  public int getApiKeyLength() {
    return client.getApiKeyLength();
  }

  public long getTtlSeconds() {
    return ttlSeconds;
  }

  public int getCacheEntryCount() {
    return cache.size();
  }

  public Optional<CacheEntryDebug> getCacheEntryDebug(String path) {
    CachedResponse cached = cache.get(path);
    if (cached == null) {
      return Optional.empty();
    }
    long ageSeconds = java.time.Duration.between(cached.fetchedAt(), Instant.now()).getSeconds();
    boolean stale = ageSeconds > ttlSeconds;
    return Optional.of(new CacheEntryDebug(cached.fetchedAt().toString(), ageSeconds, stale, cached.body()));
  }

  public record CacheEntryDebug(String fetchedAt, long ageSeconds, boolean stale, String body) {}

  public ProbeResult probe(String path) {
    var result = client.probe(path);
    return new ProbeResult(result.success(), result.statusCode(), result.latencyMs(), result.error());
  }

  public record ProbeResult(boolean success, int statusCode, long latencyMs, String error) {}

  public String getLastError() {
    return lastError;
  }

  public String getLastErrorTime() {
    return lastErrorTime != null ? lastErrorTime.toString() : null;
  }

  public Optional<String> fetch(String path) {
    if (!client.isEnabled()) {
      return Optional.empty();
    }

    CachedResponse cached = cache.get(path);

    if (cached != null && Instant.now().isBefore(cached.fetchedAt().plusSeconds(ttlSeconds))) {
      log.debug("Cache hit for: {}", path);
      return Optional.of(cached.body());
    }

    try {
      String body = client.get(path);
      cache.put(path, new CachedResponse(body, Instant.now()));
      return Optional.of(body);
    } catch (NexusClientException e) {
      lastError = e.getMessage();
      lastErrorTime = Instant.now();
      log.warn("Nexus API error for {}: {}", path, e.getMessage());
      if (cached != null) {
        log.debug("Returning stale cache for: {}", path);
        return Optional.of(cached.body());
      }
      return Optional.empty();
    }
  }
}
