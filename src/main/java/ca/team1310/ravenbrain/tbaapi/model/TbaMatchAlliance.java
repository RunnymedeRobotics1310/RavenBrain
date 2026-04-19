package ca.team1310.ravenbrain.tbaapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/**
 * One side of a TBA match alliance. Team keys are in TBA's {@code "frc1310"} format — the sync
 * service strips the {@code "frc"} prefix and parses to integers to align with RB_SCHEDULE's
 * red1-4 / blue1-4 team-number columns.
 *
 * <p>Only {@code team_keys} is consumed by P1. TBA also returns {@code score}, {@code surrogate
 * _team_keys}, {@code dq_team_keys}, and {@code captains} — those are ignored.
 */
@Serdeable
public record TbaMatchAlliance(@JsonProperty("team_keys") List<String> teamKeys) {}
