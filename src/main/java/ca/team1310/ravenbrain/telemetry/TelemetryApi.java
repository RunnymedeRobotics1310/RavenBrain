package ca.team1310.ravenbrain.telemetry;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Controller("/api/telemetry")
@Slf4j
public class TelemetryApi {

  private final TelemetryService telemetryService;
  private final TelemetryBackfillService backfillService;

  public TelemetryApi(
      TelemetryService telemetryService, TelemetryBackfillService backfillService) {
    this.telemetryService = telemetryService;
    this.backfillService = backfillService;
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

  @Get("/session/{sessionId}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_TELEMETRY_AGENT", "ROLE_TELEMETRY_USER", "ROLE_SUPERUSER"})
  public HttpResponse<TelemetrySession> getSession(@PathVariable String sessionId) {
    return telemetryService
        .findSessionBySessionId(sessionId)
        .map(HttpResponse::ok)
        .orElseGet(HttpResponse::notFound);
  }

  @Get("/match/{tournamentId}/{matchLevel}/{matchNumber}")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_TELEMETRY_USER", "ROLE_SUPERUSER"})
  public List<TelemetrySession> listSessionsForMatch(
      @PathVariable String tournamentId,
      @PathVariable TournamentLevel matchLevel,
      @PathVariable int matchNumber) {
    return telemetryService.findSessionsForMatch(tournamentId, matchLevel, matchNumber);
  }

  @Post("/session")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_TELEMETRY_AGENT", "ROLE_SUPERUSER"})
  public HttpResponse<TelemetrySession> createSession(@Body CreateSessionRequest request) {
    TelemetrySession session =
        telemetryService.createSession(
            request.sessionId(), request.teamNumber(), request.robotIp(), request.startedAt());
    return HttpResponse.ok(session);
  }

  @Post("/session/{sessionId}/data")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_TELEMETRY_AGENT", "ROLE_SUPERUSER"})
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
  @Secured({"ROLE_TELEMETRY_AGENT", "ROLE_SUPERUSER"})
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

  @Post("/backfill-match-identity")
  @Produces(APPLICATION_JSON)
  @Secured({"ROLE_SUPERUSER"})
  public HttpResponse<TelemetryBackfillService.BackfillResult> backfillMatchIdentity(
      @Nullable @QueryValue Boolean force) {
    TelemetryBackfillService.BackfillResult result =
        backfillService.backfill(Boolean.TRUE.equals(force));
    return HttpResponse.ok(result);
  }
}
