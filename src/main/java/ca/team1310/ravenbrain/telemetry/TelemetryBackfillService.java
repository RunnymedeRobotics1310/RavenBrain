package ca.team1310.ravenbrain.telemetry;

import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;

/**
 * One-shot backfill of match identity fields on {@code RB_TELEMETRY_SESSION} rows where {@code
 * match_label} is null. Reuses {@link MatchIdentityEnricher} so the behavior matches ingest-time
 * enrichment exactly.
 */
@Singleton
@Slf4j
public class TelemetryBackfillService {

  private final DataSource dataSource;
  private final MatchIdentityEnricher enricher;

  TelemetryBackfillService(DataSource dataSource, MatchIdentityEnricher enricher) {
    this.dataSource = dataSource;
    this.enricher = enricher;
  }

  @Serdeable
  public record BackfillResult(int examined, int updated, int skipped) {}

  @Transactional
  public BackfillResult backfill(boolean force) {
    int examined = 0;
    int updated = 0;
    int skipped = 0;
    try (Connection conn = dataSource.getConnection()) {
      String selectSessions =
          force
              ? "SELECT id, started_at, match_label FROM RB_TELEMETRY_SESSION ORDER BY id"
              : "SELECT id, started_at, match_label FROM RB_TELEMETRY_SESSION"
                  + " WHERE match_label IS NULL ORDER BY id";
      List<SessionRow> rows = new ArrayList<>();
      try (PreparedStatement ps = conn.prepareStatement(selectSessions);
          ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          Timestamp ts = rs.getTimestamp("started_at");
          rows.add(
              new SessionRow(
                  rs.getLong("id"),
                  ts == null ? null : ts.toInstant(),
                  rs.getString("match_label")));
        }
      }
      for (SessionRow row : rows) {
        examined++;
        if (row.startedAt == null) {
          skipped++;
          continue;
        }
        if (row.matchLabel != null && !force) {
          skipped++;
          continue;
        }
        List<TelemetryApi.TelemetryEntryRequest> fmsEntries = loadFmsEntries(conn, row.id);
        if (fmsEntries.isEmpty()) {
          skipped++;
          continue;
        }
        MatchIdentityEnricher.Result result = enricher.enrich(fmsEntries, row.startedAt);
        MatchLabelParser.ParsedMatchLabel parsed = result.parsedLabel();
        if (parsed == null || parsed.rawLabel() == null) {
          skipped++;
          continue;
        }
        if (writeIdentity(conn, row.id, parsed, result, force)) {
          updated++;
        } else {
          skipped++;
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Backfill failed: " + e.getMessage(), e);
    }
    log.info(
        "Telemetry backfill complete: examined={}, updated={}, skipped={}",
        examined,
        updated,
        skipped);
    return new BackfillResult(examined, updated, skipped);
  }

  private List<TelemetryApi.TelemetryEntryRequest> loadFmsEntries(Connection conn, long sessionId)
      throws Exception {
    String sql =
        "SELECT ts, entry_type, nt_key, nt_type, nt_value, fms_raw FROM RB_TELEMETRY_ENTRY"
            + " WHERE session_id = ? AND nt_key IN (?, ?) ORDER BY ts";
    List<TelemetryApi.TelemetryEntryRequest> out = new ArrayList<>();
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, sessionId);
      ps.setString(2, MatchIdentityEnricher.FMS_MATCH_NUMBER_KEY);
      ps.setString(3, MatchIdentityEnricher.FMS_EVENT_NAME_KEY);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          Timestamp t = rs.getTimestamp("ts");
          Integer fmsRaw = rs.getObject("fms_raw") == null ? null : rs.getInt("fms_raw");
          out.add(
              new TelemetryApi.TelemetryEntryRequest(
                  t == null ? null : t.toInstant(),
                  rs.getString("entry_type"),
                  rs.getString("nt_key"),
                  rs.getString("nt_type"),
                  rs.getString("nt_value"),
                  fmsRaw));
        }
      }
    }
    return out;
  }

  private boolean writeIdentity(
      Connection conn,
      long sessionId,
      MatchLabelParser.ParsedMatchLabel parsed,
      MatchIdentityEnricher.Result result,
      boolean force)
      throws Exception {
    String sql =
        force
            ? "UPDATE RB_TELEMETRY_SESSION SET tournament_id = ?, match_label = ?, match_level = ?,"
                + " match_number = ?, playoff_round = ?, fms_event_name = ? WHERE id = ?"
            : "UPDATE RB_TELEMETRY_SESSION SET tournament_id = ?, match_label = ?, match_level = ?,"
                + " match_number = ?, playoff_round = ?, fms_event_name = ? WHERE id = ?"
                + " AND match_label IS NULL";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, result.tournamentId());
      ps.setString(2, parsed.rawLabel());
      TournamentLevel level = parsed.level();
      ps.setString(3, level == null ? null : level.name());
      if (parsed.number() == null) {
        ps.setNull(4, java.sql.Types.INTEGER);
      } else {
        ps.setInt(4, parsed.number());
      }
      ps.setString(5, parsed.playoffRound());
      ps.setString(6, result.fmsEventName());
      ps.setLong(7, sessionId);
      return ps.executeUpdate() > 0;
    }
  }

  private record SessionRow(long id, Instant startedAt, String matchLabel) {}
}
