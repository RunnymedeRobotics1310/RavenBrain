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
    int teamNumber, int matchCount, GeneralSection general, HopperSection hopper, SwiSection swi) {

  @Serdeable
  public record GeneralSection(
      int breakdownCount,
      int noBreakdownCount,
      double breakdownPercentage,
      List<MatchBreakdown> breakdownMatches,
      List<MatchComment> breakdownNotes,
      List<MatchComment> intakeComments,
      List<MatchComment> shooterComments,
      List<MatchComment> generalComments,
      List<MatchComment> suggestions) {}

  @Serdeable
  public record MatchBreakdown(int matchId, String level, String note, String videoLink) {}

  @Serdeable
  public record MatchComment(int matchId, String level, String note) {}

  @Serdeable
  public record HopperSection(
      LoadingStats loading,
      ShootingView shootingAll,
      ShootingView shootingClose,
      ShootingView shootingMid,
      ShootingView shootingFar,
      ShootingView shootingMoving,
      ShootingView shootingIntaking) {}

  @Serdeable
  public record LoadingStats(
      double avgFillCount,
      double hopperFilledPercentage,
      double maxFillExcludingIntaking,
      double hopperFilledRating,
      List<MatchComment> loadComments,
      List<MatchComment> shootComments) {}

  @Serdeable
  public record ShootingView(
      String filter,
      int sequenceCount,
      List<MatchCycleData> matchCycles,
      List<SequenceShotData> sequenceShots,
      double avgCyclesPerMatch,
      int maxCyclesPerMatch) {}

  @Serdeable
  public record MatchCycleData(
      int matchId,
      String level,
      int cycleCount,
      double totalShots,
      double totalScores,
      double totalMisses,
      double totalStuck) {}

  @Serdeable
  public record SequenceShotData(
      int matchId,
      String level,
      int sequenceIndex,
      int shots,
      int scores,
      int misses,
      int stuck,
      double unloadSeconds,
      double shotsPerSecond,
      double scoresPerSecond) {}

  @Serdeable
  public record SwiSection(
      double avgSequencesPerMatch,
      double avgScoresPerSequence,
      double avgScorePercentPerSequence,
      double avgStuckPerSequence,
      double avgDurationSeconds,
      List<MatchSwiData> perMatch,
      List<MatchComment> stuckComments,
      List<MatchComment> generalComments,
      List<MatchComment> positionComments) {}

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
