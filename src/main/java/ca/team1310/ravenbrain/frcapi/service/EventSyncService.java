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
import ca.team1310.ravenbrain.frcapi.model.year2025.Alliance;
import ca.team1310.ravenbrain.frcapi.model.year2025.MatchScores2025;
import ca.team1310.ravenbrain.frcapi.model.year2025.ScoreData;
import ca.team1310.ravenbrain.schedule.ScheduleRecord;
import ca.team1310.ravenbrain.schedule.ScheduleService;
import ca.team1310.ravenbrain.schedule.TeamScheduleService;
import ca.team1310.ravenbrain.tournament.TeamTournamentService;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import ca.team1310.ravenbrain.tournament.WatchedTournamentService;
import io.micronaut.context.annotation.Property;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  private final WatchedTournamentService watchedTournamentService;
  private final int teamNumber;
  private final ScheduleService scheduleService;
  private final TeamScheduleService teamScheduleService;

  EventSyncService(
      FrcClientService frcClientService,
      TournamentService tournamentService,
      TeamTournamentService teamTournamentService,
      WatchedTournamentService watchedTournamentService,
      @Property(name = "raven-eye.team") int teamNumber,
      ScheduleService scheduleService,
      TeamScheduleService teamScheduleService) {
    this.frcClientService = frcClientService;
    this.tournamentService = tournamentService;
    this.teamTournamentService = teamTournamentService;
    this.watchedTournamentService = watchedTournamentService;
    this.teamNumber = teamNumber;
    this.scheduleService = scheduleService;
    this.teamScheduleService = teamScheduleService;
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
        teamScheduleService.refreshCache(t.id());
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
      watchedTournamentService.watchAll(allTeamTournamentIds);
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
    Map<Integer, String> teamNames =
        teamsResponse.teams().stream()
            .filter(t -> t.nameShort() != null)
            .collect(java.util.stream.Collectors.toMap(
                TeamListing::teamNumber, TeamListing::nameShort, (a, b) -> a));
    log.info(
        "Tournament {}: saving {} teams from FRC API",
        tournament.id(),
        teamNumbers.size());
    teamTournamentService.replaceTeamsForTournament(tournament.id(), teamNumbers, teamNames);
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

  private List<TournamentRecord> getOwnerTeamActiveTournaments() {
    Set<String> ownerTournamentIds =
        Set.copyOf(teamTournamentService.findTournamentIdsForTeam(teamNumber));
    return tournamentService.findUpcomingAndActiveTournaments().stream()
        .filter(t -> ownerTournamentIds.contains(t.id()))
        .toList();
  }

  /**
   * Returns active tournaments that should be synced: owner team tournaments plus any watched
   * tournaments.
   */
  private List<TournamentRecord> getActiveTournamentsToSync() {
    Set<String> ownerIds =
        Set.copyOf(teamTournamentService.findTournamentIdsForTeam(teamNumber));
    Set<String> watchedIds = watchedTournamentService.getWatchedTournamentIds();
    return tournamentService.findUpcomingAndActiveTournaments().stream()
        .filter(t -> ownerIds.contains(t.id()) || watchedIds.contains(t.id()))
        .toList();
  }

  /**
   * Every three minutes, load the tournament schedule for active tournaments (owner team +
   * watched).
   */
  @Scheduled(fixedDelay = "3m")
  void loadScheduleForCurrentTournament() {
    log.info("Loading tournament schedule for active tournaments (owner team + watched)");
    for (TournamentRecord tournamentRecord : getActiveTournamentsToSync()) {
      log.info("Syncing schedule for tournament {} (season={}, code={})",
          tournamentRecord.id(), tournamentRecord.season(), tournamentRecord.code());
      try {
        _populateScheduleForTournament(tournamentRecord);
        teamScheduleService.refreshCache(tournamentRecord.id());
      } catch (FrcClientException e) {
        log.error(
            "Scheduled sync: failed to fetch schedule for {}: {}",
            tournamentRecord.id(),
            e.getMessage());
      }
    }
  }

  /** Every 30 seconds, sync scores for active tournaments (owner team + watched). */
  @Scheduled(fixedDelay = "30s")
  void syncScoresForActiveTournaments() {
    for (TournamentRecord tournament : getActiveTournamentsToSync()) {
      boolean updated = false;
      for (TournamentLevel level : TournamentLevel.values()) {
        if (level == TournamentLevel.None || level == TournamentLevel.Practice) continue;

        if (isLevelScoreSyncComplete(tournament, level)) {
          log.debug("Skipping {} score sync for {} — level complete", level, tournament.id());
          continue;
        }

        MatchScores2025 scoresResp =
            frcClientService.peekScores(tournament.season(), tournament.code(), level);
        if (scoresResp == null || scoresResp.scores() == null) continue;

        for (ScoreData scoreData : scoresResp.scores()) {
          Optional<ScheduleRecord> existing =
              scheduleService.findByTournamentIdAndLevelAndMatch(
                  tournament.id(), scoreData.matchLevel(), scoreData.matchNumber());
          if (existing.isEmpty()) continue;

          ScheduleRecord rec = existing.get();
          Integer redScore = null, blueScore = null, redRp = null, blueRp = null;
          int winningAlliance = scoreData.winningAlliance();

          if (scoreData.alliances() != null) {
            for (Alliance a : scoreData.alliances()) {
              if ("Red".equalsIgnoreCase(a.alliance())) {
                redScore = a.totalPoints();
                redRp = a.rp();
              } else if ("Blue".equalsIgnoreCase(a.alliance())) {
                blueScore = a.totalPoints();
                blueRp = a.rp();
              }
            }
          }

          // Only update if scores have changed
          if (!equals(rec.redScore(), redScore)
              || !equals(rec.blueScore(), blueScore)
              || !equals(rec.redRp(), redRp)
              || !equals(rec.blueRp(), blueRp)
              || rec.winningAlliance() != winningAlliance) {
            ScheduleRecord updatedRecord =
                new ScheduleRecord(
                    rec.id(),
                    rec.tournamentId(),
                    rec.level(),
                    rec.match(),
                    rec.startTime(),
                    rec.red1(),
                    rec.red2(),
                    rec.red3(),
                    rec.red4(),
                    rec.blue1(),
                    rec.blue2(),
                    rec.blue3(),
                    rec.blue4(),
                    redScore,
                    blueScore,
                    redRp,
                    blueRp,
                    winningAlliance);
            scheduleService.update(updatedRecord);
            updated = true;
          }
        }
      }
      if (updated) {
        teamScheduleService.refreshCache(tournament.id());
      }
    }
  }

  private static boolean equals(Object a, Object b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    return a.equals(b);
  }

  /**
   * Check if score syncing is complete for a tournament level. Qualification is complete when all
   * scheduled matches have scores. Playoff is complete when (a) the last scheduled match has a
   * score, or (b) the second-to-last match has a score and 2+ hours have passed since the
   * tournament's scheduled end time (the final 3 playoff matches are best-of-3, so the last match
   * may be skipped).
   */
  private boolean isLevelScoreSyncComplete(TournamentRecord tournament, TournamentLevel level) {
    if (level == TournamentLevel.Qualification) {
      long unscored =
          scheduleService.countUnscoredByTournamentIdAndLevel(tournament.id(), level);
      return unscored == 0
          && scheduleService.countScoredQualificationByTournamentId(tournament.id()) > 0;
    }
    if (level == TournamentLevel.Playoff) {
      List<ScheduleRecord> lastTwo = scheduleService.findLastTwoPlayoffMatches(tournament.id());
      if (lastTwo.isEmpty()) return false;
      // Last match has a score — playoffs are fully complete
      if (lastTwo.get(0).redScore() != null) return true;
      // Second-to-last match is scored and 2+ hours past tournament end — final was skipped
      if (lastTwo.size() >= 2
          && lastTwo.get(1).redScore() != null
          && Instant.now().isAfter(tournament.endTime().plus(2, ChronoUnit.HOURS))) {
        return true;
      }
      return false;
    }
    return false;
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
    teamScheduleService.refreshCache(tournamentId);
    return true;
  }

  private void _populateScheduleForTournament(TournamentRecord tournamentRecord) {
    // Once qualification scores exist, Practice schedule is static — skip fetching it
    boolean qualsHaveScores =
        scheduleService.countScoredQualificationByTournamentId(tournamentRecord.id()) > 0;

    for (var level : TournamentLevel.values()) {
      if (level == TournamentLevel.None) continue;
      if (level == TournamentLevel.Practice && qualsHaveScores) {
        log.debug("Skipping Practice schedule fetch for {} — quals have scores",
            tournamentRecord.id());
        continue;
      }
      ScheduleResponse scheduleData =
          frcClientService.peekSchedule(
              tournamentRecord.season(), tournamentRecord.code(), level);

      if (scheduleData == null || scheduleData.schedule() == null) {
        log.info("No schedule response for {} {}", tournamentRecord.code(), level);
        continue;
      }
      log.info("Got {} {} matches for {} {}", scheduleData.schedule().size(),
          level, tournamentRecord.code(),
          scheduleData.schedule().isEmpty() ? "" :
              "firstStartTime=" + scheduleData.schedule().get(0).startTime());

      // Build a score lookup map for this level (Practice has no scores in FRC API)
      Map<Integer, ScoreData> scoresByMatch = new HashMap<>();
      if (level != TournamentLevel.Practice) {
        MatchScores2025 scoresResp =
            frcClientService.peekScores(
                tournamentRecord.season(), tournamentRecord.code(), level);
        if (scoresResp != null && scoresResp.scores() != null) {
          for (ScoreData sd : scoresResp.scores()) {
            scoresByMatch.put(sd.matchNumber(), sd);
          }
        }
      }

      for (Schedule schedule : scheduleData.schedule()) {
        int red1 = 0, red2 = 0, red3 = 0, red4 = 0, blue1 = 0, blue2 = 0, blue3 = 0, blue4 = 0;
        for (ScheduleTeam team : schedule.teams()) {
          switch (team.station()) {
            case "Red1" -> red1 = team.teamNumber();
            case "Red2" -> red2 = team.teamNumber();
            case "Red3" -> red3 = team.teamNumber();
            case "Red4" -> red4 = team.teamNumber();
            case "Blue1" -> blue1 = team.teamNumber();
            case "Blue2" -> blue2 = team.teamNumber();
            case "Blue3" -> blue3 = team.teamNumber();
            case "Blue4" -> blue4 = team.teamNumber();
          }
        }

        // Extract start time
        String startTime = null;
        if (schedule.startTime() != null) {
          String timeStr = schedule.startTime().toLocalTime().toString();
          startTime = timeStr.length() >= 5 ? timeStr.substring(0, 5) : timeStr;
        }

        // Extract score data
        Integer redScore = null, blueScore = null, redRp = null, blueRp = null;
        int winningAlliance = 0;
        ScoreData scoreData = scoresByMatch.get(schedule.matchNumber());
        if (scoreData != null && scoreData.alliances() != null) {
          winningAlliance = scoreData.winningAlliance();
          for (Alliance a : scoreData.alliances()) {
            if ("Red".equalsIgnoreCase(a.alliance())) {
              redScore = a.totalPoints();
              redRp = a.rp();
            } else if ("Blue".equalsIgnoreCase(a.alliance())) {
              blueScore = a.totalPoints();
              blueRp = a.rp();
            }
          }
        }

        Optional<ScheduleRecord> existingRecord =
            scheduleService.findByTournamentIdAndLevelAndMatch(
                tournamentRecord.id(), level, schedule.matchNumber());
        if (existingRecord.isPresent()) {
          // Preserve existing start time if the new one is null (FRC API may omit it after event)
          String effectiveStartTime =
              startTime != null ? startTime : existingRecord.get().startTime();
          ScheduleRecord scheduleRecord =
              new ScheduleRecord(
                  existingRecord.get().id(),
                  tournamentRecord.id(),
                  level,
                  schedule.matchNumber(),
                  effectiveStartTime,
                  red1,
                  red2,
                  red3,
                  red4,
                  blue1,
                  blue2,
                  blue3,
                  blue4,
                  redScore,
                  blueScore,
                  redRp,
                  blueRp,
                  winningAlliance);
          scheduleService.update(scheduleRecord);
        } else {
          ScheduleRecord scheduleRecord =
              new ScheduleRecord(
                  0,
                  tournamentRecord.id(),
                  level,
                  schedule.matchNumber(),
                  startTime,
                  red1,
                  red2,
                  red3,
                  red4,
                  blue1,
                  blue2,
                  blue3,
                  blue4,
                  redScore,
                  blueScore,
                  redRp,
                  blueRp,
                  winningAlliance);
          scheduleService.save(scheduleRecord);
        }
      }
    }
  }
}
