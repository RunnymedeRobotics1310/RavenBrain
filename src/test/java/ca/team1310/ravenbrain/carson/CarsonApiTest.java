package ca.team1310.ravenbrain.carson;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.connect.Config;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * @author Tony Field
 * @since 2026-02-11 20:26
 */
@MicronautTest // Tells micronaut to set up the test properly (like @Inject)
public class CarsonApiTest {

  @Inject // Tells Micronaut test to construct this as part of the test infrastructure
  @Client("/") // Specifies the base URL for the HTTP client (this is what was missed yesterday)
  HttpClient httpClient;

  @Inject // Inject the wrapper over our config file
  Config config;

  @Test // Identifies this method as a test method that you can run
  void verifyHello() {
    String response = httpClient.toBlocking().retrieve("/api/carson/hello");
    assertEquals("Hello, Carson!", response);
  }

  @Test
  void testSecure() {

    // first check - better fail if not authenticated
    try {
      httpClient.toBlocking().retrieve("/api/carson/secure");
      fail("Should not have been able to retrieve a secured API without logging in");
    } catch (HttpClientResponseException e) {
      if (e.getMessage().equals("Unauthorized")) {
        // good!
      } else {
        fail("Unexpected response: " + e.getMessage());
      }
    }

    // second check - authenticate first
    String response =
        httpClient
            .toBlocking()
            .retrieve(
                HttpRequest.GET("/api/carson/secure").basicAuth("superuser", config.superuser()));

    assertEquals("Hello, Logged In Carson!", response);
  }
}
