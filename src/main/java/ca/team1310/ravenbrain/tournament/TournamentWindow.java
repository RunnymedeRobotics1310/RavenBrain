package ca.team1310.ravenbrain.tournament;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import java.util.List;

/**
 * Config-aware facade over {@link TournamentService}'s parameterized tournament-window queries.
 * Every caller that needs "is this tournament currently active?" or "which tournaments are
 * upcoming/active?" routes through here so the lead/tail values come from one place
 * ({@code raven-eye.sync.tournament-window-lead-hours} and
 * {@code raven-eye.sync.tournament-window-tail-hours}).
 *
 * <p>Replaces the previous hardcoded {@code INTERVAL 24 HOUR} / {@code INTERVAL 4 HOUR} literals
 * in {@code TournamentService}.
 */
@Singleton
public class TournamentWindow {

  private final TournamentService tournamentService;
  private final int leadHours;
  private final int tailHours;

  TournamentWindow(
      TournamentService tournamentService,
      @Property(name = "raven-eye.sync.tournament-window-lead-hours", defaultValue = "12")
          int leadHours,
      @Property(name = "raven-eye.sync.tournament-window-tail-hours", defaultValue = "10")
          int tailHours) {
    this.tournamentService = tournamentService;
    this.leadHours = leadHours;
    this.tailHours = tailHours;
  }

  /** Tournaments that have started and ended no more than {@code tailHours} ago. */
  public List<TournamentRecord> findActive() {
    return tournamentService.findActiveTournaments(tailHours);
  }

  /**
   * Tournaments starting within {@code leadHours} or ended within {@code tailHours}. This is the
   * canonical "in the tournament window" signal; most schedulers key their behavior off it.
   */
  public List<TournamentRecord> findUpcomingAndActive() {
    return tournamentService.findUpcomingAndActiveTournaments(leadHours, tailHours);
  }

  /** Configured lead-hours; exposed for enrichment (Unit 4) to compute {@code activeFrom}. */
  public int getLeadHours() {
    return leadHours;
  }

  /** Configured tail-hours; exposed for enrichment (Unit 4) to compute {@code activeUntil}. */
  public int getTailHours() {
    return tailHours;
  }
}
