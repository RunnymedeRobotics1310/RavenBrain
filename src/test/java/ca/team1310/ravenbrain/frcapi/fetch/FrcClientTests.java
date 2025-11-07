/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.frcapi.fetch;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import ca.team1310.ravenbrain.frcapi.DistrictCode;
import ca.team1310.ravenbrain.frcapi.TournamentLevel;
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

  public FrcClientTests(FrcClientService client) {
    this.client = client;
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
      String seasonSummary = client.getSeasonSummary(2025);
      assertNotNull(seasonSummary);
    } catch (Exception e) {
      log.error("testGetSeasonSummary", e);
      Assertions.fail(e);
    }
  }

  @Test
  void testGetDistrictListing() {
    try {
      String districts = client.getDistrictListings(2025);
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
      Assertions.assertEquals(7, events.getEvents().size());
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
    } catch (Exception e) {
      log.error("testGetSchedule", e);
      Assertions.fail(e);
    }
  }
}
