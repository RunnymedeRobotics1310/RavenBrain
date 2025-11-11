package ca.team1310.ravenbrain.frcapi.model.year2025;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import lombok.Data;

/**
 * @author Tony Field
 * @since 2025-11-10 22:51
 */
@Serdeable
@Data
public class MatchScores {
  @JsonProperty("MatchScores")
  List<ScoreData> scores;
}
