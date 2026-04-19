package ca.team1310.ravenbrain.sequencetype;

import ca.team1310.ravenbrain.http.ResponseEtags;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.transaction.annotation.Transactional;
import java.util.Set;

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

  private String etagVersion() {
    return Long.toString(sequenceTypeService.maxUpdatedAt().toEpochMilli());
  }

  @Get
  @Transactional(readOnly = true)
  public HttpResponse<?> list(HttpRequest<?> request) {
    return ResponseEtags.withWeakEtag(request, etagVersion(), sequenceTypeService::list);
  }

  @Get("/year/{year}")
  @Transactional(readOnly = true)
  public HttpResponse<?> findByFrcyear(int year, HttpRequest<?> request) {
    return ResponseEtags.withWeakEtag(
        request, etagVersion() + ":" + year, () -> sequenceTypeService.findByFrcyear(year));
  }

  @Get("/in-use")
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public Set<Long> findInUse() {
    return sequenceTypeService.findInUseIds();
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
            sequenceType.code(),
            sequenceType.name(),
            sequenceType.description(),
            sequenceType.frcyear(),
            sequenceType.disabled(),
            sequenceType.strategyareaId(),
            sequenceType.events());
    return sequenceTypeService.update(toUpdate);
  }

  @Delete("/{id}")
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public void delete(Long id) {
    sequenceTypeService.delete(id);
  }
}
