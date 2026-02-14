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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@MicronautTest
public class UserApiTest {

  @Inject
  @Client("/")
  HttpClient client;

  @Inject Config config;

  @Inject TestUserHelper testUserHelper;

  @AfterEach
  void cleanup() {
    testUserHelper.deleteTestUsers();
  }

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
    // First, set forgot password flag so password can be changed
    client
        .toBlocking()
        .exchange(HttpRequest.POST("/api/users/forgot-password?login=" + testUserLogin, null));

    User updatedUserRequest =
        new User(
            id,
            testUserLogin,
            "Updated Name",
            "newPassword",
            true,
            true,
            List.of("ROLE_MEMBER", "ROLE_ADMIN"));
    HttpRequest<User> updateRequest =
        HttpRequest.PUT("/api/users/" + id, updatedUserRequest)
            .basicAuth("superuser", config.superuser());
    User updatedUser = client.toBlocking().retrieve(updateRequest, User.class);
    assertEquals("Updated Name", updatedUser.displayName());
    assertTrue(updatedUser.roles().contains("ROLE_ADMIN"));

    // 5.1 Update user as admin (non-superuser target)
    User updatedUserRequest2 =
        new User(
            id,
            testUserLogin,
            "Updated Name 2",
            "newPassword",
            true,
            true,
            List.of("ROLE_MEMBER", "ROLE_ADMIN"));
    HttpRequest<User> updateRequest2 =
        HttpRequest.PUT("/api/users/" + id, updatedUserRequest2).basicAuth(adminLogin, "adminPass");
    User updatedUser2 = client.toBlocking().retrieve(updateRequest2, User.class);
    assertEquals("Updated Name 2", updatedUser2.displayName());

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
        new User(
            0,
            "unauthorized-testuser",
            "Unauthorized",
            "pass",
            true,
            false,
            List.of("ROLE_MEMBER"));

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

    // 2. Call forgot-password (unauthenticated, by login)
    HttpResponse<Void> response =
        client
            .toBlocking()
            .exchange(
                HttpRequest.POST("/api/users/forgot-password?login=" + memberLogin, null));
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

  @Test
  void testGetOwnProfile() {
    String memberLogin = "member-me-testuser-" + System.currentTimeMillis();
    String password = "memberPass";

    // 1. Create a member user
    User memberUser =
        new User(0, memberLogin, "Member User", password, true, false, List.of("ROLE_MEMBER"));
    client
        .toBlocking()
        .exchange(
            HttpRequest.POST("/api/users", memberUser).basicAuth("superuser", config.superuser()));

    // 2. Member can view their own profile via /me
    User me =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.GET("/api/users/me").basicAuth(memberLogin, password), User.class);
    assertEquals(memberLogin, me.login());
    assertEquals("Member User", me.displayName());
    assertEquals("REDACTED", me.passwordHash());
    assertTrue(me.roles().contains("ROLE_MEMBER"));

    // 3. Unauthenticated request to /me is rejected
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.GET("/api/users/me"), User.class));
    assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
  }

  @Test
  void testUpdateOwnProfile() {
    String memberLogin = "member-putme-testuser-" + System.currentTimeMillis();
    String password = "memberPass";

    // 1. Create a member user
    User memberUser =
        new User(0, memberLogin, "Original Name", password, true, false, List.of("ROLE_MEMBER"));
    client
        .toBlocking()
        .exchange(
            HttpRequest.POST("/api/users", memberUser).basicAuth("superuser", config.superuser()));

    // 2. Update display name via PUT /me
    User updateRequest =
        new User(0, memberLogin, "Updated Name", "REDACTED", true, false, List.of("ROLE_MEMBER"));
    User updated =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.PUT("/api/users/me", updateRequest).basicAuth(memberLogin, password),
                User.class);
    assertEquals("Updated Name", updated.displayName());
    assertEquals("REDACTED", updated.passwordHash());

    // 3. Change password via PUT /me
    String newPassword = "newSecurePass";
    User passwordUpdate =
        new User(
            0, memberLogin, "Updated Name", newPassword, true, false, List.of("ROLE_MEMBER"));
    User afterPasswordChange =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.PUT("/api/users/me", passwordUpdate).basicAuth(memberLogin, password),
                User.class);
    assertEquals("Updated Name", afterPasswordChange.displayName());

    // 4. Verify new password works
    User meWithNewPass =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.GET("/api/users/me").basicAuth(memberLogin, newPassword), User.class);
    assertEquals(memberLogin, meWithNewPass.login());

    // 5. Verify roles/enabled/login cannot be changed by the user
    User escalationAttempt =
        new User(
            0,
            "hacked-login",
            "Updated Name",
            "REDACTED",
            false,
            false,
            List.of("ROLE_SUPERUSER"));
    User afterEscalation =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.PUT("/api/users/me", escalationAttempt)
                    .basicAuth(memberLogin, newPassword),
                User.class);
    assertEquals(memberLogin, afterEscalation.login());
    assertTrue(afterEscalation.enabled());
    assertTrue(afterEscalation.roles().contains("ROLE_MEMBER"));
    assertFalse(afterEscalation.roles().contains("ROLE_SUPERUSER"));
  }

  @Test
  void testAdminCannotModifySuperuser() {
    String adminLogin = "admin-security-testuser-" + System.currentTimeMillis();
    String adminPass = "adminPass";

    // 1. Create an admin user
    User adminUser =
        new User(0, adminLogin, "Admin User", adminPass, true, false, List.of("ROLE_ADMIN"));
    client
        .toBlocking()
        .exchange(
            HttpRequest.POST("/api/users", adminUser).basicAuth("superuser", config.superuser()));

    // 2. Find the superuser's ID
    HttpRequest<?> listRequest =
        HttpRequest.GET("/api/users").basicAuth("superuser", config.superuser());
    List<User> users = client.toBlocking().retrieve(listRequest, Argument.listOf(User.class));
    User superuser =
        users.stream()
            .filter(u -> u.roles().contains("ROLE_SUPERUSER"))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Superuser not found"));

    // 3. Try to modify the superuser as the admin
    User superuserUpdate =
        new User(
            superuser.id(),
            superuser.login(),
            "Hacked Name",
            "hackedPass",
            true,
            false,
            superuser.roles());

    HttpRequest<User> updateRequest =
        HttpRequest.PUT("/api/users/" + superuser.id(), superuserUpdate)
            .basicAuth(adminLogin, adminPass);

    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().exchange(updateRequest));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
  }
}
