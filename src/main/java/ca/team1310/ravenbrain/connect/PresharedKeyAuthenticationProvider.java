package ca.team1310.ravenbrain.connect;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationFailureReason;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider;
import jakarta.inject.Singleton;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-25 09:11
 */
@Singleton
@Slf4j
public class PresharedKeyAuthenticationProvider<B> implements HttpRequestAuthenticationProvider<B> {

  private final UserService userService;

  public PresharedKeyAuthenticationProvider(UserService userService) {
    this.userService = userService;
  }

  @Override
  public AuthenticationResponse authenticate(
      @Nullable HttpRequest<B> httpRequest,
      @NonNull AuthenticationRequest<String, String> authenticationRequest) {

    String login = authenticationRequest.getIdentity();
    String secret = authenticationRequest.getSecret();

    Optional<User> ou = userService.findByLogin(login);
    if (ou.isEmpty()) {
      return AuthenticationResponse.failure(AuthenticationFailureReason.USER_NOT_FOUND);
    }

    if (!ou.get().passwordHash().equals(userService.hashPassword(secret))) {
      return AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH);
    }

    if (!ou.get().enabled()) {
      return AuthenticationResponse.failure(AuthenticationFailureReason.USER_DISABLED);
    }

    Map<String, Object> map = new LinkedHashMap<>();
    map.put("userid", ou.get().id());
    map.put("login", login);
    map.put("displayName", ou.get().displayName());
    return AuthenticationResponse.success(login, ou.get().roles(), map);
  }
}
