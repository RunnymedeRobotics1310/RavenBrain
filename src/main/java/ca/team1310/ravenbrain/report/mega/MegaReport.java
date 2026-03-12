package ca.team1310.ravenbrain.report.mega;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record MegaReport(
    List<MegaReportColumn> columns,
    List<MegaReportRow> rows) {}
