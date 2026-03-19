package ca.team1310.ravenbrain.report.mega;

import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;

@Serdeable
public record MegaReportRow(
    int matchId,
    TournamentLevel level,
    Map<String, Double> values,
    Map<String, Integer> counts) {}
