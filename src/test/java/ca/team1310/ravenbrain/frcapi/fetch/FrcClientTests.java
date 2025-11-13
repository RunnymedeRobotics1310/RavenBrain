package ca.team1310.ravenbrain.frcapi.fetch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ca.team1310.ravenbrain.frcapi.DistrictCode;
import ca.team1310.ravenbrain.frcapi.TournamentLevel;
import ca.team1310.ravenbrain.frcapi.model.EventResponse;
import ca.team1310.ravenbrain.frcapi.model.FrcDistrictsResponse;
import ca.team1310.ravenbrain.frcapi.model.SeasonSummaryResponse;
import ca.team1310.ravenbrain.frcapi.model.year2025.MatchScores2025;
import ca.team1310.ravenbrain.frcapi.model.year2025.ScoreData;
import ca.team1310.ravenbrain.frcapi.service.FrcClientService;
import ca.team1310.ravenbrain.frcapi.service.ServiceResponse;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author Tony Field
 * @since 2025-09-21 13:00
 */
@MicronautTest(rollback = false)
@Slf4j
public class FrcClientTests {

  private final FrcCachingClient frcCachingClient;
  private final FrcClientService service;

  public FrcClientTests(
      FrcCachingClient frcCachingClient, FrcClientService service) {
    this.frcCachingClient = frcCachingClient;
    this.service = service;
  }

  @Test
  void testPing() {
    try {
      Assertions.assertTrue(service.ping());
    } catch (Exception e) {
      log.error("testPing", e);
      Assertions.fail(e);
    }
  }

  @Test
  void testGetSeasonSummary() {
    try {
      frcCachingClient.clearProcessed();
      ServiceResponse<SeasonSummaryResponse> seasonSummary = service.getSeasonSummary(2025);
      assertNotNull(seasonSummary);

    } catch (Exception e) {
      log.error("testGetSeasonSummary", e);
      Assertions.fail(e);
    }
  }

  @Test
  void testGetDistrictListing() {
    try {
      frcCachingClient.clearProcessed();
      ServiceResponse<FrcDistrictsResponse> districts = service.getDistrictListings(2025);
      assertNotNull(districts);
    } catch (Exception e) {
      log.error("testGetDistrictListing", e);
      Assertions.fail(e);
    }
  }

  @Test
  void testGetEventListing() {
    try {
      frcCachingClient.clearProcessed();
      var events = service.getEventListingsForDistrict(2025, DistrictCode.ONT);
      assertNotNull(events);
    } catch (Exception e) {
      log.error("testGetEventListing", e);
      Assertions.fail(e);
    }
  }

  @Test
  void testGetEventListingForTeam() {
    try {
      frcCachingClient.clearProcessed();
      ServiceResponse<EventResponse> events = service.getEventListingsForTeam(2025, 1310);
      assertEquals(7, events.getResponse().getEvents().size());
      assertNotNull(events);
    } catch (Exception e) {
      log.error("testGetEventListingForTeam", e);
      Assertions.fail(e);
    }
  }

  @Test
  void testGetSchedule() {
    try {
      frcCachingClient.clearProcessed();
      var schedule = service.getEventSchedule(2025, "ONNOB", TournamentLevel.Qualification);
      assertNotNull(schedule);
    } catch (Exception e) {
      log.error("testGetSchedule", e);
      Assertions.fail(e);
    }
  }

  @Test
  void testGetScheduleForTeam() {
    try {
      frcCachingClient.clearProcessed();
      var schedule = service.getEventSchedule(2025, "ONSCA2", 1310);
      assertNotNull(schedule);
    } catch (Exception e) {
      log.error("testGetSchedule", e);
      Assertions.fail(e);
    }
  }

  @Test
  void testGetScoreDetails() {
    try {
      frcCachingClient.clearProcessed();
      ServiceResponse<MatchScores2025> sr =
          service.get2025Scores("ONSCA2", TournamentLevel.Playoff);
      assertNotNull(sr);
      MatchScores2025 scores = sr.getResponse();
      assertNotNull(scores);
      ScoreData data = scores.getScores().get(1);

      assertEquals(1, data.getWinningAlliance(), "Winning alliance is 1");
    } catch (Exception e) {
      log.error("testGetSchedule", e);
      Assertions.fail(e);
    }
  }
}
