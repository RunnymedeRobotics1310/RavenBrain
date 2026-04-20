package ca.team1310.ravenbrain.quickcomment;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import ca.team1310.ravenbrain.report.TournamentAggregatesService;
import ca.team1310.ravenbrain.report.cache.ReportCacheService;
import ca.team1310.ravenbrain.teamcapability.TeamCapabilityCache;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-31 00:28
 */
@Controller("/api/quickcomment")
@Slf4j
public class QuickCommentApi {
  private final QuickCommentService quickCommentService;
  private final TournamentAggregatesService tournamentAggregatesService;
  private final ReportCacheService reportCacheService;
  private final TeamCapabilityCache teamCapabilityCache;

  public QuickCommentApi(
      QuickCommentService quickCommentService,
      TournamentAggregatesService tournamentAggregatesService,
      ReportCacheService reportCacheService,
      TeamCapabilityCache teamCapabilityCache) {
    this.quickCommentService = quickCommentService;
    this.tournamentAggregatesService = tournamentAggregatesService;
    this.reportCacheService = reportCacheService;
    this.teamCapabilityCache = teamCapabilityCache;
  }

  @Serdeable
  public record QuickCommentPostResult(QuickComment comment, boolean success, String reason) {}

  @Post
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public List<QuickCommentPostResult> postComments(@Body List<QuickComment> comments) {
    var result = new ArrayList<QuickCommentPostResult>();
    for (QuickComment record : comments) {
      try {
        record = quickCommentService.save(record);
        result.add(new QuickCommentPostResult(record, true, null));
      } catch (DataAccessException e) {
        if (e.getMessage().contains("Duplicate entry")) {
          log.warn("Duplicate Quick Comment: {}", record);
          result.add(new QuickCommentPostResult(record, true, null));
        } else {
          log.error("Failed to save Quick Comment: {}", record, e);
          result.add(new QuickCommentPostResult(record, false, e.getMessage()));
        }
      } catch (Exception e) {
        log.error("Failed to save quick comment: {}", record, e);
        result.add(new QuickCommentPostResult(record, false, e.getMessage()));
      }
    }
    reportCacheService.invalidateByPrefix("team-summary:");
    tournamentAggregatesService.invalidateAll();
    teamCapabilityCache.invalidateAll();
    return result;
  }

  @Get
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public List<QuickComment> getAll() {
    return quickCommentService.findAllOrderByTeamAndTimestamp();
  }
}
