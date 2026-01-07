package ca.team1310.ravenbrain.sequencetype;

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

  public SequenceTypeService(SequenceTypeRepository sequenceTypeRepository) {
    this.sequenceTypeRepository = sequenceTypeRepository;
  }

  public List<SequenceType> list() {
    return sequenceTypeRepository.findAll();
  }

  public Optional<SequenceType> findById(String name) {
    return sequenceTypeRepository.findById(name);
  }

  public SequenceType create(SequenceType sequenceType) {
    return sequenceTypeRepository.save(sequenceType);
  }

  public SequenceType update(SequenceType sequenceType) {
    return sequenceTypeRepository.update(sequenceType);
  }

  public void delete(String name) {
    sequenceTypeRepository.deleteById(name);
  }
}
