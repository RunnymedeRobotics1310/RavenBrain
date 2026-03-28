package ca.team1310.ravenbrain.nexusapi;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import java.util.Optional;

@Controller("/api/nexus")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class NexusApi {
  private final NexusService nexusService;

  NexusApi(NexusService nexusService) {
    this.nexusService = nexusService;
  }

  @Get("/queue-status/{tournamentId}")
  @Produces(APPLICATION_JSON)
  @Secured(SecurityRule.IS_ANONYMOUS)
  public HttpResponse<NexusQueueStatus> getQueueStatus(@Parameter String tournamentId) {
    Optional<NexusQueueStatus> status = nexusService.getQueueStatus(tournamentId);
    return status.map(HttpResponse::ok).orElseGet(HttpResponse::noContent);
  }
}
