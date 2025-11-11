package ca.team1310.ravenbrain.frcapi;

import ca.team1310.ravenbrain.frcapi.fetch.FrcClientService;
import ca.team1310.ravenbrain.frcapi.model.EventResponse;
import ca.team1310.ravenbrain.tournament.TournamentService;
import io.micronaut.scheduling.annotation.Scheduled;
import java.time.Year;
import java.time.ZoneOffset;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads all of the events for the current season and the last season.
 *
 * <p>TODO: "Tournament" in the API maps to "Event" at FRC. TODO: FINISH IMPLEMENTATION
 *
 * @author Tony Field
 * @since 2025-09-21 23:22
 */
@Slf4j
class EventSyncService {
  private final FrcClientService frcClientService;
  private final TournamentService processedService;

  EventSyncService(FrcClientService frcClientService, TournamentService processedService) {
    this.frcClientService = frcClientService;
    this.processedService = processedService;
  }

  // This is a scheduled sync that happens in the background, only weekly. As a team is added to an
  // event, the event data will appear
  // [sec] min hr dom mon dow
  @Scheduled(cron = "0 23 * * 1")
  void loadEvents() {
    int thisYear = Year.now(ZoneOffset.UTC).getValue();
    int lastYear = thisYear--;

    loadEvents(thisYear);
    loadEvents(lastYear);
    // todo: work in progress - this is not done

    // todo: intelligently update tournament repository with new events if any
  }

  private void loadEvents(int year) {
    EventResponse resp = frcClientService.getEventListingsForTeam(year, 1310);
    log.info("Loading events for year {}: {}", year, resp);
    // todo: work in progress - this is not done
  }
}
