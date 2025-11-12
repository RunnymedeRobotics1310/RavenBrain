package ca.team1310.ravenbrain.frcapi.model.year2025;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

/**
 * @author Tony Field
 * @since 2025-11-10 23:00
 */
@Serdeable
@Data
public class Alliance {
  private String alliance;
  private String autoLineRobot1;
  private String endGameRobot1;
  private String autoLineRobot2;
  private String endGameRobot2;
  private String autoLineRobot3;
  private String endGameRobot3;
  private int autoCoralCount;
  private int autoMobilityPoints;
  private int autoPoints;
  private int autoCoralPoints;
  private int teleopCoralCount;
  private int teleopPoints;
  private int teleopCoralPoints;
  private int algaePoints;
  private int netAlgaeCount;
  private int wallAlgaeCount;
  private int endGameBargePoints;
  private boolean autoBonusAchieved;
  private boolean coralBonusAchieved;
  private boolean bargeBonusAchieved;
  private boolean coopertitionCriteriaMet;
  private int foulCount;
  private int techFoulCount;
  private boolean g206Penalty;
  private boolean g410Penalty;
  private boolean g418Penalty;
  private boolean g428Penalty;
  private int adjustPoints;
  private int foulPoints;
  private int rp;
  private int totalPoints;
  private Reef autoReef;
  private Reef teleopReef;
}
