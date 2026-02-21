package ca.team1310.ravenbrain.connect;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.errors.IssuingAnAccessTokenErrorCode;
import io.micronaut.security.errors.OauthErrorResponseException;
import io.micronaut.security.token.event.RefreshTokenGeneratedEvent;
import io.micronaut.security.token.refresh.RefreshTokenPersistence;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;

@Singleton
@Slf4j
public class RefreshTokenPersistenceImpl implements RefreshTokenPersistence {

  private final RefreshTokenRepository refreshTokenRepository;
  private final UserService userService;

  public RefreshTokenPersistenceImpl(
      RefreshTokenRepository refreshTokenRepository, UserService userService) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.userService = userService;
  }

  @Override
  public void persistToken(RefreshTokenGeneratedEvent event) {
    RefreshToken token =
        new RefreshToken(
            0,
            event.getAuthentication().getName(),
            event.getRefreshToken(),
            false,
            Instant.now());
    refreshTokenRepository.save(token);
  }

  @Override
  public Publisher<Authentication> getAuthentication(String refreshToken) {
    Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByRefreshToken(refreshToken);

    if (tokenOpt.isEmpty()) {
      return Publishers.just(
          new OauthErrorResponseException(
              IssuingAnAccessTokenErrorCode.INVALID_GRANT, "Refresh token not found", null));
    }

    RefreshToken token = tokenOpt.get();

    if (token.revoked()) {
      return Publishers.just(
          new OauthErrorResponseException(
              IssuingAnAccessTokenErrorCode.INVALID_GRANT,
              "Refresh token has been revoked",
              null));
    }

    Optional<User> userOpt = userService.findByLogin(token.username());

    if (userOpt.isEmpty()) {
      return Publishers.just(
          new OauthErrorResponseException(
              IssuingAnAccessTokenErrorCode.INVALID_GRANT, "User not found", null));
    }

    User user = userOpt.get();

    if (!user.enabled()) {
      return Publishers.just(
          new OauthErrorResponseException(
              IssuingAnAccessTokenErrorCode.INVALID_GRANT, "User is disabled", null));
    }

    return Publishers.just(
        Authentication.build(user.login(), user.roles(), userService.buildClaims(user)));
  }
}
