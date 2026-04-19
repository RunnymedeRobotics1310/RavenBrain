package ca.team1310.ravenbrain.http;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.security.authentication.Authentication;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Emits {@code X-RavenBrain-Role-FP} on {@code 200} responses to authenticated requests. The
 * header carries a short, one-way fingerprint (first 12 hex chars of SHA-256 of the sorted
 * role list) — enough for RavenEye to detect a role change and re-fetch the user's roles via
 * {@code /api/validate}, but not enough to enumerate the full role list from a single response.
 *
 * <p>The header is deliberately <b>not</b> in the CORS {@code exposed-headers} list in
 * {@code application.yml}. Browser JavaScript running in the RavenEye origin cannot read it
 * directly — only Micronaut's response-writing layer sees the value, so an XSS payload in the
 * RavenEye origin cannot harvest role fingerprints across users. The real roles live in
 * {@code sessionStorage} from the JWT (same security posture as before); this filter only
 * provides the change-detection signal.
 *
 * <p>Emission rules (all must hold):
 *
 * <ul>
 *   <li>Response status is 200 (not 401, 403, 4xx, 5xx — authentication failures and errors
 *       do not advertise role information).
 *   <li>A Micronaut security context is present on the request — i.e., the request was
 *       authenticated successfully.
 * </ul>
 *
 * <p>A missing header (on error paths, anonymous endpoints, login/validate responses before the
 * principal is populated) is meaningful: the client treats missing-header differently from
 * empty-header and does not evict caches when the header is absent.
 */
@ServerFilter("/api/**")
public class RoleFingerprintFilter {

  /** Header name. Must match RavenEye's expected constant. */
  public static final String HEADER = "X-RavenBrain-Role-FP";

  @ResponseFilter
  public void addRoleFingerprint(HttpRequest<?> request, MutableHttpResponse<?> response) {
    if (response.status() != HttpStatus.OK) return;
    Optional<Authentication> auth = request.getUserPrincipal(Authentication.class);
    if (auth.isEmpty()) return;
    Collection<String> roles = auth.get().getRoles();
    if (roles == null || roles.isEmpty()) return;
    response.header(HEADER, fingerprint(roles));
  }

  /**
   * Short one-way fingerprint over the sorted role list. First 12 hex chars of SHA-256; 48 bits
   * of entropy — collision probability for any realistic role-set count is effectively zero.
   *
   * <p>Package-private for test coverage.
   */
  static String fingerprint(Collection<String> roles) {
    List<String> sorted = new ArrayList<>(roles);
    Collections.sort(sorted);
    String joined = String.join(",", sorted);
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(joined.getBytes(StandardCharsets.UTF_8));
      String hex = HexFormat.of().formatHex(digest);
      return hex.substring(0, 12);
    } catch (Exception e) {
      // SHA-256 is always available on a standard JVM; this is here so the header failure is
      // never fatal to the response path.
      return "";
    }
  }
}
