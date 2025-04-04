/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.report;

import ca.team1310.ravenbrain.quickcomment.QuickComment;
import ca.team1310.ravenbrain.quickcomment.QuickCommentService;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.util.List;

/**
 * @author Tony Field
 * @since 2025-04-04 16:33
 */
@Singleton
public class TeamReportService {
  private final QuickCommentService quickCommentService;

  @Serdeable
  public record TeamReport(List<QuickComment> comments) {}

  public TeamReportService(QuickCommentService quickCommentService) {
    this.quickCommentService = quickCommentService;
  }

  public TeamReport getTeamReport(int team) {
    List<QuickComment> comments = quickCommentService.findAllByTeamOrderByTimestamp(team);
    return new TeamReport(comments);
  }
}
