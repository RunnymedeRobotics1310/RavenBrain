package ca.team1310.ravenbrain.telemetry;

import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
  private final TelemetryEntryRepository entryRepository;
  private final DataSource dataSource;
  private final MatchIdentityEnricher enricher;

  TelemetryService(
      TelemetrySessionRepository sessionRepository,
      TelemetryEntryRepository entryRepository,
      DataSource dataSource,
      MatchIdentityEnricher enricher) {
    this.sessionRepository = sessionRepository;
    this.entryRepository = entryRepository;
    this.dataSource = dataSource;
    this.enricher = enricher;
  }

  public List<String> findDistinctNtKeys() {
    return entryRepository.findDistinctNtKeys();
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
            null,
            sessionId,
            teamNumber,
            robotIp,
            startedAt,
            null,
            0,
            0,
            startedAt,
            null,
            null,
            null,
            null,
            null,
            null);
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

        enrichMatchIdentity(conn, sessionId, entries);

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

  /**
   * Resolve match identity for the session if not already set. First-write-wins: once {@code
   * match_label} is non-null we never overwrite it. Failures here are logged but must not abort the
   * entry insert — the caller has already added enrichment calls inside the same transaction, so a
   * throw would roll the entries back.
   */
  private void enrichMatchIdentity(
      Connection conn, long sessionId, List<TelemetryApi.TelemetryEntryRequest> entries) {
    try {
      String existingLabel = null;
      Instant startedAt = null;
      try (PreparedStatement ps =
          conn.prepareStatement(
              "SELECT match_label, started_at FROM RB_TELEMETRY_SESSION WHERE id = ?")) {
        ps.setLong(1, sessionId);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            existingLabel = rs.getString("match_label");
            Timestamp ts = rs.getTimestamp("started_at");
            startedAt = ts == null ? null : ts.toInstant();
          }
        }
      }
      if (existingLabel != null) {
        // First-write-wins. If a different value appears now, record it at DEBUG but don't write.
        for (TelemetryApi.TelemetryEntryRequest e : entries) {
          if (MatchIdentityEnricher.FMS_MATCH_NUMBER_KEY.equals(e.ntKey())
              && e.ntValue() != null
              && !e.ntValue().isBlank()
              && !e.ntValue().equals(existingLabel)) {
            log.debug(
                "Session {} already has match_label={}, ignoring new value {}",
                sessionId,
                existingLabel,
                e.ntValue());
            break;
          }
        }
        return;
      }
      if (startedAt == null) {
        return;
      }
      MatchIdentityEnricher.Result result = enricher.enrich(entries, startedAt);
      if (result == null || !result.hasAny()) {
        return;
      }
      MatchLabelParser.ParsedMatchLabel parsed = result.parsedLabel();
      if (parsed == null || parsed.rawLabel() == null) {
        // Only FMS event name observed, no match label — don't partially populate.
        return;
      }
      try (PreparedStatement ps =
          conn.prepareStatement(
              "UPDATE RB_TELEMETRY_SESSION SET tournament_id = ?, match_label = ?, match_level = ?,"
                  + " match_number = ?, playoff_round = ?, fms_event_name = ? WHERE id = ?"
                  + " AND match_label IS NULL")) {
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
        int updated = ps.executeUpdate();
        if (updated > 0) {
          log.info(
              "Enriched session {} with match_label={}, tournament_id={}",
              sessionId,
              parsed.rawLabel(),
              result.tournamentId());
        }
      }
    } catch (Exception ex) {
      log.warn("Match identity enrichment failed for session {}: {}", sessionId, ex.getMessage());
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
            session.createdAt(),
            session.tournamentId(),
            session.matchLabel(),
            session.matchLevel(),
            session.matchNumber(),
            session.playoffRound(),
            session.fmsEventName());
    sessionRepository.update(updated);
    log.info("Completed telemetry session {} with {} entries", sessionId, entryCount);
  }

  public Optional<TelemetrySession> findSessionBySessionId(String sessionId) {
    return sessionRepository.findBySessionId(sessionId);
  }

  public List<TelemetrySession> findSessionsForMatch(
      String tournamentId, TournamentLevel matchLevel, int matchNumber) {
    return sessionRepository
        .findAllByTournamentIdAndMatchLevelAndMatchNumberOrderByStartedAtAsc(
            tournamentId, matchLevel, matchNumber);
  }
}
