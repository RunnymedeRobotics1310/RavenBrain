package ca.team1310.ravenbrain.teamcapability;

import ca.team1310.ravenbrain.report.TournamentAggregates;
import ca.team1310.ravenbrain.report.TournamentAggregatesService;
import ca.team1310.ravenbrain.statboticsapi.service.StatboticsTeamEventRecord;
import ca.team1310.ravenbrain.statboticsapi.service.StatboticsTeamEventRepo;
import ca.team1310.ravenbrain.tbaapi.service.TbaEventOprsRecord;
import ca.team1310.ravenbrain.tbaapi.service.TbaEventOprsRepo;
import ca.team1310.ravenbrain.tournament.TeamTournamentService;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Joins the three capability data sources — TBA OPR, Statbotics EPA, and RavenBrain scouting
 * aggregates — into one list of enriched team rows for a tournament. One batched lookup per
 * source, in-memory join, result cached via {@link TeamCapabilityCache}.
 *
 * <p>Merge rules:
 *
 * <ul>
 *   <li>Roster comes from {@link TeamTournamentService#findTeamNumbersForTournament(String)} — the
 *       stored tournament team list. All teams in the roster emit a row; missing sources are left
 *       null and flagged stale.
 *   <li>OPR / EPA lookups are skipped entirely when the tournament has no {@code tba_event_key}
 *       (unmapped tournaments have scouting signals only; OPR/EPA stale flags are {@code false}
 *       because there is no external source to be stale against).
 *   <li>{@code oprStale = true} when the OPR row is missing or {@code last_status != 200}.
 *   <li>{@code epaStale = true} when the Statbotics row is missing or {@code last_status != 200}.
 *   <li>{@code withdrawn = true} when the team is in the roster but not in the latest Statbotics
 *       response, AND Statbotics returned at least one row for the event (guards against false
 *       positives pre-first-sync).
 *   <li>{@code scoutingCoverage} is event-log-only: {@code "full"} when autoAccuracy,
 *       teleopSuccessRate and pickupAverage are all non-null; {@code "none"} when all three are
 *       null AND quickCommentCount = 0 AND robotAlertCount = 0; {@code "thin"} otherwise.
 * </ul>
 *
 * <p>Default sort: OPR descending, nulls last, withdrawn rows always last regardless of direction.
 */
@Slf4j
@Singleton
public class TeamCapabilityEnricher {

  private final TeamTournamentService teamTournamentService;
  private final TournamentService tournamentService;
  private final TbaEventOprsRepo tbaEventOprsRepo;
  private final StatboticsTeamEventRepo statboticsTeamEventRepo;
  private final TournamentAggregatesService tournamentAggregatesService;

  TeamCapabilityEnricher(
      TeamTournamentService teamTournamentService,
      TournamentService tournamentService,
      TbaEventOprsRepo tbaEventOprsRepo,
      StatboticsTeamEventRepo statboticsTeamEventRepo,
      TournamentAggregatesService tournamentAggregatesService) {
    this.teamTournamentService = teamTournamentService;
    this.tournamentService = tournamentService;
    this.tbaEventOprsRepo = tbaEventOprsRepo;
    this.statboticsTeamEventRepo = statboticsTeamEventRepo;
    this.tournamentAggregatesService = tournamentAggregatesService;
  }

  /**
   * Enrich the full roster for one tournament.
   *
   * @param tournamentId the RB_TOURNAMENT id
   * @return list of per-team capability rows, default-sorted (OPR desc, nulls last, withdrawn
   *     last). Empty list when the tournament has no roster.
   */
  public List<TeamCapabilityResponse> enrich(String tournamentId) {
    List<Integer> roster = teamTournamentService.findTeamNumbersForTournament(tournamentId);
    if (roster.isEmpty()) {
      return List.of();
    }
    Map<Integer, String> teamNames =
        teamTournamentService.findTeamNamesForTournament(tournamentId);

    Optional<TournamentRecord> tournament = tournamentService.findById(tournamentId);
    String tbaEventKey =
        tournament
            .map(TournamentRecord::tbaEventKey)
            .filter(k -> k != null && !k.isBlank())
            .orElse(null);

    Map<Integer, TbaEventOprsRecord> oprByTeam;
    Map<Integer, StatboticsTeamEventRecord> epaByTeam;
    int statboticsRowCount;
    if (tbaEventKey == null) {
      oprByTeam = Map.of();
      epaByTeam = Map.of();
      statboticsRowCount = 0;
    } else {
      oprByTeam = new HashMap<>();
      for (TbaEventOprsRecord r : tbaEventOprsRepo.findByTbaEventKey(tbaEventKey)) {
        oprByTeam.put(r.teamNumber(), r);
      }
      List<StatboticsTeamEventRecord> statboticsRows =
          statboticsTeamEventRepo.findByTbaEventKey(tbaEventKey);
      statboticsRowCount = statboticsRows.size();
      epaByTeam = new HashMap<>();
      for (StatboticsTeamEventRecord r : statboticsRows) {
        epaByTeam.put(r.teamNumber(), r);
      }
    }

    Map<Integer, TournamentAggregates> aggregatesByTeam =
        tournamentAggregatesService.getAggregatesForAllTeams(tournamentId);

    List<TeamCapabilityResponse> out = new ArrayList<>(roster.size());
    for (int team : roster) {
      TbaEventOprsRecord oprRow = oprByTeam.get(team);
      StatboticsTeamEventRecord epaRow = epaByTeam.get(team);
      TournamentAggregates agg =
          aggregatesByTeam.getOrDefault(team, TournamentAggregatesService.emptyFor(team));

      boolean oprStale;
      boolean epaStale;
      if (tbaEventKey == null) {
        // Unmapped tournaments: no external source exists, so "stale" is meaningless. Render the
        // row with scouting signals only and leave the stale flags false.
        oprStale = false;
        epaStale = false;
      } else {
        oprStale = oprRow == null || oprRow.lastStatus() == null || oprRow.lastStatus() != 200;
        epaStale = epaRow == null || epaRow.lastStatus() == null || epaRow.lastStatus() != 200;
      }

      boolean withdrawn = tbaEventKey != null && statboticsRowCount > 0 && epaRow == null;

      String coverage = classifyCoverage(agg);

      out.add(
          new TeamCapabilityResponse(
              team,
              teamNames.get(team),
              oprRow == null ? null : oprRow.opr(),
              oprStale,
              epaRow == null ? null : epaRow.epaTotal(),
              epaRow == null ? null : epaRow.epaAuto(),
              epaRow == null ? null : epaRow.epaTeleop(),
              epaRow == null ? null : epaRow.epaEndgame(),
              epaRow == null ? null : epaRow.epaUnitless(),
              epaRow == null ? null : epaRow.epaNorm(),
              epaStale,
              agg.autoAccuracy(),
              agg.teleopSuccessRate(),
              agg.pickupAverage(),
              agg.quickCommentCount(),
              agg.robotAlertCount(),
              agg.robotAlertMaxSeverity(),
              coverage,
              withdrawn));
    }

    out.sort(DEFAULT_ORDER);
    return List.copyOf(out);
  }

  /**
   * Event-log-only coverage classifier. Comment count and alert count are rendered separately but
   * do not influence this field — callers decide on their own whether to display coverage pills
   * differently in dark mode, etc.
   */
  static String classifyCoverage(TournamentAggregates agg) {
    boolean hasAuto = agg.autoAccuracy() != null;
    boolean hasTeleop = agg.teleopSuccessRate() != null;
    boolean hasPickup = agg.pickupAverage() != null;
    if (hasAuto && hasTeleop && hasPickup) {
      return "full";
    }
    if (!hasAuto
        && !hasTeleop
        && !hasPickup
        && agg.quickCommentCount() == 0
        && agg.robotAlertCount() == 0) {
      return "none";
    }
    return "thin";
  }

  /**
   * Default sort order: withdrawn rows last; then OPR descending with nulls sorted after real
   * values.
   */
  static final Comparator<TeamCapabilityResponse> DEFAULT_ORDER =
      (a, b) -> {
        if (a.withdrawn() != b.withdrawn()) {
          return a.withdrawn() ? 1 : -1;
        }
        Double ao = a.opr();
        Double bo = b.opr();
        if (ao == null && bo == null) {
          return Integer.compare(a.teamNumber(), b.teamNumber());
        }
        if (ao == null) return 1;
        if (bo == null) return -1;
        int cmp = Double.compare(bo, ao); // descending
        if (cmp != 0) return cmp;
        return Integer.compare(a.teamNumber(), b.teamNumber());
      };
}
