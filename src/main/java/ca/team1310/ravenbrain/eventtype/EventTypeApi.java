package ca.team1310.ravenbrain.eventtype;

import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import java.util.List;

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

  @Get
  public List<EventType> list() {
    return eventTypeService.list();
  }

  @Get("/year/{year}")
  public List<EventType> findByFrcyear(int year) {
    return eventTypeService.findByFrcyear(year);
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
            eventType.strategyareaId());
    return eventTypeService.update(toUpdate);
  }

  @Delete("/{eventtype}")
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public void delete(String eventtype) {
    eventTypeService.delete(eventtype);
  }
}
