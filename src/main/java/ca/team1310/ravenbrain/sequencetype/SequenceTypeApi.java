package ca.team1310.ravenbrain.sequencetype;

import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import java.util.List;

/**
 * @author Junie
 * @since 2026-01-07
 */
@Controller("/api/sequence-types")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class SequenceTypeApi {

  private final SequenceTypeService sequenceTypeService;

  public SequenceTypeApi(SequenceTypeService sequenceTypeService) {
    this.sequenceTypeService = sequenceTypeService;
  }

  @Get
  public List<SequenceType> list() {
    return sequenceTypeService.list();
  }

  @Get("/year/{year}")
  public List<SequenceType> findByFrcyear(int year) {
    return sequenceTypeService.findByFrcyear(year);
  }

  @Get("/{id}")
  public SequenceType findById(Long id) {
    return sequenceTypeService.findById(id).orElse(null);
  }

  @Post
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public SequenceType create(@Body SequenceType sequenceType) {
    return sequenceTypeService.create(sequenceType);
  }

  @Put("/{id}")
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public SequenceType update(Long id, @Body SequenceType sequenceType) {
    SequenceType toUpdate =
        new SequenceType(
            id,
            sequenceType.name(),
            sequenceType.description(),
            sequenceType.frcyear(),
            sequenceType.events());
    return sequenceTypeService.update(toUpdate);
  }

  @Delete("/{id}")
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public void delete(Long id) {
    sequenceTypeService.delete(id);
  }
}
