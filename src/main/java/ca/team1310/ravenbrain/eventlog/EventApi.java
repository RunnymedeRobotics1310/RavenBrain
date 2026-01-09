package ca.team1310.ravenbrain.eventlog;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-24 20:00
 */
@Controller("/api/event")
@Slf4j
public class EventApi {
  private final EventLogService eventLogService;

  public EventApi(EventLogService eventLogService) {
    this.eventLogService = eventLogService;
  }

  @Serdeable
  public record EventLogPostResult(EventLogRecord eventLogRecord, boolean success, String reason) {}

  @Post
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_DATASCOUT", "ROLE_EXPERTSCOUT", "ROLE_ADMIN", "ROLE_SUPERUSER"})
  public List<EventLogPostResult> postEventLogs(@Body EventLogRecord[] eventLogRecord) {
    var result = new ArrayList<EventLogPostResult>();
    for (EventLogRecord record : eventLogRecord) {
      try {
        record = eventLogService.save(record);
        result.add(new EventLogPostResult(record, true, null));
      } catch (DataAccessException e) {
        if (e.getMessage().contains("Duplicate entry")) {
          log.warn("Duplicate event log record: {}", record);
          result.add(new EventLogPostResult(record, true, null));
        } else {
          log.error("Failed to save event log record: {}", record, e);
          result.add(new EventLogPostResult(record, false, e.getMessage()));
        }
      } catch (Exception e) {
        log.error("Failed to save event log record: {}", record, e);
        result.add(new EventLogPostResult(record, false, e.getMessage()));
      }
    }
    return result;
  }
}
