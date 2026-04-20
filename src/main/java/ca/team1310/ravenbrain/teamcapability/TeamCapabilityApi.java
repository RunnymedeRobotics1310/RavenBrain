package ca.team1310.ravenbrain.teamcapability;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import ca.team1310.ravenbrain.http.ResponseEtags;
import ca.team1310.ravenbrain.tournament.TournamentService;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Serves enriched per-team capability rows for one tournament. Output is built by
 * {@link TeamCapabilityEnricher}, cached per-tournament in {@link TeamCapabilityCache}, and
 * returned with a weak ETag computed from a SHA-256 digest of the serialized body (first 12 hex
 * chars). Clients that honour {@code If-None-Match} receive {@code 304 Not Modified} when nothing
 * has changed since the last request — matches the {@code cacheFetch} contract on the RavenEye
 * side.
 *
 * <p>Content-hash ETags (rather than {@code MAX(updated_at)}-based weak validators as elsewhere)
 * are the right shape here because the response aggregates four independent sources, each with
 * its own update clock — a single max-timestamp doesn't exist without adding a view or a
 * denormalized column. Hashing the already-computed body is cheap compared with the enrichment
 * itself (which the cache already amortizes).
 *
 * <p>Endpoint is {@code @Secured(IS_AUTHENTICATED)} with no role gate — all authenticated users
 * (including DRIVE_TEAM / MEMBER) can read team capability data, per the plan's documented IDOR
 * acceptance.
 */
@Controller("/api/team-capability")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Slf4j
public class TeamCapabilityApi {

  private final TeamCapabilityEnricher enricher;
  private final TeamCapabilityCache cache;
  private final TournamentService tournamentService;
  private final ObjectMapper objectMapper;

  TeamCapabilityApi(
      TeamCapabilityEnricher enricher,
      TeamCapabilityCache cache,
      TournamentService tournamentService,
      ObjectMapper objectMapper) {
    this.enricher = enricher;
    this.cache = cache;
    this.tournamentService = tournamentService;
    this.objectMapper = objectMapper;
  }

  @Get("/{tournamentId}")
  @Produces(APPLICATION_JSON)
  public HttpResponse<?> getByTournament(
      HttpRequest<?> request, @PathVariable String tournamentId) {
    if (tournamentService.findById(tournamentId).isEmpty()) {
      return HttpResponse.notFound();
    }

    List<TeamCapabilityResponse> rows = cache.get(tournamentId);
    if (rows == null) {
      rows = enricher.enrich(tournamentId);
      cache.put(tournamentId, rows);
    }

    String tag = ResponseEtags.weakTag(bodyFingerprint(rows));
    Optional<String> ifNoneMatch = request.getHeaders().findFirst(HttpHeaders.IF_NONE_MATCH);
    if (ifNoneMatch.isPresent() && tag.equals(ifNoneMatch.get())) {
      return HttpResponse.notModified().header(HttpHeaders.ETAG, tag);
    }
    MutableHttpResponse<List<TeamCapabilityResponse>> ok = HttpResponse.ok(rows);
    return ok.header(HttpHeaders.ETAG, tag);
  }

  /**
   * Fingerprint the serialized response body as the first 12 hex chars of its SHA-256 digest —
   * matches {@link ca.team1310.ravenbrain.http.RoleFingerprintFilter#fingerprint}'s truncation for
   * consistency. On any serializer/digest failure we fall back to a constant so the read path
   * still succeeds (clients will skip the 304 optimization but otherwise behave correctly).
   */
  private String bodyFingerprint(List<TeamCapabilityResponse> rows) {
    try {
      byte[] bytes = objectMapper.writeValueAsString(rows).getBytes(StandardCharsets.UTF_8);
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(bytes);
      return HexFormat.of().formatHex(digest).substring(0, 12);
    } catch (IOException | java.security.NoSuchAlgorithmException e) {
      log.warn("Failed to fingerprint team-capability body: {}", e.getMessage());
      return "fallback";
    }
  }
}
