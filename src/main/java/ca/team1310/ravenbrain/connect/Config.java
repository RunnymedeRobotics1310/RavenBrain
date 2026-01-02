package ca.team1310.ravenbrain.connect;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Introspected;

/**
 * @author Tony Field
 * @since 2025-03-29 23:58
 */
@Introspected
@ConfigurationProperties("raven-eye.role-passwords")
public record Config(
    String superuser, String admin, String expertscout, String datascout, String member) {

  public String toString() {
    return "raven-eye.role-passwords";
  }
}
