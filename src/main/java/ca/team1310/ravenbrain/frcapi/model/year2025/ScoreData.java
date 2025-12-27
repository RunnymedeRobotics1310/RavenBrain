package ca.team1310.ravenbrain.frcapi.model.year2025;

import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * @author Tony Field
 * @since 2025-11-10 22:54
 */
@Serdeable
public record ScoreData(
    TournamentLevel matchLevel,
    int matchNumber,
    int winningAlliance,
    boolean coopertitionBonusAchieved,
    int coralBonusLevelsThresholdCoop,
    int coralBonusLevelsThresholdNonCoop,
    int coralBonusLevelsThreshold,
    int bargeBonusThreshold,
    int autoBonusCoralThreshold,
    Tiebreaker tiebreaker,
    List<Alliance> alliances) {}
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
