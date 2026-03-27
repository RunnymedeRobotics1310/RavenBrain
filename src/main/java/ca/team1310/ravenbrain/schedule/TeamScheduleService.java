package ca.team1310.ravenbrain.schedule;

import ca.team1310.ravenbrain.frcapi.model.TeamListing;
import ca.team1310.ravenbrain.frcapi.model.TeamListingResponse;
import ca.team1310.ravenbrain.frcapi.service.FrcClientService;
import ca.team1310.ravenbrain.tournament.TeamTournamentService;
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
  private final TeamTournamentService teamTournamentService;
  private final FrcClientService frcClientService;
  private final TeamScheduleCache cache;
  private final int teamNumber;

  TeamScheduleService(
      ScheduleService scheduleService,
      TournamentService tournamentService,
      TeamTournamentService teamTournamentService,
      FrcClientService frcClientService,
      TeamScheduleCache cache,
      @Property(name = "raven-eye.team") int teamNumber) {
    this.scheduleService = scheduleService;
    this.tournamentService = tournamentService;
    this.teamTournamentService = teamTournamentService;
    this.frcClientService = frcClientService;
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
    var opt = tournamentService.findById(tournamentId);
    if (opt.isEmpty()) {
      log.debug("Tournament {} not found in DB, returning empty response", tournamentId);
      return new TeamScheduleResponse(
          tournamentId, "Loading...", teamNumber, false, false, false, List.of(), List.of());
    }
    TournamentRecord tournament = opt.get();

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

    Map<Integer, String> teamNames = teamTournamentService.findTeamNamesForTournament(tournamentId);
    if (teamNames.isEmpty()) {
      teamNames = peekTeamNamesFromFrcCache(tournament);
    }
    List<TeamRanking> rankings = buildRankings(matches, teamNames);

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

  private Map<Integer, String> peekTeamNamesFromFrcCache(TournamentRecord tournament) {
    TeamListingResponse resp =
        frcClientService.peekTeamListingsForEvent(tournament.season(), tournament.code());
    if (resp == null || resp.teams() == null) return Map.of();
    Map<Integer, String> names = new HashMap<>();
    for (TeamListing t : resp.teams()) {
      if (t.nameShort() != null) {
        names.put(t.teamNumber(), t.nameShort());
      }
    }
    return names;
  }

  /** Build rankings from qualification matches by summing RP per team. */
  private List<TeamRanking> buildRankings(
      List<TeamScheduleMatch> matches, Map<Integer, String> teamNames) {
    Map<Integer, Integer> rpByTeam = new HashMap<>();
    Map<Integer, Integer> matchesByTeam = new HashMap<>();

    for (TeamScheduleMatch m : matches) {
      if (!"Qualification".equals(m.level()) || m.winningAlliance() == 0) continue;

      int[] redTeams = {m.red1(), m.red2(), m.red3(), m.red4()};
      int[] blueTeams = {m.blue1(), m.blue2(), m.blue3(), m.blue4()};
      Integer redRp = m.redRp() != null ? m.redRp() : 0;
      Integer blueRp = m.blueRp() != null ? m.blueRp() : 0;

      for (int t : redTeams) {
        if (t != 0) {
          rpByTeam.merge(t, redRp, Integer::sum);
          matchesByTeam.merge(t, 1, Integer::sum);
        }
      }
      for (int t : blueTeams) {
        if (t != 0) {
          rpByTeam.merge(t, blueRp, Integer::sum);
          matchesByTeam.merge(t, 1, Integer::sum);
        }
      }
    }

    return rpByTeam.entrySet().stream()
        .map(
            e -> {
              int team = e.getKey();
              int rp = e.getValue();
              int played = matchesByTeam.getOrDefault(team, 1);
              double rs = (double) rp / played;
              String name = teamNames.getOrDefault(team, "");
              return new TeamRanking(team, name, rp, played, Math.round(rs * 100.0) / 100.0);
            })
        .sorted(
            Comparator.comparingDouble(TeamRanking::rs)
                .reversed()
                .thenComparingInt(TeamRanking::teamNumber))
        .toList();
  }

  @Serdeable
  public record TeamRanking(int teamNumber, String teamName, int rp, int matchesPlayed, double rs) {}

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
