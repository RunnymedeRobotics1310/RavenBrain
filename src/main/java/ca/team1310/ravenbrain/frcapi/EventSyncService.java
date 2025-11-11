package ca.team1310.ravenbrain.frcapi;

import ca.team1310.ravenbrain.frcapi.fetch.FrcClientService;
import ca.team1310.ravenbrain.frcapi.model.Event;
import ca.team1310.ravenbrain.frcapi.model.EventResponse;
import ca.team1310.ravenbrain.tournament.TournamentRecord;
import ca.team1310.ravenbrain.tournament.TournamentService;
import io.micronaut.context.annotation.Property;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads all of the events for the current season and the last season.
 *
 * <p>NOTE, "Tournament" in this app maps to "EVENT" in the FRC API.</p>
 *
 * <p>TODO: FINISH IMPLEMENTATION
 *
 * @author Tony Field
 * @since 2025-09-21 23:22
 */
@Slf4j
@Singleton
class EventSyncService {
  private final FrcClientService frcClientService;
  private final TournamentService tournamentService;
  private final int teamNumber;

  EventSyncService(
      FrcClientService frcClientService,
      TournamentService tournamentService,
      @Property(name = "raven-eye.team") int teamNumber) {
    this.frcClientService = frcClientService;
    this.tournamentService = tournamentService;
    this.teamNumber = teamNumber;
  }

  /**
   * Once a week, check with FIRST for new tournament data and save relevant parts of it to our
   * local repository.
   */
  // [sec] min hr dom mon dow
  @Scheduled(cron = "0 22 * * 1")
  void loadTournaments() {
    int thisYear = Year.now(ZoneOffset.UTC).getValue();

    loadTournamentsForYear(thisYear);
    loadTournamentsForYear(--thisYear);
    loadTournamentsForYear(--thisYear);
  }

  private void loadTournamentsForYear(int year) {
    EventResponse resp = frcClientService.getEventListingsForTeam(year, teamNumber);
    if (resp != null) {
      for (Event event : resp.getEvents()) {
        Instant start = event.getDateStart().atZone(ZoneId.of("America/New_York")).toInstant();
        Instant end = event.getDateEnd().atZone(ZoneId.of("America/New_York")).toInstant();
        TournamentRecord tournamentRecord = new TournamentRecord();
        tournamentRecord.setId("" + year + event.getCode());
        tournamentRecord.setSeason(year);
        tournamentRecord.setCode(event.getCode());
        tournamentRecord.setName(event.getName());
        tournamentRecord.setStartTime(start);
        tournamentRecord.setEndTime(end);
        log.trace("Saving tournament {}", tournamentRecord);
        try {
          tournamentService.save(tournamentRecord);
        } catch (DataAccessException e) {
          if (e.getCause() != null
              && e.getCause() instanceof SQLIntegrityConstraintViolationException) {
            tournamentService.update(tournamentRecord);
          } else {
            throw e;
          }
        }
      }
    }
  }
}
