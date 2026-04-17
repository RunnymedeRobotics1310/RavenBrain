package ca.team1310.ravenbrain.telemetry;

import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import ca.team1310.ravenbrain.tournament.WatchedTournamentService;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Given a batch of telemetry entries for a session, extract match identity from the FMSInfo
 * NetworkTables keys the robot publishes and resolve the tournament. Pure enrichment logic — does
 * not write to the database. Callers decide whether to persist the result (first-write-wins at the
 * session level).
 */
@Singleton
@Slf4j
public class MatchIdentityEnricher {

  static final String FMS_MATCH_NUMBER_KEY = "/FMSInfo/MatchNumber";
  static final String FMS_EVENT_NAME_KEY = "/FMSInfo/EventName";

  private final TournamentService tournamentService;
  private final WatchedTournamentService watchedTournamentService;

  MatchIdentityEnricher(
      TournamentService tournamentService, WatchedTournamentService watchedTournamentService) {
    this.tournamentService = tournamentService;
    this.watchedTournamentService = watchedTournamentService;
  }

  public record Result(
      MatchLabelParser.ParsedMatchLabel parsedLabel,
      @Nullable String fmsEventName,
      @Nullable String tournamentId) {

    public boolean hasAny() {
      return (parsedLabel != null && parsedLabel.rawLabel() != null)
          || fmsEventName != null
          || tournamentId != null;
    }
  }

  /**
   * Scan entries for FMSInfo keys and resolve tournament identity.
   *
   * @param entries batch of entries; timestamp-ordered by caller ideally, but not required.
   * @param sessionStartedAt used for fallback tournament resolution by time window.
   * @return enrichment result; all fields may be null if no FMS data was seen.
   */
  public Result enrich(
      List<TelemetryApi.TelemetryEntryRequest> entries, Instant sessionStartedAt) {
    String earliestMatchLabel = null;
    Instant earliestMatchTs = null;
    String latestEventName = null;
    Instant latestEventTs = null;

    for (TelemetryApi.TelemetryEntryRequest e : entries) {
      String key = e.ntKey();
      if (key == null) {
        continue;
      }
      if (FMS_MATCH_NUMBER_KEY.equals(key)) {
        String value = e.ntValue();
        if (value == null || value.isBlank()) {
          continue;
        }
        if (earliestMatchTs == null || e.ts().isBefore(earliestMatchTs)) {
          earliestMatchLabel = value;
          earliestMatchTs = e.ts();
        }
      } else if (FMS_EVENT_NAME_KEY.equals(key)) {
        String value = e.ntValue();
        if (value == null || value.isBlank()) {
          continue;
        }
        if (latestEventTs == null || e.ts().isAfter(latestEventTs)) {
          latestEventName = value;
          latestEventTs = e.ts();
        }
      }
    }

    MatchLabelParser.ParsedMatchLabel parsed =
        earliestMatchLabel != null ? MatchLabelParser.parse(earliestMatchLabel) : null;
    String tournamentId = resolveTournamentId(latestEventName, sessionStartedAt);
    return new Result(parsed, latestEventName, tournamentId);
  }

  @Nullable
  private String resolveTournamentId(@Nullable String fmsEventName, Instant sessionStartedAt) {
    if (fmsEventName != null && !fmsEventName.isBlank()) {
      int season = sessionStartedAt.atZone(ZoneOffset.UTC).getYear();
      Optional<TournamentRecord> byCode =
          tournamentService.findByCodeAndSeason(fmsEventName.trim(), season);
      if (byCode.isPresent()) {
        return byCode.get().id();
      }
    }
    List<TournamentRecord> covering = tournamentService.findCoveringInstant(sessionStartedAt);
    List<TournamentRecord> watched =
        covering.stream().filter(t -> watchedTournamentService.isWatched(t.id())).toList();
    if (watched.size() == 1) {
      return watched.get(0).id();
    }
    if (watched.isEmpty() && covering.size() == 1) {
      return covering.get(0).id();
    }
    return null;
  }
}
