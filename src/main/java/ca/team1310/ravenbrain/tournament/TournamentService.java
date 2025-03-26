/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.tournament;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Tony Field
 * @since 2025-03-26 07:17
 */
@Slf4j
@Singleton
public class TournamentService {
    private final Map<Integer, TournamentRecord> FAKE_REPO = new HashMap<>();
    void addTournament(TournamentRecord tournamentRecord) {
        log.info("Adding tournament record: {}", tournamentRecord);
        // todo: fixme: implement
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
