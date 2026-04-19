package ca.team1310.ravenbrain.sequencetype;

import ca.team1310.ravenbrain.eventlog.EventLogRepository;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Junie
 * @since 2026-01-07
 */
@Singleton
public class SequenceTypeService {

  private final SequenceTypeRepository sequenceTypeRepository;
  private final SequenceEventRepository sequenceEventRepository;
  private final EventLogRepository eventLogRepository;

  public SequenceTypeService(
      SequenceTypeRepository sequenceTypeRepository,
      SequenceEventRepository sequenceEventRepository,
      EventLogRepository eventLogRepository) {
    this.sequenceTypeRepository = sequenceTypeRepository;
    this.sequenceEventRepository = sequenceEventRepository;
    this.eventLogRepository = eventLogRepository;
  }

  public List<SequenceType> list() {
    return sequenceTypeRepository.findAll();
  }

  /** Returns max(updated_at) across RB_SEQUENCETYPE, or {@link Instant#EPOCH} when empty. */
  public Instant maxUpdatedAt() {
    return sequenceTypeRepository.findMaxUpdatedAt().orElse(Instant.EPOCH);
  }

  public List<SequenceType> findByFrcyear(int frcyear) {
    return sequenceTypeRepository.findByFrcyear(frcyear);
  }

  public Optional<SequenceType> findById(Long id) {
    return sequenceTypeRepository.findById(id);
  }

  @jakarta.transaction.Transactional
  public SequenceType create(SequenceType sequenceType) {
    SequenceType saved = sequenceTypeRepository.save(sequenceType);
    if (sequenceType.events() != null && !sequenceType.events().isEmpty()) {
      List<SequenceEvent> eventsToSave =
          sequenceType.events().stream()
              .map(
                  se ->
                      new SequenceEvent(
                          null, saved, se.eventtype(), se.startOfSequence(), se.endOfSequence()))
              .toList();
      sequenceEventRepository.saveAll(eventsToSave);
    }
    return findById(saved.id()).orElse(saved);
  }

  @jakarta.transaction.Transactional
  public SequenceType update(SequenceType sequenceType) {
    SequenceType updated = sequenceTypeRepository.update(sequenceType);
    sequenceEventRepository.deleteBySequencetype(updated);
    if (sequenceType.events() != null && !sequenceType.events().isEmpty()) {
      List<SequenceEvent> eventsToSave =
          sequenceType.events().stream()
              .map(
                  se ->
                      new SequenceEvent(
                          null, updated, se.eventtype(), se.startOfSequence(), se.endOfSequence()))
              .toList();
      sequenceEventRepository.saveAll(eventsToSave);
    }
    return findById(updated.id()).orElse(updated);
  }

  public Set<Long> findInUseIds() {
    Set<String> usedEventTypes = Set.copyOf(eventLogRepository.findDistinctEventTypes());
    return sequenceTypeRepository.findAll().stream()
        .filter(
            st ->
                st.events() != null
                    && st.events().stream()
                        .anyMatch(se -> usedEventTypes.contains(se.eventtype().eventtype())))
        .map(SequenceType::id)
        .collect(Collectors.toSet());
  }

  public void delete(Long id) {
    if (!sequenceTypeRepository.existsById(id)) {
      throw new HttpStatusException(HttpStatus.NOT_FOUND, "Sequence type not found");
    }
    SequenceType st = sequenceTypeRepository.findById(id).orElseThrow();
    if (st.events() != null) {
      Set<String> usedEventTypes = Set.copyOf(eventLogRepository.findDistinctEventTypes());
      boolean inUse =
          st.events().stream()
              .anyMatch(se -> usedEventTypes.contains(se.eventtype().eventtype()));
      if (inUse) {
        throw new HttpStatusException(
            HttpStatus.CONFLICT,
            "Cannot delete sequence type because its events have recorded data");
      }
    }
    sequenceTypeRepository.deleteById(id);
  }
}
