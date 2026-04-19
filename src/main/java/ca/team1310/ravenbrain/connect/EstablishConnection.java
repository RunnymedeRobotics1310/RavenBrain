package ca.team1310.ravenbrain.connect;

import ca.team1310.ravenbrain.Application;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Tony Field
 * @since 2025-03-25 08:02
 */
@Controller("/api")
public class EstablishConnection {

  private final RefreshTokenRepository refreshTokenRepository;

  public EstablishConnection(RefreshTokenRepository refreshTokenRepository) {
    this.refreshTokenRepository = refreshTokenRepository;
  }

  /**
   * Recognizable-body JSON shape returned by {@code GET /api/ping}. RavenEye's
   * liveness-qualification rules (Unit 8) require a 200 response with
   * {@code Content-Type: application/json}, the {@code X-RavenBrain-Version} header, AND a body
   * whose shape matches {@link #pong} {@code == true} and a string {@link #version}. Captive
   * portals that return {@code 200 text/html} or a generic {@code {"portal": true}} JSON
   * blob fail all of these and correctly leave the indicator offline.
   */
  @Introspected
  @Serdeable
  public record PingResponse(boolean pong, String version) {}

  @Get("/ping")
  @Produces(MediaType.APPLICATION_JSON)
  @Secured(SecurityRule.IS_ANONYMOUS)
  public HttpResponse<PingResponse> ping() {
    return HttpResponse.ok(new PingResponse(true, Application.getVersion()))
        .header("X-RavenBrain-Version", Application.getVersion());
  }

  @Get("/validate")
  @Produces(MediaType.APPLICATION_JSON)
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public Map<String, String> validate(Authentication authentication) {
    var map = new LinkedHashMap<String, String>();
    map.put("status", "ok");
    return map;
  }

  @Post("/logout")
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public HttpResponse<?> logout(Authentication authentication) {
    refreshTokenRepository.updateByUsername(authentication.getName(), true);
    return HttpResponse.ok();
  }
}
