package ca.team1310.ravenbrain.tbaapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/**
 * Subset of the TBA Match response consumed by the match-video sync. TBA returns many more fields
 * (score breakdowns, times, post_result_time, etc.); they are ignored so adding new TBA fields
 * later does not break deserialization.
 *
 * <p>Match identity for the RB read-time join is the 6-team alliance composition, not {@code key}
 * or {@code match_number}. {@code compLevel} values observed from TBA: {@code qm} (qualification),
 * {@code ef} (eighthfinal — rare), {@code qf} (quarterfinal), {@code sf} (semifinal), {@code f}
 * (final). {@code setNumber} defaults to 1 for {@code qm}.
 */
@Serdeable
public record TbaMatch(
    String key,
    @JsonProperty("comp_level") String compLevel,
    @JsonProperty("set_number") int setNumber,
    @JsonProperty("match_number") int matchNumber,
    @Nullable TbaMatchAlliances alliances,
    @Nullable List<TbaMatchVideo> videos) {

  /**
   * Nested alliances holder — modelled as its own record so the {@code red}/{@code blue} nesting
   * deserializes directly rather than via a generic {@code Map}.
   */
  @Serdeable
  public record TbaMatchAlliances(
      @Nullable TbaMatchAlliance red, @Nullable TbaMatchAlliance blue) {}
}
