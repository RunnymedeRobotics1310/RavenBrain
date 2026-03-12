package ca.team1310.ravenbrain.frcapi.model;

import static org.junit.jupiter.api.Assertions.*;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.io.IOException;
import org.junit.jupiter.api.Test;

@MicronautTest
public class TeamListingResponseTest {

  @Inject ObjectMapper objectMapper;

  @Test
  void testDeserializeTeamListingResponse() throws IOException {
    String json =
        """
        {
          "teams": [
            {
              "teamNumber": 1310,
              "nameFull": "Runnymede Robotics Full Name",
              "nameShort": "Runnymede Robotics",
              "city": "Toronto",
              "stateProv": "Ontario",
              "country": "Canada",
              "website": "http://team1310.ca",
              "rookieYear": 2004,
              "robotName": "Raven",
              "districtCode": "ONT",
              "homeCMP": "CMPTX",
              "schoolName": "Runnymede CI"
            },
            {
              "teamNumber": 2056,
              "nameFull": "OP Robotics",
              "nameShort": "OP Robotics",
              "city": "Mississauga",
              "stateProv": "Ontario",
              "country": "Canada",
              "website": null,
              "rookieYear": 2007,
              "robotName": null,
              "districtCode": "ONT",
              "homeCMP": null,
              "schoolName": null
            }
          ],
          "teamCountTotal": 2,
          "teamCountPage": 2,
          "pageCurrent": 1,
          "pageTotal": 1
        }
        """;

    TeamListingResponse response =
        objectMapper.readValue(json, Argument.of(TeamListingResponse.class));

    assertNotNull(response);
    assertEquals(2, response.teams().size());
    assertEquals(2, response.teamCountTotal());
    assertEquals(2, response.teamCountPage());
    assertEquals(1, response.pageCurrent());
    assertEquals(1, response.pageTotal());

    TeamListing team1 = response.teams().get(0);
    assertEquals(1310, team1.teamNumber());
    assertEquals("Runnymede Robotics Full Name", team1.nameFull());
    assertEquals("Runnymede Robotics", team1.nameShort());
    assertEquals("Toronto", team1.city());
    assertEquals("Ontario", team1.stateProv());
    assertEquals("Canada", team1.country());
    assertEquals("http://team1310.ca", team1.website());
    assertEquals(2004, team1.rookieYear());
    assertEquals("Raven", team1.robotName());
    assertEquals("ONT", team1.districtCode());
    assertEquals("CMPTX", team1.homeCMP());
    assertEquals("Runnymede CI", team1.schoolName());

    TeamListing team2 = response.teams().get(1);
    assertEquals(2056, team2.teamNumber());
    assertNull(team2.website());
    assertNull(team2.robotName());
  }

  @Test
  void testDeserializeEmptyTeamsList() throws IOException {
    String json =
        """
        {
          "teams": [],
          "teamCountTotal": 0,
          "teamCountPage": 0,
          "pageCurrent": 0,
          "pageTotal": 0
        }
        """;

    TeamListingResponse response =
        objectMapper.readValue(json, Argument.of(TeamListingResponse.class));

    assertNotNull(response);
    assertTrue(response.teams().isEmpty());
    assertEquals(0, response.teamCountTotal());
  }

  @Test
  void testDeserializeWithMinimalFields() throws IOException {
    String json =
        """
        {
          "teams": [
            {
              "teamNumber": 4917,
              "nameFull": null,
              "nameShort": null,
              "city": null,
              "stateProv": null,
              "country": null,
              "website": null,
              "rookieYear": 2014,
              "robotName": null,
              "districtCode": null,
              "homeCMP": null,
              "schoolName": null
            }
          ],
          "teamCountTotal": 1,
          "teamCountPage": 1,
          "pageCurrent": 1,
          "pageTotal": 1
        }
        """;

    TeamListingResponse response =
        objectMapper.readValue(json, Argument.of(TeamListingResponse.class));

    assertNotNull(response);
    assertEquals(1, response.teams().size());
    assertEquals(4917, response.teams().getFirst().teamNumber());
    assertEquals(2014, response.teams().getFirst().rookieYear());
  }
}
