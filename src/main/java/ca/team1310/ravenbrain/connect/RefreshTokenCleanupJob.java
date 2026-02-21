package ca.team1310.ravenbrain.connect;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class RefreshTokenCleanupJob {

  private final RefreshTokenRepository refreshTokenRepository;

  public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokenRepository) {
    this.refreshTokenRepository = refreshTokenRepository;
  }

  @Scheduled(cron = "0 0 3 * * ?")
  void cleanupExpiredTokens() {
    Instant cutoff = Instant.now().minus(14, ChronoUnit.DAYS);
    refreshTokenRepository.deleteByDateCreatedBefore(cutoff);
    log.info("Cleaned up refresh tokens older than {}", cutoff);
  }
}
