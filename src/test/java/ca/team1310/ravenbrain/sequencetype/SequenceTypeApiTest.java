package ca.team1310.ravenbrain.sequencetype;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.connect.Config;
import ca.team1310.ravenbrain.connect.TestUserHelper;
import ca.team1310.ravenbrain.eventtype.EventType;
import ca.team1310.ravenbrain.eventtype.EventTypeService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Junie
 * @since 2026-01-07
 */
@MicronautTest
public class SequenceTypeApiTest {

  @Inject
  @Client("/")
  HttpClient client;

  @Inject Config config;

  @Inject SequenceTypeService sequenceTypeService;
  @Inject EventTypeService eventTypeService;

  @Inject TestUserHelper testUserHelper;

  @BeforeEach
  void setup() {
    if (eventTypeService.findById("test-event-1").isEmpty()) {
      eventTypeService.create(
          new EventType("test-event-1", "Test Event 1", "Desc 1", 2025, -1L, false, false));
    }
    if (eventTypeService.findById("test-event-2").isEmpty()) {
      eventTypeService.create(
          new EventType("test-event-2", "Test Event 2", "Desc 2", 2025, -1L, false, false));
    }
  }

  @AfterEach
  void cleanup() {
    testUserHelper.deleteTestUsers();
    sequenceTypeService
        .list()
        .forEach(
            st -> {
              if (st.name().startsWith("test-") || st.name().startsWith("security-")) {
                sequenceTypeService.delete(st.id());
              }
            });
    eventTypeService
        .findById("test-event-1")
        .ifPresent(et -> eventTypeService.delete(et.eventtype()));
    eventTypeService
        .findById("test-event-2")
        .ifPresent(et -> eventTypeService.delete(et.eventtype()));
  }

  @Test
  void testSequenceTypeCrud() {
    String adminLogin = "admin-testuser-" + System.currentTimeMillis();
    String adminPass = "adminPass";
    testUserHelper.createTestUser(adminLogin, adminPass, "ROLE_ADMIN");

    EventType et1 = eventTypeService.findById("test-event-1").orElseThrow();

    // 1. Create as admin
    SequenceEvent se1 = new SequenceEvent(null, null, et1, true, false);
    SequenceType st =
        new SequenceType(
            null,
            "test-sequence",
            "test-sequence",
            "Test Sequence Description",
            2025,
            false,
            -1L,
            List.of(se1));

    HttpRequest<SequenceType> createRequest =
        HttpRequest.POST("/api/sequence-types", st).basicAuth(adminLogin, adminPass);
    SequenceType created = client.toBlocking().retrieve(createRequest, SequenceType.class);

    assertNotNull(created);
    assertNotNull(created.id());
    assertEquals("test-sequence", created.code());
    assertEquals("test-sequence", created.name());
    assertEquals("Test Sequence Description", created.description());
    assertEquals(2025, created.frcyear());
    assertFalse(created.disabled());
    assertEquals(-1L, created.strategyareaId());
    assertEquals(1, created.events().size());
    assertEquals("test-event-1", created.events().get(0).eventtype().eventtype());

    // 1.1 Verify re-fetch
    HttpRequest<?> getRequest =
        HttpRequest.GET("/api/sequence-types/" + created.id()).basicAuth(adminLogin, adminPass);
    SequenceType fetched = client.toBlocking().retrieve(getRequest, SequenceType.class);
    assertEquals(1, fetched.events().size(), "Events should be persisted and re-fetchable");
    assertEquals("test-event-1", fetched.events().get(0).eventtype().eventtype());
    assertEquals(2025, fetched.frcyear());
    assertFalse(fetched.disabled());

    // 2. List
    HttpRequest<?> listRequest =
        HttpRequest.GET("/api/sequence-types").basicAuth(adminLogin, adminPass);
    List<SequenceType> list =
        client.toBlocking().retrieve(listRequest, Argument.listOf(SequenceType.class));
    assertTrue(list.stream().anyMatch(s -> s.name().equals("test-sequence")));

    // 2.1 List by year
    HttpRequest<?> listByYearRequest =
        HttpRequest.GET("/api/sequence-types/year/2025").basicAuth(adminLogin, adminPass);
    List<SequenceType> listByYear =
        client.toBlocking().retrieve(listByYearRequest, Argument.listOf(SequenceType.class));
    assertTrue(listByYear.stream().anyMatch(s -> s.name().equals("test-sequence")));

    HttpRequest<?> listByOtherYearRequest =
        HttpRequest.GET("/api/sequence-types/year/2026").basicAuth(adminLogin, adminPass);
    List<SequenceType> listByOtherYear =
        client.toBlocking().retrieve(listByOtherYearRequest, Argument.listOf(SequenceType.class));
    assertFalse(listByOtherYear.stream().anyMatch(s -> s.name().equals("test-sequence")));

    // 3. Update
    EventType et2 = eventTypeService.findById("test-event-2").orElseThrow();
    SequenceEvent se2 = new SequenceEvent(null, null, et2, false, true);
    SequenceType updateSt =
        new SequenceType(
            created.id(),
            "test-sequence",
            "test-sequence",
            "Updated Description",
            2025,
            true,
            -1L,
            List.of(se2));

    HttpRequest<SequenceType> updateRequest =
        HttpRequest.PUT("/api/sequence-types/" + created.id(), updateSt)
            .basicAuth("superuser", config.superuser());
    SequenceType updated = client.toBlocking().retrieve(updateRequest, SequenceType.class);
    assertEquals(created.id(), updated.id());
    assertEquals("Updated Description", updated.description());
    assertTrue(updated.disabled());

    // 3.1 Verify update re-fetch
    SequenceType fetchedUpdated = client.toBlocking().retrieve(getRequest, SequenceType.class);
    assertEquals(1, fetchedUpdated.events().size());
    assertEquals("test-event-2", fetchedUpdated.events().get(0).eventtype().eventtype());
    assertTrue(fetchedUpdated.disabled());

    // 4. Delete
    HttpRequest<?> deleteRequest =
        HttpRequest.DELETE("/api/sequence-types/" + created.id()).basicAuth(adminLogin, adminPass);
    client.toBlocking().exchange(deleteRequest);

    // 5. Verify delete
    list = client.toBlocking().retrieve(listRequest, Argument.listOf(SequenceType.class));
    assertFalse(list.stream().anyMatch(s -> s.name().equals("test-sequence")));
  }

  @Test
  void testSecurity() {
    String memberLogin = "member-testuser-" + System.currentTimeMillis();
    String memberPass = "memberPass";
    testUserHelper.createTestUser(memberLogin, memberPass, "ROLE_MEMBER");

    SequenceType st =
        new SequenceType(
            null, "security-test", "security-test", "Should fail", 2025, false, -1L, List.of());

    // Create as member should fail
    HttpRequest<SequenceType> createRequest =
        HttpRequest.POST("/api/sequence-types", st).basicAuth(memberLogin, memberPass);
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(createRequest, SequenceType.class));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
  }
}
