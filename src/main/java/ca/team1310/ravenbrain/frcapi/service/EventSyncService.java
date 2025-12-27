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
      loadTournamentsForYear(year--);
    }
  }

  private void loadTournamentsForYear(int year) {
    log.debug("Loading tournaments for year {}", year);
    ServiceResponse<EventResponse> resp =
        frcClientService.getEventListingsForTeam(year, teamNumber);
    if (resp == null) return;
    for (Event event : resp.getResponse().events()) {
      Instant start = event.dateStart().atZone(ZoneId.of("America/New_York")).toInstant();
      Instant end = event.dateEnd().atZone(ZoneId.of("America/New_York")).toInstant();
      TournamentRecord tournamentRecord = new TournamentRecord();
      tournamentRecord.setId(year + event.code());
      tournamentRecord.setSeason(year);
      tournamentRecord.setCode(event.code());
      tournamentRecord.setName(event.name());
      tournamentRecord.setStartTime(start);
      tournamentRecord.setEndTime(end);
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

  /** Once a week, load tournament schedules for all tournaments this year. */
  @Scheduled(cron = "0 23 * * 1")
  //  @Scheduled(fixedDelay = "1m")
  void loadAllTournamentSchedulesForThisYear() {
    log.debug("Loading all tournament schedules for this year");
    int thisYear = Year.now(ZoneOffset.UTC).getValue();
    for (TournamentRecord tournamentRecord : tournamentService.findAll()) {
      if (tournamentRecord.getSeason() == thisYear) {
        _populateScheduleForTournament(tournamentRecord);
      }
    }
  }

  /** Every five minutes, load the tournament schedule for the current tournament. */
  @Scheduled(fixedDelay = "3m")
  void loadScheduleForCurrentTournament() {
    log.debug("Loading tournament schedule for current tournament");
    for (TournamentRecord tournamentRecord : tournamentService.findCurrentTournaments()) {
      _populateScheduleForTournament(tournamentRecord);
    }
  }

  private void _populateScheduleForTournament(TournamentRecord tournamentRecord) {
    for (var level : TournamentLevel.values()) {
      if (level == TournamentLevel.None) continue;
      ServiceResponse<ScheduleResponse> scheduleResponse =
          frcClientService.getEventSchedule(
              tournamentRecord.getSeason(), tournamentRecord.getCode(), level);

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
                tournamentRecord.getId(), level, schedule.matchNumber());
        if (existingRecord.isPresent()) {
          ScheduleRecord scheduleRecord =
              new ScheduleRecord(
                  existingRecord.get().id(),
                  tournamentRecord.getId(),
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
                  tournamentRecord.getId(),
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
