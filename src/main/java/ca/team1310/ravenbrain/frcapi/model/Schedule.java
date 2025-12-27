package ca.team1310.ravenbrain.frcapi.model;

import io.micronaut.serde.annotation.Serdeable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Tony Field
 * @since 2025-09-22 00:43
 */
@Serdeable
public record Schedule(
    String description,
    LocalDateTime startTime,
    int matchNumber,
    String field,
    String tournamentLevel,
    List<ScheduleTeam> teams) {}
  /*
   "description": "Qualification 1",
   "startTime": "2025-03-22T11:30:00",
   "matchNumber": 1,
   "field": "Primary",
   "tournamentLevel": "Qualification",
   "teams": [
     {
       "teamNumber": 9127,
       "station": "Red1",
       "surrogate": false
     },
  */
