package ca.team1310.ravenbrain.fieldcalibration;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.connect.TestUserHelper;
import ca.team1310.ravenbrain.connect.UserService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
public class FieldCalibrationApiTest {

  private static final int TEST_YEAR = 2099;
  private static final String USER_ADMIN = "fc-admin-testuser";
  private static final String USER_ADMIN_2 = "fc-admin2-testuser";
  private static final String USER_SUPERUSER = "fc-superuser-testuser";
  private static final String USER_MEMBER = "fc-member-testuser";
  private static final String USER_DATASCOUT = "fc-datascout-testuser";
  private static final String PASSWORD = "password";

  @Inject
  @Client("/")
  HttpClient client;

  @Inject FieldCalibrationService service;
  @Inject TestUserHelper testUserHelper;
  @Inject UserService userService;

  @BeforeEach
  void setup() {
    testUserHelper.createTestUser(USER_ADMIN, PASSWORD, "ROLE_ADMIN");
    testUserHelper.createTestUser(USER_ADMIN_2, PASSWORD, "ROLE_ADMIN");
    testUserHelper.createTestUser(USER_SUPERUSER, PASSWORD, "ROLE_SUPERUSER");
    testUserHelper.createTestUser(USER_MEMBER, PASSWORD, "ROLE_MEMBER");
    testUserHelper.createTestUser(USER_DATASCOUT, PASSWORD, "ROLE_DATASCOUT");
    service.deleteByYear(TEST_YEAR);
  }

  @AfterEach
  void tearDown() {
    service.deleteByYear(TEST_YEAR);
    testUserHelper.deleteTestUsers();
  }

  private FieldCalibration sampleCalibration() {
    return new FieldCalibration(
        null,
        TEST_YEAR,
        16.54,
        8.07,
        0.84,
        0.84,
        0.05, 0.95, // corner 0
        0.95, 0.95, // corner 1
        0.95, 0.05, // corner 2
        0.05, 0.05, // corner 3
        null,
        null);
  }

  @Test
  void getUnauthenticatedReturns401() {
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.GET("/api/field-calibration/" + TEST_YEAR)));
    assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
  }

  @Test
  void getMissingYearReturns404() {
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class,
            () ->
                client
                    .toBlocking()
                    .exchange(
                        HttpRequest.GET("/api/field-calibration/" + TEST_YEAR)
                            .basicAuth(USER_MEMBER, PASSWORD)));
    assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
  }

  @Test
  void putUnauthenticatedReturns401() {
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class,
            () ->
                client
                    .toBlocking()
                    .exchange(
                        HttpRequest.PUT(
                            "/api/field-calibration/" + TEST_YEAR, sampleCalibration())));
    assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
  }

  @Test
  void putAsMemberReturns403() {
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class,
            () ->
                client
                    .toBlocking()
                    .exchange(
                        HttpRequest.PUT(
                                "/api/field-calibration/" + TEST_YEAR, sampleCalibration())
                            .basicAuth(USER_MEMBER, PASSWORD)));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
  }

  @Test
  void putAsDatascoutReturns403() {
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class,
            () ->
                client
                    .toBlocking()
                    .exchange(
                        HttpRequest.PUT(
                                "/api/field-calibration/" + TEST_YEAR, sampleCalibration())
                            .basicAuth(USER_DATASCOUT, PASSWORD)));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
  }

  @Test
  void putAsAdminCreatesThenMemberCanGet() {
    FieldCalibration created =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.PUT("/api/field-calibration/" + TEST_YEAR, sampleCalibration())
                    .basicAuth(USER_ADMIN, PASSWORD),
                FieldCalibration.class);
    assertNotNull(created.id());
    assertEquals(TEST_YEAR, created.year());
    assertEquals(16.54, created.fieldLengthM(), 1e-9);
    assertEquals(8.07, created.fieldWidthM(), 1e-9);
    assertEquals(0.05, created.corner0X(), 1e-9);
    assertNotNull(created.updatedAt());
    assertNotNull(created.updatedByUserId());

    long adminId = userService.findByLogin(USER_ADMIN).orElseThrow().id();
    assertEquals(adminId, created.updatedByUserId().longValue());

    FieldCalibration fetched =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.GET("/api/field-calibration/" + TEST_YEAR)
                    .basicAuth(USER_MEMBER, PASSWORD),
                FieldCalibration.class);
    assertEquals(created.id(), fetched.id());
    assertEquals(created.corner2X(), fetched.corner2X(), 1e-9);
  }

  @Test
  void putAsSuperuserAlsoCreates() {
    FieldCalibration created =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.PUT("/api/field-calibration/" + TEST_YEAR, sampleCalibration())
                    .basicAuth(USER_SUPERUSER, PASSWORD),
                FieldCalibration.class);
    assertNotNull(created.id());
    long superId = userService.findByLogin(USER_SUPERUSER).orElseThrow().id();
    assertEquals(superId, created.updatedByUserId().longValue());
  }

  @Test
  void secondPutUpdatesRatherThanInserts() throws InterruptedException {
    FieldCalibration first =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.PUT("/api/field-calibration/" + TEST_YEAR, sampleCalibration())
                    .basicAuth(USER_ADMIN, PASSWORD),
                FieldCalibration.class);

    // Sleep briefly so updatedAt is guaranteed to advance on the second write.
    Thread.sleep(5);

    FieldCalibration changed =
        new FieldCalibration(
            null, TEST_YEAR, 17.0, 9.0, 0.9, 0.9, 0.1, 0.9, 0.9, 0.9, 0.9, 0.1, 0.1, 0.1, null,
            null);
    FieldCalibration second =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.PUT("/api/field-calibration/" + TEST_YEAR, changed)
                    .basicAuth(USER_ADMIN_2, PASSWORD),
                FieldCalibration.class);

    assertEquals(first.id(), second.id(), "second PUT must reuse the row, not create a new one");
    assertEquals(17.0, second.fieldLengthM(), 1e-9);
    assertEquals(9.0, second.fieldWidthM(), 1e-9);
    assertEquals(0.1, second.corner0X(), 1e-9);
    assertTrue(
        second.updatedAt().isAfter(first.updatedAt()),
        "updatedAt must advance on second write");

    long admin2Id = userService.findByLogin(USER_ADMIN_2).orElseThrow().id();
    assertEquals(admin2Id, second.updatedByUserId().longValue());
  }

  @Test
  void updatedByUserIdIsServerStampedNotFromBody() {
    // Craft a body whose updatedByUserId points at someone else. The server must ignore it and
    // stamp the authenticated caller's id.
    long memberId = userService.findByLogin(USER_MEMBER).orElseThrow().id();
    long adminId = userService.findByLogin(USER_ADMIN).orElseThrow().id();
    FieldCalibration body =
        new FieldCalibration(
            null, TEST_YEAR, 16.54, 8.07, 0.84, 0.84,
            0.05, 0.95, 0.95, 0.95, 0.95, 0.05, 0.05, 0.05,
            null,
            memberId);

    FieldCalibration saved =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.PUT("/api/field-calibration/" + TEST_YEAR, body)
                    .basicAuth(USER_ADMIN, PASSWORD),
                FieldCalibration.class);

    assertEquals(adminId, saved.updatedByUserId().longValue());
    assertNotEquals(memberId, saved.updatedByUserId().longValue());
  }

  @Test
  void pathYearIsAuthoritativeOverBodyYear() {
    // Body.year contradicts the path. The service treats the path year as authoritative; body year
    // is silently discarded.
    FieldCalibration body =
        new FieldCalibration(
            null, 1999, 16.54, 8.07, 0.84, 0.84,
            0.05, 0.95, 0.95, 0.95, 0.95, 0.05, 0.05, 0.05,
            null, null);
    FieldCalibration saved =
        client
            .toBlocking()
            .retrieve(
                HttpRequest.PUT("/api/field-calibration/" + TEST_YEAR, body)
                    .basicAuth(USER_ADMIN, PASSWORD),
                FieldCalibration.class);
    assertEquals(TEST_YEAR, saved.year());

    // Sanity: no phantom record for the body-supplied year.
    Optional<FieldCalibration> phantom = service.findByYear(1999);
    assertTrue(phantom.isEmpty(), "no row should exist for the body-only year");
  }

  @Test
  void roundTripResponseShape() {
    // Happy-path GET returns HttpStatus.OK and the full record shape.
    client
        .toBlocking()
        .retrieve(
            HttpRequest.PUT("/api/field-calibration/" + TEST_YEAR, sampleCalibration())
                .basicAuth(USER_ADMIN, PASSWORD),
            FieldCalibration.class);

    HttpResponse<FieldCalibration> resp =
        client
            .toBlocking()
            .exchange(
                HttpRequest.GET("/api/field-calibration/" + TEST_YEAR)
                    .basicAuth(USER_MEMBER, PASSWORD),
                FieldCalibration.class);
    assertEquals(HttpStatus.OK, resp.getStatus());
    FieldCalibration cal = resp.body();
    assertNotNull(cal);
    assertEquals(TEST_YEAR, cal.year());
    assertEquals(0.84, cal.robotLengthM(), 1e-9);
  }
}
