package ca.team1310.ravenbrain.matchstrategy;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import ca.team1310.ravenbrain.connect.User;
import ca.team1310.ravenbrain.connect.UserService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Controller("/api/match-strategy")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Slf4j
public class MatchStrategyApi {

  private final MatchStrategyPlanService planService;
  private final MatchStrategyDrawingService drawingService;
  private final UserService userService;

  public MatchStrategyApi(
      MatchStrategyPlanService planService,
      MatchStrategyDrawingService drawingService,
      UserService userService) {
    this.planService = planService;
    this.drawingService = drawingService;
    this.userService = userService;
  }

  @Serdeable
  public record PlanWithDrawings(MatchStrategyPlan plan, List<MatchStrategyDrawing> drawings) {}

  @Serdeable
  public record PlanUpsertRequest(
      String tournamentId,
      String matchLevel,
      int matchNumber,
      String shortSummary,
      String strategyText) {}

  @Serdeable
  public record DrawingUpsertRequest(
      Long id,
      String tournamentId,
      String matchLevel,
      int matchNumber,
      String label,
      String strokes) {}

  @Get("/{tournamentId}")
  @Secured({"ROLE_DRIVE_TEAM", "ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  @Produces(APPLICATION_JSON)
  public List<PlanWithDrawings> listForTournament(@PathVariable String tournamentId) {
    List<MatchStrategyPlan> plans = planService.findAllForTournament(tournamentId);
    if (plans.isEmpty()) {
      return List.of();
    }
    List<Long> planIds = plans.stream().map(MatchStrategyPlan::id).toList();
    List<MatchStrategyDrawing> drawings =
        drawingService.findAllByPlanIdInListOrderByCreatedAtAsc(planIds);
    Map<Long, List<MatchStrategyDrawing>> byPlan = new HashMap<>();
    for (MatchStrategyDrawing d : drawings) {
      byPlan.computeIfAbsent(d.planId(), k -> new ArrayList<>()).add(d);
    }
    List<PlanWithDrawings> result = new ArrayList<>();
    for (MatchStrategyPlan plan : plans) {
      result.add(new PlanWithDrawings(plan, byPlan.getOrDefault(plan.id(), List.of())));
    }
    return result;
  }

  @Get("/{tournamentId}/{matchLevel}/{matchNumber}")
  @Secured({"ROLE_DRIVE_TEAM", "ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  @Produces(APPLICATION_JSON)
  public HttpResponse<PlanWithDrawings> getOne(
      @PathVariable String tournamentId,
      @PathVariable String matchLevel,
      @PathVariable int matchNumber) {
    Optional<MatchStrategyPlan> plan =
        planService.findByTournamentIdAndMatchLevelAndMatchNumber(
            tournamentId, matchLevel, matchNumber);
    if (plan.isEmpty()) {
      return HttpResponse.notFound();
    }
    List<MatchStrategyDrawing> drawings =
        drawingService.findAllByPlanIdOrderByCreatedAtAsc(plan.get().id());
    return HttpResponse.ok(new PlanWithDrawings(plan.get(), drawings));
  }

  @Post
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public MatchStrategyPlan upsertPlan(
      @Body PlanUpsertRequest req, Authentication authentication) {
    User user = resolveUser(authentication);
    String summary = truncate(req.shortSummary(), 32);
    Instant now = Instant.now();
    Optional<MatchStrategyPlan> existing =
        planService.findByTournamentIdAndMatchLevelAndMatchNumber(
            req.tournamentId(), req.matchLevel(), req.matchNumber());
    MatchStrategyPlan toSave =
        new MatchStrategyPlan(
            existing.map(MatchStrategyPlan::id).orElse(null),
            req.tournamentId(),
            req.matchLevel(),
            req.matchNumber(),
            summary == null ? "" : summary,
            req.strategyText(),
            user.id(),
            user.displayName(),
            now);
    if (existing.isPresent()) {
      return planService.update(toSave);
    } else {
      return planService.save(toSave);
    }
  }

  @Post("/drawing")
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public MatchStrategyDrawing upsertDrawing(
      @Body DrawingUpsertRequest req, Authentication authentication) {
    User user = resolveUser(authentication);
    Instant now = Instant.now();

    // Ensure a plan row exists for this match; create a stub if not.
    Optional<MatchStrategyPlan> planOpt =
        planService.findByTournamentIdAndMatchLevelAndMatchNumber(
            req.tournamentId(), req.matchLevel(), req.matchNumber());
    MatchStrategyPlan plan;
    if (planOpt.isEmpty()) {
      plan =
          planService.save(
              new MatchStrategyPlan(
                  null,
                  req.tournamentId(),
                  req.matchLevel(),
                  req.matchNumber(),
                  "",
                  null,
                  user.id(),
                  user.displayName(),
                  now));
    } else {
      plan = planOpt.get();
    }

    if (req.id() != null) {
      Optional<MatchStrategyDrawing> existing = drawingService.findById(req.id());
      if (existing.isPresent()) {
        MatchStrategyDrawing prev = existing.get();
        // Preserve creator fields; last-write-wins on label / strokes / updated_by.
        MatchStrategyDrawing updated =
            new MatchStrategyDrawing(
                prev.id(),
                prev.planId(),
                req.label(),
                req.strokes(),
                prev.createdByUserId(),
                prev.createdByDisplayName(),
                user.id(),
                user.displayName(),
                prev.createdAt(),
                now);
        return drawingService.update(updated);
      }
      // id supplied but not found — fall through to insert
    }

    MatchStrategyDrawing fresh =
        new MatchStrategyDrawing(
            null,
            plan.id(),
            req.label(),
            req.strokes(),
            user.id(),
            user.displayName(),
            user.id(),
            user.displayName(),
            now,
            now);
    return drawingService.save(fresh);
  }

  @Delete("/drawing/{id}")
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public HttpResponse<?> deleteDrawing(@PathVariable long id) {
    drawingService.deleteById(id);
    return HttpResponse.noContent();
  }

  private User resolveUser(Authentication authentication) {
    return userService
        .findByLogin(authentication.getName())
        .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database"));
  }

  private static String truncate(String s, int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max);
  }
}
