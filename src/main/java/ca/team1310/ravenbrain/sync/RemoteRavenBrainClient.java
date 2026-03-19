package ca.team1310.ravenbrain.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class RemoteRavenBrainClient {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Get the version of a remote RavenBrain instance via its /api/ping endpoint.
   *
   * @return the version string from the X-RavenBrain-Version header
   */
  public String getVersion(String baseUrl) {
    try {
      String url = baseUrl.replaceAll("/+$", "") + "/api/ping";

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(url))
              .GET()
              .build();

      log.debug("Pinging {}", url);
      HttpClient client = HttpClient.newHttpClient();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new RuntimeException(
            "Could not reach source server at " + baseUrl + " (HTTP " + response.statusCode() + ")");
      }

      String version = response.headers().firstValue("X-RavenBrain-Version").orElse(null);
      if (version == null) {
        throw new RuntimeException(
            "Source server did not report a version — it may be too old to support config sync");
      }

      log.debug("Source server version: {}", version);
      return version;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Could not connect to source server at " + baseUrl + ": " + e.getMessage(), e);
    }
  }

  /**
   * Authenticate against a remote RavenBrain instance using its /login endpoint.
   *
   * @return the JWT access_token
   */
  public String authenticate(String baseUrl, String user, String password) {
    try {
      String loginUrl = baseUrl.replaceAll("/+$", "") + "/login";
      String body =
          objectMapper.writeValueAsString(
              new java.util.LinkedHashMap<>() {
                {
                  put("username", user);
                  put("password", password);
                }
              });

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(loginUrl))
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .header("Content-Type", "application/json")
              .build();

      log.debug("Authenticating to {}", loginUrl);
      HttpClient client = HttpClient.newHttpClient();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 401) {
        throw new RuntimeException(
            "Source server credentials do not match — check username and password");
      }
      if (response.statusCode() != 200) {
        throw new RuntimeException(
            "Source server authentication failed (HTTP " + response.statusCode() + ")");
      }

      JsonNode json = objectMapper.readTree(response.body());
      String token = json.get("access_token").asText();
      log.debug("Successfully authenticated to {}", baseUrl);
      return token;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to authenticate to " + baseUrl + ": " + e.getMessage(), e);
    }
  }

  /**
   * Fetch JSON from a remote RavenBrain instance using a Bearer token.
   *
   * @return the response body as a string
   */
  public String fetchJson(String baseUrl, String token, String path) {
    try {
      String url = baseUrl.replaceAll("/+$", "") + path;

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(url))
              .GET()
              .header("Authorization", "Bearer " + token)
              .build();

      log.debug("Fetching {}", url);
      HttpClient client = HttpClient.newHttpClient();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new RuntimeException(
            "Failed to fetch " + path + " (HTTP " + response.statusCode() + "): " + response.body());
      }

      return response.body();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to fetch " + path + ": " + e.getMessage(), e);
    }
  }
}
