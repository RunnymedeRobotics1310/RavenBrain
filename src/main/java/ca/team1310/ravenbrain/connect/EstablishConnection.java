package ca.team1310.ravenbrain.connect;

import ca.team1310.ravenbrain.Application;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Tony Field
 * @since 2025-03-25 08:02
 */
@Controller("/api")
public class EstablishConnection {

  @Get("/ping")
  @Produces(MediaType.TEXT_PLAIN)
  @Secured(SecurityRule.IS_ANONYMOUS)
  public String ping() {
    return "pong";
  }

  @Get("/validate")
  @Produces(MediaType.APPLICATION_JSON)
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public Map<String, String> validate(Authentication authentication) {
    var map = new LinkedHashMap<String, String>();
    map.put("status", "ok");
    map.put("version", Application.getVersion());
    return map;
  }
}
