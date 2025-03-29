/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.tournament;

import jakarta.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-26 07:17
 */
@Slf4j
@Singleton
public class TournamentService {
  private final Map<String, TournamentRecord> FAKE_REPO = new HashMap<>();

  public TournamentService() {
    var t = new TournamentRecord();
    t.setId("fake-newmarket");
    t.setName("Fake Newmarket");
    t.setStartTime(Instant.now());
    t.setEndTime(Instant.now().plus(48, ChronoUnit.HOURS));
    FAKE_REPO.put(t.getId(), t);
    t = new TournamentRecord();
    t.setId("fake-centennial");
    t.setName("Fake Centennial");
    t.setStartTime(Instant.now());
    t.setEndTime(Instant.now().plus(48, ChronoUnit.HOURS));
    FAKE_REPO.put(t.getId(), t);
    t = new TournamentRecord();
    t.setId("fake-north-bay");
    t.setName("Fake North Bay");
    t.setStartTime(Instant.now());
    t.setEndTime(Instant.now().plus(48, ChronoUnit.HOURS));
    FAKE_REPO.put(t.getId(), t);
  }

  void addTournament(TournamentRecord tournamentRecord) {
    tournamentRecord.setId(tournamentRecord.getName().toLowerCase(Locale.ROOT).replace(" ", "-"));
    log.info("Adding tournament record: {}", tournamentRecord);
    // todo: fixme: implement
    // table needs to auto-increment the ID field

    FAKE_REPO.put(tournamentRecord.getId(), tournamentRecord);
  }

  List<TournamentRecord> listTournaments() {
    log.info("Listing tournament records");
    // todo: fixme: implement
    return new ArrayList<>(FAKE_REPO.values());
  }

  void updateTournament(TournamentRecord tournamentRecord) {
    log.info("Updating tournament record: {}", tournamentRecord);
    FAKE_REPO.put(tournamentRecord.getId(), tournamentRecord);
    // todo: fixme: implement
  }
}
