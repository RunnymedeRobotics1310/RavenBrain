package ca.team1310.ravenbrain.connect;

import static org.junit.jupiter.api.Assertions.*;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

@MicronautTest
public class UserApiTest {

  @Inject
  @Client("/")
  HttpClient client;

  @Inject Config config;

  @Test
  void testUserCrud() {
    String adminLogin = "admin-testuser" + System.currentTimeMillis();
    String memberLogin = "member-testuser" + System.currentTimeMillis();
    String testUserLogin = "testuser-" + System.currentTimeMillis();

    // 1. Create an admin user as superuser
    User adminUser =
        new User(0, adminLogin, "Admin User", "adminPass", true, false, List.of("ROLE_ADMIN"));
    HttpRequest<User> createAdminRequest =
        HttpRequest.POST("/api/users", adminUser).basicAuth("superuser", config.superuser());
    User createdAdmin = client.toBlocking().retrieve(createAdminRequest, User.class);
    assertNotNull(createdAdmin);

    // 2. Create a member user as the new admin
    User memberUser =
        new User(0, memberLogin, "Member User", "memberPass", true, false, List.of("ROLE_MEMBER"));
    HttpRequest<User> createMemberRequest =
        HttpRequest.POST("/api/users", memberUser).basicAuth(adminLogin, "adminPass");
    User createdMember = client.toBlocking().retrieve(createMemberRequest, User.class);
    assertNotNull(createdMember);

    // 3. Create a test user as admin
    User newUser =
        new User(0, testUserLogin, "Test User", "password123", true, false, List.of("ROLE_MEMBER"));
    HttpRequest<User> createRequest =
        HttpRequest.POST("/api/users", newUser).basicAuth(adminLogin, "adminPass");
    User createdUser = client.toBlocking().retrieve(createRequest, User.class);
    assertEquals(testUserLogin, createdUser.login());
    assertEquals("REDACTED", createdUser.passwordHash());

    long id = createdUser.id();

    // 4. Get user as admin
    HttpRequest<?> getRequest =
        HttpRequest.GET("/api/users/" + id).basicAuth(adminLogin, "adminPass");
    User fetchedUser = client.toBlocking().retrieve(getRequest, User.class);
    assertEquals(testUserLogin, fetchedUser.login());

    // 5. Update user as superuser
    User updatedUserRequest =
        new User(
            id,
            testUserLogin,
            "Updated Name",
            "newPassword",
            true,
            false,
            List.of("ROLE_MEMBER", "ROLE_ADMIN"));
    HttpRequest<User> updateRequest =
        HttpRequest.PUT("/api/users/" + id, updatedUserRequest)
            .basicAuth("superuser", config.superuser());
    User updatedUser = client.toBlocking().retrieve(updateRequest, User.class);
    assertEquals("Updated Name", updatedUser.displayName());
    assertTrue(updatedUser.roles().contains("ROLE_ADMIN"));

    // 6. List users as admin
    HttpRequest<?> listRequest = HttpRequest.GET("/api/users").basicAuth(adminLogin, "adminPass");
    List<User> users = client.toBlocking().retrieve(listRequest, Argument.listOf(User.class));
    assertTrue(users.stream().anyMatch(u -> u.id() == id));

    // 7. Delete user as admin
    HttpRequest<?> deleteRequest =
        HttpRequest.DELETE("/api/users/" + id).basicAuth(adminLogin, "adminPass");
    HttpResponse<Void> deleteResponse = client.toBlocking().exchange(deleteRequest);
    assertEquals(HttpStatus.OK, deleteResponse.getStatus());

    // 8. Verify deleted
    HttpRequest<?> getRequestDeleted =
        HttpRequest.GET("/api/users/" + id).basicAuth(adminLogin, "adminPass");
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(getRequestDeleted, User.class));
    assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
  }

  @Test
  void testCreateUserUnauthorized() {
    String memberLogin = "member-unauth-testuser-" + System.currentTimeMillis();
    // Create a member user first
    User memberUser =
        new User(0, memberLogin, "Member User", "memberPass", true, false, List.of("ROLE_MEMBER"));
    client
        .toBlocking()
        .exchange(
            HttpRequest.POST("/api/users", memberUser).basicAuth("superuser", config.superuser()));

    User newUser =
        new User(0, "unauthorized", "Unauthorized", "pass", true, false, List.of("ROLE_MEMBER"));

    // Try creating as member
    HttpRequest<User> createRequest =
        HttpRequest.POST("/api/users", newUser).basicAuth(memberLogin, "memberPass");

    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(createRequest));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
  }

  @Test
  void testForgotPassword() {
    String memberLogin = "member-forgot-testuser-" + System.currentTimeMillis();
    String password = "memberPass";
    // 1. Create a member user
    User memberUser =
        new User(0, memberLogin, "Member User", password, true, false, List.of("ROLE_MEMBER"));
    User createdMember =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.POST("/api/users", memberUser)
                    .basicAuth("superuser", config.superuser()),
                User.class);
    assertFalse(createdMember.forgotPassword());

    // 2. Call forgot-password as the member
    HttpResponse<Void> response =
        client
            .toBlocking()
            .exchange(
                HttpRequest.POST("/api/users/forgot-password", null)
                    .basicAuth(memberLogin, password));
    assertEquals(HttpStatus.OK, response.getStatus());

    // 3. Verify flag is updated
    User updatedUser =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.GET("/api/users/" + createdMember.id())
                    .basicAuth("superuser", config.superuser()),
                User.class);
    assertTrue(updatedUser.forgotPassword());

    // 4. List users with forgotPassword flag as admin
    HttpRequest<?> listForgotRequest =
        HttpRequest.GET("/api/users/forgot-password").basicAuth("superuser", config.superuser());
    List<User> forgotUsers =
        client.toBlocking().retrieve(listForgotRequest, Argument.listOf(User.class));
    assertTrue(forgotUsers.stream().anyMatch(u -> u.id() == createdMember.id()));
    assertTrue(
        forgotUsers.stream()
            .filter(u -> u.id() == createdMember.id())
            .allMatch(u -> "REDACTED".equals(u.passwordHash())));
  }
}
