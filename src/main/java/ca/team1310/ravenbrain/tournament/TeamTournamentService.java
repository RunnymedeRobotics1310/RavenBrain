package ca.team1310.ravenbrain.tournament;

import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class TeamTournamentService {

  private final DataSource dataSource;

  TeamTournamentService(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Transactional
  public List<String> findTournamentIdsForTeam(int teamNumber) {
    String sql = "SELECT tournament_id FROM RB_TEAM_TOURNAMENT WHERE team_number = ?";
    List<String> ids = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, teamNumber);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          ids.add(rs.getString("tournament_id"));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to find team tournaments: " + e.getMessage(), e);
    }
    return ids;
  }

  @Transactional
  public void replaceTeamTournaments(int teamNumber, List<String> tournamentIds) {
    try (Connection conn = dataSource.getConnection()) {
      try (PreparedStatement ps =
          conn.prepareStatement("DELETE FROM RB_TEAM_TOURNAMENT WHERE team_number = ?")) {
        ps.setInt(1, teamNumber);
        ps.executeUpdate();
      }
      if (!tournamentIds.isEmpty()) {
        try (PreparedStatement ps =
            conn.prepareStatement(
                "INSERT INTO RB_TEAM_TOURNAMENT (tournament_id, team_number) VALUES (?, ?)")) {
          for (String id : tournamentIds) {
            ps.setString(1, id);
            ps.setInt(2, teamNumber);
            ps.addBatch();
          }
          ps.executeBatch();
        }
      }
      log.info("Replaced team tournaments for team {}: {} entries", teamNumber, tournamentIds.size());
    } catch (Exception e) {
      throw new RuntimeException("Failed to replace team tournaments: " + e.getMessage(), e);
    }
  }
}
