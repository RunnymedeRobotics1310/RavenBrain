package ca.team1310.ravenbrain.eventtype;

import ca.team1310.ravenbrain.eventlog.EventLogRepository;
import ca.team1310.ravenbrain.sequencetype.SequenceEventRepository;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
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
  private final EventLogRepository eventLogRepository;
  private final SequenceEventRepository sequenceEventRepository;

  public EventTypeService(
      EventTypeRepository eventTypeRepository,
      EventLogRepository eventLogRepository,
      SequenceEventRepository sequenceEventRepository) {
    this.eventTypeRepository = eventTypeRepository;
    this.eventLogRepository = eventLogRepository;
    this.sequenceEventRepository = sequenceEventRepository;
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
    if (!eventTypeRepository.existsById(eventtype)) {
      throw new HttpStatusException(HttpStatus.NOT_FOUND, "Event type not found");
    }
    if (eventLogRepository.existsByEventType(eventtype)
        || sequenceEventRepository.existsByEventtypeId(eventtype)) {
      throw new HttpStatusException(
          HttpStatus.CONFLICT,
          "Cannot delete event type because it is referenced by event logs or sequence types");
    }
    eventTypeRepository.deleteById(eventtype);
  }
}
