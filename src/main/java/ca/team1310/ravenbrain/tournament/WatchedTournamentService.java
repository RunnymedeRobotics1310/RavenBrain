package ca.team1310.ravenbrain.tournament;

import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the set of tournaments whose schedules and scores should be actively synced from the FRC
 * API. Tournaments are added to the watch list when a user views their schedule. The in-memory
 * cache avoids a DB hit on every request.
 *
 * @author Tony Field
 * @since 2026-03-22
 */
@Slf4j
@Singleton
public class WatchedTournamentService {

  private final DataSource dataSource;
  private final Set<String> watchedIds = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean cacheLoaded = new AtomicBoolean(false);

  WatchedTournamentService(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Transactional
  void ensureCacheLoaded() {
    if (cacheLoaded.compareAndSet(false, true)) {
      String sql = "SELECT tournament_id FROM RB_WATCHED_TOURNAMENT";
      try (Connection conn = dataSource.getConnection();
          PreparedStatement ps = conn.prepareStatement(sql);
          ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          watchedIds.add(rs.getString("tournament_id"));
        }
        log.info("Loaded {} watched tournaments into cache", watchedIds.size());
      } catch (Exception e) {
        cacheLoaded.set(false);
        log.warn("Failed to load watched tournaments cache: {}", e.getMessage());
      }
    }
  }

  /**
   * Register a tournament to be watched. Idempotent — safe to call multiple times for the same
   * tournament.
   */
  @Transactional
  public void watch(String tournamentId) {
    ensureCacheLoaded();
    if (watchedIds.contains(tournamentId)) {
      return;
    }
    String sql = "INSERT INTO RB_WATCHED_TOURNAMENT (tournament_id) VALUES (?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, tournamentId);
      ps.executeUpdate();
      watchedIds.add(tournamentId);
      log.info("Tournament {} added to watch list", tournamentId);
    } catch (java.sql.SQLIntegrityConstraintViolationException e) {
      if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
        // Already watched — update the in-memory cache to match
        watchedIds.add(tournamentId);
      } else {
        // FK violation — tournament doesn't exist in RB_TOURNAMENT
        log.debug("Cannot watch tournament {}: not found in tournament table", tournamentId);
      }
    } catch (Exception e) {
      log.warn("Failed to watch tournament {}: {}", tournamentId, e.getMessage());
    }
  }

  /** Bulk-watch a list of tournament IDs (used for owner team tournaments). */
  public void watchAll(List<String> tournamentIds) {
    for (String id : tournamentIds) {
      watch(id);
    }
  }

  /** Check whether a tournament is currently watched (in-memory only, no DB hit). */
  public boolean isWatched(String tournamentId) {
    ensureCacheLoaded();
    return watchedIds.contains(tournamentId);
  }

  /** Return an unmodifiable view of the watched tournament IDs. */
  public Set<String> getWatchedTournamentIds() {
    ensureCacheLoaded();
    return Collections.unmodifiableSet(watchedIds);
  }
}
