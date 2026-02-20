package ca.team1310.ravenbrain.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ConfigSyncService {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final RemoteRavenBrainClient remoteClient;
  private final DataSource dataSource;

  ConfigSyncService(RemoteRavenBrainClient remoteClient, DataSource dataSource) {
    this.remoteClient = remoteClient;
    this.dataSource = dataSource;
  }

  @Transactional
  public SyncResult syncFromSource(SyncRequest request) {
    String baseUrl = request.sourceUrl().replaceAll("/+$", "");
    log.info("Starting config sync from {}", baseUrl);

    // Authenticate to source
    String token = remoteClient.authenticate(baseUrl, request.sourceUser(), request.sourcePassword());

    // Fetch config data from source
    String strategyAreasJson = remoteClient.fetchJson(baseUrl, token, "/api/strategy-areas");
    String eventTypesJson = remoteClient.fetchJson(baseUrl, token, "/api/event-types");
    String sequenceTypesJson = remoteClient.fetchJson(baseUrl, token, "/api/sequence-types");
    String tournamentsJson = remoteClient.fetchJson(baseUrl, token, "/api/tournament");

    try {
      JsonNode strategyAreas = objectMapper.readTree(strategyAreasJson);
      JsonNode eventTypes = objectMapper.readTree(eventTypesJson);
      JsonNode sequenceTypes = objectMapper.readTree(sequenceTypesJson);
      JsonNode tournaments = objectMapper.readTree(tournamentsJson);

      // Fetch schedules per tournament
      JsonNode[] schedules = new JsonNode[tournaments.size()];
      for (int i = 0; i < tournaments.size(); i++) {
        String tournamentId = tournaments.get(i).get("id").asText();
        String schedulesJson =
            remoteClient.fetchJson(baseUrl, token, "/api/schedule/" + tournamentId);
        schedules[i] = objectMapper.readTree(schedulesJson);
      }

      // Execute sync within the Micronaut-managed transaction
      try (Connection conn = dataSource.getConnection()) {
        int[] counts =
            writeData(conn, strategyAreas, eventTypes, sequenceTypes, tournaments, schedules);

        String message = "Sync completed successfully from " + baseUrl;
        log.info(message);
        return new SyncResult(
            counts[0], counts[1], counts[2], counts[3], counts[4], counts[5], message);
      }

    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Config sync failed: " + e.getMessage(), e);
    }
  }

  private int[] writeData(
      Connection conn,
      JsonNode strategyAreas,
      JsonNode eventTypes,
      JsonNode sequenceTypes,
      JsonNode tournaments,
      JsonNode[] schedules)
      throws Exception {

    try (Statement stmt = conn.createStatement()) {
      // Disable FK checks
      stmt.execute("SET FOREIGN_KEY_CHECKS=0");

      // Truncate all tables
      stmt.execute("TRUNCATE TABLE RB_SEQUENCEEVENT");
      stmt.execute("TRUNCATE TABLE RB_SCHEDULE");
      stmt.execute("TRUNCATE TABLE RB_EVENT");
      stmt.execute("TRUNCATE TABLE RB_COMMENT");
      stmt.execute("TRUNCATE TABLE RB_EVENTTYPE");
      stmt.execute("TRUNCATE TABLE RB_SEQUENCETYPE");
      stmt.execute("TRUNCATE TABLE RB_TOURNAMENT");
      stmt.execute("TRUNCATE TABLE RB_STRATEGYAREA");

      // Insert strategy areas
      int saCount = insertStrategyAreas(conn, strategyAreas);

      // Insert event types
      int etCount = insertEventTypes(conn, eventTypes);

      // Insert sequence types and their events
      int stCount = insertSequenceTypes(conn, sequenceTypes);
      int seCount = insertSequenceEvents(conn, sequenceTypes);

      // Insert tournaments
      int tCount = insertTournaments(conn, tournaments);

      // Insert schedules
      int schCount = 0;
      for (JsonNode schedule : schedules) {
        schCount += insertSchedules(conn, schedule);
      }

      // Reset auto-increment counters
      resetAutoIncrement(stmt, "RB_STRATEGYAREA");
      resetAutoIncrement(stmt, "RB_SEQUENCETYPE");
      resetAutoIncrement(stmt, "RB_SEQUENCEEVENT");
      resetAutoIncrement(stmt, "RB_SCHEDULE");

      // Re-enable FK checks
      stmt.execute("SET FOREIGN_KEY_CHECKS=1");

      return new int[] {saCount, etCount, stCount, seCount, tCount, schCount};
    }
  }

  private int insertStrategyAreas(Connection conn, JsonNode strategyAreas) throws Exception {
    String sql =
        "INSERT INTO RB_STRATEGYAREA (id, frcyear, code, name, description) VALUES (?,?,?,?,?)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      int count = 0;
      for (JsonNode sa : strategyAreas) {
        ps.setLong(1, sa.get("id").asLong());
        ps.setInt(2, sa.get("frcyear").asInt());
        ps.setString(3, sa.get("code").asText());
        ps.setString(4, sa.get("name").asText());
        ps.setString(5, sa.has("description") ? sa.get("description").asText(null) : null);
        ps.addBatch();
        count++;
      }
      ps.executeBatch();
      log.info("Inserted {} strategy areas", count);
      return count;
    }
  }

  private int insertEventTypes(Connection conn, JsonNode eventTypes) throws Exception {
    String sql =
        "INSERT INTO RB_EVENTTYPE (eventtype, name, description, frcyear, strategyarea_id, showNote, showQuantity) VALUES (?,?,?,?,?,?,?)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      int count = 0;
      for (JsonNode et : eventTypes) {
        ps.setString(1, et.get("eventtype").asText());
        ps.setString(2, et.get("name").asText());
        ps.setString(3, et.has("description") ? et.get("description").asText(null) : null);
        ps.setInt(4, et.get("frcyear").asInt());
        if (et.has("strategyareaId") && !et.get("strategyareaId").isNull()) {
          ps.setLong(5, et.get("strategyareaId").asLong());
        } else {
          ps.setNull(5, java.sql.Types.BIGINT);
        }
        ps.setBoolean(6, et.has("showNote") && et.get("showNote").asBoolean());
        ps.setBoolean(7, et.has("showQuantity") && et.get("showQuantity").asBoolean());
        ps.addBatch();
        count++;
      }
      ps.executeBatch();
      log.info("Inserted {} event types", count);
      return count;
    }
  }

  private int insertSequenceTypes(Connection conn, JsonNode sequenceTypes) throws Exception {
    String sql =
        "INSERT INTO RB_SEQUENCETYPE (id, code, name, description, frcyear, disabled, strategyarea_id) VALUES (?,?,?,?,?,?,?)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      int count = 0;
      for (JsonNode st : sequenceTypes) {
        ps.setLong(1, st.get("id").asLong());
        ps.setString(2, st.get("code").asText());
        ps.setString(3, st.get("name").asText());
        ps.setString(4, st.has("description") ? st.get("description").asText(null) : null);
        ps.setInt(5, st.get("frcyear").asInt());
        ps.setBoolean(6, st.has("disabled") && st.get("disabled").asBoolean());
        if (st.has("strategyareaId") && !st.get("strategyareaId").isNull()) {
          ps.setLong(7, st.get("strategyareaId").asLong());
        } else {
          ps.setNull(7, java.sql.Types.BIGINT);
        }
        ps.addBatch();
        count++;
      }
      ps.executeBatch();
      log.info("Inserted {} sequence types", count);
      return count;
    }
  }

  private int insertSequenceEvents(Connection conn, JsonNode sequenceTypes) throws Exception {
    String sql =
        "INSERT INTO RB_SEQUENCEEVENT (id, sequencetype_id, eventtype_id, startOfSequence, endOfSequence) VALUES (?,?,?,?,?)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      int count = 0;
      for (JsonNode st : sequenceTypes) {
        long sequenceTypeId = st.get("id").asLong();
        JsonNode events = st.get("events");
        if (events != null && events.isArray()) {
          for (JsonNode event : events) {
            ps.setLong(1, event.get("id").asLong());
            ps.setLong(2, sequenceTypeId);
            ps.setString(3, event.get("eventtype").get("eventtype").asText());
            ps.setBoolean(4, event.get("startOfSequence").asBoolean());
            ps.setBoolean(5, event.get("endOfSequence").asBoolean());
            ps.addBatch();
            count++;
          }
        }
      }
      ps.executeBatch();
      log.info("Inserted {} sequence events", count);
      return count;
    }
  }

  private int insertTournaments(Connection conn, JsonNode tournaments) throws Exception {
    String sql =
        "INSERT INTO RB_TOURNAMENT (id, code, season, tournamentname, starttime, endtime) VALUES (?,?,?,?,?,?)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      int count = 0;
      for (JsonNode t : tournaments) {
        ps.setString(1, t.get("id").asText());
        ps.setString(2, t.has("code") ? t.get("code").asText(null) : null);
        ps.setInt(3, t.get("season").asInt());
        ps.setString(4, t.get("name").asText());
        ps.setTimestamp(5, parseTimestamp(t.get("startTime")));
        ps.setTimestamp(6, parseTimestamp(t.get("endTime")));
        ps.addBatch();
        count++;
      }
      ps.executeBatch();
      log.info("Inserted {} tournaments", count);
      return count;
    }
  }

  private int insertSchedules(Connection conn, JsonNode schedules) throws Exception {
    String sql =
        "INSERT INTO RB_SCHEDULE (id, tournamentid, level, matchnum, red1, red2, red3, blue1, blue2, blue3) VALUES (?,?,?,?,?,?,?,?,?,?)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      int count = 0;
      for (JsonNode s : schedules) {
        ps.setLong(1, s.get("id").asLong());
        ps.setString(2, s.get("tournamentId").asText());
        ps.setString(3, s.get("level").asText());
        ps.setInt(4, s.get("match").asInt());
        ps.setInt(5, s.get("red1").asInt());
        ps.setInt(6, s.get("red2").asInt());
        ps.setInt(7, s.get("red3").asInt());
        ps.setInt(8, s.get("blue1").asInt());
        ps.setInt(9, s.get("blue2").asInt());
        ps.setInt(10, s.get("blue3").asInt());
        ps.addBatch();
        count++;
      }
      ps.executeBatch();
      log.info("Inserted {} schedule entries", count);
      return count;
    }
  }

  private Timestamp parseTimestamp(JsonNode node) {
    if (node == null || node.isNull()) return null;
    // Micronaut serializes Instant as epoch seconds (numeric)
    if (node.isNumber()) {
      return Timestamp.from(Instant.ofEpochSecond(node.asLong()));
    }
    // Fallback: parse as ISO string
    return Timestamp.from(Instant.parse(node.asText()));
  }

  private void resetAutoIncrement(Statement stmt, String tableName) throws Exception {
    try (ResultSet rs =
        stmt.executeQuery("SELECT COALESCE(MAX(id), 0) + 1 FROM " + tableName)) {
      if (rs.next()) {
        long nextId = rs.getLong(1);
        stmt.execute("ALTER TABLE " + tableName + " AUTO_INCREMENT = " + nextId);
        log.debug("Reset {} AUTO_INCREMENT to {}", tableName, nextId);
      }
    }
  }
}
