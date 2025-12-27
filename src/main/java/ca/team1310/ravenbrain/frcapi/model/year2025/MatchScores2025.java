package ca.team1310.ravenbrain.frcapi.model.year2025;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/**
 * @author Tony Field
 * @since 2025-11-10 22:51
 */
@Serdeable
public record MatchScores2025(@JsonProperty("MatchScores") List<ScoreData> scores) {}
