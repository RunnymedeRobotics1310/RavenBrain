package ca.team1310.ravenbrain.frcapi.model.year2025;

import ca.team1310.ravenbrain.frcapi.TournamentLevel;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import lombok.Data;

/**
 * @author Tony Field
 * @since 2025-11-10 22:54
 */
@Serdeable
@Data
public class ScoreData {
  private TournamentLevel matchLevel;
  private int matchNumber;
  private int winningAlliance;
  private boolean coopertitionBonusAchieved;
  private int coralBonusLevelsThresholdCoop;
  private int coralBonusLevelsThresholdNonCoop;
  private int coralBonusLevelsThreshold;
  private int bargeBonusThreshold;
  private int autoBonusCoralThreshold;
  private Tiebreaker tiebreaker;
  private List<Alliance> alliances;
}
 /*
  "matchLevel": "Playoff",
  "matchNumber": 1,
  "winningAlliance": 1,
  "tiebreaker": {
    "item1": -1,
    "item2": ""
  },
  "coopertitionBonusAchieved": false,
  "coralBonusLevelsThresholdCoop": 3,
  "coralBonusLevelsThresholdNonCoop": 4,
  "coralBonusLevelsThreshold": 4,
  "bargeBonusThreshold": 0,
  "autoBonusCoralThreshold": 1,
 */
