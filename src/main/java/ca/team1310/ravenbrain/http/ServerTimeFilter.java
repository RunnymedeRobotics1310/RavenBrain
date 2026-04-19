package ca.team1310.ravenbrain.http;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;

/**
 * Adds {@code X-RavenBrain-Time} (epoch milliseconds) to every server response. RavenEye uses this
 * header to compute and bound a client-server clock offset so that time-sensitive comparisons
 * (JWT expiration, tournament-window membership, "N minutes ago" displays) are correct even when
 * the user's device clock is wrong.
 *
 * <p>The filter is unconditional: even {@code /login}, {@code /api/validate}, {@code /api/ping},
 * error paths, and anonymous endpoints emit the header. RavenEye's skew module clamps single-update
 * changes to ±5 minutes and retains the last-good offset for 12 hours without a fresh header, so a
 * missing header on some paths is never worse than a stale offset.
 */
@ServerFilter("/**")
public class ServerTimeFilter {

  /** The header name used for the server's current time (epoch milliseconds). */
  public static final String HEADER = "X-RavenBrain-Time";

  @ResponseFilter
  public void addServerTime(HttpRequest<?> request, MutableHttpResponse<?> response) {
    response.header(HEADER, Long.toString(System.currentTimeMillis()));
  }
}
