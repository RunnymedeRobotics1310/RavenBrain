package ca.team1310.ravenbrain.frcapi.model;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/**
 * @author Tony Field
 * @since 2025-11-10 21:49
 */
@Serdeable
public record FrcDistrictsResponse(
    long id, boolean processed, List<FrcDistrict> districts, int districtCount) {}
