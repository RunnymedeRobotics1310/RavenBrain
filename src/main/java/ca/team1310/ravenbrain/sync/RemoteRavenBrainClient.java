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

      if (response.statusCode() != 200) {
        throw new RuntimeException(
            "Authentication failed (HTTP " + response.statusCode() + "): " + response.body());
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
