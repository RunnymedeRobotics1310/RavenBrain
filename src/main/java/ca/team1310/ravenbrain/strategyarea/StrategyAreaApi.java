package ca.team1310.ravenbrain.strategyarea;

import ca.team1310.ravenbrain.http.ResponseEtags;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.transaction.annotation.Transactional;

/**
 * @author Junie
 * @since 2026-01-04
 */
@Controller("/api/strategy-areas")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class StrategyAreaApi {

  private final StrategyAreaService strategyAreaService;

  public StrategyAreaApi(StrategyAreaService strategyAreaService) {
    this.strategyAreaService = strategyAreaService;
  }

  private String etagVersion() {
    return Long.toString(strategyAreaService.maxUpdatedAt().toEpochMilli());
  }

  @Get
  @Transactional(readOnly = true)
  public HttpResponse<?> list(HttpRequest<?> request) {
    return ResponseEtags.withWeakEtag(request, etagVersion(), strategyAreaService::list);
  }

  @Get("/{id}")
  @Transactional(readOnly = true)
  public StrategyArea get(long id) {
    return strategyAreaService.findById(id);
  }

  @Post
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public StrategyArea create(@Body StrategyArea strategyArea) {
    return strategyAreaService.create(strategyArea);
  }

  @Put("/{id}")
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public StrategyArea update(long id, @Body StrategyArea strategyArea) {
    // Ensure id matches
    StrategyArea toUpdate =
        new StrategyArea(
            id, strategyArea.frcyear(), strategyArea.code(), strategyArea.name(), strategyArea.description(), strategyArea.disabled());
    return strategyAreaService.update(toUpdate);
  }

  @Delete("/{id}")
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public void delete(long id) {
    strategyAreaService.delete(id);
  }
}
