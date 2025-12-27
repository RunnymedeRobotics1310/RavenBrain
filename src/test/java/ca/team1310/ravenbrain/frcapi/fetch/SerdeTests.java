package ca.team1310.ravenbrain.frcapi.fetch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ca.team1310.ravenbrain.frcapi.model.Event;
import ca.team1310.ravenbrain.frcapi.model.EventResponse;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * @author Tony Field
 * @since 2025-09-22 00:39
 */
@Slf4j
@MicronautTest
public class SerdeTests {

  private final ObjectMapper mapper;

  public SerdeTests(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Test
  void testSerdeEvent() throws IOException {
    String json =
        "{\"allianceCount\":\"TwoAlliance\",\"weekNumber\":6,\"announcements\":[],\"code\":\"ONCMP\",\"divisionCode\":null,\"name\":\"FIRST Ontario Provincial Championship\",\"type\":\"DistrictChampionshipWithLevels\",\"districtCode\":\"ONT\",\"venue\":\"The International Centre\",\"city\":\"Mississauga\",\"stateprov\":\"ON\",\"country\":\"Canada\",\"dateStart\":\"2025-04-02T00:00:00\",\"dateEnd\":\"2025-04-05T23:59:59\",\"address\":\"6900 Airport Rd\",\"website\":\"https://firstroboticscanada.org/frc/championship/\",\"webcasts\":[],\"timezone\":\"Eastern Standard Time\"}";
    var resp = mapper.readValue(json, Event.class);
    assertNotNull(resp);
  }

  @Test
  void testSerdeEventResponse() throws IOException {
    String json =
        """
                        {"Events":[{"allianceCount":"TwoAlliance","weekNumber":6,"announcements":[],"code":"ONCMP","divisionCode":null,"name":"FIRST Ontario Provincial Championship","type":"DistrictChampionshipWithLevels","districtCode":"ONT","venue":"The International Centre","city":"Mississauga","stateprov":"ON","country":"Canada","dateStart":"2025-04-02T00:00:00","dateEnd":"2025-04-05T23:59:59","address":"6900 Airport Rd","website":"https://firstroboticscanada.org/frc/championship/","webcasts":[],"timezone":"Eastern Standard Time"},{"allianceCount":"EightAlliance","weekNumber":6,"announcements":[],"code":"ONCMP2","divisionCode":"ONCMP2","name":"FIRST Ontario Provincial Championship - Technology Division","type":"DistrictChampionshipDivision","districtCode":"ONT","venue":"The International Centre","city":"Mississauga","stateprov":"ON","country":"Canada","dateStart":"2025-04-02T00:00:00","dateEnd":"2025-04-05T23:59:59","address":"6900 Airport Rd","website":"https://firstroboticscanada.org/frc/championship/","webcasts":[],"timezone":"Eastern Standard Time"},{"allianceCount":"EightAlliance","weekNumber":4,"announcements":[],"code":"ONNOB","divisionCode":null,"name":"ONT District North Bay Event","type":"DistrictEvent","districtCode":"ONT","venue":"Nipissing University","city":"North Bay","stateprov":"ON","country":"Canada","dateStart":"2025-03-21T00:00:00","dateEnd":"2025-03-23T23:59:59","address":"Robert J. Surtees Athletics Centre 100 College Drive","website":"https://firstroboticscanada.org/frc/northbay","webcasts":[],"timezone":"Eastern Standard Time"},{"allianceCount":"EightAlliance","weekNumber":2,"announcements":[],"code":"ONSCA","divisionCode":null,"name":"ONT District Centennial College Event","type":"DistrictEvent","districtCode":"ONT","venue":"Centennial College","city":"Scarborough","stateprov":"ON","country":"Canada","dateStart":"2025-03-06T00:00:00","dateEnd":"2025-03-08T23:59:59","address":"941 Progress Ave.","website":"https://firstroboticscanada.org/frc/centennial","webcasts":[],"timezone":"Eastern Standard Time"},{"allianceCount":"EightAlliance","weekNumber":0,"announcements":[],"code":"ONSCA1","divisionCode":null,"name":"Robots@Mary Ward Day 1","type":"OffSeasonWithAzureSync","districtCode":null,"venue":"Mary Ward Catholic Secondary School","city":"Scarborough","stateprov":"ON","country":"Canada","dateStart":"2025-09-20T00:00:00","dateEnd":"2025-09-20T23:59:59","address":"3200 Kennedy Road","website":"https://www.robotsatcne.com/","webcasts":[],"timezone":"Eastern Standard Time"},{"allianceCount":"FourAlliance","weekNumber":0,"announcements":[],"code":"ONSCA2","divisionCode":null,"name":"Robots@Mary Ward Day 2","type":"OffSeasonWithAzureSync","districtCode":null,"venue":"Mary Ward Catholic Secondary School","city":"Scarborough","stateprov":"ON","country":"Canada","dateStart":"2025-09-21T00:00:00","dateEnd":"2025-09-21T23:59:59","address":"3200 Kennedy Road","website":"https://www.robotsatcne.com/","webcasts":[],"timezone":"Eastern Standard Time"}],"eventCount":6}
                        """;
    var resp = mapper.readValue(json, EventResponse.class);
    assertNotNull(resp.events());
    assertEquals(6, resp.events().size());
    assertEquals("ONCMP", resp.events().get(0).code());
  }

  @Test
  void testSerdeScheduleResponse() throws Exception {
    String json =
        """
                          {
                            "Schedule": [
                              {
                                "description": "Qualification 1",
                                "startTime": "2025-03-22T11:30:00",
                                "matchNumber": 1,
                                "field": "Primary",
                                "tournamentLevel": "Qualification",
                                "teams": [
                                  { "teamNumber": 9127, "station": "Red1", "surrogate": false },
                                  { "teamNumber": 244,  "station": "Red2", "surrogate": false },
                                  { "teamNumber": 610,  "station": "Red3", "surrogate": false },
                                  { "teamNumber": 3543, "station": "Blue1","surrogate": false },
                                  { "teamNumber": 6987, "station": "Blue2","surrogate": false },
                                  { "teamNumber": 288,  "station": "Blue3","surrogate": false }
                                ]
                              },
                              {
                                "description": "Qualification 2",
                                "startTime": "2025-03-22T11:38:00",
                                "matchNumber": 2,
                                "field": "Primary",
                                "tournamentLevel": "Qualification",
                                "teams": [
                                  { "teamNumber": 865,  "station": "Red1", "surrogate": false },
                                  { "teamNumber": 8729, "station": "Red2", "surrogate": false },
                                  { "teamNumber": 1305, "station": "Red3", "surrogate": false },
                                  { "teamNumber": 8081, "station": "Blue1","surrogate": false },
                                  { "teamNumber": 4946, "station": "Blue2","surrogate": false },
                                  { "teamNumber": 3756, "station": "Blue3","surrogate": false }
                                ]
                              }
                            ]
                          }
                        """;

    var resp = mapper.readValue(json, ca.team1310.ravenbrain.frcapi.model.ScheduleResponse.class);
    assertNotNull(resp, "ScheduleResponse should not be null");
    assertNotNull(resp.schedule(), "Schedule list should not be null");
    assertEquals(2, resp.schedule().size(), "Expected 2 schedule entries");

    var m1 = resp.schedule().get(0);
    assertEquals("Qualification 1", m1.description());
    assertEquals(1, m1.matchNumber());
    assertEquals("Primary", m1.field());
    assertEquals("Qualification", m1.tournamentLevel());
    assertNotNull(m1.teams());
    assertEquals(6, m1.teams().size());
    assertEquals(9127, m1.teams().get(0).teamNumber());
    assertEquals("Red1", m1.teams().get(0).station());
    assertEquals(288, m1.teams().get(5).teamNumber());
    assertEquals("Blue3", m1.teams().get(5).station());

    // Round-trip serialization/deserialization
    String out = mapper.writeValueAsString(resp);
    var roundTrip =
        mapper.readValue(out, ca.team1310.ravenbrain.frcapi.model.ScheduleResponse.class);
    assertNotNull(roundTrip);
    assertEquals(resp.schedule().size(), roundTrip.schedule().size(), "Round-trip size mismatch");
  }
}
