package ca.team1310.ravenbrain.connect;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

/**
 * @author Tony Field
 * @since 2025-03-29 23:58
 */
@Introspected
@Serdeable
@ConfigurationProperties("raven-eye")
public record Config(String team, Security security) {
  @Introspected
  @Serdeable
  @ConfigurationProperties("security")
  public record Security(
      @NotBlank String encryptionSeed, String superuserPassword, String registrationSecret) {}

  public String encryptionSeed() {
    return security != null ? security.encryptionSeed() : null;
  }

  public String superuser() {
    return security != null ? security.superuserPassword() : null;
  }

  @Override
  public String toString() {
    return "raven-eye configuration";
  }
}
