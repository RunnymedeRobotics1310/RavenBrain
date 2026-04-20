package ca.team1310.ravenbrain.tbaapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;

/**
 * TBA {@code /event/{key}/oprs} response. Three parallel maps keyed by TBA team key (e.g.
 * {@code "frc1310"}) with offensive / defensive / contribution-to-winning-margin values per team.
 *
 * <p>Any of the three blocks may be missing on partial data (early in an event, pre-quals, or when
 * TBA has only partially computed results); each is {@link Nullable}. Unknown top-level fields are
 * ignored so future additions do not break deserialization.
 *
 * <p>Transformation from {@code "frc1310"} → {@code teamNumber=1310} happens in the sync layer
 * (matches {@code TbaMatchSyncService.parseTeamNumber} style); malformed keys are logged and
 * skipped per-team, not per-event.
 */
@Serdeable
@JsonIgnoreProperties(ignoreUnknown = true)
public record TbaEventOprs(
    @Nullable Map<String, Double> oprs,
    @Nullable Map<String, Double> dprs,
    @Nullable Map<String, Double> ccwms) {}
