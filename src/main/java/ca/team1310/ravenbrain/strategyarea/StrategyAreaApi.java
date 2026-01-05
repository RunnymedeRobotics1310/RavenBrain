package ca.team1310.ravenbrain.strategyarea;

import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import java.util.List;

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

  @Get
  public List<StrategyArea> list() {
    return strategyAreaService.list();
  }

  @Get("/{id}")
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
            id, strategyArea.frcyear(), strategyArea.name(), strategyArea.description());
    return strategyAreaService.update(toUpdate);
  }
}
