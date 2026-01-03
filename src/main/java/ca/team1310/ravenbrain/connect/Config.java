package ca.team1310.ravenbrain.connect;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Tony Field
 * @since 2025-03-29 23:58
 */
@Data
@NoArgsConstructor
@Introspected
@Serdeable
@ConfigurationProperties("raven-eye")
public class Config {
  private String team;
  private Security security;

  @Data
  @NoArgsConstructor
  @Introspected
  @Serdeable
  @ConfigurationProperties("security")
  public static class Security {
    private Encryption encryption;
    private String superuserPassword;
    private String registrationSecret;

    @Data
    @NoArgsConstructor
    @Introspected
    @Serdeable
    @ConfigurationProperties("encryption")
    public static class Encryption {
      @NotBlank private String seed;
    }
  }

  public String encryptionSeed() {
    return security != null && security.getEncryption() != null
        ? security.getEncryption().getSeed()
        : null;
  }

  public String superuser() {
    return security != null ? security.getSuperuserPassword() : null;
  }

  @Override
  public String toString() {
    return "raven-eye configuration";
  }
}
