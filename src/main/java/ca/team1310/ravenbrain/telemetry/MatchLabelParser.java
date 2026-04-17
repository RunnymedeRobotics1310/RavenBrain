package ca.team1310.ravenbrain.telemetry;

import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

public final class MatchLabelParser {

  private MatchLabelParser() {}

  @Serdeable
  public record ParsedMatchLabel(
      @Nullable TournamentLevel level,
      @Nullable Integer number,
      @Nullable String playoffRound,
      @Nullable String rawLabel) {

    public boolean isStructured() {
      return level != null && number != null;
    }
  }

  public static ParsedMatchLabel parse(@Nullable String label) {
    if (label == null) {
      return new ParsedMatchLabel(null, null, null, null);
    }
    String stripped = label.replaceAll("\\s+", "");
    if (stripped.isEmpty()) {
      return new ParsedMatchLabel(null, null, null, null);
    }
    String raw = stripped;
    String upper = stripped.toUpperCase();

    String prefix = null;
    String digits = null;
    for (String p : new String[] {"QF", "SF", "P", "Q", "F"}) {
      if (upper.startsWith(p)) {
        prefix = p;
        digits = upper.substring(p.length());
        break;
      }
    }
    if (prefix == null || digits.isEmpty()) {
      return new ParsedMatchLabel(null, null, null, raw);
    }
    int number;
    try {
      number = Integer.parseInt(digits);
    } catch (NumberFormatException e) {
      return new ParsedMatchLabel(null, null, null, raw);
    }
    if (number <= 0) {
      return new ParsedMatchLabel(null, null, null, raw);
    }
    TournamentLevel level;
    String playoffRound = null;
    switch (prefix) {
      case "P" -> level = TournamentLevel.Practice;
      case "Q" -> level = TournamentLevel.Qualification;
      case "QF" -> {
        level = TournamentLevel.Playoff;
        playoffRound = "QF";
      }
      case "SF" -> {
        level = TournamentLevel.Playoff;
        playoffRound = "SF";
      }
      case "F" -> {
        level = TournamentLevel.Playoff;
        playoffRound = "F";
      }
      default -> {
        return new ParsedMatchLabel(null, null, null, raw);
      }
    }
    return new ParsedMatchLabel(level, number, playoffRound, raw);
  }
}
