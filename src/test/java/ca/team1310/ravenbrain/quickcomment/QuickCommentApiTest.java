package ca.team1310.ravenbrain.quickcomment;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.connect.Config;
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

  @Inject
  @Client("/")
  HttpClient client;

  @Inject QuickCommentService quickCommentService;

  @Inject Config config;

  @Test
  void testPostComments() {
    QuickComment comment =
        new QuickComment(
            null, "Test Scout", "ROLE_MEMBER", 9999, Instant.now(), "Basic valid comment");

    List<QuickComment> comments = Collections.singletonList(comment);
    HttpRequest<List<QuickComment>> request =
        HttpRequest.POST("/api/quickcomment", comments)
            .basicAuth("user", config.getMember()); // ROLE_MEMBER

    HttpResponse<List<QuickCommentApi.QuickCommentPostResult>> response =
        client
            .toBlocking()
            .exchange(request, Argument.listOf(QuickCommentApi.QuickCommentPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<QuickCommentApi.QuickCommentPostResult> body = response.body();
    assertNotNull(body);
    assertEquals(1, body.size());
    assertTrue(body.get(0).success());

    // Verify it was actually saved
    List<QuickComment> saved = quickCommentService.findAllByTeamOrderByTimestamp(9999);
    assertFalse(saved.isEmpty());
    assertEquals("Basic valid comment", saved.get(0).quickComment());
  }

  @Test
  void testPostDuplicateComment() {
    QuickComment comment =
        new QuickComment(
            null,
            "Duplicate Scout",
            "ROLE_MEMBER",
            9998,
            Instant.parse("2025-01-01T12:00:00Z"),
            "Duplicate comment");

    // Save it once using the API
    List<QuickComment> comments = Collections.singletonList(comment);
    HttpRequest<List<QuickComment>> request1 =
        HttpRequest.POST("/api/quickcomment", comments).basicAuth("user", config.getMember());
    client
        .toBlocking()
        .exchange(request1, Argument.listOf(QuickCommentApi.QuickCommentPostResult.class));

    // Try to post it again
    HttpRequest<List<QuickComment>> request2 =
        HttpRequest.POST("/api/quickcomment", comments).basicAuth("user", config.getMember());

    HttpResponse<List<QuickCommentApi.QuickCommentPostResult>> response =
        client
            .toBlocking()
            .exchange(request2, Argument.listOf(QuickCommentApi.QuickCommentPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<QuickCommentApi.QuickCommentPostResult> body = response.body();
    assertNotNull(body);
    assertEquals(1, body.size());
    // Api handles duplicate by returning success: true as per implementation
    assertTrue(body.get(0).success());
  }

  @Test
  void testPostInvalidData() {
    QuickComment invalidComment = new QuickComment(null, null, null, 0, null, null);
    // Missing required fields (e.g., name, comment, timestamp are NOT NULL in DB)
    // This should cause an exception in quickCommentService.save() which the API catches.

    List<QuickComment> comments = Collections.singletonList(invalidComment);
    HttpRequest<List<QuickComment>> request =
        HttpRequest.POST("/api/quickcomment", comments).basicAuth("user", config.getMember());

    HttpResponse<List<QuickCommentApi.QuickCommentPostResult>> response =
        client
            .toBlocking()
            .exchange(request, Argument.listOf(QuickCommentApi.QuickCommentPostResult.class));

    assertEquals(HttpStatus.OK, response.getStatus());
    List<QuickCommentApi.QuickCommentPostResult> body = response.body();
    assertNotNull(body);
    assertEquals(1, body.size());
    // Since it's missing fields, it should fail
    assertFalse(body.get(0).success());
    assertNotNull(body.get(0).reason());
  }

  @Test
  void testGetAll() {
    HttpRequest<?> request =
        HttpRequest.GET("/api/quickcomment")
            .basicAuth("user", config.getExpertscout()); // ROLE_EXPERTSCOUT

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
