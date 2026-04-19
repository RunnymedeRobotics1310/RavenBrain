package ca.team1310.ravenbrain.report.cache;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

/**
 * Projection of the {@code cachekey} + {@code created} columns from {@code RB_REPORT_CACHE}.
 * Returned by {@link ReportCacheRepository#findAllMetadata()} and served by
 * {@code /api/report/metadata} (Unit 6) as the client-side version signal for report caching.
 *
 * <p>Created is the "last rebuild" time of the cached body and serves as the content version:
 * when a scout's event invalidates a report, the next read rebuilds the cache with a new
 * {@code created}; clients diff their stored {@code created} against the server's and know
 * whether to re-pull the body.
 */
@Introspected
@Serdeable
public record CachekeyCreated(String cachekey, Instant created) {}
