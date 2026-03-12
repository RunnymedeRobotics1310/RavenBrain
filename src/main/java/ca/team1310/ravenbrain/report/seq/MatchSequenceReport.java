package ca.team1310.ravenbrain.report.seq;

import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record MatchSequenceReport(int matchId, TournamentLevel level, SequenceReport report) {}
