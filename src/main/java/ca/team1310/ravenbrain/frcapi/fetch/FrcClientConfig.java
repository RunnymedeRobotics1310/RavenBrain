package ca.team1310.ravenbrain.frcapi.fetch;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Configuration data for FRC API
 *
 * @author Tony Field
 * @since 2025-09-21 12:43
 */
@ConfigurationProperties("raven-eye.frc-api")
class FrcClientConfig {
  String user;
  String key;
}
