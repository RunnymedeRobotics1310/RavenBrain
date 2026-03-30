package ca.team1310.ravenbrain.matchvideo;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Controller("/api/match-video")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Slf4j
public class MatchVideoApi {
  private final MatchVideoService matchVideoService;

  public MatchVideoApi(MatchVideoService matchVideoService) {
    this.matchVideoService = matchVideoService;
  }

  @Get("/{tournamentId}")
  @Produces(APPLICATION_JSON)
  public List<MatchVideoRecord> getByTournament(@PathVariable String tournamentId) {
    return matchVideoService.findAllByTournamentId(tournamentId);
  }

  @Get("/{tournamentId}/{level}/{match}")
  @Produces(APPLICATION_JSON)
  public List<MatchVideoRecord> getByMatch(
      @PathVariable String tournamentId,
      @PathVariable String level,
      @PathVariable int match) {
    return matchVideoService.findByMatch(tournamentId, level, match);
  }

  @Introspected
  @Serdeable
  record AddMatchVideoRequest(
      String tournamentId, String matchLevel, int matchNumber, String label, String videoUrl) {}

  @Post
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public HttpResponse<MatchVideoRecord> addVideo(@Body AddMatchVideoRequest request) {
    var record =
        new MatchVideoRecord(
            null,
            request.tournamentId(),
            request.matchLevel(),
            request.matchNumber(),
            request.label(),
            request.videoUrl());
    var saved = matchVideoService.save(record);
    log.info(
        "Added match video: {} {} M{} [{}]",
        request.tournamentId(),
        request.matchLevel(),
        request.matchNumber(),
        request.label());
    return HttpResponse.created(saved);
  }

  @Delete("/{id}")
  @Secured("ROLE_ADMIN")
  public HttpResponse<?> deleteVideo(@PathVariable long id) {
    matchVideoService.deleteById(id);
    log.info("Deleted match video id={}", id);
    return HttpResponse.ok();
  }
}
