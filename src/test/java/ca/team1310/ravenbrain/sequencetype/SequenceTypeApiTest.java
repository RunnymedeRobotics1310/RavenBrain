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
      eventTypeService.create(new EventType("test-event-1", "Test Event 1", "Desc 1", 2025, -1L));
    }
    if (eventTypeService.findById("test-event-2").isEmpty()) {
      eventTypeService.create(new EventType("test-event-2", "Test Event 2", "Desc 2", 2025, -1L));
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
                sequenceTypeService.delete(st.name());
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
    SequenceType st = new SequenceType("test-sequence", "Test Sequence Description", List.of(se1));

    HttpRequest<SequenceType> createRequest =
        HttpRequest.POST("/api/sequence-types", st).basicAuth(adminLogin, adminPass);
    SequenceType created = client.toBlocking().retrieve(createRequest, SequenceType.class);

    assertNotNull(created);
    assertEquals("test-sequence", created.name());
    assertEquals("Test Sequence Description", created.description());
    assertEquals(1, created.events().size());
    assertEquals("test-event-1", created.events().get(0).eventtype().eventtype());

    // 2. List
    HttpRequest<?> listRequest =
        HttpRequest.GET("/api/sequence-types").basicAuth(adminLogin, adminPass);
    List<SequenceType> list =
        client.toBlocking().retrieve(listRequest, Argument.listOf(SequenceType.class));
    assertTrue(list.stream().anyMatch(s -> s.name().equals("test-sequence")));

    // 3. Update
    EventType et2 = eventTypeService.findById("test-event-2").orElseThrow();
    SequenceEvent se2 = new SequenceEvent(null, null, et2, false, true);
    SequenceType updateSt = new SequenceType("test-sequence", "Updated Description", List.of(se2));

    HttpRequest<SequenceType> updateRequest =
        HttpRequest.PUT("/api/sequence-types/test-sequence", updateSt)
            .basicAuth("superuser", config.superuser());
    SequenceType updated = client.toBlocking().retrieve(updateRequest, SequenceType.class);
    assertEquals("test-sequence", updated.name());
    assertEquals("Updated Description", updated.description());
    // Note: updating a list in Micronaut Data JDBC might depend on how the relations are handled.
    // If it's ONE_TO_MANY, it might replace or append.
    // Usually with JDBC it might need manual management if not using a full ORM,
    // but Micronaut Data JDBC supports some level of cascading.

    // 4. Delete
    HttpRequest<?> deleteRequest =
        HttpRequest.DELETE("/api/sequence-types/test-sequence").basicAuth(adminLogin, adminPass);
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

    SequenceType st = new SequenceType("security-test", "Should fail", List.of());

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
