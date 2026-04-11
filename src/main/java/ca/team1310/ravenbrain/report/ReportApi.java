package ca.team1310.ravenbrain.report;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import ca.team1310.ravenbrain.connect.User;
import ca.team1310.ravenbrain.connect.UserService;
import ca.team1310.ravenbrain.eventlog.EventLogService;
import ca.team1310.ravenbrain.eventtype.EventType;
import ca.team1310.ravenbrain.eventtype.EventTypeRepository;
import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import ca.team1310.ravenbrain.report.cache.ReportCacheService;
import ca.team1310.ravenbrain.report.drill.DrillReportService;
import ca.team1310.ravenbrain.report.mega.MegaReport;
import ca.team1310.ravenbrain.report.mega.MegaReportService;
import ca.team1310.ravenbrain.report.pmva.PmvaReport;
import ca.team1310.ravenbrain.report.pmva.PmvaReportService;
import ca.team1310.ravenbrain.report.seq.SequenceReport;
import ca.team1310.ravenbrain.report.seq.SequenceReportService;
import ca.team1310.ravenbrain.report.seq.TournamentSequenceReport;
import ca.team1310.ravenbrain.sequencetype.SequenceTypeService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Tony Field
 * @since 2025-04-04 16:39
 */
@Controller("/api/report")
public class ReportApi {
  private final TeamReportService teamReportService;
  private final DrillReportService drillReportService;
  private final SequenceReportService sequenceReportService;
  private final EventLogService eventLogService;
  private final MegaReportService megaReportService;
  private final EventTypeRepository eventTypeRepository;
  private final UserService userService;
  private final SequenceTypeService sequenceTypeService;
  private final CustomTournamentStatsService customTournamentStatsService;
  private final PmvaReportService pmvaReportService;
  private final ReportCacheService reportCacheService;
  private final ObjectMapper objectMapper;

  public ReportApi(
      TeamReportService teamReportService,
      DrillReportService drillReportService,
      SequenceReportService sequenceReportService,
      EventLogService eventLogService,
      MegaReportService megaReportService,
      EventTypeRepository eventTypeRepository,
      UserService userService,
      SequenceTypeService sequenceTypeService,
      CustomTournamentStatsService customTournamentStatsService,
      PmvaReportService pmvaReportService,
      ReportCacheService reportCacheService,
      ObjectMapper objectMapper) {
    this.teamReportService = teamReportService;
    this.drillReportService = drillReportService;
    this.sequenceReportService = sequenceReportService;
    this.eventLogService = eventLogService;
    this.megaReportService = megaReportService;
    this.eventTypeRepository = eventTypeRepository;
    this.userService = userService;
    this.sequenceTypeService = sequenceTypeService;
    this.customTournamentStatsService = customTournamentStatsService;
    this.pmvaReportService = pmvaReportService;
    this.reportCacheService = reportCacheService;
    this.objectMapper = objectMapper;
  }

  private Set<String> resolveAllowedEventTypes(long sequenceTypeId) {
    if (sequenceTypeId <= 0) return null;
    var seqType = sequenceTypeService.findById(sequenceTypeId).orElse(null);
    if (seqType == null || seqType.events() == null) return null;
    return seqType.events().stream()
        .map(se -> se.eventtype().eventtype())
        .collect(Collectors.toSet());
  }

  @Serdeable
  public record TeamReportResponse(
      TeamReportService.TeamReport report, boolean success, String reason) {}

  @Serdeable
  public record DrillReportResponse(SequenceReport report, boolean success, String reason) {}

  @Serdeable
  public record TournamentSequenceReportResponse(
      TournamentSequenceReport report, boolean success, String reason) {}

  @Serdeable
  public record MegaReportResponse(MegaReport report, boolean success, String reason) {}

  @Serdeable
  public record ChronoReportRow(
      long id,
      String timestamp,
      String level,
      int matchId,
      String eventType,
      String eventTypeName,
      double amount,
      String note,
      String recorder) {}

  @Serdeable
  public record ChronoReportResponse(
      List<ChronoReportRow> rows, boolean success, String reason) {}

  @Serdeable
  public record CustomTournamentStatsResponse(
      List<CustomTournamentStatsService.CustomTournamentStats> stats,
      boolean success,
      String reason) {}

  @Get("/team/teams")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public List<Integer> getTeamReportTeams() {
    return eventLogService.listDistinctTeamNumbers();
  }

  @Get("/team/{teamId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public TeamReportResponse getTeamReport(@PathVariable int teamId) {
    try {
      String cacheKey = "team-summary:" + teamId;
      var cached = reportCacheService.get(cacheKey);
      if (cached.isPresent()) {
        var report = objectMapper.readValue(cached.get(), TeamReportService.TeamReport.class);
        return new TeamReportResponse(report, true, null);
      }
      var report = teamReportService.getTeamReport(teamId);
      reportCacheService.put(cacheKey, objectMapper.writeValueAsString(report));
      return new TeamReportResponse(report, true, null);
    } catch (IOException e) {
      try {
        var report = teamReportService.getTeamReport(teamId);
        return new TeamReportResponse(report, true, null);
      } catch (Exception e2) {
        return new TeamReportResponse(null, false, e2.getMessage());
      }
    } catch (Exception e) {
      return new TeamReportResponse(null, false, e.getMessage());
    }
  }

  @Get("/drill-sessions")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public List<String> getDrillSessions(
      @QueryValue(defaultValue = "0") int team,
      @QueryValue(defaultValue = "0") int year,
      @QueryValue(defaultValue = "0") long sequenceTypeId) {
    if (sequenceTypeId > 0 && team > 0 && year > 0) {
      return drillReportService.listDrillSessionsWithSequences(team, year, sequenceTypeId);
    }
    return drillReportService.listDrillSessions();
  }

  @Get("/drill/{tournamentId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public DrillReportResponse getDrillReport(
      @PathVariable String tournamentId,
      @QueryValue int team,
      @QueryValue int year,
      @QueryValue(defaultValue = "0") long sequenceTypeId) {
    try {
      var report =
          sequenceTypeId > 0
              ? drillReportService.getDrillReport(team, tournamentId, year, sequenceTypeId)
              : drillReportService.getDrillReport(team, tournamentId, year);
      return new DrillReportResponse(report, true, null);
    } catch (Exception e) {
      return new DrillReportResponse(null, false, e.getMessage());
    }
  }

  @Get("/sequence/teams")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public List<Integer> getSequenceTeams() {
    return eventLogService.listDistinctTeamNumbers();
  }

  @Get("/sequence/tournaments")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public List<String> getSequenceTournaments(@QueryValue int team) {
    return eventLogService.listDistinctTournamentIdsByTeamNumber(team);
  }

  @Get("/sequence/tournament/{tournamentId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public TournamentSequenceReportResponse getTournamentSequenceReport(
      @PathVariable String tournamentId,
      @QueryValue int team,
      @QueryValue int year,
      @QueryValue long sequenceTypeId) {
    try {
      var report =
          sequenceReportService.getTournamentSequenceReport(
              team, tournamentId, year, sequenceTypeId);
      return new TournamentSequenceReportResponse(report, true, null);
    } catch (Exception e) {
      return new TournamentSequenceReportResponse(null, false, e.getMessage());
    }
  }

  @Get("/mega/tournaments")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public List<String> getMegaReportTournaments() {
    return megaReportService.listTournamentsWithData();
  }

  @Get("/mega/teams/{tournamentId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public List<Integer> getMegaReportTeams(@PathVariable String tournamentId) {
    return megaReportService.listTeamsForTournament(tournamentId);
  }

  @Get("/mega/{tournamentId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public MegaReportResponse getMegaReport(
      @PathVariable String tournamentId,
      @QueryValue int team,
      @QueryValue int year,
      @QueryValue(defaultValue = "0") long sequenceTypeId) {
    try {
      var allowedEventTypes = resolveAllowedEventTypes(sequenceTypeId);
      var report = megaReportService.generateReport(team, tournamentId, year, allowedEventTypes);
      return new MegaReportResponse(report, true, null);
    } catch (Exception e) {
      return new MegaReportResponse(null, false, e.getMessage());
    }
  }

  @Get("/chrono/{tournamentId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public ChronoReportResponse getChronoReport(
      @PathVariable String tournamentId,
      @QueryValue int team,
      @QueryValue int year,
      @QueryValue(defaultValue = "0") long sequenceTypeId) {
    try {
      var levels = List.of(TournamentLevel.Qualification, TournamentLevel.Playoff);
      var allRecords = eventLogService.listEventsForTournament(team, tournamentId, levels);
      var allowedEventTypes = resolveAllowedEventTypes(sequenceTypeId);
      var records =
          allowedEventTypes != null
              ? allRecords.stream()
                  .filter(r -> allowedEventTypes.contains(r.eventType()))
                  .toList()
              : allRecords;

      Map<String, EventType> eventTypeMap =
          eventTypeRepository.findByFrcyear(year).stream()
              .collect(Collectors.toMap(EventType::eventtype, e -> e));

      // Build user lookup for recorder names
      Map<Long, String> userNameMap =
          records.stream()
              .map(r -> r.userId())
              .distinct()
              .collect(
                  Collectors.toMap(
                      id -> id,
                      id -> {
                        try {
                          return userService.getUser(id).displayName();
                        } catch (Exception e) {
                          return "Unknown";
                        }
                      }));

      var rows =
          records.stream()
              .map(
                  r -> {
                    var et = eventTypeMap.get(r.eventType());
                    return new ChronoReportRow(
                        r.id(),
                        r.timestamp().toString(),
                        r.level().name(),
                        r.matchId(),
                        r.eventType(),
                        et != null ? et.name() : r.eventType(),
                        r.amount(),
                        r.note() != null ? r.note() : "",
                        userNameMap.getOrDefault(r.userId(), "Unknown"));
                  })
              .toList();

      return new ChronoReportResponse(rows, true, null);
    } catch (Exception e) {
      return new ChronoReportResponse(null, false, e.getMessage());
    }
  }

  @Get("/custom-stats")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public CustomTournamentStatsResponse getCustomTournamentStats(@QueryValue int team) {
    try {
      String cacheKey = "custom-stats:" + team;
      var cached = reportCacheService.get(cacheKey);
      if (cached.isPresent()) {
        var stats =
            objectMapper.readValue(
                cached.get(),
                io.micronaut.core.type.Argument.listOf(
                    CustomTournamentStatsService.CustomTournamentStats.class));
        return new CustomTournamentStatsResponse(stats, true, null);
      }
      var stats = customTournamentStatsService.getStatsForTeam(team);
      reportCacheService.put(cacheKey, objectMapper.writeValueAsString(stats));
      return new CustomTournamentStatsResponse(stats, true, null);
    } catch (IOException e) {
      try {
        var stats = customTournamentStatsService.getStatsForTeam(team);
        return new CustomTournamentStatsResponse(stats, true, null);
      } catch (Exception e2) {
        return new CustomTournamentStatsResponse(null, false, e2.getMessage());
      }
    } catch (Exception e) {
      return new CustomTournamentStatsResponse(null, false, e.getMessage());
    }
  }

  // ── PMVA Report ─────────────────────────────────────────────────────────

  @Serdeable
  public record PmvaReportResponse(PmvaReport report, boolean success, String reason) {}

  @Get("/pmva/tournaments")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public List<String> getPmvaTournaments() {
    return pmvaReportService.listTournamentsWithData();
  }

  @Get("/pmva/{tournamentId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public PmvaReportResponse getPmvaReport(@PathVariable String tournamentId) {
    try {
      String cacheKey = "pmva:v5:" + tournamentId;
      var cached = reportCacheService.get(cacheKey);
      if (cached.isPresent()) {
        PmvaReport report = objectMapper.readValue(cached.get(), PmvaReport.class);
        return new PmvaReportResponse(report, true, null);
      }
      var report = pmvaReportService.generate(tournamentId);
      reportCacheService.put(cacheKey, objectMapper.writeValueAsString(report));
      return new PmvaReportResponse(report, true, null);
    } catch (IOException e) {
      // Cache deserialization failed — regenerate
      try {
        var report = pmvaReportService.generate(tournamentId);
        return new PmvaReportResponse(report, true, null);
      } catch (Exception e2) {
        return new PmvaReportResponse(null, false, e2.getMessage());
      }
    } catch (Exception e) {
      return new PmvaReportResponse(null, false, e.getMessage());
    }
  }

  // ── Robot Performance Report (season-wide PMVA aggregation) ─────────────

  @Get("/robot-performance")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public PmvaReportResponse getRobotPerformanceReport() {
    try {
      int season = pmvaReportService.getCurrentSeason();
      String cacheKey =
          "robot-perf:v4:" + pmvaReportService.getOwnerTeamNumber() + ":" + season;
      var cached = reportCacheService.get(cacheKey);
      if (cached.isPresent()) {
        var report = objectMapper.readValue(cached.get(), PmvaReport.class);
        return new PmvaReportResponse(report, true, null);
      }
      var report = pmvaReportService.generateForSeason(season);
      reportCacheService.put(cacheKey, objectMapper.writeValueAsString(report));
      return new PmvaReportResponse(report, true, null);
    } catch (IOException e) {
      // Cache deserialization failed — regenerate
      try {
        int season = pmvaReportService.getCurrentSeason();
        var report = pmvaReportService.generateForSeason(season);
        return new PmvaReportResponse(report, true, null);
      } catch (Exception e2) {
        return new PmvaReportResponse(null, false, e2.getMessage());
      }
    } catch (Exception e) {
      return new PmvaReportResponse(null, false, e.getMessage());
    }
  }

  // ── Report Cache Management ─────────────────────────────────────────────

  @Get("/cache/clear")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public Map<String, Object> clearReportCache() {
    reportCacheService.clearAll();
    return Map.of("success", true);
  }
}
