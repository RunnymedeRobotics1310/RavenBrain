package ca.team1310.ravenbrain.frcapi.fetch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ca.team1310.ravenbrain.frcapi.DistrictCode;
import ca.team1310.ravenbrain.frcapi.TournamentLevel;
import ca.team1310.ravenbrain.frcapi.model.FrcDistrictsResponse;
import ca.team1310.ravenbrain.frcapi.model.ScoreParser;
import ca.team1310.ravenbrain.frcapi.model.SeasonSummaryResponse;
import ca.team1310.ravenbrain.frcapi.model.year2025.MatchScores;
import ca.team1310.ravenbrain.frcapi.model.year2025.ScoreData;
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

  private final FrcClientService client;
  private final ScoreParser scoreParser;

  public FrcClientTests(FrcClientService client, ScoreParser scoreParser) {
    this.client = client;
    this.scoreParser = scoreParser;
  }

  @Test
  void testPing() {
    try {
      Assertions.assertTrue(client.ping());
    } catch (Exception e) {
      log.error("testPing", e);
      Assertions.fail(e);
    }
  }

  @Test
  void testGetSeasonSummary() {
    try {
      SeasonSummaryResponse seasonSummary = client.getSeasonSummary(2025);
      assertNotNull(seasonSummary);

    } catch (Exception e) {
      log.error("testGetSeasonSummary", e);
      Assertions.fail(e);
    }
  }

  @Test
  void testGetDistrictListing() {
    try {
      FrcDistrictsResponse districts = client.getDistrictListings(2025);
      assertNotNull(districts);
    } catch (Exception e) {
      log.error("testGetDistrictListing", e);
      Assertions.fail(e);
    }
  }

  @Test
  void testGetEventListing() {
    try {
      var events = client.getEventListingsForDistrict(2025, DistrictCode.ONT);
      assertNotNull(events);
    } catch (Exception e) {
      log.error("testGetEventListing", e);
      Assertions.fail(e);
    }
  }

  @Test
  void testGetEventListingForTeam() {
    try {
      var events = client.getEventListingsForTeam(2025, 1310);
      log.info("testGetEventListingsForDistrict: {}", events);
      assertEquals(7, events.getEvents().size());
      assertNotNull(events);
    } catch (Exception e) {
      log.error("testGetEventListingForTeam", e);
      Assertions.fail(e);
    }
  }

  @Test
  void testGetSchedule() {
    try {
      var schedule = client.getEventSchedule(2025, "ONNOB", TournamentLevel.Qualification);
      log.info("testGetSchedule: {}", schedule);
      assertNotNull(schedule);
    } catch (Exception e) {
      log.error("testGetSchedule", e);
      Assertions.fail(e);
    }
  }

  @Test
  void testGetScheduleForTeam() {
    try {
      var schedule = client.getEventSchedule(2025, "ONSCA2", 1310);
      assertNotNull(schedule);
    } catch (Exception e) {
      log.error("testGetSchedule", e);
      Assertions.fail(e);
    }
  }

  @Test
  void testGetScoreDetails() {
    try {
      var scoreDetails = client.getScoreDetails(2025, "ONSCA2", TournamentLevel.Playoff);
      assertNotNull(scoreDetails);
      scoreDetails = client.getScoreDetails(2025, "ONSCA2", TournamentLevel.Playoff);
      log.info("testGetScoreDetails: {}", scoreDetails);
      assertNotNull(scoreDetails);
      MatchScores scores = scoreParser.parse2025(scoreDetails);
      ScoreData data = scores.getScores().get(1);
      assertEquals(1, data.getWinningAlliance(), "Winning alliance is 1");
    } catch (Exception e) {
      log.error("testGetSchedule", e);
      Assertions.fail(e);
    }
  }
}
