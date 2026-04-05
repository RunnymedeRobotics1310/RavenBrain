package ca.team1310.ravenbrain.sync;

import ca.team1310.ravenbrain.eventlog.EventLogRecord;
import ca.team1310.ravenbrain.matchstrategy.MatchStrategyDrawing;
import ca.team1310.ravenbrain.matchstrategy.MatchStrategyPlan;
import ca.team1310.ravenbrain.quickcomment.QuickComment;
import ca.team1310.ravenbrain.robotalert.RobotAlert;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record ScoutingDataResult(
    List<EventLogRecord> events,
    List<QuickComment> comments,
    List<RobotAlert> alerts,
    List<MatchStrategyPlan> plans,
    List<MatchStrategyDrawing> drawings) {}
