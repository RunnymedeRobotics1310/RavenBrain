package ca.team1310.ravenbrain.sequencetype;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;

/**
 * @author Junie
 * @since 2026-01-07
 */
@Singleton
public class SequenceTypeService {

  private final SequenceTypeRepository sequenceTypeRepository;
  private final SequenceEventRepository sequenceEventRepository;

  public SequenceTypeService(
      SequenceTypeRepository sequenceTypeRepository,
      SequenceEventRepository sequenceEventRepository) {
    this.sequenceTypeRepository = sequenceTypeRepository;
    this.sequenceEventRepository = sequenceEventRepository;
  }

  public List<SequenceType> list() {
    return sequenceTypeRepository.findAll();
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

  public void delete(Long id) {
    if (!sequenceTypeRepository.existsById(id)) {
      throw new HttpStatusException(HttpStatus.NOT_FOUND, "Sequence type not found");
    }
    sequenceTypeRepository.deleteById(id);
  }
}
