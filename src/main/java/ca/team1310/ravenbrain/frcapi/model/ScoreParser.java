/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.frcapi.model;

import ca.team1310.ravenbrain.frcapi.model.year2025.MatchScores;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;

/**
 * @author Tony Field
 * @since 2025-11-10 23:38
 */
@Singleton
public class ScoreParser {
  private final ObjectMapper objectMapper;

  public ScoreParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public MatchScores parse2025(String json) {
    try {
      return objectMapper.readValue(json, MatchScores.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to parse 2025 match scores", e);
    }
  }
}
