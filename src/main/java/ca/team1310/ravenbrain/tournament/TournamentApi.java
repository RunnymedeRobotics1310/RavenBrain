/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.tournament;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-26 07:17
 */
@Controller("/api/tournament")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Slf4j
public class TournamentApi {
  private final TournamentService tournamentService;

  public TournamentApi(TournamentService tournamentService) {
    this.tournamentService = tournamentService;
  }

  @Introspected
  @Serdeable
  record TournamentDTO(String id, String name, LocalDateTime startTime, LocalDateTime endTime) {}

  @Post
  @Consumes(APPLICATION_JSON)
  public void createTournament(@Body TournamentDTO tournamentRecord) {
    log.info("Saving tournament record: {}", tournamentRecord);
    var t = new TournamentRecord();
    t.setId(tournamentRecord.id());
    t.setName(tournamentRecord.name());
    t.setStartTime(tournamentRecord.startTime().toInstant(ZoneOffset.UTC));
    t.setEndTime(tournamentRecord.endTime().toInstant(ZoneOffset.UTC));
    tournamentService.save(t);
  }

  @Get
  @Produces(APPLICATION_JSON)
  public List<TournamentRecord> getTournaments() {
    return tournamentService.findAllSortByStartTime();
  }
}
