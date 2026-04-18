package ca.team1310.ravenbrain.tbaapi.fetch;

import static org.junit.jupiter.api.Assertions.*;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Smoke-level wiring test for the TBA fetch package. Verifies that Micronaut can construct the
 * TBA beans against the real configuration (application.yml + application-test.properties) without
 * attempting to stub or mock HTTP calls — the deeper caching behavior is exercised via Unit 4's
 * sync-service integration tests where the full stack is easier to drive.
 */
@MicronautTest
public class TbaFetchWiringTest {

  @Inject TbaClient tbaClient;
  @Inject TbaCachingClient tbaCachingClient;
  @Inject TbaRawResponseRepo tbaRawResponseRepo;

  @Test
  void allTbaFetchBeansAreWired() {
    assertNotNull(tbaClient, "TbaClient bean must be resolvable (verifies key + base-url injection)");
    assertNotNull(tbaCachingClient, "TbaCachingClient must be resolvable (verifies ttl-seconds injection)");
    assertNotNull(tbaRawResponseRepo, "TbaRawResponseRepo must be resolvable (verifies RB_TBA_RESPONSES schema)");
  }

  @Test
  void responseCacheCanRoundTripNullableBody() {
    // Regression guard: body is nullable in the schema (304 responses store NULL body).
    var row =
        new TbaRawResponse(
            null,
            java.time.Instant.parse("2026-04-01T12:00:00Z"),
            java.time.Instant.parse("2026-04-01T12:00:00Z"),
            "etag-xyz",
            false,
            304,
            "test/wiring",
            null);
    var saved = tbaRawResponseRepo.save(row);
    try {
      var fetched = tbaRawResponseRepo.find("test/wiring");
      assertNotNull(fetched);
      assertEquals(304, fetched.statuscode());
      assertNull(fetched.body(), "null body must round-trip through the DB");
      assertEquals("etag-xyz", fetched.etag());
    } finally {
      tbaRawResponseRepo.deleteById(saved.id());
    }
  }
}
