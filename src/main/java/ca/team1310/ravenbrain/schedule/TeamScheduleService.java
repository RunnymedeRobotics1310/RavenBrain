package ca.team1310.ravenbrain.schedule;

import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import io.micronaut.context.annotation.Property;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Assembles team schedule data from DB schedule records. All score and schedule data is stored in
 * the DB by background sync tasks, so no FRC API calls are needed at request time.
 */
@Slf4j
@Singleton
public class TeamScheduleService {
  private final ScheduleService scheduleService;
  private final TournamentService tournamentService;
  private final TeamScheduleCache cache;
  private final int teamNumber;

  TeamScheduleService(
      ScheduleService scheduleService,
      TournamentService tournamentService,
      TeamScheduleCache cache,
      @Property(name = "raven-eye.team") int teamNumber) {
    this.scheduleService = scheduleService;
    this.tournamentService = tournamentService;
    this.cache = cache;
    this.teamNumber = teamNumber;
  }

  public TeamScheduleResponse getTeamSchedule(String tournamentId) {
    TeamScheduleResponse cached = cache.get(tournamentId);
    if (cached != null) {
      log.debug("Cache hit for tournament {}", tournamentId);
      return cached;
    }
    log.debug("Cache miss for tournament {}, building from DB", tournamentId);
    return buildAndCacheResponse(tournamentId);
  }

  /** Rebuild the cache for a tournament from the DB. Called by background sync tasks. */
  public void refreshCache(String tournamentId) {
    log.debug("Refreshing cache for tournament {}", tournamentId);
    buildAndCacheResponse(tournamentId);
  }

  private TeamScheduleResponse buildAndCacheResponse(String tournamentId) {
    TournamentRecord tournament =
        tournamentService
            .findById(tournamentId)
            .orElseThrow(
                () -> new IllegalArgumentException("Tournament not found: " + tournamentId));

    List<ScheduleRecord> scheduleRecords =
        scheduleService.findAllByTournamentIdOrderByMatch(tournamentId);

    List<TeamScheduleMatch> matches = new ArrayList<>();
    for (ScheduleRecord rec : scheduleRecords) {
      matches.add(
          new TeamScheduleMatch(
              rec.level().name(),
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
              rec.redScore(),
              rec.blueScore(),
              rec.redRp(),
              rec.blueRp(),
              rec.winningAlliance()));
    }

    boolean hasPractice = matches.stream().anyMatch(m -> "Practice".equals(m.level()));
    boolean hasQualification = matches.stream().anyMatch(m -> "Qualification".equals(m.level()));
    boolean hasPlayoff = matches.stream().anyMatch(m -> "Playoff".equals(m.level()));

    TeamScheduleResponse response =
        new TeamScheduleResponse(
            tournamentId,
            tournament.name(),
            teamNumber,
            hasPractice,
            hasQualification,
            hasPlayoff,
            matches);

    cache.put(tournamentId, response);
    return response;
  }

  @Serdeable
  public record TeamScheduleMatch(
      String level,
      int match,
      String startTime,
      int red1,
      int red2,
      int red3,
      int red4,
      int blue1,
      int blue2,
      int blue3,
      int blue4,
      Integer redScore,
      Integer blueScore,
      Integer redRp,
      Integer blueRp,
      int winningAlliance) {}

  @Serdeable
  public record TeamScheduleResponse(
      String tournamentId,
      String tournamentName,
      int teamNumber,
      boolean hasPractice,
      boolean hasQualification,
      boolean hasPlayoff,
      List<TeamScheduleMatch> matches) {}
}
