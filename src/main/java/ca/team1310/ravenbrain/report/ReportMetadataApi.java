package ca.team1310.ravenbrain.report;

import ca.team1310.ravenbrain.http.ResponseEtags;
import ca.team1310.ravenbrain.report.cache.CachekeyCreated;
import ca.team1310.ravenbrain.report.cache.ReportCacheService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.transaction.annotation.Transactional;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Metadata view over {@code RB_REPORT_CACHE}. RavenEye's reports-in-IndexedDB layer (Unit 6)
 * syncs this endpoint on the same cadence as schedules/scores while a tournament window is
 * active; the client diffs the returned tuples against its locally-cached report bodies and
 * re-pulls bodies whose {@code created} timestamp has advanced server-side.
 *
 * <p>Season-wide intentionally: several cache keys in the existing schema are not
 * tournament-scoped ({@code team-summary:}, {@code robot-perf:v4:}, {@code custom-stats:}).
 * Returning the full table is tiny (one row per cached report) and avoids a schema change.
 *
 * <p>Role-gated to {@code EXPERTSCOUT/ADMIN/SUPERUSER} — the same gate as the existing report
 * endpoints these metadata tuples point at.
 *
 * <p>Weak ETag uses {@code MAX(created)} across the cache. Every scout event that invalidates a
 * report bumps the underlying row; the next read rebuilds the cache with a new {@code created};
 * a client with the older ETag receives a 200 and picks up the change. Nothing changed →
 * {@code MAX(created)} unchanged → 304.
 */
@Slf4j
@Controller("/api/report/metadata")
@Secured({"ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
public class ReportMetadataApi {

  private final ReportCacheService reportCacheService;

  public ReportMetadataApi(ReportCacheService reportCacheService) {
    this.reportCacheService = reportCacheService;
  }

  @Get
  @Produces(MediaType.APPLICATION_JSON)
  @Transactional(readOnly = true)
  public HttpResponse<?> list(HttpRequest<?> request) {
    String version = Long.toString(reportCacheService.maxCreated().toEpochMilli());
    return ResponseEtags.withWeakEtag(
        request, version, (java.util.function.Supplier<List<CachekeyCreated>>) reportCacheService::allMetadata);
  }
}
