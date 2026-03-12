package ca.team1310.ravenbrain.report.seq;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record TournamentSequenceReport(
    SequenceReport aggregate, List<MatchSequenceReport> matches) {}
