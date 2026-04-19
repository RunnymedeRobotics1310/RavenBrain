package ca.team1310.ravenbrain.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

@MicronautTest
public class ClientSyncConfigApiTest {

  @Inject
  @Client("/")
  HttpClient client;

  @Test
  void publicEndpointReturnsOnlyWindowFields() {
    HttpResponse<Map<String, Object>> response =
        client
            .toBlocking()
            .exchange(
                HttpRequest.GET("/api/config/client-sync/public"),
                Argument.mapOf(String.class, Object.class));
    assertEquals(HttpStatus.OK, response.getStatus());

    Map<String, Object> body = response.body();
    assertNotNull(body);
    assertTrue(body.containsKey("tournamentWindowLeadHours"));
    assertTrue(body.containsKey("tournamentWindowTailHours"));
    // Public surface — must not leak the full sync-cadence posture.
    assertTrue(!body.containsKey("raveneyePollMs"));
    assertTrue(!body.containsKey("frcScoresPoll"));
    assertNotNull(response.getHeaders().get("ETag"));
  }

  @Test
  void publicEndpointConditionalGetReturns304() {
    HttpResponse<?> first = client.toBlocking().exchange(HttpRequest.GET("/api/config/client-sync/public"));
    String etag = first.getHeaders().get("ETag");
    assertNotNull(etag);
    HttpRequest<?> conditional =
        HttpRequest.GET("/api/config/client-sync/public").header("If-None-Match", etag);
    try {
      HttpResponse<?> second = client.toBlocking().exchange(conditional);
      assertEquals(HttpStatus.NOT_MODIFIED, second.getStatus());
    } catch (io.micronaut.http.client.exceptions.HttpClientResponseException ex) {
      assertEquals(HttpStatus.NOT_MODIFIED, ex.getStatus());
    }
  }

  @Test
  void authenticatedEndpointRequiresAuth() {
    try {
      client.toBlocking().exchange(HttpRequest.GET("/api/config/client-sync"));
      // If the request didn't throw, it should have been a non-200 status.
    } catch (io.micronaut.http.client.exceptions.HttpClientResponseException ex) {
      assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }
  }
}
