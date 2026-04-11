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
    int teamNumber, int matchCount, GeneralSection general, ShootingSection shooting) {

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
  public record MatchBreakdown(
      String tournamentId, int matchId, String level, String note, String videoLink) {}

  @Serdeable
  public record MatchComment(String tournamentId, int matchId, String level, String note) {}

  /**
   * Intaking-and-scoring section, formerly "hopper". The six shooting views are filtered lenses on
   * the same underlying sequence data. {@code swi} folds in the former top-level SWI section as a
   * summary — shoot-while-intaking is just hopper sequences tagged with the intaking flag, so it
   * lives here alongside the other filters.
   */
  @Serdeable
  public record ShootingSection(
      LoadingStats loading,
      ShootingView shootingAll,
      ShootingView shootingClose,
      ShootingView shootingMid,
      ShootingView shootingFar,
      ShootingView shootingMoving,
      ShootingView shootingIntaking,
      SwiSummary swi) {}

  @Serdeable
  public record LoadingStats(
      double avgFillCount,
      double hopperFilledPercentage,
      double maxFillExcludingIntaking,
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
      String tournamentId,
      int matchId,
      String level,
      int cycleCount,
      double totalShots,
      double totalScores,
      double totalMisses,
      double totalStuck,
      double avgLoadAmount) {}

  @Serdeable
  public record SequenceShotData(
      String tournamentId,
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

  /**
   * Shoot-while-intaking summary. Comments are preserved here for future reporting even though the
   * current UI doesn't surface them; the aggregate metrics describe SWI-filtered sequences only.
   */
  @Serdeable
  public record SwiSummary(
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
      String tournamentId,
      int matchId,
      String level,
      int sequenceCount,
      double totalScores,
      double totalMisses,
      double hitRate,
      double avgDurationSeconds) {}
}
