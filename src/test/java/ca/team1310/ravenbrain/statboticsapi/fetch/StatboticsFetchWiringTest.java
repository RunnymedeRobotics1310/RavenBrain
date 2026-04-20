package ca.team1310.ravenbrain.statboticsapi.fetch;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.Application;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Smoke-level wiring test for the Statbotics fetch package. Verifies that Micronaut can construct
 * the Statbotics beans against the real configuration (application.yml + application-test.properties)
 * without issuing any HTTP calls.
 *
 * <p>Mirrors {@code TbaFetchWiringTest} but limited to bean-resolution (no DB round-trip) because
 * the {@code RB_STATBOTICS_RESPONSES} table is created by the V35 migration landing in Unit 2. The
 * caching-behavior tests in {@code StatboticsCachingClientTest} mock the repo so they do not
 * depend on the table existing yet either.
 */
@MicronautTest
public class StatboticsFetchWiringTest {

  @Inject StatboticsClient statboticsClient;
  @Inject StatboticsCachingClient statboticsCachingClient;
  @Inject StatboticsRawResponseRepo statboticsRawResponseRepo;

  @Test
  void allStatboticsFetchBeansAreWired() {
    assertNotNull(
        statboticsClient,
        "StatboticsClient bean must be resolvable (verifies base-url + user-agent injection)");
    assertNotNull(
        statboticsCachingClient,
        "StatboticsCachingClient must be resolvable (verifies ttl-seconds injection)");
    assertNotNull(
        statboticsRawResponseRepo,
        "StatboticsRawResponseRepo must be resolvable (verifies RB_STATBOTICS_RESPONSES mapping)");
  }

  @Test
  void baseUrlDefaultIsStatboticsIo() {
    // Default from application.yml — guards against accidental override drift.
    assertEquals("https://api.statbotics.io/", statboticsClient.getBaseUrl());
  }

  @Test
  void ttlDefaultMatchesApplicationYml() {
    // application.yml: raven-eye.statbotics-api.ttl-seconds = 60
    assertEquals(60L, statboticsClient.getTtlSeconds());
  }

  @Test
  void userAgentIdentifiesStratAppAndSubstitutesVersion() {
    // Template "StratApp/{version} (+https://raveneye.team1310.ca)" must resolve {version}.
    String ua = statboticsClient.getUserAgent();
    assertNotNull(ua);
    assertTrue(ua.startsWith("StratApp/"), "User-Agent must start with 'StratApp/' — got: " + ua);
    assertTrue(
        ua.contains(Application.getVersion()),
        "User-Agent must embed the running version '"
            + Application.getVersion()
            + "' — got: "
            + ua);
    assertFalse(
        ua.contains("{version}"),
        "Template placeholder '{version}' must be substituted — got: " + ua);
    assertTrue(
        ua.contains("https://raveneye.team1310.ca"),
        "User-Agent must advertise the RavenEye contact URL — got: " + ua);
  }

  @Test
  void encodePathSegment_roundTripsHyphensAndUnusualCharacters() {
    // Hyphens stay as-is; spaces become %20 (not '+'); '?' and '&' become %3F / %26.
    assertEquals("2026-onto", StatboticsClient.encodePathSegment("2026-onto"));
    assertEquals("event%20key", StatboticsClient.encodePathSegment("event key"));
    assertEquals(
        "weird%3Fkey%26more",
        StatboticsClient.encodePathSegment("weird?key&more"),
        "Characters that would break out of a URL path must be percent-encoded.");
    assertEquals("", StatboticsClient.encodePathSegment(null));
  }
}
