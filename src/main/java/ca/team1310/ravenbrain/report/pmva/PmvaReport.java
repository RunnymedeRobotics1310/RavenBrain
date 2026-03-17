package ca.team1310.ravenbrain.report.pmva;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/**
 * Post Match Video Analysis report for the owner team at a tournament.
 *
 * @author Tony Field
 * @since 2026-03-16
 */
@Serdeable
public record PmvaReport(
    int matchCount, GeneralSection general, HopperSection hopper, SwiSection swi) {

  @Serdeable
  public record GeneralSection(
      int breakdownCount,
      int noBreakdownCount,
      double breakdownPercentage,
      List<MatchBreakdown> breakdownMatches,
      List<String> breakdownNotes,
      List<String> intakeComments,
      List<String> shooterComments,
      List<String> generalComments,
      List<String> suggestions) {}

  @Serdeable
  public record MatchBreakdown(int matchId, String level, String note, String videoLink) {}

  @Serdeable
  public record HopperSection(
      LoadingStats loading,
      ShootingStats shootingAll,
      ShootingStats shootingClose,
      ShootingStats shootingMid,
      ShootingStats shootingFar,
      ShootingStats shootingVaried) {}

  @Serdeable
  public record LoadingStats(
      double avgFillCount,
      double maxFillCount,
      double hopperFilledPercentage,
      double avgLoadRating,
      List<String> loadComments) {}

  @Serdeable
  public record ShootingStats(
      String position,
      int sequenceCount,
      List<MatchShootingData> perMatch,
      double avgScorePerMatch,
      double avgHitRate,
      double avgUnloadSeconds,
      double shotsPerSecond,
      double scoresPerSecond,
      double avgStuckPerSequence,
      List<String> stuckComments,
      List<String> generalComments) {}

  @Serdeable
  public record MatchShootingData(
      int matchId,
      String level,
      int unloadRuns,
      double totalScores,
      double totalShots,
      double hitRate) {}

  @Serdeable
  public record SwiSection(
      double avgSequencesPerMatch,
      double avgScoresPerSequence,
      double avgScorePercentPerSequence,
      double avgStuckPerSequence,
      double avgDurationSeconds,
      List<MatchSwiData> perMatch,
      List<String> stuckComments,
      List<String> generalComments,
      List<String> positionComments) {}

  @Serdeable
  public record MatchSwiData(
      int matchId,
      String level,
      int sequenceCount,
      double totalScores,
      double totalMisses,
      double hitRate,
      double avgDurationSeconds) {}
}
