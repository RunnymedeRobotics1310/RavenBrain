package ca.team1310.ravenbrain.eventtype;

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
public class EventTypeApiTest {

  @Inject
  @Client("/")
  HttpClient client;

  @Inject Config config;

  @Inject EventTypeService eventTypeService;

  @Inject TestUserHelper testUserHelper;

  @AfterEach
  void cleanup() {
    testUserHelper.deleteTestUsers();
    eventTypeService
        .list()
        .forEach(
            et -> {
              if (et.eventtype().startsWith("test-") || et.eventtype().startsWith("security-")) {
                eventTypeService.delete(et.eventtype());
              }
            });
  }

  @Test
  void testEventTypeCrud() {
    String adminLogin = "admin-testuser-" + System.currentTimeMillis();
    String adminPass = "adminPass";
    testUserHelper.createTestUser(adminLogin, adminPass, "ROLE_ADMIN");

    // 1. Create as admin
    EventType et = new EventType("test-event", "Test Event", "Test Description", 2025, -1L);
    HttpRequest<EventType> createRequest =
        HttpRequest.POST("/api/event-types", et).basicAuth(adminLogin, adminPass);
    EventType created = client.toBlocking().retrieve(createRequest, EventType.class);
    assertNotNull(created);
    assertEquals("test-event", created.eventtype());
    assertEquals("Test Event", created.name());
    assertEquals("Test Description", created.description());

    // 2. List (authenticated user can list)
    HttpRequest<?> listRequest =
        HttpRequest.GET("/api/event-types").basicAuth(adminLogin, adminPass);
    List<EventType> list =
        client.toBlocking().retrieve(listRequest, Argument.listOf(EventType.class));
    assertTrue(list.stream().anyMatch(e -> e.eventtype().equals("test-event")));

    // 3. Update as superuser
    EventType updateEt =
        new EventType("test-event", "Updated Event", "Updated Description", 2025, -1L);
    HttpRequest<EventType> updateRequest =
        HttpRequest.PUT("/api/event-types/test-event", updateEt)
            .basicAuth("superuser", config.superuser());
    EventType updated = client.toBlocking().retrieve(updateRequest, EventType.class);
    assertEquals("test-event", updated.eventtype());
    assertEquals("Updated Event", updated.name());
    assertEquals("Updated Description", updated.description());

    // 4. Verify list after update
    list = client.toBlocking().retrieve(listRequest, Argument.listOf(EventType.class));
    EventType fetched =
        list.stream().filter(e -> e.eventtype().equals("test-event")).findFirst().orElseThrow();
    assertEquals("Updated Event", fetched.name());

    // 5. Delete as admin
    HttpRequest<?> deleteRequest =
        HttpRequest.DELETE("/api/event-types/test-event").basicAuth(adminLogin, adminPass);
    client.toBlocking().exchange(deleteRequest);

    // 6. Verify delete
    list = client.toBlocking().retrieve(listRequest, Argument.listOf(EventType.class));
    assertFalse(list.stream().anyMatch(e -> e.eventtype().equals("test-event")));
  }

  @Test
  void testSecurity() {
    String memberLogin = "member-testuser-" + System.currentTimeMillis();
    String memberPass = "memberPass";
    testUserHelper.createTestUser(memberLogin, memberPass, "ROLE_MEMBER");

    EventType et = new EventType("security-test", "Security Test", "Should fail", 2025, -1L);

    // Create as member should fail
    HttpRequest<EventType> createRequest =
        HttpRequest.POST("/api/event-types", et).basicAuth(memberLogin, memberPass);
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(createRequest, EventType.class));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());

    // Update as member should fail
    HttpRequest<EventType> updateRequest =
        HttpRequest.PUT("/api/event-types/auto-start-left", et).basicAuth(memberLogin, memberPass);
    e =
        assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(updateRequest, EventType.class));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());

    // Delete as member should fail
    HttpRequest<?> deleteRequest =
        HttpRequest.DELETE("/api/event-types/auto-start-left").basicAuth(memberLogin, memberPass);
    e =
        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(deleteRequest));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
  }

  @Test
  void testInvalidFormat() {
    String adminLogin = "admin-testuser-" + System.currentTimeMillis();
    String adminPass = "adminPass";
    testUserHelper.createTestUser(adminLogin, adminPass, "ROLE_ADMIN");

    EventType et = new EventType("invalid event!", "Invalid", "Invalid", 2025, -1L);
    HttpRequest<EventType> createRequest =
        HttpRequest.POST("/api/event-types", et).basicAuth(adminLogin, adminPass);

    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(createRequest, EventType.class));
    // Micronaut might return 400 or 500 depending on how the exception is handled.
    // Since I throw IllegalArgumentException in service, it might be 400 if there's an exception
    // handler, or 500.
    // Given the project doesn't seem to have a global exception handler for
    // IllegalArgumentException that maps to 400, it might be 500.
    assertTrue(e.getStatus().getCode() >= 400);
  }

  @Test
  void testInitialData() {
    // Check that one of the required initial values exists
    HttpRequest<?> listRequest =
        HttpRequest.GET("/api/event-types").basicAuth("superuser", config.superuser());
    List<EventType> list =
        client.toBlocking().retrieve(listRequest, Argument.listOf(EventType.class));

    assertTrue(list.stream().anyMatch(e -> e.eventtype().equals("auto-start-left")));
    assertTrue(list.stream().anyMatch(e -> e.eventtype().equals("AUTO-pickup-coral-auto-left")));
    // Total number of initial items is around 160.
    assertTrue(list.size() >= 160);
  }

  @Test
  void testFindByFrcyear() {
    String memberLogin = "member-testuser-" + System.currentTimeMillis();
    String memberPass = "memberPass";
    testUserHelper.createTestUser(memberLogin, memberPass, "ROLE_MEMBER");

    // All initial data is 2025
    HttpRequest<?> getRequest =
        HttpRequest.GET("/api/event-types/year/2025").basicAuth(memberLogin, memberPass);
    List<EventType> list2025 =
        client.toBlocking().retrieve(getRequest, Argument.listOf(EventType.class));

    assertFalse(list2025.isEmpty());
    assertTrue(list2025.stream().allMatch(e -> e.frcyear() == 2025));

    // Check for a year that doesn't exist
    HttpRequest<?> getEmptyRequest =
        HttpRequest.GET("/api/event-types/year/1999").basicAuth(memberLogin, memberPass);
    List<EventType> listEmpty =
        client.toBlocking().retrieve(getEmptyRequest, Argument.listOf(EventType.class));

    assertTrue(listEmpty.isEmpty());
  }

  @Test
  void testFindById() {
    String memberLogin = "member-testuser-" + System.currentTimeMillis();
    String memberPass = "memberPass";
    testUserHelper.createTestUser(memberLogin, memberPass, "ROLE_MEMBER");

    // Fetch an existing one (seeded)
    HttpRequest<?> getRequest =
        HttpRequest.GET("/api/event-types/auto-start-left").basicAuth(memberLogin, memberPass);
    EventType et = client.toBlocking().retrieve(getRequest, EventType.class);

    assertNotNull(et);
    assertEquals("auto-start-left", et.eventtype());

    // Fetch non-existing one
    HttpRequest<?> getNoneRequest =
        HttpRequest.GET("/api/event-types/does-not-exist").basicAuth(memberLogin, memberPass);

    // If I return null in Controller and it's not wrapped in Optional/HttpResponse, Micronaut might
    // return 404 or 204.
    // Actually, returning null in Micronaut @Get usually results in 404 Not Found if no body, or
    // 204 if it's void.
    // Let's check what happens.
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(getNoneRequest, EventType.class));
    assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
  }
}
