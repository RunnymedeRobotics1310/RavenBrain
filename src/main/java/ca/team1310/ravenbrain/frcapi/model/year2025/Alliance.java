package ca.team1310.ravenbrain.frcapi.model.year2025;

import io.micronaut.serde.annotation.Serdeable;

/**
 * @author Tony Field
 * @since 2025-11-10 23:00
 */
@Serdeable
public record Alliance(
    String alliance,
    String autoLineRobot1,
    String endGameRobot1,
    String autoLineRobot2,
    String endGameRobot2,
    String autoLineRobot3,
    String endGameRobot3,
    int autoCoralCount,
    int autoMobilityPoints,
    int autoPoints,
    int autoCoralPoints,
    int teleopCoralCount,
    int teleopPoints,
    int teleopCoralPoints,
    int algaePoints,
    int netAlgaeCount,
    int wallAlgaeCount,
    int endGameBargePoints,
    boolean autoBonusAchieved,
    boolean coralBonusAchieved,
    boolean bargeBonusAchieved,
    boolean coopertitionCriteriaMet,
    int foulCount,
    int techFoulCount,
    boolean g206Penalty,
    boolean g410Penalty,
    boolean g418Penalty,
    boolean g428Penalty,
    int adjustPoints,
    int foulPoints,
    int rp,
    int totalPoints,
    Reef autoReef,
    Reef teleopReef) {}
