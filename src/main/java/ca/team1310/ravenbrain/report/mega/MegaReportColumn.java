package ca.team1310.ravenbrain.report.mega;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record MegaReportColumn(
    String eventtype,
    String name,
    boolean isQuantity) {}
