package ca.team1310.ravenbrain.telemetry;

import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class TelemetryService {

  private final TelemetrySessionRepository sessionRepository;
  private final DataSource dataSource;

  TelemetryService(TelemetrySessionRepository sessionRepository, DataSource dataSource) {
    this.sessionRepository = sessionRepository;
    this.dataSource = dataSource;
  }

  public TelemetrySession createSession(
      String sessionId, int teamNumber, String robotIp, Instant startedAt) {
    Optional<TelemetrySession> existing = sessionRepository.findBySessionId(sessionId);
    if (existing.isPresent()) {
      log.info("Telemetry session {} already exists, returning existing", sessionId);
      return existing.get();
    }
    var session =
        new TelemetrySession(
            null, sessionId, teamNumber, robotIp, startedAt, null, 0, 0, startedAt);
    session = sessionRepository.save(session);
    log.info("Created telemetry session {} for team {}", sessionId, teamNumber);
    return session;
  }

  @Transactional
  public void bulkInsertEntries(long sessionId, List<TelemetryApi.TelemetryEntryRequest> entries) {
    String insertSql =
        "INSERT INTO RB_TELEMETRY_ENTRY (session_id, ts, entry_type, nt_key, nt_type, nt_value, fms_raw)"
            + " VALUES (?,?,?,?,?,?,?)";
    String updateCountSql =
        "UPDATE RB_TELEMETRY_SESSION SET uploaded_count = uploaded_count + ? WHERE id = ?";
    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);
      try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
        int count = 0;
        for (TelemetryApi.TelemetryEntryRequest entry : entries) {
          ps.setLong(1, sessionId);
          ps.setTimestamp(2, Timestamp.from(entry.ts()));
          ps.setString(3, entry.entryType());
          ps.setString(4, entry.ntKey());
          ps.setString(5, entry.ntType());
          ps.setString(6, entry.ntValue());
          if (entry.fmsRaw() != null) {
            ps.setInt(7, entry.fmsRaw());
          } else {
            ps.setNull(7, java.sql.Types.INTEGER);
          }
          ps.addBatch();
          count++;
        }
        ps.executeBatch();

        try (PreparedStatement up = conn.prepareStatement(updateCountSql)) {
          up.setInt(1, count);
          up.setLong(2, sessionId);
          up.executeUpdate();
        }

        conn.commit();
        log.info("Inserted {} telemetry entries for session {}", count, sessionId);
      } catch (Exception e) {
        conn.rollback();
        throw e;
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to bulk insert telemetry entries: " + e.getMessage(), e);
    }
  }

  public void completeSession(String sessionId, Instant endedAt, int entryCount) {
    Optional<TelemetrySession> existing = sessionRepository.findBySessionId(sessionId);
    if (existing.isEmpty()) {
      throw new RuntimeException("Session not found: " + sessionId);
    }
    TelemetrySession session = existing.get();
    TelemetrySession updated =
        new TelemetrySession(
            session.id(),
            session.sessionId(),
            session.teamNumber(),
            session.robotIp(),
            session.startedAt(),
            endedAt,
            entryCount,
            session.uploadedCount(),
            session.createdAt());
    sessionRepository.update(updated);
    log.info("Completed telemetry session {} with {} entries", sessionId, entryCount);
  }

  public Optional<TelemetrySession> findSessionBySessionId(String sessionId) {
    return sessionRepository.findBySessionId(sessionId);
  }
}
