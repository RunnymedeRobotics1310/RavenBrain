package ca.team1310.ravenbrain.tbaapi.service;

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
 * Raw-JDBC repository for {@code RB_TBA_EVENT_OPRS}. Raw JDBC — matching the {@code
 * StatboticsTeamEventRepo} precedent from Unit 2 — is cleaner than {@code CrudRepository} with an
 * {@code @EmbeddedId} for this table's composite primary key.
 *
 * <p>Exposes per-event read, per-row existence check, insert, update, a find-all helper used by
 * tests, and a status-only bulk update that preserves prior metric columns on non-200 fetches.
 * The sync service owns all writes.
 */
@Slf4j
@Singleton
public class TbaEventOprsRepo {

  private final DataSource dataSource;

  TbaEventOprsRepo(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** All rows for a single TBA event — drives the sync's prior-row lookup and tests. */
  @Transactional
  public List<TbaEventOprsRecord> findByTbaEventKey(String tbaEventKey) {
    String sql =
        "SELECT tba_event_key, team_number, opr, dpr, ccwm, last_sync, last_status "
            + "FROM RB_TBA_EVENT_OPRS WHERE tba_event_key = ?";
    List<TbaEventOprsRecord> results = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, tbaEventKey);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) results.add(mapRow(rs));
      }
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to find TBA OPR rows for " + tbaEventKey + ": " + e.getMessage(), e);
    }
    return results;
  }

  /** All rows across all events — used by tests for cleanup and cross-row assertions. */
  @Transactional
  public List<TbaEventOprsRecord> findAll() {
    String sql =
        "SELECT tba_event_key, team_number, opr, dpr, ccwm, last_sync, last_status "
            + "FROM RB_TBA_EVENT_OPRS";
    List<TbaEventOprsRecord> results = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) results.add(mapRow(rs));
    } catch (Exception e) {
      throw new RuntimeException("Failed to list TBA OPR rows: " + e.getMessage(), e);
    }
    return results;
  }

  /** Single-row lookup by composite key. */
  @Transactional
  public TbaEventOprsRecord find(String tbaEventKey, int teamNumber) {
    String sql =
        "SELECT tba_event_key, team_number, opr, dpr, ccwm, last_sync, last_status "
            + "FROM RB_TBA_EVENT_OPRS WHERE tba_event_key = ? AND team_number = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, tbaEventKey);
      ps.setInt(2, teamNumber);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return mapRow(rs);
      }
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to find TBA OPR row ("
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
  public void save(TbaEventOprsRecord r) {
    String sql =
        "INSERT INTO RB_TBA_EVENT_OPRS "
            + "(tba_event_key, team_number, opr, dpr, ccwm, last_sync, last_status) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, r.tbaEventKey());
      ps.setInt(2, r.teamNumber());
      setDoubleOrNull(ps, 3, r.opr());
      setDoubleOrNull(ps, 4, r.dpr());
      setDoubleOrNull(ps, 5, r.ccwm());
      setInstantOrNull(ps, 6, r.lastSync());
      setIntegerOrNull(ps, 7, r.lastStatus());
      ps.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to save TBA OPR row ("
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
  public void update(TbaEventOprsRecord r) {
    String sql =
        "UPDATE RB_TBA_EVENT_OPRS SET "
            + "opr = ?, dpr = ?, ccwm = ?, last_sync = ?, last_status = ? "
            + "WHERE tba_event_key = ? AND team_number = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      setDoubleOrNull(ps, 1, r.opr());
      setDoubleOrNull(ps, 2, r.dpr());
      setDoubleOrNull(ps, 3, r.ccwm());
      setInstantOrNull(ps, 4, r.lastSync());
      setIntegerOrNull(ps, 5, r.lastStatus());
      ps.setString(6, r.tbaEventKey());
      ps.setInt(7, r.teamNumber());
      ps.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to update TBA OPR row ("
              + r.tbaEventKey()
              + ","
              + r.teamNumber()
              + "): "
              + e.getMessage(),
          e);
    }
  }

  /** Upsert convenience — insert if absent, update in place otherwise. */
  public void upsert(TbaEventOprsRecord r) {
    if (find(r.tbaEventKey(), r.teamNumber()) != null) {
      update(r);
    } else {
      save(r);
    }
  }

  /**
   * Bump {@code last_status} on every row belonging to this event so the UI surfaces staleness.
   * Preserves {@code opr}, {@code dpr}, {@code ccwm}, and {@code last_sync} so the last successful
   * data keeps flowing through the read path.
   *
   * <p>No-op if no prior rows exist — a failed fetch with no history produces no row (consistent
   * with {@code StatboticsTeamEventSyncService.bumpStatusOnExistingRows}).
   */
  @Transactional
  public void persistStatusOnly(String tbaEventKey, int status) {
    String sql =
        "UPDATE RB_TBA_EVENT_OPRS SET last_status = ? WHERE tba_event_key = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, status);
      ps.setString(2, tbaEventKey);
      ps.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to bump TBA OPR status for " + tbaEventKey + ": " + e.getMessage(), e);
    }
  }

  /** Deletes all rows for a given event key — tests use this for cleanup. */
  @Transactional
  public int deleteByTbaEventKey(String tbaEventKey) {
    String sql = "DELETE FROM RB_TBA_EVENT_OPRS WHERE tba_event_key = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, tbaEventKey);
      return ps.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to delete TBA OPR rows for " + tbaEventKey + ": " + e.getMessage(), e);
    }
  }

  private static TbaEventOprsRecord mapRow(ResultSet rs) throws java.sql.SQLException {
    return new TbaEventOprsRecord(
        rs.getString("tba_event_key"),
        rs.getInt("team_number"),
        getDoubleOrNull(rs, "opr"),
        getDoubleOrNull(rs, "dpr"),
        getDoubleOrNull(rs, "ccwm"),
        rs.getTimestamp("last_sync") == null ? null : rs.getTimestamp("last_sync").toInstant(),
        getIntegerOrNull(rs, "last_status"));
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
