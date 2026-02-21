package ca.team1310.ravenbrain.strategyarea;

import ca.team1310.ravenbrain.eventtype.EventTypeRepository;
import ca.team1310.ravenbrain.sequencetype.SequenceTypeRepository;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import java.util.List;

/**
 * @author Junie
 * @since 2026-01-04
 */
@Singleton
public class StrategyAreaService {

  private final StrategyAreaRepository strategyAreaRepository;
  private final EventTypeRepository eventTypeRepository;
  private final SequenceTypeRepository sequenceTypeRepository;

  public StrategyAreaService(
      StrategyAreaRepository strategyAreaRepository,
      EventTypeRepository eventTypeRepository,
      SequenceTypeRepository sequenceTypeRepository) {
    this.strategyAreaRepository = strategyAreaRepository;
    this.eventTypeRepository = eventTypeRepository;
    this.sequenceTypeRepository = sequenceTypeRepository;
  }

  public List<StrategyArea> list() {
    return strategyAreaRepository.findAll();
  }

  public StrategyArea findById(long id) {
    return strategyAreaRepository.findById(id).orElse(null);
  }

  public StrategyArea create(StrategyArea strategyArea) {
    return strategyAreaRepository.save(strategyArea);
  }

  public StrategyArea update(StrategyArea strategyArea) {
    return strategyAreaRepository.update(strategyArea);
  }

  public void delete(long id) {
    if (!strategyAreaRepository.existsById(id)) {
      throw new HttpStatusException(HttpStatus.NOT_FOUND, "Strategy area not found");
    }
    if (eventTypeRepository.existsByStrategyareaId(id)
        || sequenceTypeRepository.existsByStrategyareaId(id)) {
      throw new HttpStatusException(
          HttpStatus.CONFLICT,
          "Cannot delete strategy area because it is referenced by event types or sequence types");
    }
    strategyAreaRepository.deleteById(id);
  }
}
