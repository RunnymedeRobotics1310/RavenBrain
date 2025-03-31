/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.quickcomment;

import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-31 00:28
 */
@Singleton
@Slf4j
public class QuickCommentService {
  private final List<QuickComment> FAKE_REPO = new ArrayList<>();

  public void addQuickComment(QuickComment comment) {
    FAKE_REPO.add(comment);
    log.info("Added comment to repo: {}", comment);
  }
}
