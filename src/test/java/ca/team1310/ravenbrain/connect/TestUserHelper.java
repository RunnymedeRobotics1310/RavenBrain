package ca.team1310.ravenbrain.connect;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class TestUserHelper {

  private final HttpClient client;
  private final Config config;

  public TestUserHelper(@Client("/") HttpClient client, Config config) {
    this.client = client;
    this.config = config;
  }

  public User createTestUser(String login, String password, String role) {
    if (!login.contains("testuser")) {
      throw new IllegalArgumentException("Test user login must contain 'testuser'");
    }
    User user = new User(0, login, login, password, true, false, List.of(role));
    HttpRequest<User> request =
        HttpRequest.POST("/api/users", user).basicAuth("superuser", config.superuser());
    return client.toBlocking().retrieve(request, User.class);
  }

  public void deleteTestUsers() {
    HttpRequest<?> listRequest =
        HttpRequest.GET("/api/users").basicAuth("superuser", config.superuser());
    List<User> users = client.toBlocking().retrieve(listRequest, Argument.listOf(User.class));

    for (User user : users) {
      if (user.login().contains("testuser")) {
        HttpRequest<?> deleteRequest =
            HttpRequest.DELETE("/api/users/" + user.id())
                .basicAuth("superuser", config.superuser());
        client.toBlocking().exchange(deleteRequest);
      }
    }
  }
}
