package ca.team1310.ravenbrain.statboticsapi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ca.team1310.ravenbrain.statboticsapi.model.StatboticsTeamEvent;
import ca.team1310.ravenbrain.statboticsapi.model.StatboticsTeamEventBreakdown;
import ca.team1310.ravenbrain.statboticsapi.model.StatboticsTeamEventEpa;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Lowers the {@code raven-eye.statbotics-api.breakdown-json-max-bytes} cap to 32 bytes so the
 * 422 guard fires on ordinary 4-field breakdown JSON (~70-90 bytes serialized). Proves the
 * contract without having to craft a multi-kilobyte JSON payload the model itself would never
 * produce — the cap protects against future upstream bloat, not an intrinsic shape the current
 * model can emit.
 */
@MicronautTest(transactional = false)
@Property(name = "raven-eye.statbotics-api.breakdown-json-max-bytes", value = "32")
public class StatboticsTeamEventSyncServiceCapTest {

  private static final StatboticsClientService CLIENT_MOCK = mock(StatboticsClientService.class);

  @Inject StatboticsTeamEventSyncService syncService;
  @Inject StatboticsTeamEventRepo repo;

  @MockBean(StatboticsClientService.class)
  StatboticsClientService mockClient() {
    return CLIENT_MOCK;
  }

  @BeforeEach
  void setUp() {
    reset(CLIENT_MOCK);
  }

  @AfterEach
  void tearDown() {
    for (StatboticsTeamEventRecord r : repo.findAll()) {
      repo.deleteByTbaEventKey(r.tbaEventKey());
    }
  }

  @Test
  void breakdownAboveCapIsRejectedWith422AndPreservesPrior() {
    String key = "2026sbcap";
    // Seed a prior successful row so we can assert preservation.
    repo.save(
        new StatboticsTeamEventRecord(
            key,
            1310,
            "SBSYNC_PRIOR",
            72.3,
            18.1,
            44.2,
            10.0,
            1600.0,
            1580.0,
            "{\"prior\":\"ok\"}",
            Instant.parse("2026-04-01T12:00:00Z"),
            200));

    StatboticsTeamEvent te =
        new StatboticsTeamEvent(
            1310,
            key,
            new StatboticsTeamEventEpa(
                1999.0, 1987.0, new StatboticsTeamEventBreakdown(99.9, 29.9, 49.9, 19.9)));
    when(CLIENT_MOCK.getTeamEventsByEvent(key))
        .thenReturn(
            StatboticsClientService.TeamEventsFetch.ok(
                new StatboticsClientService.TeamEventsFetchResult(99L, List.of(te))));

    // Cap is 32 bytes; the serialized breakdown will exceed that, so the 422 guard fires.
    int written = syncService.syncOne(key, null);

    // The 422 path is not counted as "written" by the sync return value. It is a preservation
    // path that still persists via upsert to bump the status. Assert the row stayed at the prior
    // values with last_status = 422.
    assertEquals(0, written);
    StatboticsTeamEventRecord saved = repo.find(key, 1310);
    assertEquals(422, saved.lastStatus(), "over-cap row must be marked 422");
    assertEquals(72.3, saved.epaTotal(), 0.0001, "flat columns must keep prior values");
    assertEquals(18.1, saved.epaAuto(), 0.0001);
    assertEquals(
        "{\"prior\":\"ok\"}",
        saved.breakdownJson(),
        "prior breakdown_json must be preserved when the upstream payload exceeds the cap");
  }

  @Test
  void breakdownAboveCapWithNoPriorRowRecords422() {
    String key = "2026sbcapfirst";
    StatboticsTeamEvent te =
        new StatboticsTeamEvent(
            2056,
            key,
            new StatboticsTeamEventEpa(
                1999.0, 1987.0, new StatboticsTeamEventBreakdown(99.9, 29.9, 49.9, 19.9)));
    when(CLIENT_MOCK.getTeamEventsByEvent(key))
        .thenReturn(
            StatboticsClientService.TeamEventsFetch.ok(
                new StatboticsClientService.TeamEventsFetchResult(100L, List.of(te))));

    assertEquals(0, syncService.syncOne(key, null));

    StatboticsTeamEventRecord saved = repo.find(key, 2056);
    assertNotNull(saved, "a 422-marked row is written even without a prior entry");
    assertEquals(422, saved.lastStatus());
    assertNull(saved.epaTotal(), "no prior row means flat columns stay null");
    assertNull(saved.breakdownJson());
  }
}
