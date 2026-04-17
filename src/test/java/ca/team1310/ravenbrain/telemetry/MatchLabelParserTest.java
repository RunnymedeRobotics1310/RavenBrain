package ca.team1310.ravenbrain.telemetry;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import org.junit.jupiter.api.Test;

class MatchLabelParserTest {

  @Test
  void parsesPracticeMatch() {
    MatchLabelParser.ParsedMatchLabel p = MatchLabelParser.parse("P7");
    assertEquals(TournamentLevel.Practice, p.level());
    assertEquals(7, p.number());
    assertNull(p.playoffRound());
    assertEquals("P7", p.rawLabel());
    assertTrue(p.isStructured());
  }

  @Test
  void parsesQualificationMatch() {
    MatchLabelParser.ParsedMatchLabel p = MatchLabelParser.parse("Q14");
    assertEquals(TournamentLevel.Qualification, p.level());
    assertEquals(14, p.number());
    assertNull(p.playoffRound());
    assertEquals("Q14", p.rawLabel());
  }

  @Test
  void parsesSemifinalMatch() {
    MatchLabelParser.ParsedMatchLabel p = MatchLabelParser.parse("SF3");
    assertEquals(TournamentLevel.Playoff, p.level());
    assertEquals(3, p.number());
    assertEquals("SF", p.playoffRound());
    assertEquals("SF3", p.rawLabel());
  }

  @Test
  void parsesQuarterfinalMatch() {
    MatchLabelParser.ParsedMatchLabel p = MatchLabelParser.parse("QF2");
    assertEquals(TournamentLevel.Playoff, p.level());
    assertEquals(2, p.number());
    assertEquals("QF", p.playoffRound());
  }

  @Test
  void parsesFinalMatch() {
    MatchLabelParser.ParsedMatchLabel p = MatchLabelParser.parse("F1");
    assertEquals(TournamentLevel.Playoff, p.level());
    assertEquals(1, p.number());
    assertEquals("F", p.playoffRound());
  }

  @Test
  void parsesWithWhitespace() {
    MatchLabelParser.ParsedMatchLabel p = MatchLabelParser.parse(" q 14 ");
    assertEquals(TournamentLevel.Qualification, p.level());
    assertEquals(14, p.number());
    assertEquals("q14", p.rawLabel());
  }

  @Test
  void handlesEmptyString() {
    MatchLabelParser.ParsedMatchLabel p = MatchLabelParser.parse("");
    assertNull(p.level());
    assertNull(p.number());
    assertNull(p.playoffRound());
    assertNull(p.rawLabel());
    assertFalse(p.isStructured());
  }

  @Test
  void handlesNull() {
    MatchLabelParser.ParsedMatchLabel p = MatchLabelParser.parse(null);
    assertNull(p.level());
    assertNull(p.number());
    assertNull(p.rawLabel());
  }

  @Test
  void handlesZeroMatchNumber() {
    MatchLabelParser.ParsedMatchLabel p = MatchLabelParser.parse("Q0");
    assertNull(p.level());
    assertNull(p.number());
    assertEquals("Q0", p.rawLabel());
  }

  @Test
  void handlesUnknownPrefix() {
    MatchLabelParser.ParsedMatchLabel p = MatchLabelParser.parse("X99");
    assertNull(p.level());
    assertNull(p.number());
    assertEquals("X99", p.rawLabel());
  }

  @Test
  void handlesPrefixWithoutNumber() {
    MatchLabelParser.ParsedMatchLabel p = MatchLabelParser.parse("Q");
    assertNull(p.level());
    assertNull(p.number());
    assertEquals("Q", p.rawLabel());
  }

  @Test
  void handlesLargeMatchNumber() {
    MatchLabelParser.ParsedMatchLabel p = MatchLabelParser.parse("Q999");
    assertEquals(TournamentLevel.Qualification, p.level());
    assertEquals(999, p.number());
  }

  @Test
  void handlesNonNumericSuffix() {
    MatchLabelParser.ParsedMatchLabel p = MatchLabelParser.parse("Q14a");
    assertNull(p.level());
    assertNull(p.number());
    assertEquals("Q14a", p.rawLabel());
  }
}
