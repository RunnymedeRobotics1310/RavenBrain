package ca.team1310.ravenbrain.tournament;

import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

  @Transactional
  public void replaceTeamsForTournament(String tournamentId, List<Integer> teamNumbers) {
    replaceTeamsForTournament(tournamentId, teamNumbers, Map.of());
  }

  @Transactional
  public void replaceTeamsForTournament(
      String tournamentId, List<Integer> teamNumbers, Map<Integer, String> teamNames) {
    try (Connection conn = dataSource.getConnection()) {
      try (PreparedStatement ps =
          conn.prepareStatement("DELETE FROM RB_TEAM_TOURNAMENT WHERE tournament_id = ?")) {
        ps.setString(1, tournamentId);
        ps.executeUpdate();
      }
      if (!teamNumbers.isEmpty()) {
        try (PreparedStatement ps =
            conn.prepareStatement(
                "INSERT INTO RB_TEAM_TOURNAMENT (tournament_id, team_number, team_name) VALUES (?, ?, ?)")) {
          for (int teamNumber : teamNumbers) {
            ps.setString(1, tournamentId);
            ps.setInt(2, teamNumber);
            String name = teamNames.get(teamNumber);
            if (name != null) {
              ps.setString(3, name);
            } else {
              ps.setNull(3, java.sql.Types.VARCHAR);
            }
            ps.addBatch();
          }
          ps.executeBatch();
        }
      }
      log.info(
          "Replaced teams for tournament {}: {} teams", tournamentId, teamNumbers.size());
    } catch (Exception e) {
      throw new RuntimeException("Failed to replace teams for tournament: " + e.getMessage(), e);
    }
  }

  @Transactional
  public List<Integer> findTeamNumbersForTournament(String tournamentId) {
    String sql = "SELECT team_number FROM RB_TEAM_TOURNAMENT WHERE tournament_id = ?";
    List<Integer> teamNumbers = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, tournamentId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          teamNumbers.add(rs.getInt("team_number"));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to find teams for tournament: " + e.getMessage(), e);
    }
    return teamNumbers;
  }

  @Transactional
  public Map<Integer, String> findTeamNamesForTournament(String tournamentId) {
    String sql =
        "SELECT team_number, team_name FROM RB_TEAM_TOURNAMENT WHERE tournament_id = ?";
    Map<Integer, String> names = new java.util.HashMap<>();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, tournamentId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          String name = rs.getString("team_name");
          if (name != null) {
            names.put(rs.getInt("team_number"), name);
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to find team names for tournament: " + e.getMessage(), e);
    }
    return names;
  }
}
