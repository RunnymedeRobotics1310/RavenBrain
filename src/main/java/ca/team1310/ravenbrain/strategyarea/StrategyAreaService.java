/*
 * Copyright 2026 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.strategyarea;

import jakarta.inject.Singleton;
import java.util.List;

/**
 * @author Junie
 * @since 2026-01-04
 */
@Singleton
public class StrategyAreaService {

  private final StrategyAreaRepository strategyAreaRepository;

  public StrategyAreaService(StrategyAreaRepository strategyAreaRepository) {
    this.strategyAreaRepository = strategyAreaRepository;
  }

  public List<StrategyArea> list() {
    return strategyAreaRepository.findAll();
  }

  public StrategyArea create(StrategyArea strategyArea) {
    return strategyAreaRepository.save(strategyArea);
  }

  public StrategyArea update(StrategyArea strategyArea) {
    return strategyAreaRepository.update(strategyArea);
  }

  public void delete(long id) {
    strategyAreaRepository.deleteById(id);
  }
}
