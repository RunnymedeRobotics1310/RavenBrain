package ca.team1310.ravenbrain.telemetry;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Controller("/api/telemetry")
@Slf4j
public class TelemetryApi {

  private final TelemetryService telemetryService;

  public TelemetryApi(TelemetryService telemetryService) {
    this.telemetryService = telemetryService;
  }

  @Serdeable
  public record CreateSessionRequest(
      String sessionId, int teamNumber, String robotIp, Instant startedAt) {}

  @Serdeable
  public record TelemetryEntryRequest(
      Instant ts,
      String entryType,
      @Nullable String ntKey,
      @Nullable String ntType,
      @Nullable String ntValue,
      @Nullable Integer fmsRaw) {}

  @Serdeable
  public record CompleteSessionRequest(Instant endedAt, int entryCount) {}

  @Serdeable
  public record BatchInsertResult(int count) {}

  @Post("/session")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Secured(SecurityRule.IS_ANONYMOUS)
  public HttpResponse<TelemetrySession> createSession(@Body CreateSessionRequest request) {
    log.info(
        "Creating telemetry session {} for team {}", request.sessionId(), request.teamNumber());
    TelemetrySession session =
        telemetryService.createSession(
            request.sessionId(), request.teamNumber(), request.robotIp(), request.startedAt());
    return HttpResponse.created(session);
  }

  @Post("/session/{sessionId}/data")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Secured(SecurityRule.IS_ANONYMOUS)
  public HttpResponse<BatchInsertResult> postEntries(
      @PathVariable String sessionId, @Body List<TelemetryEntryRequest> entries) {
    Optional<TelemetrySession> session = telemetryService.findSessionBySessionId(sessionId);
    if (session.isEmpty()) {
      return HttpResponse.notFound();
    }
    telemetryService.bulkInsertEntries(session.get().id(), entries);
    return HttpResponse.ok(new BatchInsertResult(entries.size()));
  }

  @Post("/session/{sessionId}/complete")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Secured(SecurityRule.IS_ANONYMOUS)
  public HttpResponse<TelemetrySession> completeSession(
      @PathVariable String sessionId, @Body CompleteSessionRequest request) {
    try {
      telemetryService.completeSession(sessionId, request.endedAt(), request.entryCount());
      Optional<TelemetrySession> updated = telemetryService.findSessionBySessionId(sessionId);
      return updated.map(HttpResponse::ok).orElseGet(HttpResponse::notFound);
    } catch (RuntimeException e) {
      log.error("Failed to complete session {}: {}", sessionId, e.getMessage());
      return HttpResponse.notFound();
    }
  }
}
