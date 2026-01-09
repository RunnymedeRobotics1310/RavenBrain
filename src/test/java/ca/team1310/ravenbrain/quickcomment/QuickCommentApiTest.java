package ca.team1310.ravenbrain.quickcomment;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.connect.TestUserHelper;
import ca.team1310.ravenbrain.connect.User;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

@MicronautTest
public class QuickCommentApiTest {

  private static final String USER_MEMBER = "comment-member-testuser";
  private static final String USER_EXPERT = "comment-expert-testuser";

  @Inject
  @Client("/")
  HttpClient client;

  @Inject QuickCommentService quickCommentService;

  @Inject TestUserHelper testUserHelper;

  private long memberUserId;
  private long expertUserId;

  @org.junit.jupiter.api.BeforeEach
  void setup() {
    User member = testUserHelper.createTestUser(USER_MEMBER, "password", "ROLE_MEMBER");
    memberUserId = member.id();
    User expert = testUserHelper.createTestUser(USER_EXPERT, "password", "ROLE_EXPERTSCOUT");
    expertUserId = expert.id();
  }

  @org.junit.jupiter.api.AfterEach
  void tearDown() {
    testUserHelper.deleteTestUsers();
    // Delete all quick comments created during tests
    quickCommentService
        .findAllOrderByTeamAndTimestamp()
        .forEach(
            comment -> {
              if (comment.team() >= 100_000) {
                quickCommentService.delete(comment);
              }
            });
  }

  @Test
  void testPostComments() {
    QuickComment comment =
        new QuickComment(
            null, memberUserId, "ROLE_MEMBER", 999_999, Instant.now(), "Basic valid comment");

    List<QuickComment> comments = Collections.singletonList(comment);
    HttpRequest<List<QuickComment>> request =
        HttpRequest.POST("/api/quickcomment", comments)
            .basicAuth(USER_MEMBER, "password"); // ROLE_MEMBER

    HttpResponse<List<QuickCommentApi.QuickCommentPostResult>> response =
        client
            .toBlocking()
            .exchange(request, Argument.listOf(QuickCommentApi.QuickCommentPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<QuickCommentApi.QuickCommentPostResult> body = response.body();
    assertNotNull(body);
    assertEquals(1, body.size());
    assertTrue(body.getFirst().success());

    // Verify it was actually saved
    List<QuickComment> saved = quickCommentService.findAllByTeamOrderByTimestamp(999_999);
    assertFalse(saved.isEmpty());
    assertNotNull(saved.getFirst().id(), "Saved comment should have a generated ID");
    assertEquals("Basic valid comment", saved.getFirst().quickComment());
    assertEquals(memberUserId, saved.getFirst().userId());

    // Verify the returned result from API also has the ID
    assertNotNull(body.getFirst().comment().id(), "Result comment should have a generated ID");
  }

  @Test
  void testPostDuplicateComment() {
    QuickComment comment =
        new QuickComment(
            null,
            memberUserId,
            "ROLE_MEMBER",
            999_998,
            Instant.parse("2025-01-01T12:00:00Z"),
            "Duplicate comment");

    // Save it once using the API
    List<QuickComment> comments = Collections.singletonList(comment);
    HttpRequest<List<QuickComment>> request1 =
        HttpRequest.POST("/api/quickcomment", comments).basicAuth(USER_MEMBER, "password");
    client
        .toBlocking()
        .exchange(request1, Argument.listOf(QuickCommentApi.QuickCommentPostResult.class));

    // Try to post it again
    HttpRequest<List<QuickComment>> request2 =
        HttpRequest.POST("/api/quickcomment", comments).basicAuth(USER_MEMBER, "password");

    HttpResponse<List<QuickCommentApi.QuickCommentPostResult>> response =
        client
            .toBlocking()
            .exchange(request2, Argument.listOf(QuickCommentApi.QuickCommentPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<QuickCommentApi.QuickCommentPostResult> body = response.body();
    assertNotNull(body);
    assertEquals(1, body.size());
    // Api handles duplicate by returning success: true as per implementation
    assertTrue(body.getFirst().success());
  }

  @Test
  void testPostInvalidData() {
    QuickComment invalidComment = new QuickComment(null, -1L, null, 100_000, null, null);
    // Missing required fields (e.g., userId, comment, timestamp are NOT NULL in DB)
    // This should cause an exception in quickCommentService.save() which the API catches.

    List<QuickComment> comments = Collections.singletonList(invalidComment);
    HttpRequest<List<QuickComment>> request =
        HttpRequest.POST("/api/quickcomment", comments).basicAuth(USER_MEMBER, "password");

    HttpResponse<List<QuickCommentApi.QuickCommentPostResult>> response =
        client
            .toBlocking()
            .exchange(request, Argument.listOf(QuickCommentApi.QuickCommentPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<QuickCommentApi.QuickCommentPostResult> body = response.body();
    assertNotNull(body);
    assertEquals(1, body.size());
    // Since it's missing fields, it should fail
    assertFalse(body.getFirst().success());
    assertNotNull(body.getFirst().reason());
  }

  @Test
  void testGetAll() {
    HttpRequest<?> request =
        HttpRequest.GET("/api/quickcomment").basicAuth(USER_EXPERT, "password"); // ROLE_EXPERTSCOUT

    HttpResponse<List<QuickComment>> response =
        client.toBlocking().exchange(request, Argument.listOf(QuickComment.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    assertNotNull(response.body());
  }

  @Test
  void testUnauthorized() {
    HttpRequest<?> request = HttpRequest.GET("/api/quickcomment");
    assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
  }
}
