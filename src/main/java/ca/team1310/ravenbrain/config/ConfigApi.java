package ca.team1310.ravenbrain.config;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Exposes application-level config values that the frontend needs at runtime — most importantly
 * the owner team number, so UI code can stop hardcoding it.
 *
 * @author Tony Field
 * @since 2026-04-10
 */
@Controller("/api/config")
public class ConfigApi {

  private final int ownerTeamNumber;

  public ConfigApi(@Property(name = "raven-eye.team") int teamNumber) {
    this.ownerTeamNumber = teamNumber;
  }

  @Get("/owner-team")
  @Produces(APPLICATION_JSON)
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public OwnerTeamResponse getOwnerTeam() {
    return new OwnerTeamResponse(ownerTeamNumber);
  }

  @Serdeable
  public record OwnerTeamResponse(int teamNumber) {}
}
