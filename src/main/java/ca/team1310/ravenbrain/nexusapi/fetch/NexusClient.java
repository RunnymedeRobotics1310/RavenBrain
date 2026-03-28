package ca.team1310.ravenbrain.nexusapi.fetch;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
class NexusClient {
  private static final String ENDPOINT = "https://frc.nexus/api/v1";

  private final String apiKey;
  private final boolean enabled;

  NexusClient(@Nullable @Property(name = "raven-eye.nexus-api.key") String key) {
    this.enabled = key != null && !key.isBlank();
    this.apiKey = this.enabled ? key : "";
    if (this.enabled) {
      log.info("Nexus API client enabled");
    } else {
      log.info("Nexus API client disabled (no key configured)");
    }
  }

  boolean isEnabled() {
    return enabled;
  }

  int getApiKeyLength() {
    return apiKey.length();
  }

  String getApiKey() {
    return apiKey;
  }

  ProbeResult probe(String path) {
    if (!enabled) {
      return new ProbeResult(false, 0, 0, "Nexus API client is not enabled");
    }
    if (path == null) path = "";
    if (path.startsWith("/")) path = path.substring(1);
    long start = System.currentTimeMillis();
    try {
      String url = ENDPOINT + "/" + path;
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(url))
              .GET()
              .header("Nexus-Api-Key", apiKey)
              .build();
      HttpClient client = HttpClient.newHttpClient();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      long latency = System.currentTimeMillis() - start;
      int code = response.statusCode();
      if (code == 200) {
        return new ProbeResult(true, code, latency, null);
      } else {
        return new ProbeResult(false, code, latency, response.body());
      }
    } catch (Exception e) {
      long latency = System.currentTimeMillis() - start;
      String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      return new ProbeResult(false, 0, latency, detail);
    }
  }

  record ProbeResult(boolean success, int statusCode, long latencyMs, String error) {}

  String get(String path) {
    if (!enabled) {
      throw new NexusClientException("Nexus API client is not enabled");
    }
    if (path == null) path = "";
    if (path.startsWith("/")) path = path.substring(1);
    try {
      String url = ENDPOINT + "/" + path;
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(url))
              .GET()
              .header("Nexus-Api-Key", apiKey)
              .build();

      log.debug("Request: GET {}", url);

      HttpClient client = HttpClient.newHttpClient();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      int code = response.statusCode();
      log.debug("Response Status: {}", code);

      if (code == 200) {
        return response.body();
      } else {
        throw new NexusClientException("Nexus API returned " + code + ": " + response.body());
      }
    } catch (NexusClientException e) {
      throw e;
    } catch (java.net.ConnectException e) {
      throw new NexusClientException(
          "Could not connect to Nexus API for " + path + " — is the network available?", e);
    } catch (Exception e) {
      String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      throw new NexusClientException("Exception retrieving Nexus response for " + path + ": " + detail, e);
    }
  }
}
