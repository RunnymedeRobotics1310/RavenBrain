package ca.team1310.ravenbrain.eventtype;

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
 * @since 2026-01-04
 */
@Controller("/api/event-types")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class EventTypeApi {

  private final EventTypeService eventTypeService;

  public EventTypeApi(EventTypeService eventTypeService) {
    this.eventTypeService = eventTypeService;
  }

  private String etagVersion() {
    return Long.toString(eventTypeService.maxUpdatedAt().toEpochMilli());
  }

  @Get
  @Transactional(readOnly = true)
  public HttpResponse<?> list(HttpRequest<?> request) {
    return ResponseEtags.withWeakEtag(request, etagVersion(), eventTypeService::list);
  }

  @Get("/year/{year}")
  @Transactional(readOnly = true)
  public HttpResponse<?> findByFrcyear(int year, HttpRequest<?> request) {
    // The year scope means a different subset, but the version source is still table-wide;
    // any change to RB_EVENTTYPE bumps the tag for every year filter. Acceptable since this
    // table changes rarely.
    return ResponseEtags.withWeakEtag(
        request, etagVersion() + ":" + year, () -> eventTypeService.findByFrcyear(year));
  }

  @Get("/in-use")
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public Set<String> findInUse() {
    // Admin endpoint; not part of the bulk-sync surface.
    return eventTypeService.findInUseEventTypeIds();
  }

  @Get("/{eventtype}")
  public EventType findById(String eventtype) {
    return eventTypeService.findById(eventtype).orElse(null);
  }

  @Post
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public EventType create(@Body EventType eventType) {
    return eventTypeService.create(eventType);
  }

  @Put("/{eventtype}")
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public EventType update(String eventtype, @Body EventType eventType) {
    // Ensure eventtype matches
    EventType toUpdate =
        new EventType(
            eventtype,
            eventType.name(),
            eventType.description(),
            eventType.frcyear(),
            eventType.strategyareaId(),
            eventType.showNote(),
            eventType.showQuantity(),
            eventType.disabled());
    return eventTypeService.update(toUpdate);
  }

  @Delete("/{eventtype}")
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public void delete(String eventtype) {
    eventTypeService.delete(eventtype);
  }
}
