/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.report;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import ca.team1310.ravenbrain.eventlog.EventLogRecord;
import ca.team1310.ravenbrain.eventlog.EventLogService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-04-02 21:39
 */
@Controller("/api/report")
@Slf4j
public class ReportApi {

  private final EventLogService eventService;

  public ReportApi(EventLogService eventService) {
    this.eventService = eventService;
  }

  /*
  export type TournamentReportCell = {
    colId: string;
    value: string | number | undefined;
  };*/

  @Serdeable
  public record TournamentReportCell(String colId, String value) {}

  /*
  export type TournamentReportRow = {
    values: TournamentReportCell[];
  };*/

  @Serdeable
  public record TournamentReportRow(TournamentReportCell[] values) {}

  /*
  export type TournamentReportTable = {
    headerRows: TournamentReportRow[];
    dataRows: TournamentReportRow[];
    footerRows: TournamentReportRow[];
  };*/

  @Serdeable
  public record TournamentReportTable(
      TournamentReportRow[] headerRows,
      TournamentReportRow[] dataRows,
      TournamentReportRow[] footerRows) {}

  @Serdeable
  public record TournamentReportResponse(
      TournamentReportTable report, boolean success, String reason) {}

  @Get("/tournament/{tournamentId}/{teamId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_EXPERTSCOUT"})
  public TournamentReportResponse getTournamentReport(
      @PathVariable String tournamentId, @PathVariable int teamId) {
    log.info("Getting tournament report for tournament {} and team {}", tournamentId, teamId);
    final TournamentReportResponse resp;

    List<EventLogRecord> data =
        eventService.findAllByTournamentIdAndTeamNumberOrderByMatchId(tournamentId, teamId);
    Map<Integer, List<EventLogRecord>> recordsByMatch = new LinkedHashMap<>();
    for (EventLogRecord record : data) {
      var match = record.getMatchId();
      List<EventLogRecord> records = recordsByMatch.computeIfAbsent(match, k -> new ArrayList<>());
      records.add(record);
    }

    var headerRow = getHeaderRow(teamId);
    var bodyRows = new ArrayList<TournamentReportRow>();
    // for each match, create a single row in the report with totals.
    for (int match : recordsByMatch.keySet()) {
      bodyRows.add(getBodyRow(match, recordsByMatch.get(match)));
    }
    var footerRows = getFooterRow(data);

    var report =
        new TournamentReportTable(
            Collections.singletonList(headerRow).toArray(new TournamentReportRow[0]),
            bodyRows.toArray(new TournamentReportRow[0]),
            Collections.singletonList(footerRows).toArray(new TournamentReportRow[0]));
    resp = new TournamentReportResponse(report, true, null);
    return resp;
  }

  private TournamentReportRow getHeaderRow(int teamId) {
    var row1 = new ArrayList<TournamentReportCell>();
    row1.add(new TournamentReportCell("INFO-teamName", Integer.toString(teamId)));
    row1.add(new TournamentReportCell("COMMENT", "Comments"));
    row1.add(new TournamentReportCell("INFO-mistake", "Mistake"));
    row1.add(new TournamentReportCell("INFO-auto-start", "Auto Start"));
    row1.add(new TournamentReportCell("INFO-auto-preloaded", "Preloaded"));
    row1.add(new TournamentReportCell("INFO-auto-leave", "Leave Starting Zone"));
    row1.add(new TournamentReportCell("AUTO-algae-remove", "Remove Algae"));
    row1.add(new TournamentReportCell("AUTO-algae-pluck", "Pluck Algae"));
    row1.add(new TournamentReportCell("AUTO-algae-pickup", "Pickup Algae"));
    row1.add(new TournamentReportCell("AUTO-algae-drop", "Drop Algae"));
    row1.add(new TournamentReportCell("AUTO-algae-net", "Score Net"));
    row1.add(new TournamentReportCell("AUTO-algae-processor", "Score Processor"));
    row1.add(new TournamentReportCell("AUTO-coral-pickup-floor", "Pickup Floor"));
    row1.add(new TournamentReportCell("AUTO-coral-pickup-left", "Pickup Left"));
    row1.add(new TournamentReportCell("AUTO-coral-pickup-right", "Pickup Right"));
    row1.add(new TournamentReportCell("AUTO-coral-pickup-drop", "Drop Coral"));
    row1.add(new TournamentReportCell("AUTO-coral-score-l1", "Score L1"));
    row1.add(new TournamentReportCell("AUTO-coral-score-l2", "Score L2"));
    row1.add(new TournamentReportCell("AUTO-coral-score-l3", "Score L3"));
    row1.add(new TournamentReportCell("AUTO-coral-score-l4", "Score L4"));
    row1.add(new TournamentReportCell("AUTO-coral-score-miss", "Miss Reef"));
    row1.add(new TournamentReportCell("TELEOP-algae-remove", "Remove Algae"));
    row1.add(new TournamentReportCell("TELEOP-algae-pluck", "Pluck Algae"));
    row1.add(new TournamentReportCell("TELEOP-algae-pickup", "Pickup Algae"));
    row1.add(new TournamentReportCell("TELEOP-algae-drop", "Drop Algae"));
    row1.add(new TournamentReportCell("TELEOP-algae-net", "Score Net"));
    row1.add(new TournamentReportCell("TELEOP-algae-processor", "Score Processor"));
    row1.add(new TournamentReportCell("TELEOP-coral-pickup-floor", "Pickup Floor"));
    row1.add(new TournamentReportCell("TELEOP-coral-pickup-left", "Pickup Left"));
    row1.add(new TournamentReportCell("TELEOP-coral-pickup-right", "Pickup Right"));
    row1.add(new TournamentReportCell("TELEOP-coral-pickup-drop", "Drop Coral"));
    row1.add(new TournamentReportCell("TELEOP-coral-score-l1", "Score L1"));
    row1.add(new TournamentReportCell("TELEOP-coral-score-l2", "Score L2"));
    row1.add(new TournamentReportCell("TELEOP-coral-score-l3", "Score L3"));
    row1.add(new TournamentReportCell("TELEOP-coral-score-l4", "Score L4"));
    row1.add(new TournamentReportCell("TELEOP-coral-score-miss", "Miss Reef"));
    row1.add(new TournamentReportCell("TELEOP-coral-score-cycles", "Total Coral Cycles"));
    row1.add(new TournamentReportCell("TELEOP-coral-score-scores", "Total Coral Scores"));
    row1.add(new TournamentReportCell("ENDGAME", "Endgame"));
    row1.add(new TournamentReportCell("PENALTY-pin", "Pin"));
    row1.add(new TournamentReportCell("PENALTY-zone", "Zone Violation"));
    row1.add(new TournamentReportCell("PENALTY-contact", "Off-Limit Contact"));
    row1.add(new TournamentReportCell("PENALTY-field-damage", "Field Damage"));
    row1.add(new TournamentReportCell("PENALTY-too-many-pieces", "Too Many Game Pieces"));
    row1.add(new TournamentReportCell("PENALTY-other", "Other"));
    row1.add(new TournamentReportCell("INFO-rp-auto", "Auto RP"));
    row1.add(new TournamentReportCell("INFO-rp-barge", "Barge RP"));
    row1.add(new TournamentReportCell("INFO-rp-coral", "Coral RP"));
    row1.add(new TournamentReportCell("COMMENT-stars", "Stars Rating"));
    row1.add(new TournamentReportCell("DEFENCE-started", "Defence Started"));
    row1.add(new TournamentReportCell("DEFENCE-time", "Defence Time"));
    row1.add(new TournamentReportCell("DEFENCE-played", "Played Defence"));
    row1.add(new TournamentReportCell("DEFENCE-effective", "Effective Defence"));
    row1.add(new TournamentReportCell("INFO-fast", "Drove Fast"));
    row1.add(new TournamentReportCell("INFO-beached", "Beached"));
    row1.add(new TournamentReportCell("INFO-consistent", "Consistent Scoring"));
    row1.add(new TournamentReportCell("INFO-shutdown", "Shut Down"));
    row1.add(new TournamentReportCell("INFO-falldowngoboom", "Fell Over"));
    row1.add(new TournamentReportCell("INFO-recovered", "Recovered"));
    row1.add(new TournamentReportCell("INFO-mean-or-incompetent", "Foul Often"));

    return new TournamentReportRow(row1.toArray(new TournamentReportCell[0]));
  }

  private TournamentReportRow getBodyRow(int matchId, List<EventLogRecord> data) {
    var row1 = new ArrayList<TournamentReportCell>();
    row1.add(new TournamentReportCell("INFO-teamName", Integer.toString(matchId)));
    row1.add(new TournamentReportCell("COMMENT", getComments(data)));
    row1.add(new TournamentReportCell("INFO-mistake", getMistakes(data)));
    row1.add(new TournamentReportCell("INFO-auto-start", getAutoStart(data)));
    row1.add(new TournamentReportCell("INFO-auto-preloaded", getPreloaded(data)));
    row1.add(new TournamentReportCell("INFO-auto-leave", getAutoLeave(data)));
    row1.add(new TournamentReportCell("AUTO-algae-remove", getAutoAlgaeRemove(data)));
    row1.add(new TournamentReportCell("AUTO-algae-pluck", getAutoAlgaePluck(data)));
    row1.add(new TournamentReportCell("AUTO-algae-pickup", getAutoAlgaePickup(data)));
    row1.add(new TournamentReportCell("AUTO-algae-drop", getAutoAlgaeDrop(data)));
    row1.add(new TournamentReportCell("AUTO-algae-net", getAutoAlgaeNet(data)));
    row1.add(new TournamentReportCell("AUTO-algae-processor", getAutoAlgaeProcessor(data)));
    row1.add(new TournamentReportCell("AUTO-coral-pickup-floor", getAutoCoralPickupFloor(data)));
    row1.add(new TournamentReportCell("AUTO-coral-pickup-left", getAutoCoralPickupLeft(data)));
    row1.add(new TournamentReportCell("AUTO-coral-pickup-right", getAutoCoralPickupRight(data)));
    row1.add(new TournamentReportCell("AUTO-coral-pickup-drop", getAutoCoralPickupDrop(data)));
    row1.add(new TournamentReportCell("AUTO-coral-score-l1", getAutoCoralScoreL1(data)));
    row1.add(new TournamentReportCell("AUTO-coral-score-l2", getAutoCoralScoreL2(data)));
    row1.add(new TournamentReportCell("AUTO-coral-score-l3", getAutoCoralScoreL3(data)));
    row1.add(new TournamentReportCell("AUTO-coral-score-l4", getAutoCoralScoreL4(data)));
    row1.add(new TournamentReportCell("AUTO-coral-score-miss", getAutoCoralMiss(data)));
    row1.add(new TournamentReportCell("TELEOP-algae-remove", getTeleopAlgaeRemove(data)));
    row1.add(new TournamentReportCell("TELEOP-algae-pluck", getTeleopAlgaePluck(data)));
    row1.add(new TournamentReportCell("TELEOP-algae-pickup", getTeleopAlgaePickup(data)));
    row1.add(new TournamentReportCell("TELEOP-algae-drop", getTeleopAlgaeDrop(data)));
    row1.add(new TournamentReportCell("TELEOP-algae-net", getTeleopAlgaeNet(data)));
    row1.add(new TournamentReportCell("TELEOP-algae-processor", getTeleopAlgaeProcewssor(data)));
    row1.add(
        new TournamentReportCell("TELEOP-coral-pickup-floor", getTeleopCoralPickupFloor(data)));
    row1.add(new TournamentReportCell("TELEOP-coral-pickup-left", getTeleopCoralPickupLeft(data)));
    row1.add(
        new TournamentReportCell("TELEOP-coral-pickup-right", getTeleopCoralPickupRight(data)));
    row1.add(new TournamentReportCell("TELEOP-coral-pickup-drop", getTeleopCoralPickupDrop(data)));
    row1.add(new TournamentReportCell("TELEOP-coral-score-l1", getTeleopCoralScoreL1(data)));
    row1.add(new TournamentReportCell("TELEOP-coral-score-l2", getTeleopCoralScoreL2(data)));
    row1.add(new TournamentReportCell("TELEOP-coral-score-l3", getTeleopCoralScoreL3(data)));
    row1.add(new TournamentReportCell("TELEOP-coral-score-l4", getTeleopCoralScoreL4(data)));
    row1.add(new TournamentReportCell("TELEOP-coral-score-miss", getTeleopCoralScoreMiss(data)));
    row1.add(
        new TournamentReportCell("TELEOP-coral-score-cycles", getTeleopCoralScoreCycles(data)));
    row1.add(
        new TournamentReportCell("TELEOP-coral-score-scores", getTeleopCoralScoreScores(data)));
    row1.add(new TournamentReportCell("ENDGAME", getEndgame(data)));
    row1.add(new TournamentReportCell("PENALTY-pin", getPenaltyPin(data)));
    row1.add(new TournamentReportCell("PENALTY-zone", getPenaltyZone(data)));
    row1.add(new TournamentReportCell("PENALTY-contact", getPenaltyContact(data)));
    row1.add(new TournamentReportCell("PENALTY-field-damage", getPenaltyFieldDamage(data)));
    row1.add(new TournamentReportCell("PENALTY-too-many-pieces", getPenaltyTooManyPieces(data)));
    row1.add(new TournamentReportCell("PENALTY-other", getPenaltyOther(data)));
    row1.add(new TournamentReportCell("INFO-rp-auto", getInfoRpAuto(data)));
    row1.add(new TournamentReportCell("INFO-rp-barge", getInfoRpBarge(data)));
    row1.add(new TournamentReportCell("INFO-rp-coral", getInfoRpCoral(data)));
    row1.add(new TournamentReportCell("COMMENT-stars", getCommentStars(data)));
    row1.add(new TournamentReportCell("DEFENCE-started", getDefenceStarted(data)));
    row1.add(new TournamentReportCell("DEFENCE-time", getDefenceTime(data)));
    row1.add(new TournamentReportCell("DEFENCE-played", getDefencePlayed(data)));
    row1.add(new TournamentReportCell("DEFENCE-effective", getDefenceEffective(data)));
    row1.add(new TournamentReportCell("INFO-fast", getInfoFast(data)));
    row1.add(new TournamentReportCell("INFO-beached", getInfoBeached(data)));
    row1.add(new TournamentReportCell("INFO-consistent", getInfoConsistent(data)));
    row1.add(new TournamentReportCell("INFO-shutdown", getInfoShutDown(data)));
    row1.add(new TournamentReportCell("INFO-falldowngoboom", getInfoFalldowngoboom(data)));
    row1.add(new TournamentReportCell("INFO-recovered", getInfoRecovered(data)));
    row1.add(new TournamentReportCell("INFO-mean-or-incompetent", getInfoMeanOrIncompetent(data)));

    return new TournamentReportRow(row1.toArray(new TournamentReportCell[0]));
  }

  private String getComments(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("comment")) {
        val.add(record.getNote());
      }
      if (record.getEventType().equals("COMMENTS-comment")) {
        val.add(record.getNote());
      }
    }
    return String.join(", ", val);
  }

  private String getMistakes(List<EventLogRecord> data) {
    var mistakes = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("COMMENTS-mistake")) {
        mistakes.add(record.getNote());
      }
    }
    return Integer.toString(mistakes.size());
  }

  private String getAutoStart(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-auto-start-center")) {
        val.add("Center");
      }
      if (record.getEventType().equals("auto-start-center")) {
        val.add("Center");
      }
      if (record.getEventType().equals("AUTO-auto-start-left")) {
        val.add("Left");
      }
      if (record.getEventType().equals("auto-start-left")) {
        val.add("Left");
      }
      if (record.getEventType().equals("AUTO-auto-start-right")) {
        val.add("Right");
      }
      if (record.getEventType().equals("auto-start-right")) {
        val.add("Right");
      }
    }
    return String.join(", ", val);
  }

  private String getPreloaded(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-preloaded-coral")) {
        val.add("Coral");
      }
      if (record.getEventType().equals("preloaded-coral")) {
        val.add("Coral");
      }
      if (record.getEventType().equals("AUTO-preloaded-algae")) {
        val.add("Algae");
      }
      if (record.getEventType().equals("AUTO-preloaded-nothing")) {
        val.add("--");
      }
      if (record.getEventType().equals("preloaded-nothing")) {
        val.add("--");
      }
    }
    return String.join(", ", val);
  }

  private String getAutoLeave(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-leave-starting-line")) {
        val.add("Y");
      }
    }
    return String.join(", ", val);
  }

  private String getAutoAlgaeRemove(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-remove-algae")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getAutoAlgaePluck(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-pickup-algae-reef")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getAutoAlgaePickup(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-pickup-algae-auto-center")) {
        val.add("Y");
      }
      if (record.getEventType().equals("pickup-algae-auto-left")) {
        val.add("Y");
      }
      if (record.getEventType().equals("pickup-algae-auto-center")) {
        val.add("Y");
      }
      if (record.getEventType().equals("pickup-algae-auto-right")) {
        val.add("Y");
      }
      if (record.getEventType().equals("AUTO-pickup-algae-reef")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getAutoAlgaeDrop(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("drop-algae")) {
        val.add("Y");
      }
      if (record.getEventType().equals("AUTO-drop-algae")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getAutoAlgaeNet(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-score-algae-net")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getAutoAlgaeProcessor(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-score-algae-processor")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getAutoCoralPickupFloor(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-pickup-coral-floor")) {
        val.add("Y");
      }
      if (record.getEventType().equals("AUTO-pickup-coral-auto-center")) {
        val.add("Y");
      }
      if (record.getEventType().equals("pickup-coral-auto-left")) {
        val.add("Y");
      }
      if (record.getEventType().equals("pickup-coral-auto-center")) {
        val.add("Y");
      }
      if (record.getEventType().equals("pickup-coral-auto-right")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getAutoCoralPickupLeft(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-pickup-coral-station-left")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getAutoCoralPickupRight(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-pickup-coral-station-right")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getAutoCoralPickupDrop(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-drop-coral")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getAutoCoralScoreL1(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-score-reef-l1")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getAutoCoralScoreL2(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-score-reef-l2")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getAutoCoralScoreL3(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-score-reef-l3")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getAutoCoralScoreL4(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-score-reef-l4")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getAutoCoralMiss(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-score-reef-miss")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getTeleopAlgaeRemove(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("TELEOP-remove-algae")) {
        val.add("Y");
      }
      if (record.getEventType().equals("remove-algae")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getTeleopAlgaePluck(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("pickup-algae-reef")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-pickup-algae-reef")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getTeleopAlgaePickup(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("pickup-algae-floor")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-pickup-algae-floor")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getTeleopAlgaeDrop(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("TELEOP-drop-algae")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getTeleopAlgaeNet(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("TELEOP-score-algae-net")) {
        val.add("Y");
      }
      if (record.getEventType().equals("score-algae-net")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getTeleopAlgaeProcewssor(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("TELEOP-score-algae-processor")) {
        val.add("Y");
      }
      if (record.getEventType().equals("score-algae-processor")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getTeleopCoralPickupFloor(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("TELEOP-pickup-coral-floor")) {
        val.add("Y");
      }
      if (record.getEventType().equals("pickup-coral-floor")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getTeleopCoralPickupLeft(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("TELEOP-pickup-coral-station-left")) {
        val.add("Y");
      }
      if (record.getEventType().equals("pickup-coral-station-left")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getTeleopCoralPickupRight(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("TELEOP-pickup-coral-station-right")) {
        val.add("Y");
      }
      if (record.getEventType().equals("pickup-coral-station-right")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getTeleopCoralPickupDrop(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("TELEOP-drop-coral")) {
        val.add("Y");
      }
      if (record.getEventType().equals("drop-coral")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getTeleopCoralScoreL1(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("score-reef-l1")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-score-reef-l1")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getTeleopCoralScoreL2(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("score-reef-l2")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-score-reef-l2")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getTeleopCoralScoreL3(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("score-reef-l3")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-score-reef-l3")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getTeleopCoralScoreL4(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("score-reef-l4")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-score-reef-l4")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getTeleopCoralScoreMiss(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("score-reef-miss")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-score-reef-miss")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getTeleopCoralScoreCycles(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("TELEOP-score-reef-l1")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-score-reef-l2")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-score-reef-l3")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-score-reef-l4")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-score-reef-miss")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getTeleopCoralScoreScores(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("TELEOP-score-reef-l1")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-score-reef-l2")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-score-reef-l3")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-score-reef-l4")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getEndgame(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("climb-park")) {
        val.add("Park");
      }
      if (record.getEventType().equals("COMMENTS-climb-park")) {
        val.add("Park");
      }
      if (record.getEventType().equals("ENDGAME-climb-deep")) {
        val.add("Deep");
      }
      if (record.getEventType().equals("climb-deep")) {
        val.add("Deep");
      }
      if (record.getEventType().equals("climb-none")) {
        val.add("None");
      }
      if (record.getEventType().equals("ENDGAME-climb-none")) {
        val.add("None");
      }
      if (record.getEventType().equals("ENDGAME-climb-park")) {
        val.add("Park");
      }
      if (record.getEventType().equals("ENDGAME-climb-shallow")) {
        val.add("Shallow");
      }
      if (record.getEventType().equals("climb-shallow")) {
        val.add("Shallow");
      }
      if (record.getEventType().equals("attempted-climb")) {
        val.add("Attempted");
      }
      if (record.getEventType().equals("ENDGAME-attempted-climb")) {
        val.add("Attempted");
      }
    }
    return String.join(", ", val);
  }

  private String getPenaltyPin(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("TELEOP-penalty-pin")) {
        val.add("Y");
      }
      if (record.getEventType().equals("AUTO-penalty-pin")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getPenaltyZone(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("TELEOP-penalty-zone-violation")) {
        val.add("Y");
      }
      if (record.getEventType().equals("AUTO-penalty-zone-violation")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getPenaltyContact(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-penalty-off-limit-contact")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-penalty-off-limit-contact")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getPenaltyFieldDamage(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-penalty-field-damage")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-penalty-field-damage")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getPenaltyTooManyPieces(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-penalty-too-many-game-pieces")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-penalty-too-many-game-pieces")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getPenaltyOther(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("AUTO-penalty-other")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-penalty-other")) {
        val.add("Y");
      }
      if (record.getEventType().equals("AUTO-penalty-throwing-algae")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-penalty-throwing-algae")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-penalty-opponent-contact")) {
        val.add("Y");
      }
      if (record.getEventType().equals("AUTO-penalty-opponent-contact")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getInfoRpAuto(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("auto-rp")) {
        val.add("Y");
      }
      if (record.getEventType().equals("COMMENTS-auto-rp")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getInfoRpBarge(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("barge-rp")) {
        val.add("Y");
      }
      if (record.getEventType().equals("COMMENTS-barge-rp")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getInfoRpCoral(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("coral-rp")) {
        val.add("Y");
      }
      if (record.getEventType().equals("COMMENTS-coral-rp")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getCommentStars(List<EventLogRecord> data) {
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("COMMENTS-star-rating-1")) {
        return "1";
      }
      if (record.getEventType().equals("COMMENTS-star-rating-2")) {
        return "2";
      }
      if (record.getEventType().equals("COMMENTS-star-rating-3")) {
        return "3";
      }
      if (record.getEventType().equals("COMMENTS-star-rating-4")) {
        return "4";
      }
      if (record.getEventType().equals("COMMENTS-star-rating-5")) {
        return "5";
      }
      if (record.getEventType().equals("star-rating-1")) {
        return "1";
      }
      if (record.getEventType().equals("star-rating-2")) {
        return "2";
      }
      if (record.getEventType().equals("star-rating-3")) {
        return "3";
      }
      if (record.getEventType().equals("star-rating-4")) {
        return "4";
      }
      if (record.getEventType().equals("star-rating-5")) {
        return "5";
      }
    }
    return "";
  }

  private String getDefenceStarted(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("defence-started")) {
        val.add("Y");
      }
      if (record.getEventType().equals("TELEOP-defence-started")) {
        val.add("Y");
      }
    }
    return Integer.toString(val.size());
  }

  private String getDefenceTime(List<EventLogRecord> data) {
    double secs = 0;
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("TELEOP-defence-stopped")) {
        secs += record.getAmount();
      }
      if (record.getEventType().equals("defence-stopped")) {
        secs += record.getAmount();
      }
    }
    return String.format("%.1f", secs);
  }

  private String getDefencePlayed(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("feedback-play-defence")) {
        val.add("Y");
      }
      if (record.getEventType().equals("COMMENTS-feedback-play-defence")) {
        val.add("Y");
      }
    }
    return String.join(", ", val);
  }

  private String getDefenceEffective(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("COMMENTS-feedback-effective-defence")) {
        val.add("Y");
      }
      if (record.getEventType().equals("feedback-effective-defence")) {
        val.add("Y");
      }
    }
    return String.join(", ", val);
  }

  private String getInfoFast(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("COMMENTS-feedback-drove-fast")) {
        val.add("Y");
      }
      if (record.getEventType().equals("feedback-drove-fast")) {
        val.add("Y");
      }
    }
    return String.join(", ", val);
  }

  private String getInfoBeached(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("COMMENTS-feedback-beached")) {
        val.add("Y");
      }
    }
    return String.join(", ", val);
  }

  private String getInfoConsistent(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("feedback-score-consistently")) {
        val.add("Y");
      }
      if (record.getEventType().equals("COMMENTS-feedback-score-consistently")) {
        val.add("Y");
      }
    }
    return String.join(", ", val);
  }

  private String getInfoShutDown(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("feedback-shut-down")) {
        val.add("Y");
      }
      if (record.getEventType().equals("COMMENTS-feedback-shut-down")) {
        val.add("Y");
      }
    }
    return String.join(", ", val);
  }

  private String getInfoFalldowngoboom(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("feedback-fell-over")) {
        val.add("Y");
      }
      if (record.getEventType().equals("COMMENTS-feedback-fell-over")) {
        val.add("Y");
      }
    }
    return String.join(", ", val);
  }

  private String getInfoRecovered(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("feedback-recover")) {
        val.add("Y");
      }
      if (record.getEventType().equals("COMMENTS-feedback-recover")) {
        val.add("Y");
      }
    }
    return String.join(", ", val);
  }

  private String getInfoMeanOrIncompetent(List<EventLogRecord> data) {
    var val = new ArrayList<String>();
    for (EventLogRecord record : data) {
      if (record.getEventType().equals("feedback-foul-often")) {
        val.add("Y");
      }
      if (record.getEventType().equals("COMMENTS-feedback-foul-often")) {
        val.add("Y");
      }
    }
    return String.join(", ", val);
  }

  private TournamentReportRow getFooterRow(List<EventLogRecord> data) {
    var row1 = new ArrayList<TournamentReportCell>();
    row1.add(new TournamentReportCell("INFO-teamName", "Avg"));
    row1.add(new TournamentReportCell("COMMENT", ""));
    row1.add(new TournamentReportCell("INFO-mistake", ""));
    row1.add(new TournamentReportCell("INFO-auto-start", ""));
    row1.add(new TournamentReportCell("INFO-auto-preloaded", ""));
    row1.add(new TournamentReportCell("INFO-auto-leave", ""));
    row1.add(new TournamentReportCell("AUTO-algae-remove", ""));
    row1.add(new TournamentReportCell("AUTO-algae-pluck", ""));
    row1.add(new TournamentReportCell("AUTO-algae-pickup", ""));
    row1.add(new TournamentReportCell("AUTO-algae-drop", ""));
    row1.add(new TournamentReportCell("AUTO-algae-net", ""));
    row1.add(new TournamentReportCell("AUTO-algae-processor", ""));
    row1.add(new TournamentReportCell("AUTO-coral-pickup-floor", ""));
    row1.add(new TournamentReportCell("AUTO-coral-pickup-left", ""));
    row1.add(new TournamentReportCell("AUTO-coral-pickup-right", ""));
    row1.add(new TournamentReportCell("AUTO-coral-pickup-drop", ""));
    row1.add(new TournamentReportCell("AUTO-coral-score-l1", ""));
    row1.add(new TournamentReportCell("AUTO-coral-score-l2", ""));
    row1.add(new TournamentReportCell("AUTO-coral-score-l3", ""));
    row1.add(new TournamentReportCell("AUTO-coral-score-l4", ""));
    row1.add(new TournamentReportCell("AUTO-coral-score-miss", getAutoMissReefAverage(data)));
    row1.add(new TournamentReportCell("TELEOP-algae-remove", ""));
    row1.add(new TournamentReportCell("TELEOP-algae-pluck", ""));
    row1.add(new TournamentReportCell("TELEOP-algae-pickup", ""));
    row1.add(new TournamentReportCell("TELEOP-algae-drop", ""));
    row1.add(new TournamentReportCell("TELEOP-algae-net", ""));
    row1.add(new TournamentReportCell("TELEOP-algae-processor", ""));
    row1.add(new TournamentReportCell("TELEOP-coral-pickup-floor", ""));
    row1.add(new TournamentReportCell("TELEOP-coral-pickup-left", ""));
    row1.add(new TournamentReportCell("TELEOP-coral-pickup-right", ""));
    row1.add(new TournamentReportCell("TELEOP-coral-pickup-drop", ""));
    row1.add(new TournamentReportCell("TELEOP-coral-score-l1", ""));
    row1.add(new TournamentReportCell("TELEOP-coral-score-l2", ""));
    row1.add(new TournamentReportCell("TELEOP-coral-score-l3", ""));
    row1.add(new TournamentReportCell("TELEOP-coral-score-l4", ""));
    row1.add(new TournamentReportCell("TELEOP-coral-score-miss", getTeleopMissReefAverage(data)));
    row1.add(new TournamentReportCell("TELEOP-coral-score-cycles", ""));
    row1.add(new TournamentReportCell("TELEOP-coral-score-scores", ""));
    row1.add(new TournamentReportCell("ENDGAME", ""));
    row1.add(new TournamentReportCell("PENALTY-pin", ""));
    row1.add(new TournamentReportCell("PENALTY-zone", ""));
    row1.add(new TournamentReportCell("PENALTY-contact", ""));
    row1.add(new TournamentReportCell("PENALTY-field-damage", ""));
    row1.add(new TournamentReportCell("PENALTY-too-many-pieces", ""));
    row1.add(new TournamentReportCell("PENALTY-other", ""));
    row1.add(new TournamentReportCell("INFO-rp-auto", ""));
    row1.add(new TournamentReportCell("INFO-rp-barge", ""));
    row1.add(new TournamentReportCell("INFO-rp-coral", ""));
    row1.add(new TournamentReportCell("COMMENT-stars", ""));
    row1.add(new TournamentReportCell("DEFENCE-started", ""));
    row1.add(new TournamentReportCell("DEFENCE-time", ""));
    row1.add(new TournamentReportCell("DEFENCE-played", ""));
    row1.add(new TournamentReportCell("DEFENCE-effective", ""));
    row1.add(new TournamentReportCell("INFO-fast", ""));
    row1.add(new TournamentReportCell("INFO-beached", ""));
    row1.add(new TournamentReportCell("INFO-consistent", ""));
    row1.add(new TournamentReportCell("INFO-shutdown", ""));
    row1.add(new TournamentReportCell("INFO-falldowngoboom", ""));
    row1.add(new TournamentReportCell("INFO-recovered", ""));
    row1.add(new TournamentReportCell("INFO-mean-or-incompetent", ""));

    return new TournamentReportRow(row1.toArray(new TournamentReportCell[0]));
  }

  private String getAutoMissReefAverage(List<EventLogRecord> data) {
    int miss = 0;
    int score = 0;
    for (EventLogRecord record : data) {

      if (record.getEventType().equals("AUTO-score-reef-l1")) {
        score++;
      }
      if (record.getEventType().equals("AUTO-score-reef-l2")) {
        score++;
      }
      if (record.getEventType().equals("AUTO-score-reef-l3")) {
        score++;
      }
      if (record.getEventType().equals("AUTO-score-reef-l4")) {
        score++;
      }
      if (record.getEventType().equals("AUTO-score-reef-miss")) {
        miss++;
      }
    }
    return _pct(miss, miss + score);
  }

  private String getTeleopMissReefAverage(List<EventLogRecord> data) {
    int miss = 0;
    int score = 0;
    for (EventLogRecord record : data) {

      if (record.getEventType().equals("TELEOP-score-reef-l1")) {
        score++;
      }
      if (record.getEventType().equals("TELEOP-score-reef-l2")) {
        score++;
      }
      if (record.getEventType().equals("TELEOP-score-reef-l3")) {
        score++;
      }
      if (record.getEventType().equals("TELEOP-score-reef-l4")) {
        score++;
      }
      if (record.getEventType().equals("TELEOP-score-reef-miss")) {
        miss++;
      }
    }
    return _pct(miss, miss + score);
  }

  private static String _pct(double qty, double total) {
    if (total == 0) {
      return "-";
    } else {
      return String.format("%.0f %%", (qty / total) * 100);
    }
  }
}
