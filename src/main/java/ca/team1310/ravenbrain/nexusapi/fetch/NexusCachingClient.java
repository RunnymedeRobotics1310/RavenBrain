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
      log.warn("Nexus API error for {}: {}", path, e.getMessage());
      if (cached != null) {
        log.debug("Returning stale cache for: {}", path);
        return Optional.of(cached.body());
      }
      return Optional.empty();
    }
  }
}
