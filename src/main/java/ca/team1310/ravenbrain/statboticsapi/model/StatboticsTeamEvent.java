package ca.team1310.ravenbrain.statboticsapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * One element of the Statbotics {@code /v3/team_events?event={key}} response array. Only the
 * fields RavenBrain consumes are modelled — everything else (year / country / district /
 * record.wins.losses / etc.) is ignored so adding Statbotics fields later does not break
 * deserialization.
 *
 * <p>{@code team} is the FRC team number as a numeric JSON value; {@code event} is the TBA event
 * key. {@link #epa} nests to {@link StatboticsTeamEventEpa} which nests to
 * {@link StatboticsTeamEventBreakdown}.
 */
@Serdeable
@JsonIgnoreProperties(ignoreUnknown = true)
public record StatboticsTeamEvent(
    @Nullable @JsonProperty("team") Integer team,
    @Nullable @JsonProperty("event") String event,
    @Nullable StatboticsTeamEventEpa epa) {}
