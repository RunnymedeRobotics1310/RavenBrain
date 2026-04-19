package ca.team1310.ravenbrain.http;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Weak-ETag helper for conditional GET endpoints.
 *
 * <p>Reads {@code If-None-Match} from the incoming request. If it matches the computed weak tag,
 * returns {@code 304 Not Modified} with the tag echoed and the body never computed. Otherwise
 * invokes {@code bodySupplier}, returns {@code 200 OK} with the tag in the {@code ETag} header.
 *
 * <p>We emit <b>weak</b> validators ({@code W/"<version>"}) rather than strong ones because
 * Cloudflare re-compression can change the byte-for-byte representation of a response without
 * changing its semantic content, and weak validators survive that. The controller's {@code
 * version} argument is a cheap, monotonic version source — typically {@code MAX(updated_at)} over
 * the relevant rows, or an already-materialized per-key timestamp such as {@code
 * RB_REPORT_CACHE.created}.
 *
 * <p>Intended call shape from a controller:
 *
 * <pre>{@code
 * @Get
 * @Transactional(readOnly = true, isolation = TransactionIsolation.REPEATABLE_READ)
 * public HttpResponse<?> list(HttpRequest<?> request) {
 *     String version = String.valueOf(service.maxUpdatedAtMillis());
 *     return ResponseEtags.withWeakEtag(request, version, () -> service.list());
 * }
 * }</pre>
 *
 * <p>The {@code @Transactional} annotation is what makes the ETag honest: the version query and
 * the body query run against the same MVCC snapshot, so a writer committing between the two
 * cannot produce a 304 that hides its changes.
 */
public final class ResponseEtags {

  private ResponseEtags() {}

  /**
   * Compute a weak ETag, compare it to the request's {@code If-None-Match}, and either short-circuit
   * to {@code 304} or invoke {@code bodySupplier} and return {@code 200} with the tag.
   *
   * @param request inbound request; {@code If-None-Match} (if present) is read from its headers
   * @param version version token. Callers typically pass an epoch-millis string or an integer. The
   *     token is embedded inside {@code W/"..."}, so anything that serializes uniquely per
   *     content revision is acceptable
   * @param bodySupplier producer of the response body. Not invoked when the ETag matches
   */
  public static <T> HttpResponse<?> withWeakEtag(
      HttpRequest<?> request, String version, Supplier<T> bodySupplier) {
    String tag = weakTag(version);
    Optional<String> ifNoneMatch = request.getHeaders().findFirst(HttpHeaders.IF_NONE_MATCH);
    if (ifNoneMatch.isPresent() && tag.equals(ifNoneMatch.get())) {
      return HttpResponse.notModified().header(HttpHeaders.ETAG, tag);
    }
    MutableHttpResponse<T> ok = HttpResponse.ok(bodySupplier.get());
    return ok.header(HttpHeaders.ETAG, tag);
  }

  /** Build a weak ETag string from a cheap version token. */
  public static String weakTag(String version) {
    return "W/\"" + version + "\"";
  }
}
