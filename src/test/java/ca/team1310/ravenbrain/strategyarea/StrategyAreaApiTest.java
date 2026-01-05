package ca.team1310.ravenbrain.strategyarea;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.connect.Config;
import ca.team1310.ravenbrain.connect.TestUserHelper;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * @author Junie
 * @since 2026-01-04
 */
@MicronautTest
public class StrategyAreaApiTest {

  @Inject
  @Client("/")
  HttpClient client;

  @Inject Config config;

  @Inject StrategyAreaService strategyAreaService;

  @Inject TestUserHelper testUserHelper;

  @AfterEach
  void cleanup() {
    testUserHelper.deleteTestUsers();
    strategyAreaService
        .list()
        .forEach(
            area -> {
              if (area.name().contains("Autonomous") || area.name().contains("Security Test")) {
                strategyAreaService.delete(area.id());
              }
            });
  }

  @Test
  void testStrategyAreaCrud() {
    String adminLogin = "admin-testuser-" + System.currentTimeMillis();
    String adminPass = "adminPass";
    testUserHelper.createTestUser(adminLogin, adminPass, "ROLE_ADMIN");

    // 1. Create as admin
    StrategyArea area = new StrategyArea(0, 2026, "Autonomous", "Autonomous strategy area");
    HttpRequest<StrategyArea> createRequest =
        HttpRequest.POST("/api/strategy-areas", area).basicAuth(adminLogin, adminPass);
    StrategyArea created = client.toBlocking().retrieve(createRequest, StrategyArea.class);
    assertNotNull(created);
    assertTrue(created.id() > 0);
    assertEquals("Autonomous", created.name());
    assertEquals("Autonomous strategy area", created.description());

    long id = created.id();

    // 2. List (authenticated user can list)
    HttpRequest<?> listRequest =
        HttpRequest.GET("/api/strategy-areas").basicAuth(adminLogin, adminPass);
    List<StrategyArea> list =
        client.toBlocking().retrieve(listRequest, Argument.listOf(StrategyArea.class));
    assertTrue(list.stream().anyMatch(a -> a.id() == id));

    // 2.5 Get by ID
    HttpRequest<?> getRequest =
        HttpRequest.GET("/api/strategy-areas/" + id).basicAuth(adminLogin, adminPass);
    StrategyArea fetchedById = client.toBlocking().retrieve(getRequest, StrategyArea.class);
    assertEquals(id, fetchedById.id());
    assertEquals("Autonomous", fetchedById.name());

    // 3. Update as superuser
    StrategyArea updateArea =
        new StrategyArea(id, 2026, "Autonomous Updated", "Updated description");
    HttpRequest<StrategyArea> updateRequest =
        HttpRequest.PUT("/api/strategy-areas/" + id, updateArea)
            .basicAuth("superuser", config.superuser());
    StrategyArea updated = client.toBlocking().retrieve(updateRequest, StrategyArea.class);
    assertEquals(id, updated.id());
    assertEquals("Autonomous Updated", updated.name());
    assertEquals("Updated description", updated.description());

    // 4. Verify list after update
    list = client.toBlocking().retrieve(listRequest, Argument.listOf(StrategyArea.class));
    StrategyArea fetched = list.stream().filter(a -> a.id() == id).findFirst().orElseThrow();
    assertEquals("Autonomous Updated", fetched.name());
  }

  @Test
  void testSecurity() {
    String memberLogin = "member-testuser-" + System.currentTimeMillis();
    String memberPass = "memberPass";
    testUserHelper.createTestUser(memberLogin, memberPass, "ROLE_MEMBER");

    StrategyArea area = new StrategyArea(0, 2026, "Security Test", "Should fail");

    // Create as member should fail
    HttpRequest<StrategyArea> createRequest =
        HttpRequest.POST("/api/strategy-areas", area).basicAuth(memberLogin, memberPass);
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(createRequest, StrategyArea.class));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());

    // Update as member should fail
    HttpRequest<StrategyArea> updateRequest =
        HttpRequest.PUT("/api/strategy-areas/1", area).basicAuth(memberLogin, memberPass);
    e =
        assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(updateRequest, StrategyArea.class));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
  }
}
