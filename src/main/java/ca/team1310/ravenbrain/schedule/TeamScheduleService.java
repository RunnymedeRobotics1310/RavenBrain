package ca.team1310.ravenbrain.schedule;

import ca.team1310.ravenbrain.frcapi.model.Schedule;
import ca.team1310.ravenbrain.frcapi.model.ScheduleResponse;
import ca.team1310.ravenbrain.frcapi.model.ScheduleTeam;
import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import ca.team1310.ravenbrain.frcapi.model.year2025.Alliance;
import ca.team1310.ravenbrain.frcapi.model.year2025.MatchScores2025;
import ca.team1310.ravenbrain.frcapi.model.year2025.ScoreData;
import ca.team1310.ravenbrain.frcapi.service.FrcClientService;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import io.micronaut.context.annotation.Property;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Assembles team schedule data by merging DB schedule records with FRC API data (start times,
 * scores, ranking points).
 */
@Slf4j
@Singleton
public class TeamScheduleService {
  private final ScheduleService scheduleService;
  private final FrcClientService frcClientService;
  private final TournamentService tournamentService;
  private final int teamNumber;

  TeamScheduleService(
      ScheduleService scheduleService,
      FrcClientService frcClientService,
      TournamentService tournamentService,
      @Property(name = "raven-eye.team") int teamNumber) {
    this.scheduleService = scheduleService;
    this.frcClientService = frcClientService;
    this.tournamentService = tournamentService;
    this.teamNumber = teamNumber;
  }

  public TeamScheduleResponse getTeamSchedule(String tournamentId) {
    TournamentRecord tournament =
        tournamentService
            .findById(tournamentId)
            .orElseThrow(() -> new IllegalArgumentException("Tournament not found: " + tournamentId));

    List<ScheduleRecord> scheduleRecords =
        scheduleService.findAllByTournamentIdOrderByMatch(tournamentId);

    // Build lookup maps from FRC API cache
    Map<String, Schedule> frcScheduleByKey = new HashMap<>();
    Map<String, ScoreData> frcScoreByKey = new HashMap<>();

    for (TournamentLevel level : TournamentLevel.values()) {
      if (level == TournamentLevel.None) continue;

      ScheduleResponse schedResp =
          frcClientService.peekSchedule(tournament.season(), tournament.code(), level);
      if (schedResp != null && schedResp.schedule() != null) {
        for (Schedule s : schedResp.schedule()) {
          frcScheduleByKey.put(level.name() + ":" + s.matchNumber(), s);
        }
      }

      MatchScores2025 scoresResp =
          frcClientService.peekScores(tournament.season(), tournament.code(), level);
      if (scoresResp != null && scoresResp.scores() != null) {
        for (ScoreData s : scoresResp.scores()) {
          frcScoreByKey.put(s.matchLevel().name() + ":" + s.matchNumber(), s);
        }
      }
    }

    // Merge schedule records with FRC API data
    List<TeamScheduleMatch> matches = new ArrayList<>();
    for (ScheduleRecord rec : scheduleRecords) {
      String key = rec.level().name() + ":" + rec.match();

      // Start time and extra team slots from FRC schedule
      Schedule frcSched = frcScheduleByKey.get(key);
      String startTime = null;
      int red4 = 0, blue4 = 0;
      if (frcSched != null) {
        if (frcSched.startTime() != null) {
          String timeStr = frcSched.startTime().toLocalTime().toString();
          startTime = timeStr.length() >= 5 ? timeStr.substring(0, 5) : timeStr;
        }
        for (ScheduleTeam team : frcSched.teams()) {
          switch (team.station()) {
            case "Red4" -> red4 = team.teamNumber();
            case "Blue4" -> blue4 = team.teamNumber();
          }
        }
      }

      // Scores and RP from FRC score data
      ScoreData scoreData = frcScoreByKey.get(key);
      Integer redScore = null, blueScore = null, redRp = null, blueRp = null;
      int winningAlliance = 0;
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

      matches.add(
          new TeamScheduleMatch(
              rec.level().name(),
              rec.match(),
              startTime,
              rec.red1(),
              rec.red2(),
              rec.red3(),
              red4,
              rec.blue1(),
              rec.blue2(),
              rec.blue3(),
              blue4,
              redScore,
              blueScore,
              redRp,
              blueRp,
              winningAlliance));
    }

    boolean hasPractice = matches.stream().anyMatch(m -> "Practice".equals(m.level()));
    boolean hasQualification = matches.stream().anyMatch(m -> "Qualification".equals(m.level()));
    boolean hasPlayoff = matches.stream().anyMatch(m -> "Playoff".equals(m.level()));

    return new TeamScheduleResponse(
        tournamentId,
        tournament.name(),
        teamNumber,
        hasPractice,
        hasQualification,
        hasPlayoff,
        matches);
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
