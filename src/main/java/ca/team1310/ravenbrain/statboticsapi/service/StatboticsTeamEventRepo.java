package ca.team1310.ravenbrain.statboticsapi.service;

import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;

/**
 * Raw-JDBC repository for {@code RB_STATBOTICS_TEAM_EVENT}. Raw JDBC (matching the
 * {@code TeamTournamentService} precedent) is cleaner than {@code CrudRepository} with an
 * {@code @EmbeddedId} for this table's composite primary key.
 *
 * <p>Exposes per-event read, per-row existence check, insert, update, and a find-all helper used
 * by tests. The sync service owns all writes.
 */
@Slf4j
@Singleton
public class StatboticsTeamEventRepo {

  private final DataSource dataSource;

  StatboticsTeamEventRepo(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** All rows for a single TBA event — drives the sync's prior-row lookup and tests. */
  @Transactional
  public List<StatboticsTeamEventRecord> findByTbaEventKey(String tbaEventKey) {
    String sql =
        "SELECT tba_event_key, team_number, tournament_id, epa_total, epa_auto, epa_teleop, "
            + "epa_endgame, epa_unitless, epa_norm, breakdown_json, last_sync, last_status "
            + "FROM RB_STATBOTICS_TEAM_EVENT WHERE tba_event_key = ?";
    List<StatboticsTeamEventRecord> results = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, tbaEventKey);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) results.add(mapRow(rs));
      }
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to find statbotics team events for " + tbaEventKey + ": " + e.getMessage(), e);
    }
    return results;
  }

  /** All rows across all events — used by tests for cleanup and cross-row assertions. */
  @Transactional
  public List<StatboticsTeamEventRecord> findAll() {
    String sql =
        "SELECT tba_event_key, team_number, tournament_id, epa_total, epa_auto, epa_teleop, "
            + "epa_endgame, epa_unitless, epa_norm, breakdown_json, last_sync, last_status "
            + "FROM RB_STATBOTICS_TEAM_EVENT";
    List<StatboticsTeamEventRecord> results = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) results.add(mapRow(rs));
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to list statbotics team events: " + e.getMessage(), e);
    }
    return results;
  }

  /** Single-row lookup by composite key. */
  @Transactional
  public StatboticsTeamEventRecord find(String tbaEventKey, int teamNumber) {
    String sql =
        "SELECT tba_event_key, team_number, tournament_id, epa_total, epa_auto, epa_teleop, "
            + "epa_endgame, epa_unitless, epa_norm, breakdown_json, last_sync, last_status "
            + "FROM RB_STATBOTICS_TEAM_EVENT WHERE tba_event_key = ? AND team_number = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, tbaEventKey);
      ps.setInt(2, teamNumber);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return mapRow(rs);
      }
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to find statbotics team event ("
              + tbaEventKey
              + ","
              + teamNumber
              + "): "
              + e.getMessage(),
          e);
    }
    return null;
  }

  /** Insert a new row. */
  @Transactional
  public void save(StatboticsTeamEventRecord r) {
    String sql =
        "INSERT INTO RB_STATBOTICS_TEAM_EVENT "
            + "(tba_event_key, team_number, tournament_id, epa_total, epa_auto, epa_teleop, "
            + "epa_endgame, epa_unitless, epa_norm, breakdown_json, last_sync, last_status) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      bindAll(ps, r);
      ps.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to save statbotics team event ("
              + r.tbaEventKey()
              + ","
              + r.teamNumber()
              + "): "
              + e.getMessage(),
          e);
    }
  }

  /** Update an existing row in place. */
  @Transactional
  public void update(StatboticsTeamEventRecord r) {
    String sql =
        "UPDATE RB_STATBOTICS_TEAM_EVENT SET "
            + "tournament_id = ?, epa_total = ?, epa_auto = ?, epa_teleop = ?, epa_endgame = ?, "
            + "epa_unitless = ?, epa_norm = ?, breakdown_json = ?, last_sync = ?, last_status = ? "
            + "WHERE tba_event_key = ? AND team_number = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      setStringOrNull(ps, 1, r.tournamentId());
      setDoubleOrNull(ps, 2, r.epaTotal());
      setDoubleOrNull(ps, 3, r.epaAuto());
      setDoubleOrNull(ps, 4, r.epaTeleop());
      setDoubleOrNull(ps, 5, r.epaEndgame());
      setDoubleOrNull(ps, 6, r.epaUnitless());
      setDoubleOrNull(ps, 7, r.epaNorm());
      setStringOrNull(ps, 8, r.breakdownJson());
      setInstantOrNull(ps, 9, r.lastSync());
      setIntegerOrNull(ps, 10, r.lastStatus());
      ps.setString(11, r.tbaEventKey());
      ps.setInt(12, r.teamNumber());
      ps.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to update statbotics team event ("
              + r.tbaEventKey()
              + ","
              + r.teamNumber()
              + "): "
              + e.getMessage(),
          e);
    }
  }

  /** Upsert convenience — insert if absent, update in place otherwise. */
  public void upsert(StatboticsTeamEventRecord r) {
    if (find(r.tbaEventKey(), r.teamNumber()) != null) {
      update(r);
    } else {
      save(r);
    }
  }

  /** Deletes all rows for a given event key — tests use this for cleanup. */
  @Transactional
  public int deleteByTbaEventKey(String tbaEventKey) {
    String sql = "DELETE FROM RB_STATBOTICS_TEAM_EVENT WHERE tba_event_key = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, tbaEventKey);
      return ps.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to delete statbotics team events for "
              + tbaEventKey
              + ": "
              + e.getMessage(),
          e);
    }
  }

  private static StatboticsTeamEventRecord mapRow(ResultSet rs) throws java.sql.SQLException {
    return new StatboticsTeamEventRecord(
        rs.getString("tba_event_key"),
        rs.getInt("team_number"),
        rs.getString("tournament_id"),
        getDoubleOrNull(rs, "epa_total"),
        getDoubleOrNull(rs, "epa_auto"),
        getDoubleOrNull(rs, "epa_teleop"),
        getDoubleOrNull(rs, "epa_endgame"),
        getDoubleOrNull(rs, "epa_unitless"),
        getDoubleOrNull(rs, "epa_norm"),
        rs.getString("breakdown_json"),
        rs.getTimestamp("last_sync") == null ? null : rs.getTimestamp("last_sync").toInstant(),
        getIntegerOrNull(rs, "last_status"));
  }

  private static void bindAll(PreparedStatement ps, StatboticsTeamEventRecord r)
      throws java.sql.SQLException {
    ps.setString(1, r.tbaEventKey());
    ps.setInt(2, r.teamNumber());
    setStringOrNull(ps, 3, r.tournamentId());
    setDoubleOrNull(ps, 4, r.epaTotal());
    setDoubleOrNull(ps, 5, r.epaAuto());
    setDoubleOrNull(ps, 6, r.epaTeleop());
    setDoubleOrNull(ps, 7, r.epaEndgame());
    setDoubleOrNull(ps, 8, r.epaUnitless());
    setDoubleOrNull(ps, 9, r.epaNorm());
    setStringOrNull(ps, 10, r.breakdownJson());
    setInstantOrNull(ps, 11, r.lastSync());
    setIntegerOrNull(ps, 12, r.lastStatus());
  }

  private static void setStringOrNull(PreparedStatement ps, int idx, String v)
      throws java.sql.SQLException {
    if (v == null) ps.setNull(idx, Types.VARCHAR);
    else ps.setString(idx, v);
  }

  private static void setDoubleOrNull(PreparedStatement ps, int idx, Double v)
      throws java.sql.SQLException {
    if (v == null) ps.setNull(idx, Types.DOUBLE);
    else ps.setDouble(idx, v);
  }

  private static void setIntegerOrNull(PreparedStatement ps, int idx, Integer v)
      throws java.sql.SQLException {
    if (v == null) ps.setNull(idx, Types.INTEGER);
    else ps.setInt(idx, v);
  }

  private static void setInstantOrNull(PreparedStatement ps, int idx, Instant v)
      throws java.sql.SQLException {
    if (v == null) ps.setNull(idx, Types.TIMESTAMP);
    else ps.setTimestamp(idx, Timestamp.from(v));
  }

  private static Double getDoubleOrNull(ResultSet rs, String col) throws java.sql.SQLException {
    double v = rs.getDouble(col);
    return rs.wasNull() ? null : v;
  }

  private static Integer getIntegerOrNull(ResultSet rs, String col) throws java.sql.SQLException {
    int v = rs.getInt(col);
    return rs.wasNull() ? null : v;
  }
}
