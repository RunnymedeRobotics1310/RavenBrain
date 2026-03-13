package ca.team1310.ravenbrain.schedule;

import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import io.micronaut.context.annotation.Property;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    List<TeamRanking> rankings = buildRankings(matches);

    TeamScheduleResponse response =
        new TeamScheduleResponse(
            tournamentId,
            tournament.name(),
            teamNumber,
            hasPractice,
            hasQualification,
            hasPlayoff,
            matches,
            rankings);

    cache.put(tournamentId, response);
    return response;
  }

  /** Build rankings from qualification matches by summing RP per team. */
  private List<TeamRanking> buildRankings(List<TeamScheduleMatch> matches) {
    Map<Integer, Integer> rpByTeam = new HashMap<>();

    for (TeamScheduleMatch m : matches) {
      if (!"Qualification".equals(m.level()) || m.winningAlliance() == 0) continue;

      // Red alliance teams
      int[] redTeams = {m.red1(), m.red2(), m.red3(), m.red4()};
      int[] blueTeams = {m.blue1(), m.blue2(), m.blue3(), m.blue4()};
      Integer redRp = m.redRp() != null ? m.redRp() : 0;
      Integer blueRp = m.blueRp() != null ? m.blueRp() : 0;

      for (int t : redTeams) {
        if (t != 0) rpByTeam.merge(t, redRp, Integer::sum);
      }
      for (int t : blueTeams) {
        if (t != 0) rpByTeam.merge(t, blueRp, Integer::sum);
      }
    }

    return rpByTeam.entrySet().stream()
        .map(e -> new TeamRanking(e.getKey(), e.getValue()))
        .sorted(Comparator.comparingInt(TeamRanking::rp).reversed()
            .thenComparingInt(TeamRanking::teamNumber))
        .toList();
  }

  @Serdeable
  public record TeamRanking(int teamNumber, int rp) {}

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
      List<TeamScheduleMatch> matches,
      List<TeamRanking> rankings) {}
}
