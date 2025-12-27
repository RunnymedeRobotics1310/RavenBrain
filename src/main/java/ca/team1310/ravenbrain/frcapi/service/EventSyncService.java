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
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

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
        for (Event event : resp.getResponse().getEvents()) {
            Instant start = event.getDateStart().atZone(ZoneId.of("America/New_York")).toInstant();
            Instant end = event.getDateEnd().atZone(ZoneId.of("America/New_York")).toInstant();
            TournamentRecord tournamentRecord = new TournamentRecord();
            tournamentRecord.setId(year + event.getCode());
            tournamentRecord.setSeason(year);
            tournamentRecord.setCode(event.getCode());
            tournamentRecord.setName(event.getName());
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

    /**
     * Once a week, load tournament schedules for all tournaments this year.
     */
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

    /**
     * Every five minutes, load the tournament schedule for the current tournament.
     */
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

            for (Schedule schedule : scheduleResponse.getResponse().getSchedule()) {
                ScheduleRecord scheduleRecord = new ScheduleRecord();
                scheduleRecord.setTournamentId(tournamentRecord.getId());
                scheduleRecord.setLevel(level);
                scheduleRecord.setMatch(schedule.getMatchNumber());
                for (ScheduleTeam team : schedule.getTeams()) {
                    switch (team.getStation()) {
                        case "Red1" -> scheduleRecord.setRed1(team.getTeamNumber());
                        case "Red2" -> scheduleRecord.setRed2(team.getTeamNumber());
                        case "Red3" -> scheduleRecord.setRed3(team.getTeamNumber());
                        case "Blue1" -> scheduleRecord.setBlue1(team.getTeamNumber());
                        case "Blue2" -> scheduleRecord.setBlue2(team.getTeamNumber());
                        case "Blue3" -> scheduleRecord.setBlue3(team.getTeamNumber());
                    }
                }
                Optional<ScheduleRecord> existingRecord =
                        scheduleService.findByTournamentIdAndLevelAndMatch(
                                tournamentRecord.getId(), level, schedule.getMatchNumber());
                if (existingRecord.isPresent()) {
                    scheduleRecord.setId(existingRecord.get().getId());
                    scheduleService.update(scheduleRecord);
                } else {
                    scheduleService.save(scheduleRecord);
                }
            }
            frcClientService.markProcessed(scheduleResponse.getId());
        }
    }
}
