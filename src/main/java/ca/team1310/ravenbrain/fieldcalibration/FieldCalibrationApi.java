package ca.team1310.ravenbrain.fieldcalibration;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import ca.team1310.ravenbrain.connect.User;
import ca.team1310.ravenbrain.connect.UserService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Put;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import java.util.Optional;

@Controller("/api/field-calibration")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class FieldCalibrationApi {

  private final FieldCalibrationService service;
  private final UserService userService;

  public FieldCalibrationApi(FieldCalibrationService service, UserService userService) {
    this.service = service;
    this.userService = userService;
  }

  @Get("/{year}")
  @Produces(APPLICATION_JSON)
  public HttpResponse<FieldCalibration> get(@PathVariable int year) {
    Optional<FieldCalibration> cal = service.findByYear(year);
    return cal.map(HttpResponse::ok).orElseGet(HttpResponse::notFound);
  }

  @Put("/{year}")
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public FieldCalibration upsert(
      @PathVariable int year, @Body FieldCalibration body, Authentication authentication) {
    User user = resolveUser(authentication);
    // Path-year is authoritative. Any client-supplied body.year, updatedAt, or updatedByUserId is
    // discarded by the service, which stamps audit fields server-side.
    FieldCalibration toSave =
        new FieldCalibration(
            null,
            year,
            body.fieldLengthM(),
            body.fieldWidthM(),
            body.robotLengthM(),
            body.robotWidthM(),
            body.corner0X(),
            body.corner0Y(),
            body.corner1X(),
            body.corner1Y(),
            body.corner2X(),
            body.corner2Y(),
            body.corner3X(),
            body.corner3Y(),
            null,
            null);
    return service.upsert(toSave, user.id());
  }

  private User resolveUser(Authentication authentication) {
    return userService
        .findByLogin(authentication.getName())
        .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database"));
  }
}
