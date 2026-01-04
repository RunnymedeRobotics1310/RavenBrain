package ca.team1310.ravenbrain.eventtype;

import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;

/**
 * @author Junie
 * @since 2026-01-04
 */
@Singleton
public class EventTypeService {

  private final EventTypeRepository eventTypeRepository;

  public EventTypeService(EventTypeRepository eventTypeRepository) {
    this.eventTypeRepository = eventTypeRepository;
  }

  public List<EventType> list() {
    return eventTypeRepository.findAll();
  }

  public List<EventType> findByFrcyear(int frcyear) {
    return eventTypeRepository.findByFrcyear(frcyear);
  }

  public Optional<EventType> findById(String eventtype) {
    return eventTypeRepository.findById(eventtype);
  }

  public EventType create(EventType eventType) {
    if (!eventType.eventtype().matches("^[0-9a-zA-Z-]+$")) {
      throw new IllegalArgumentException(
          "Invalid eventtype format. Only numbers, upper and lower case characters, and the - character are allowed.");
    }
    return eventTypeRepository.save(eventType);
  }

  public EventType update(EventType eventType) {
    return eventTypeRepository.update(eventType);
  }

  public void delete(String eventtype) {
    eventTypeRepository.deleteById(eventtype);
  }
}
