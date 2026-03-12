package ca.team1310.ravenbrain.frcapi.service;

import ca.team1310.ravenbrain.frcapi.fetch.FrcClientException;
import ca.team1310.ravenbrain.frcapi.model.Event;
import ca.team1310.ravenbrain.frcapi.model.EventResponse;
import ca.team1310.ravenbrain.frcapi.model.Schedule;
import ca.team1310.ravenbrain.frcapi.model.ScheduleResponse;
import ca.team1310.ravenbrain.frcapi.model.ScheduleTeam;
import ca.team1310.ravenbrain.frcapi.model.TeamListing;
import ca.team1310.ravenbrain.frcapi.model.TeamListingResponse;
import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import ca.team1310.ravenbrain.schedule.ScheduleRecord;
import ca.team1310.ravenbrain.schedule.ScheduleService;
import ca.team1310.ravenbrain.tournament.TeamTournamentService;
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
import java.util.ArrayList;
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
  private final TeamTournamentService teamTournamentService;
  private final int teamNumber;
  private final ScheduleService scheduleService;

  EventSyncService(
      FrcClientService frcClientService,
      TournamentService tournamentService,
      TeamTournamentService teamTournamentService,
      @Property(name = "raven-eye.team") int teamNumber,
      ScheduleService scheduleService) {
    this.frcClientService = frcClientService;
    this.tournamentService = tournamentService;
    this.teamTournamentService = teamTournamentService;
    this.teamNumber = teamNumber;
    this.scheduleService = scheduleService;
  }

  /** Force an immediate synchronization of all tournament and schedule data from the FRC API. */
  void forceSync() {
    long start = System.currentTimeMillis();
    log.info("Force sync: starting — clearing processed flags");
    frcClientService.clearProcessed();
    loadTournaments();
    log.info(
        "Force sync: tournaments loaded in {}s, now loading schedules and teams for active tournaments",
        (System.currentTimeMillis() - start) / 1000);
    for (TournamentRecord t : tournamentService.findUpcomingAndActiveTournaments()) {
      try {
        _populateScheduleForTournament(t);
      } catch (FrcClientException e) {
        log.error("Force sync: failed to fetch schedule for {}: {}", t.id(), e.getMessage());
      }
      loadTeamsForTournament(t);
    }
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
    List<String> allTeamTournamentIds = new ArrayList<>();
    boolean gotFreshTeamData = false;
    while (year >= 2020) { // api versions before 2020 not supported
      log.info("Loading tournaments for year {}", year);
      List<String> yearIds = loadTournamentsForYear(year--);
      if (yearIds != null) {
        allTeamTournamentIds.addAll(yearIds);
        gotFreshTeamData = true;
      }
    }
    if (gotFreshTeamData) {
      teamTournamentService.replaceTeamTournaments(teamNumber, allTeamTournamentIds);
    } else {
      log.info("No fresh team event data from FRC API — preserving existing team tournament IDs");
    }
  }

  /**
   * Load teams for all upcoming/active tournaments from the FRC teams API and populate
   * RB_TEAM_TOURNAMENT with every team registered for each event.
   */
  @Scheduled(cron = "0 22 * * 1")
  void loadTeamsForTournaments() {
    log.info("Loading teams for upcoming and active tournaments");
    for (TournamentRecord tournament : tournamentService.findUpcomingAndActiveTournaments()) {
      loadTeamsForTournament(tournament);
    }
  }

  /**
   * Fetch all teams for a single tournament from the FRC teams API and save them.
   *
   * @param tournament the tournament to load teams for
   */
  void loadTeamsForTournament(TournamentRecord tournament) {
    ServiceResponse<TeamListingResponse> teamsResp =
        frcClientService.getTeamListingsForEvent(tournament.season(), tournament.code());
    if (teamsResp == null) {
      return;
    }
    TeamListingResponse teamsResponse = teamsResp.getResponse();
    if (teamsResponse == null || teamsResponse.teams() == null) {
      log.warn("No teams data for tournament {}", tournament.id());
      frcClientService.markProcessed(teamsResp.getId());
      return;
    }
    List<Integer> teamNumbers =
        teamsResponse.teams().stream().map(TeamListing::teamNumber).toList();
    log.info(
        "Tournament {}: saving {} teams from FRC API",
        tournament.id(),
        teamNumbers.size());
    teamTournamentService.replaceTeamsForTournament(tournament.id(), teamNumbers);
    frcClientService.markProcessed(teamsResp.getId());
  }

  /**
   * @return list of team tournament IDs if fresh team data was fetched, or null if cached/processed
   */
  private List<String> loadTournamentsForYear(int year) {
    List<String> teamTournamentIds = null;

    // Load team events — needed to identify team tournaments for scoped schedule sync
    ServiceResponse<EventResponse> teamResp =
        frcClientService.getEventListingsForTeam(year, teamNumber);
    if (teamResp != null) {
      log.info(
          "Year {}: saving {} team events",
          year,
          teamResp.getResponse().events().size());
      saveEvents(year, teamResp);
      teamTournamentIds = new ArrayList<>();
      for (Event event : teamResp.getResponse().events()) {
        teamTournamentIds.add(year + event.code());
      }
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

    return teamTournamentIds;
  }

  private void saveEvents(int year, ServiceResponse<EventResponse> resp) {
    for (Event event : resp.getResponse().events()) {
      Instant start = event.dateStart().atZone(ZoneId.of("America/New_York")).toInstant();
      Instant end = event.dateEnd().atZone(ZoneId.of("America/New_York")).toInstant();
      TournamentRecord tournamentRecord =
          new TournamentRecord(
              year + event.code(),
              event.code(),
              year,
              event.name(),
              start,
              end,
              event.weekNumber());
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

  /** Every three minutes, load the tournament schedule for upcoming and active tournaments. */
  @Scheduled(fixedDelay = "3m")
  void loadScheduleForCurrentTournament() {
    log.debug("Loading tournament schedule for upcoming and active tournaments");
    for (TournamentRecord tournamentRecord : tournamentService.findUpcomingAndActiveTournaments()) {
      try {
        _populateScheduleForTournament(tournamentRecord);
      } catch (FrcClientException e) {
        log.error(
            "Scheduled sync: failed to fetch schedule for {}: {}",
            tournamentRecord.id(),
            e.getMessage());
      }
    }
  }

  /**
   * Fetch and persist the schedule for a single tournament on demand. Uses the caching client, so
   * repeated calls within the TTL window will not hit the FRC API again.
   *
   * @return true if the tournament was found and schedule fetch was attempted
   */
  boolean fetchScheduleForTournament(String tournamentId) {
    var opt = tournamentService.findById(tournamentId);
    if (opt.isEmpty()) {
      log.warn("On-demand schedule fetch: tournament {} not found", tournamentId);
      return false;
    }
    log.info("On-demand schedule fetch for tournament {}", tournamentId);
    _populateScheduleForTournament(opt.get());
    return true;
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
