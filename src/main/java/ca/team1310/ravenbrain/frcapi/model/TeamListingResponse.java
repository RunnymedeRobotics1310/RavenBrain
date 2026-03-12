package ca.team1310.ravenbrain.frcapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/**
 * Response from the FRC API teams endpoint: /:season/teams?eventCode=X
 *
 * @author Tony Field
 * @since 2026-03-12
 */
@Serdeable
public record TeamListingResponse(
    @JsonProperty("teams") List<TeamListing> teams,
    int teamCountTotal,
    int teamCountPage,
    int pageCurrent,
    int pageTotal) {}
