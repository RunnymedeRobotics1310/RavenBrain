package ca.team1310.ravenbrain.frcapi.service;

import ca.team1310.ravenbrain.frcapi.model.*;
import ca.team1310.ravenbrain.schedule.ScheduleRecord;
import ca.team1310.ravenbrain.schedule.ScheduleService;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import io.micronaut.context.annotation.Property;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads all of the events for the current season and the last season.
 *
 * <p>NOTE, "Tournament" in this app maps to "EVENT" in the FRC API.
 *
 * <p>TODO: FINISH IMPLEMENTATION
 *
 * @author Tony Field
 * @since 2025-09-21 23:22
 */
@Slf4j
@Singleton
class EventSyncService {
  private final FrcClientService frcClientService;
  private final TournamentService tournamentService;
  private final int teamNumber;
  private final ScheduleService scheduleService;

  EventSyncService(
      FrcClientService frcClientService,
      TournamentService tournamentService,
      @Property(name = "raven-eye.team") int teamNumber,
      ScheduleService scheduleService) {
    this.frcClientService = frcClientService;
    this.tournamentService = tournamentService;
    this.teamNumber = teamNumber;
    this.scheduleService = scheduleService;
  }

  /** Force an immediate synchronization of all tournament and schedule data from the FRC API. */
  void forceSync() {
    long start = System.currentTimeMillis();
    log.info("Force sync: starting");
    loadTournaments();
    int year = Year.now(ZoneOffset.UTC).getValue();
    log.info(
        "Force sync: tournaments loaded in {}s, now loading all schedules for season {}",
        (System.currentTimeMillis() - start) / 1000,
        year);
    loadAllSchedulesForSeason(year);
    log.info(
        "Force sync: complete in {}s", (System.currentTimeMillis() - start) / 1000);
  }

  /**
   * Once a week, check with FIRST for new tournament data and save relevant parts of it to our
   * local repository.
   */
  // [sec] min hr dom mon dow
  @Scheduled(cron = "0 22 * * 1")
  //  @Scheduled(fixedDelay = "1m")
  void loadTournaments() {
    int year = Year.now(ZoneOffset.UTC).getValue();
    while (year >= 2020) { // api versions before 2020 not supported
      log.info("Loading tournaments for year {}", year);
      loadTournamentsForYear(year--);
    }
  }

  private void loadTournamentsForYear(int year) {
    // Load team events — needed to identify team tournaments for scoped schedule sync
    ServiceResponse<EventResponse> teamResp =
        frcClientService.getEventListingsForTeam(year, teamNumber);
    if (teamResp != null) {
      log.info(
          "Year {}: saving {} team events",
          year,
          teamResp.getResponse().events().size());
      saveEvents(year, teamResp);
    }

    // Load ALL events globally for the season
    ServiceResponse<EventResponse> allResp = frcClientService.getEventListings(year);
    if (allResp != null) {
      log.info(
          "Year {}: saving {} global events",
          year,
          allResp.getResponse().events().size());
      saveEvents(year, allResp);
    }
  }

  private void saveEvents(int year, ServiceResponse<EventResponse> resp) {
    for (Event event : resp.getResponse().events()) {
      Instant start = event.dateStart().atZone(ZoneId.of("America/New_York")).toInstant();
      Instant end = event.dateEnd().atZone(ZoneId.of("America/New_York")).toInstant();
      TournamentRecord tournamentRecord =
          new TournamentRecord(year + event.code(), event.code(), year, event.name(), start, end);
      log.trace("Saving tournament {}", tournamentRecord);
      try {
        tournamentService.save(tournamentRecord);
      } catch (DataAccessException e) {
        if (e.getCause() != null
            && e.getCause() instanceof SQLIntegrityConstraintViolationException) {
          tournamentService.update(tournamentRecord);
        } else {
          throw e;
        }
      }
    }
    frcClientService.markProcessed(resp.getId());
  }

  /**
   * Load tournament schedules for the team's tournaments this year and last year. Scoped to team
   * 1310's events only (identified via cached FRC team events response).
   */
  @Scheduled(cron = "0 23 * * 1")
  //  @Scheduled(fixedDelay = "1m")
  void loadAllCurrentTournamentSchedules() {
    log.debug("Loading team tournament schedules for this year and last year");
    int thisYear = Year.now(ZoneOffset.UTC).getValue();
    int lastYear = thisYear - 1;
    var thisYearCodes = frcClientService.peekTeamEventCodes(thisYear, teamNumber);
    var lastYearCodes = frcClientService.peekTeamEventCodes(lastYear, teamNumber);
    for (TournamentRecord tournamentRecord : tournamentService.findAll()) {
      if (tournamentRecord.season() == thisYear
          && thisYearCodes != null
          && thisYearCodes.contains(tournamentRecord.code())) {
        _populateScheduleForTournament(tournamentRecord);
      } else if (tournamentRecord.season() == lastYear
          && lastYearCodes != null
          && lastYearCodes.contains(tournamentRecord.code())) {
        _populateScheduleForTournament(tournamentRecord);
      }
    }
  }

  /** Load schedules for every tournament in the given season. Used by force sync. */
  void loadAllSchedulesForSeason(int year) {
    List<TournamentRecord> seasonTournaments =
        tournamentService.findAll().stream()
            .filter(t -> t.season() == year)
            .toList();
    log.info(
        "Loading schedules for all {} tournaments in season {}", seasonTournaments.size(), year);
    int count = 0;
    for (TournamentRecord tournamentRecord : seasonTournaments) {
      _populateScheduleForTournament(tournamentRecord);
      count++;
      if (count % 25 == 0) {
        log.info(
            "Schedule sync progress: {}/{} tournaments processed", count, seasonTournaments.size());
      }
    }
    log.info("Schedule sync complete: {}/{} tournaments processed", count, seasonTournaments.size());
  }

  /** Every five minutes, load the tournament schedule for the current tournament. */
  @Scheduled(fixedDelay = "3m")
  void loadScheduleForCurrentTournament() {
    log.debug("Loading tournament schedule for current tournament");
    for (TournamentRecord tournamentRecord : tournamentService.findActiveTournaments()) {
      _populateScheduleForTournament(tournamentRecord);
    }
  }

  private void _populateScheduleForTournament(TournamentRecord tournamentRecord) {
    for (var level : TournamentLevel.values()) {
      if (level == TournamentLevel.None) continue;
      ServiceResponse<ScheduleResponse> scheduleResponse =
          frcClientService.getEventSchedule(
              tournamentRecord.season(), tournamentRecord.code(), level);

      if (scheduleResponse == null) continue;

      for (Schedule schedule : scheduleResponse.getResponse().schedule()) {
        int red1 = 0, red2 = 0, red3 = 0, blue1 = 0, blue2 = 0, blue3 = 0;
        for (ScheduleTeam team : schedule.teams()) {
          switch (team.station()) {
            case "Red1" -> red1 = team.teamNumber();
            case "Red2" -> red2 = team.teamNumber();
            case "Red3" -> red3 = team.teamNumber();
            case "Blue1" -> blue1 = team.teamNumber();
            case "Blue2" -> blue2 = team.teamNumber();
            case "Blue3" -> blue3 = team.teamNumber();
          }
        }
        Optional<ScheduleRecord> existingRecord =
            scheduleService.findByTournamentIdAndLevelAndMatch(
                tournamentRecord.id(), level, schedule.matchNumber());
        if (existingRecord.isPresent()) {
          ScheduleRecord scheduleRecord =
              new ScheduleRecord(
                  existingRecord.get().id(),
                  tournamentRecord.id(),
                  level,
                  schedule.matchNumber(),
                  red1,
                  red2,
                  red3,
                  blue1,
                  blue2,
                  blue3);
          scheduleService.update(scheduleRecord);
        } else {
          ScheduleRecord scheduleRecord =
              new ScheduleRecord(
                  0,
                  tournamentRecord.id(),
                  level,
                  schedule.matchNumber(),
                  red1,
                  red2,
                  red3,
                  blue1,
                  blue2,
                  blue3);
          scheduleService.save(scheduleRecord);
        }
      }
      frcClientService.markProcessed(scheduleResponse.getId());
    }
  }
}
