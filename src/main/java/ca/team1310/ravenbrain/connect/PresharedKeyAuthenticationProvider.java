package ca.team1310.ravenbrain.connect;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationFailureReason;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider;
import jakarta.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-25 09:11
 */
@Singleton
@Slf4j
public class PresharedKeyAuthenticationProvider<B> implements HttpRequestAuthenticationProvider<B> {

  private final Config config;

  public PresharedKeyAuthenticationProvider(Config config) {
    this.config = config;
  }

  @Override
  public AuthenticationResponse authenticate(
      @Nullable HttpRequest<B> httpRequest,
      @NonNull AuthenticationRequest<String, String> authenticationRequest) {
    String identity = authenticationRequest.getIdentity();
    String secret = authenticationRequest.getSecret();
    if (config.member().equals(secret)) {
      return AuthenticationResponse.success(identity, List.of("ROLE_MEMBER"));
    }
    if (config.datascout().equals(secret)) {
      return AuthenticationResponse.success(
          identity, Arrays.asList("ROLE_DATASCOUT", "ROLE_MEMBER"));
    }
    if (config.expertscout().equals(secret)) {
      return AuthenticationResponse.success(
          identity, Arrays.asList("ROLE_EXPERTSCOUT", "ROLE_DATASCOUT", "ROLE_MEMBER"));
    }
    if (config.admin().equals(secret)) {
      return AuthenticationResponse.success(
          identity,
          Arrays.asList("ROLE_ADMIN", "ROLE_EXPERTSCOUT", "ROLE_DATASCOUT", "ROLE_MEMBER"));
    }
    if (config.superuser().equals(secret)) {
      return AuthenticationResponse.success(
          identity,
          Arrays.asList(
              "ROLE_SUPERUSER", "ROLE_ADMIN", "ROLE_EXPERTSCOUT", "ROLE_DATASCOUT", "ROLE_MEMBER"));
    }
    return AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH);
  }
}
