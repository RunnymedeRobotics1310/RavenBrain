package ca.team1310.ravenbrain.connect;

import static org.junit.jupiter.api.Assertions.*;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@MicronautTest(transactional = false)
public class RefreshTokenTest {

  @Inject
  @Client("/")
  HttpClient client;

  @Inject Config config;
  @Inject TestUserHelper testUserHelper;
  @Inject RefreshTokenRepository refreshTokenRepository;

  @AfterEach
  void cleanup() {
    refreshTokenRepository.deleteAll();
    testUserHelper.deleteTestUsers();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> login(String username, String password) {
    HttpRequest<?> request =
        HttpRequest.POST("/login", Map.of("username", username, "password", password));
    return client.toBlocking().retrieve(request, Map.class);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> refreshToken(String refreshToken) {
    HttpRequest<?> request =
        HttpRequest.POST(
            "/oauth/access_token",
            Map.of("grant_type", "refresh_token", "refresh_token", refreshToken));
    return client.toBlocking().retrieve(request, Map.class);
  }

  @Test
  void testLoginReturnsBothTokens() {
    String login = "testuser-refresh-" + System.currentTimeMillis();
    String password = "testPassword123";
    testUserHelper.createTestUser(login, password, "ROLE_MEMBER");

    Map<String, Object> response = login(login, password);
    assertNotNull(response.get("access_token"));
    assertNotNull(response.get("refresh_token"));
  }

  @Test
  void testRefreshTokenReturnsNewTokens() {
    String login = "testuser-refresh2-" + System.currentTimeMillis();
    String password = "testPassword123";
    testUserHelper.createTestUser(login, password, "ROLE_MEMBER");

    Map<String, Object> loginResponse = login(login, password);
    String refreshTokenValue = (String) loginResponse.get("refresh_token");

    Map<String, Object> refreshResponse = refreshToken(refreshTokenValue);
    assertNotNull(refreshResponse.get("access_token"));
    assertNotNull(refreshResponse.get("refresh_token"));
  }

  @Test
  void testInvalidRefreshTokenRejected() {
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class, () -> refreshToken("invalid-token-value"));
    assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
  }

  @Test
  void testRevokedRefreshTokenRejected() {
    String login = "testuser-revoked-" + System.currentTimeMillis();
    String password = "testPassword123";
    testUserHelper.createTestUser(login, password, "ROLE_MEMBER");

    Map<String, Object> loginResponse = login(login, password);
    String refreshTokenValue = (String) loginResponse.get("refresh_token");

    // Revoke all tokens for this user
    refreshTokenRepository.updateByUsername(login, true);

    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class, () -> refreshToken(refreshTokenValue));
    assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
  }

  @Test
  void testDisabledUserCannotRefresh() {
    String login = "testuser-disabled-" + System.currentTimeMillis();
    String password = "testPassword123";
    testUserHelper.createTestUser(login, password, "ROLE_MEMBER");

    Map<String, Object> loginResponse = login(login, password);
    String refreshTokenValue = (String) loginResponse.get("refresh_token");

    // Disable the user
    User disabledUser =
        new User(0, login, login, "REDACTED", false, false, List.of("ROLE_MEMBER"));
    HttpRequest<User> updateRequest =
        HttpRequest.PUT("/api/users/" + loginResponse.get("access_token"), disabledUser)
            .basicAuth("superuser", config.superuser());

    // Find the user ID and disable via superuser
    List<User> users =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.GET("/api/users").basicAuth("superuser", config.superuser()),
                io.micronaut.core.type.Argument.listOf(User.class));
    User targetUser =
        users.stream().filter(u -> u.login().equals(login)).findFirst().orElseThrow();

    User disableUpdate =
        new User(
            targetUser.id(), login, login, "REDACTED", false, false, List.of("ROLE_MEMBER"));
    client
        .toBlocking()
        .exchange(
            HttpRequest.PUT("/api/users/" + targetUser.id(), disableUpdate)
                .basicAuth("superuser", config.superuser()));

    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class, () -> refreshToken(refreshTokenValue));
    assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
  }

  @Test
  void testLogoutRevokesRefreshTokens() {
    String login = "testuser-logout-" + System.currentTimeMillis();
    String password = "testPassword123";
    testUserHelper.createTestUser(login, password, "ROLE_MEMBER");

    Map<String, Object> loginResponse = login(login, password);
    String accessToken = (String) loginResponse.get("access_token");
    String refreshTokenValue = (String) loginResponse.get("refresh_token");

    // Call logout (revokes all refresh tokens for the user)
    HttpResponse<?> logoutResponse =
        client
            .toBlocking()
            .exchange(
                HttpRequest.POST("/api/logout", "{}").bearerAuth(accessToken));
    assertEquals(HttpStatus.OK, logoutResponse.getStatus());

    // Verify the refresh token is now rejected
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class, () -> refreshToken(refreshTokenValue));
    assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
  }
}
