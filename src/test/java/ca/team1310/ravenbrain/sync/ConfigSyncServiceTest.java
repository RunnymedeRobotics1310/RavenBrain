package ca.team1310.ravenbrain.sync;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ca.team1310.ravenbrain.eventtype.EventType;
import ca.team1310.ravenbrain.eventtype.EventTypeService;
import ca.team1310.ravenbrain.schedule.ScheduleService;
import ca.team1310.ravenbrain.sequencetype.SequenceTypeService;
import ca.team1310.ravenbrain.strategyarea.StrategyArea;
import ca.team1310.ravenbrain.strategyarea.StrategyAreaService;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

@MicronautTest(transactional = false)
public class ConfigSyncServiceTest {

  @Inject ConfigSyncService configSyncService;

  @Inject RemoteRavenBrainClient remoteClient;

  @Inject StrategyAreaService strategyAreaService;
  @Inject EventTypeService eventTypeService;
  @Inject SequenceTypeService sequenceTypeService;
  @Inject TournamentService tournamentService;
  @Inject ScheduleService scheduleService;

  @MockBean(RemoteRavenBrainClient.class)
  RemoteRavenBrainClient mockRemoteClient() {
    return mock(RemoteRavenBrainClient.class);
  }

  @Test
  void testSyncFromSource() throws Exception {
    // Pre-populate "old" data that should be wiped by the sync
    StrategyArea oldSa = strategyAreaService.create(new StrategyArea(0, 2025, "OLD", "Old Area", "should be wiped"));
    eventTypeService.create(
        new EventType("OLD-EVENT", "Old Event", "should be wiped", 2025, oldSa.id(), false, false));

    // Verify old data exists
    assertFalse(strategyAreaService.list().isEmpty());
    assertFalse(eventTypeService.list().isEmpty());

    // Configure mock remote client
    when(remoteClient.authenticate(anyString(), anyString(), anyString()))
        .thenReturn("mock-jwt-token");

    // Strategy areas JSON
    String strategyAreasJson =
        """
        [
          {"id": 10, "frcyear": 2026, "code": "AUTO", "name": "Autonomous", "description": "Auto period"},
          {"id": 20, "frcyear": 2026, "code": "TELE", "name": "Teleop", "description": "Teleop period"}
        ]
        """;

    // Event types JSON
    String eventTypesJson =
        """
        [
          {"eventtype": "AUTO-SCORE", "name": "Auto Score", "description": "Score during auto", "frcyear": 2026, "strategyareaId": 10, "showNote": true, "showQuantity": false},
          {"eventtype": "TELE-SCORE", "name": "Teleop Score", "description": "Score during teleop", "frcyear": 2026, "strategyareaId": 20, "showNote": false, "showQuantity": true},
          {"eventtype": "TELE-ASSIST", "name": "Teleop Assist", "description": "Assist during teleop", "frcyear": 2026, "strategyareaId": 20, "showNote": false, "showQuantity": false}
        ]
        """;

    // Sequence types JSON (with nested events)
    String sequenceTypesJson =
        """
        [
          {
            "id": 100,
            "code": "AUTO-SEQ",
            "name": "Auto Sequence",
            "description": "Full auto sequence",
            "frcyear": 2026,
            "disabled": false,
            "strategyareaId": 10,
            "events": [
              {"id": 200, "eventtype": {"eventtype": "AUTO-SCORE", "name": "Auto Score", "description": "Score during auto", "frcyear": 2026, "strategyareaId": 10, "showNote": true, "showQuantity": false}, "startOfSequence": true, "endOfSequence": false},
              {"id": 201, "eventtype": {"eventtype": "TELE-SCORE", "name": "Teleop Score", "description": "Score during teleop", "frcyear": 2026, "strategyareaId": 20, "showNote": false, "showQuantity": true}, "startOfSequence": false, "endOfSequence": true}
            ]
          }
        ]
        """;

    // Tournaments JSON
    String tournamentsJson =
        """
        [
          {"id": "2026onham", "code": "ONHAM", "season": 2026, "name": "Hamilton District", "startTime": 1740000000, "endTime": 1740100000}
        ]
        """;

    // Schedules JSON for the tournament
    String schedulesJson =
        """
        [
          {"id": 500, "tournamentId": "2026onham", "level": "Qualification", "match": 1, "red1": 1310, "red2": 2056, "red3": 4917, "blue1": 1114, "blue2": 2200, "blue3": 3456},
          {"id": 501, "tournamentId": "2026onham", "level": "Qualification", "match": 2, "red1": 1310, "red2": 1114, "red3": 3456, "blue1": 2056, "blue2": 4917, "blue3": 2200}
        ]
        """;

    when(remoteClient.fetchJson(anyString(), eq("mock-jwt-token"), eq("/api/strategy-areas")))
        .thenReturn(strategyAreasJson);
    when(remoteClient.fetchJson(anyString(), eq("mock-jwt-token"), eq("/api/event-types")))
        .thenReturn(eventTypesJson);
    when(remoteClient.fetchJson(anyString(), eq("mock-jwt-token"), eq("/api/sequence-types")))
        .thenReturn(sequenceTypesJson);
    when(remoteClient.fetchJson(anyString(), eq("mock-jwt-token"), eq("/api/tournament")))
        .thenReturn(tournamentsJson);
    when(remoteClient.fetchJson(anyString(), eq("mock-jwt-token"), eq("/api/schedule/2026onham")))
        .thenReturn(schedulesJson);

    // Execute sync
    SyncRequest request = new SyncRequest("http://source:8888", "user", "pass");
    SyncResult result = configSyncService.syncFromSource(request);

    // Verify counts
    assertEquals(2, result.strategyAreas());
    assertEquals(3, result.eventTypes());
    assertEquals(1, result.sequenceTypes());
    assertEquals(2, result.sequenceEvents());
    assertEquals(1, result.tournaments());
    assertEquals(2, result.schedules());
    assertTrue(result.message().contains("http://source:8888"));

    // Verify old data is gone and new data is present
    List<StrategyArea> areas = strategyAreaService.list();
    assertEquals(2, areas.size());
    assertTrue(areas.stream().anyMatch(a -> a.id() == 10 && "AUTO".equals(a.code())));
    assertTrue(areas.stream().anyMatch(a -> a.id() == 20 && "TELE".equals(a.code())));

    List<EventType> types = eventTypeService.list();
    assertEquals(3, types.size());
    assertTrue(types.stream().anyMatch(e -> "AUTO-SCORE".equals(e.eventtype())));
    assertTrue(types.stream().anyMatch(e -> "TELE-SCORE".equals(e.eventtype())));
    assertTrue(types.stream().anyMatch(e -> "TELE-ASSIST".equals(e.eventtype())));

    // Verify FK relationships
    EventType autoScore = types.stream().filter(e -> "AUTO-SCORE".equals(e.eventtype())).findFirst().orElseThrow();
    assertEquals(10L, autoScore.strategyareaId());
    EventType teleScore = types.stream().filter(e -> "TELE-SCORE".equals(e.eventtype())).findFirst().orElseThrow();
    assertEquals(20L, teleScore.strategyareaId());

    // Verify sequence types
    var seqTypes = sequenceTypeService.list();
    assertEquals(1, seqTypes.size());
    assertEquals(100L, seqTypes.getFirst().id());
    assertEquals("AUTO-SEQ", seqTypes.getFirst().code());
    assertEquals(10L, seqTypes.getFirst().strategyareaId());

    // Verify sequence events
    var seqEvents = seqTypes.getFirst().events();
    assertEquals(2, seqEvents.size());
    assertTrue(seqEvents.stream().anyMatch(e -> e.id() == 200 && e.startOfSequence()));
    assertTrue(seqEvents.stream().anyMatch(e -> e.id() == 201 && e.endOfSequence()));

    // Verify tournaments
    List<TournamentRecord> tList = tournamentService.findAllSortByStartTime();
    assertEquals(1, tList.size());
    assertEquals("2026onham", tList.getFirst().id());
    assertEquals("Hamilton District", tList.getFirst().name());

    // Verify schedules
    var scheduleList = scheduleService.findAllByTournamentIdOrderByMatch("2026onham");
    assertEquals(2, scheduleList.size());
    assertTrue(scheduleList.stream().anyMatch(s -> s.id() == 500 && s.match() == 1));
    assertTrue(scheduleList.stream().anyMatch(s -> s.id() == 501 && s.match() == 2));

    // Verify auto-increment is set correctly
    // Creating a new strategy area should get an ID > 20 (max synced ID)
    StrategyArea newSa = strategyAreaService.create(new StrategyArea(0, 2026, "NEW", "New", "test"));
    assertTrue(newSa.id() > 20, "Auto-increment should be reset; got id=" + newSa.id());
  }
}
