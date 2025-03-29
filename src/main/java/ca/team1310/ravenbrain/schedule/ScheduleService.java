/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.schedule;

import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-23 22:59
 */
@Slf4j
@Singleton
public class ScheduleService {
  private final List<ScheduleRecord> FAKE_REPO = new ArrayList<>();

  public ScheduleService() {
    var t = new ScheduleRecord();
    t.setTournamentId("fake-newmarket");
    t.setMatch(1);
    t.setRed1(1310);
    t.setRed2(9262);
    t.setRed3(865);
    t.setBlue1(2056);
    t.setBlue2(1114);
    t.setBlue3(1690);
    FAKE_REPO.add(t);

    t = new ScheduleRecord();
    t.setTournamentId("fake-newmarket");
    t.setMatch(2);
    t.setRed1(1310);
    t.setRed2(9262);
    t.setRed3(865);
    t.setBlue1(2056);
    t.setBlue2(1114);
    t.setBlue3(1690);
    FAKE_REPO.add(t);

    t = new ScheduleRecord();
    t.setTournamentId("fake-newmarket");
    t.setMatch(3);
    t.setRed1(1310);
    t.setRed2(9262);
    t.setRed3(865);
    t.setBlue1(2056);
    t.setBlue2(1114);
    t.setBlue3(1690);
    FAKE_REPO.add(t);
  }

  void addMatch(ScheduleRecord record) {
    log.info("Adding match: {}", record);
    // todo: fixme: implement
    // table needs to NOT auto-increment the ID field

    FAKE_REPO.add(record);
  }

  List<ScheduleRecord> listScheduleForTournament(String id) {
    log.info("Listing schedule for tournament {}", id);
    // todo: fixme: implement
    return FAKE_REPO.stream()
        .filter(record -> record.getTournamentId() == id)
        .collect(Collectors.toList());
  }
}
