package ca.team1310.ravenbrain.frcapi.model;

import io.micronaut.serde.annotation.Serdeable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * @author Tony Field
 * @since 2025-11-10 21:39
 */
@Serdeable
@Data
public class SeasonSummaryResponse {
  long id;
  boolean processed;
  int eventCount;
  String gameName;
  LocalDateTime kickoff;
  int rookieStart;
  int teamCount;
  //    List<Object> frcChampionships;
}
/*
{
  "gameName": "REEFSCAPE℠ presented by Haas",
  "kickoff": "0001-01-01T00:00:00",
  "eventCount": 318,
  "teamCount": 4702,
  "rookieStart": 10000,
  "frcChampionship": null,
  "frcChampionships": [
    {
      "name": "FIRST Championship - FIRST Robotics Competition",
      "startDate": "2025-04-16T00:00:00",
      "location": "Houston, TX USA"
    }
  ]
}
 */
