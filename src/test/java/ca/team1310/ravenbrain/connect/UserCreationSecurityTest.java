package ca.team1310.ravenbrain.connect;

import static org.junit.jupiter.api.Assertions.*;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
public class UserCreationSecurityTest {

  @Inject
  @Client("/")
  HttpClient client;

  @Inject Config config;

  private static final String SUPERUSER = "superuser";
  private String adminLogin;
  private String adminPassword;
  private String memberLogin;
  private String memberPassword;

  @BeforeEach
  void setup() {
    adminLogin = "admin-" + System.currentTimeMillis();
    adminPassword = "adminPassword";
    memberLogin = "member-" + System.currentTimeMillis();
    memberPassword = "memberPassword";

    // Create an admin
    User adminUser =
        new User(0, adminLogin, "Admin", adminPassword, true, false, List.of("ROLE_ADMIN"));
    client
        .toBlocking()
        .retrieve(
            HttpRequest.POST("/api/users", adminUser).basicAuth(SUPERUSER, config.superuser()),
            User.class);

    // Create a member
    User memberUser =
        new User(0, memberLogin, "Member", memberPassword, true, false, List.of("ROLE_MEMBER"));
    client
        .toBlocking()
        .retrieve(
            HttpRequest.POST("/api/users", memberUser).basicAuth(SUPERUSER, config.superuser()),
            User.class);
  }

  @Test
  void onlySuperuserCanCreateSuperuser() {
    String newSuperuserLogin = "new-superuser-" + System.currentTimeMillis();
    User newSuperuser =
        new User(
            0, newSuperuserLogin, "New Superuser", "pass", true, false, List.of("ROLE_SUPERUSER"));

    // 1. Superuser can create superuser
    User createdBySuper =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.POST("/api/users", newSuperuser)
                    .basicAuth(SUPERUSER, config.superuser()),
                User.class);
    assertNotNull(createdBySuper);
    assertTrue(createdBySuper.roles().contains("ROLE_SUPERUSER"));

    // 2. Admin cannot create superuser
    String anotherSuperuserLogin = "another-superuser-" + System.currentTimeMillis();
    User anotherSuperuser =
        new User(
            0,
            anotherSuperuserLogin,
            "Another Superuser",
            "pass",
            true,
            false,
            List.of("ROLE_SUPERUSER"));

    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class,
            () -> {
              client
                  .toBlocking()
                  .exchange(
                      HttpRequest.POST("/api/users", anotherSuperuser)
                          .basicAuth(adminLogin, adminPassword));
            });
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());

    // 3. Member cannot create superuser
    HttpClientResponseException e2 =
        assertThrows(
            HttpClientResponseException.class,
            () -> {
              client
                  .toBlocking()
                  .exchange(
                      HttpRequest.POST("/api/users", anotherSuperuser)
                          .basicAuth(memberLogin, memberPassword));
            });
    assertEquals(HttpStatus.FORBIDDEN, e2.getStatus());
  }

  @Test
  void onlySuperuserCanCreateAdmin() {
    String adminBySuperLogin = "admin-by-super-" + System.currentTimeMillis();
    User adminBySuper =
        new User(
            0, adminBySuperLogin, "Admin By Super", "pass", true, false, List.of("ROLE_ADMIN"));

    // 1. Superuser can create admin
    User createdBySuper =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.POST("/api/users", adminBySuper)
                    .basicAuth(SUPERUSER, config.superuser()),
                User.class);
    assertNotNull(createdBySuper);
    assertTrue(createdBySuper.roles().contains("ROLE_ADMIN"));

    // 2. Admin can create admin
    String adminByAdminLogin = "admin-by-admin-" + System.currentTimeMillis();
    User adminByAdmin =
        new User(
            0, adminByAdminLogin, "Admin By Admin", "pass", true, false, List.of("ROLE_ADMIN"));

    HttpClientResponseException e2 =
        assertThrows(
            HttpClientResponseException.class,
            () -> {
              client
                  .toBlocking()
                  .exchange(
                      HttpRequest.POST("/api/users", adminByAdmin)
                          .basicAuth(adminLogin, adminPassword));
            });
    assertEquals(HttpStatus.FORBIDDEN, e2.getStatus());

    // 3. Member cannot create admin
    String adminByMemberLogin = "admin-by-member-" + System.currentTimeMillis();
    User adminByMember =
        new User(
            0, adminByMemberLogin, "Admin By Member", "pass", true, false, List.of("ROLE_ADMIN"));

    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class,
            () -> {
              client
                  .toBlocking()
                  .exchange(
                      HttpRequest.POST("/api/users", adminByMember)
                          .basicAuth(memberLogin, memberPassword));
            });
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
  }
}
