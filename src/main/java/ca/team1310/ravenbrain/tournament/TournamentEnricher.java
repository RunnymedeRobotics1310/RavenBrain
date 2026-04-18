package ca.team1310.ravenbrain.tournament;

import ca.team1310.ravenbrain.tbaapi.service.TbaEventRecord;
import ca.team1310.ravenbrain.tbaapi.service.TbaEventRepo;
import ca.team1310.ravenbrain.tbaapi.service.WebcastUrlReconstructor;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Projects {@link TournamentRecord} instances into the enriched {@link TournamentResponse} shape
 * by joining in {@code RB_TBA_EVENT} data. One batched {@link TbaEventRepo#findByEventKeyIn} per
 * enrichment keeps this O(1) queries regardless of tournament count.
 *
 * <p>The merge rule is:
 *
 * <pre>
 *   webcasts = distinct(canonicalize(manual_webcasts) ∪ canonicalize(tba_webcasts_json))
 * </pre>
 *
 * Manual URLs come first so the admin-intentional order is preserved. The public invariant that
 * {@code webcastsFromTba ⊆ webcasts} is enforced by canonicalizing both inputs before the union.
 */
@Singleton
public class TournamentEnricher {

  private static final List<String> EMPTY = List.of();

  private final TbaEventRepo tbaEventRepo;
  private final Duration staleThreshold;

  TournamentEnricher(
      TbaEventRepo tbaEventRepo,
      @Property(name = "raven-eye.tba-api.stale-threshold-minutes",
          defaultValue = "90") long staleThresholdMinutes) {
    this.tbaEventRepo = tbaEventRepo;
    this.staleThreshold = Duration.ofMinutes(staleThresholdMinutes);
  }

  /** Enrich a single tournament. */
  public TournamentResponse enrich(TournamentRecord record) {
    if (record == null) return null;
    Optional<TbaEventRecord> tba =
        record.tbaEventKey() == null ? Optional.empty() : tbaEventRepo.findById(record.tbaEventKey());
    return build(record, tba.orElse(null), Instant.now());
  }

  /** Batch-enrich a list of tournaments with a single RB_TBA_EVENT lookup. */
  public List<TournamentResponse> enrich(List<TournamentRecord> records) {
    if (records == null || records.isEmpty()) return List.of();
    List<String> keys =
        records.stream()
            .map(TournamentRecord::tbaEventKey)
            .filter(k -> k != null && !k.isBlank())
            .distinct()
            .toList();
    Map<String, TbaEventRecord> byKey =
        keys.isEmpty()
            ? Map.of()
            : tbaEventRepo.findByEventKeyIn(keys).stream()
                .collect(Collectors.toMap(TbaEventRecord::eventKey, Function.identity()));
    Instant now = Instant.now();
    List<TournamentResponse> out = new ArrayList<>(records.size());
    for (TournamentRecord t : records) {
      TbaEventRecord tba = t.tbaEventKey() == null ? null : byKey.get(t.tbaEventKey());
      out.add(build(t, tba, now));
    }
    return out;
  }

  private TournamentResponse build(TournamentRecord t, TbaEventRecord tba, Instant now) {
    List<String> manualCanon = canonicalizeAll(parseWebcasts(t.manualWebcasts()));
    List<String> tbaCanon = canonicalizeAll(parseWebcasts(tba == null ? null : tba.webcastsJson()));
    List<String> merged = union(manualCanon, tbaCanon);

    Instant lastSync = null;
    boolean stale = false;
    if (t.tbaEventKey() != null && !t.tbaEventKey().isBlank()) {
      if (tba == null) {
        stale = true; // key is set but no sync has ever written a row
      } else {
        lastSync = tba.lastStatus() != null && tba.lastStatus() == 200 ? tba.lastSync() : null;
        boolean hasSuccessfulSync = lastSync != null;
        boolean staleByAge =
            hasSuccessfulSync && lastSync.isBefore(now.minus(staleThreshold));
        boolean staleByStatus = tba.lastStatus() == null || tba.lastStatus() != 200;
        stale = staleByAge || staleByStatus;
      }
    }

    return new TournamentResponse(
        t.id(),
        t.code(),
        t.season(),
        t.name(),
        t.startTime(),
        t.endTime(),
        t.weekNumber(),
        t.tbaEventKey(),
        merged,
        tbaCanon,
        lastSync,
        stale);
  }

  private static List<String> parseWebcasts(String json) {
    if (json == null || json.isBlank()) return EMPTY;
    String raw = json.trim();
    if (!raw.startsWith("[")) return EMPTY;
    if (raw.length() < 2) return EMPTY;
    String inner = raw.substring(1, raw.length() - 1);
    if (inner.isBlank()) return EMPTY;
    List<String> out = new ArrayList<>();
    for (String part : inner.split(",")) {
      String cleaned = part.trim();
      if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
        cleaned = cleaned.substring(1, cleaned.length() - 1);
      }
      if (!cleaned.isEmpty()) {
        out.add(cleaned);
      }
    }
    return out;
  }

  private static List<String> canonicalizeAll(List<String> urls) {
    if (urls.isEmpty()) return EMPTY;
    List<String> out = new ArrayList<>(urls.size());
    for (String u : urls) {
      String c = WebcastUrlReconstructor.canonicalize(u);
      if (c != null && !c.isBlank()) out.add(c);
    }
    return out;
  }

  private static List<String> union(List<String> first, List<String> second) {
    LinkedHashSet<String> seen = new LinkedHashSet<>(first);
    seen.addAll(second);
    return new ArrayList<>(seen);
  }
}
