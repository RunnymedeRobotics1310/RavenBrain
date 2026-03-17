package ca.team1310.ravenbrain.report.cache;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

/**
 * Cached report response. Stores the full JSON body of a generated report to avoid recomputation.
 *
 * @author Tony Field
 * @since 2026-03-16
 */
@MappedEntity(value = "RB_REPORT_CACHE")
@Serdeable
public record ReportCacheRecord(
    @Id @GeneratedValue Long id, String cachekey, String body, Instant created) {}
