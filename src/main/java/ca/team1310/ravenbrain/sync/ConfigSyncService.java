package ca.team1310.ravenbrain.sync;

import ca.team1310.ravenbrain.Application;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Property;
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
  private final int teamNumber;

  ConfigSyncService(
      RemoteRavenBrainClient remoteClient,
      DataSource dataSource,
      @Property(name = "raven-eye.team") int teamNumber) {
    this.remoteClient = remoteClient;
    this.dataSource = dataSource;
    this.teamNumber = teamNumber;
  }

  @Transactional
  public SyncResult syncFromSource(SyncRequest request) {
    String baseUrl = request.sourceUrl().replaceAll("/+$", "");
    log.info("Starting config sync from {}", baseUrl);

    // TODO: Re-enable version check after deploy
    // Check source server version matches (skip for development builds)
    // String localVersion = Application.getVersion();
    // String sourceVersion = remoteClient.getVersion(baseUrl);
    // boolean devBuild = "Development Build".equals(localVersion) || "Development Build".equals(sourceVersion);
    // if (!devBuild && !localVersion.equals(sourceVersion)) {
    //   throw new RuntimeException(
    //       "Source server version (" + sourceVersion + ") does not match local version ("
    //           + localVersion + ") — deploy the same version to both servers before syncing");
    // }

    // Authenticate to source
    String token = remoteClient.authenticate(baseUrl, request.sourceUser(), request.sourcePassword());

    // Fetch config data from source
    String strategyAreasJson = remoteClient.fetchJson(baseUrl, token, "/api/strategy-areas");
    String eventTypesJson = remoteClient.fetchJson(baseUrl, token, "/api/event-types");
    String sequenceTypesJson = remoteClient.fetchJson(baseUrl, token, "/api/sequence-types");

    // Optionally fetch scouting data from source
    String scoutingDataJson = null;
    if (request.syncScoutingData()) {
      try {
        scoutingDataJson = remoteClient.fetchJson(baseUrl, token, "/api/config-sync/scouting-data");
      } catch (RuntimeException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("HTTP 404")) {
          throw new RuntimeException(
              "Source server does not support scouting data sync — upgrade it first");
        }
        if (msg.contains("HTTP 401")) {
          throw new RuntimeException(
              "Authentication failed on source server — check source credentials");
        }
        if (msg.contains("HTTP 403")) {
          throw new RuntimeException(
              "Source server denied access to scouting data sync — either upgrade the source server or ensure the source user has superuser privileges");
        }
        throw e;
      }
    }

    try {
      JsonNode strategyAreas = objectMapper.readTree(strategyAreasJson);
      JsonNode eventTypes = objectMapper.readTree(eventTypesJson);
      JsonNode sequenceTypes = objectMapper.readTree(sequenceTypesJson);

      JsonNode scoutingData = scoutingDataJson != null ? objectMapper.readTree(scoutingDataJson) : null;

      try (Connection conn = dataSource.getConnection()) {
        int[] counts = writeData(conn, strategyAreas, eventTypes, sequenceTypes, request, scoutingData);

        String message = "Sync completed successfully from " + baseUrl;
        log.info(message);
        return new SyncResult(
            counts[0], counts[1], counts[2], counts[3],
            counts[4], counts[5], counts[6],
            request.clearTournaments(),
            message);
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
      SyncRequest request,
      JsonNode scoutingData)
      throws Exception {

    try (Statement stmt = conn.createStatement()) {
      // Disable FK checks
      stmt.execute("SET FOREIGN_KEY_CHECKS=0");

      // Always truncate config tables
      stmt.execute("TRUNCATE TABLE RB_SEQUENCEEVENT");
      stmt.execute("TRUNCATE TABLE RB_EVENTTYPE");
      stmt.execute("TRUNCATE TABLE RB_SEQUENCETYPE");
      stmt.execute("TRUNCATE TABLE RB_STRATEGYAREA");

      // Conditionally clear tournaments
      if (request.clearTournaments()) {
        stmt.execute("TRUNCATE TABLE RB_WATCHED_TOURNAMENT");
        stmt.execute("TRUNCATE TABLE RB_TEAM_TOURNAMENT");
        stmt.execute("TRUNCATE TABLE RB_SCHEDULE");
        stmt.execute("TRUNCATE TABLE RB_TOURNAMENT");
        log.info("Cleared tournaments, schedules, and team-tournament data");
      }

      // Handle scouting data tables
      int eventCount = 0;
      int commentCount = 0;
      int alertCount = 0;

      if (request.syncScoutingData()) {
        if (request.clearExistingScoutingData()) {
          stmt.execute("TRUNCATE TABLE RB_EVENT");
          stmt.execute("TRUNCATE TABLE RB_COMMENT");
          stmt.execute("TRUNCATE TABLE RB_ROBOT_ALERT");
          log.info("Cleared existing scouting data before import");
        }

        if (scoutingData != null) {
          eventCount = insertEvents(conn, scoutingData.get("events"));
          commentCount = insertComments(conn, scoutingData.get("comments"));
          alertCount = insertAlerts(conn, scoutingData.get("alerts"));
        }
      } else {
        // Existing behavior: clear orphaned scouting data when not syncing it
        stmt.execute("TRUNCATE TABLE RB_EVENT");
        stmt.execute("TRUNCATE TABLE RB_COMMENT");
      }

      // Insert config data
      int saCount = insertStrategyAreas(conn, strategyAreas);
      int etCount = insertEventTypes(conn, eventTypes);
      int stCount = insertSequenceTypes(conn, sequenceTypes);
      int seCount = insertSequenceEvents(conn, sequenceTypes);

      // Reset auto-increment counters
      resetAutoIncrement(stmt, "RB_STRATEGYAREA");
      resetAutoIncrement(stmt, "RB_SEQUENCETYPE");
      resetAutoIncrement(stmt, "RB_SEQUENCEEVENT");

      // Re-enable FK checks
      stmt.execute("SET FOREIGN_KEY_CHECKS=1");

      return new int[] {saCount, etCount, stCount, seCount, eventCount, commentCount, alertCount};
    }
  }

  private int insertStrategyAreas(Connection conn, JsonNode strategyAreas) throws Exception {
    String sql =
        "INSERT INTO RB_STRATEGYAREA (id, frcyear, code, name, description, disabled) VALUES (?,?,?,?,?,?)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      int count = 0;
      for (JsonNode sa : strategyAreas) {
        ps.setLong(1, sa.get("id").asLong());
        ps.setInt(2, sa.get("frcyear").asInt());
        ps.setString(3, sa.get("code").asText());
        ps.setString(4, sa.get("name").asText());
        ps.setString(5, sa.has("description") ? sa.get("description").asText(null) : null);
        ps.setBoolean(6, sa.has("disabled") && sa.get("disabled").asBoolean());
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
        "INSERT INTO RB_EVENTTYPE (eventtype, name, description, frcyear, strategyarea_id, showNote, showQuantity, disabled) VALUES (?,?,?,?,?,?,?,?)";
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
        ps.setBoolean(8, et.has("disabled") && et.get("disabled").asBoolean());
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

  private int insertEvents(Connection conn, JsonNode events) throws Exception {
    if (events == null || !events.isArray()) return 0;
    String sql =
        "INSERT INTO RB_EVENT (id, eventtimestamp, userid, tournamentid, level, matchid, alliance, teamnumber, eventtype, amount, note) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      int count = 0;
      for (JsonNode e : events) {
        ps.setLong(1, e.get("id").asLong());
        ps.setTimestamp(2, parseTimestamp(e.get("timestamp")));
        ps.setLong(3, e.get("userId").asLong());
        ps.setString(4, e.get("tournamentId").asText());
        ps.setString(5, e.get("level").asText());
        ps.setInt(6, e.get("matchId").asInt());
        ps.setString(7, e.get("alliance").asText());
        ps.setInt(8, e.get("teamNumber").asInt());
        ps.setString(9, e.get("eventType").asText());
        ps.setDouble(10, e.get("amount").asDouble());
        ps.setString(11, e.has("note") && !e.get("note").isNull() ? e.get("note").asText() : "");
        ps.addBatch();
        count++;
      }
      ps.executeBatch();
      log.info("Inserted {} events", count);
      resetAutoIncrement(conn.createStatement(), "RB_EVENT");
      return count;
    }
  }

  private int insertComments(Connection conn, JsonNode comments) throws Exception {
    if (comments == null || !comments.isArray()) return 0;
    String sql =
        "INSERT INTO RB_COMMENT (id, userid, scoutrole, teamnumber, commenttimestamp, comment) VALUES (?,?,?,?,?,?)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      int count = 0;
      for (JsonNode c : comments) {
        ps.setLong(1, c.get("id").asLong());
        ps.setLong(2, c.get("userId").asLong());
        ps.setString(3, c.get("role").asText());
        ps.setInt(4, c.get("team").asInt());
        ps.setTimestamp(5, parseTimestamp(c.get("timestamp")));
        ps.setString(6, c.get("quickComment").asText());
        ps.addBatch();
        count++;
      }
      ps.executeBatch();
      log.info("Inserted {} comments", count);
      resetAutoIncrement(conn.createStatement(), "RB_COMMENT");
      return count;
    }
  }

  private int insertAlerts(Connection conn, JsonNode alerts) throws Exception {
    if (alerts == null || !alerts.isArray()) return 0;
    String sql =
        "INSERT INTO RB_ROBOT_ALERT (id, tournament_id, team_number, user_id, created_at, alert) VALUES (?,?,?,?,?,?)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      int count = 0;
      for (JsonNode a : alerts) {
        ps.setLong(1, a.get("id").asLong());
        ps.setString(2, a.get("tournamentId").asText());
        ps.setInt(3, a.get("teamNumber").asInt());
        ps.setLong(4, a.get("userId").asLong());
        ps.setTimestamp(5, parseTimestamp(a.get("createdAt")));
        ps.setString(6, a.get("alert").asText());
        ps.addBatch();
        count++;
      }
      ps.executeBatch();
      log.info("Inserted {} alerts", count);
      resetAutoIncrement(conn.createStatement(), "RB_ROBOT_ALERT");
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
