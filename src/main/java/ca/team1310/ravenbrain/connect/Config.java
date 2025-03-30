/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.connect;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Data;

/**
 * @author Tony Field
 * @since 2025-03-29 23:58
 */
@ConfigurationProperties("raven-eye.role-passwords")
@Data
public class Config {
  private String admin;
  private String expertscout;
  private String datascout;
  private String member;
}
