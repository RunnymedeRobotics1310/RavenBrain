/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.schedule;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-27 21:42
 */
@Controller("/api/schedule")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Slf4j
public class ScheduleApi {
  private final ScheduleService scheduleService;

  public ScheduleApi(ScheduleService scheduleService) {
    this.scheduleService = scheduleService;
  }

  @Post
  @Consumes(APPLICATION_JSON)
  public void createScheduleItem(@Body ScheduleRecord item) {
    scheduleService.addMatch(item);
  }

  @Get
  @Produces(APPLICATION_JSON)
  public List<ScheduleRecord> getScheduleForTournament(@Parameter int tournamentId) {
    return scheduleService.listScheduleForTournament(tournamentId);
  }
}
