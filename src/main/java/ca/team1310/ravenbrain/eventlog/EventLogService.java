package ca.team1310.ravenbrain.eventlog;

import ca.team1310.ravenbrain.eventtype.EventTypeRepository;
import ca.team1310.ravenbrain.frcapi.model.TournamentLevel;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-23 22:59
 */
@Slf4j
@Singleton
public class EventLogService {

  private final EventLogRepository eventLogRepository;
  private final EventTypeRepository eventTypeRepository;

  public EventLogService(
      EventLogRepository eventLogRepository, EventTypeRepository eventTypeRepository) {
    this.eventLogRepository = eventLogRepository;
    this.eventTypeRepository = eventTypeRepository;
  }

  public List<EventLogRecord> findAll() {
    return eventLogRepository.findAll();
  }

  public void delete(EventLogRecord record) {
    eventLogRepository.delete(record);
  }

  public List<EventLogRecord> listEventsForTeamAndTournament(
      String tournamentId, int teamNumber, boolean includePractice) {
    List<EventLogRecord> events =
        eventLogRepository.findAllByTeamNumberAndTournamentIdOrderByTimestampAsc(
            teamNumber, tournamentId);
    if (includePractice) {
      return events;
    } else {
      return events.stream().filter(record -> record.level() != TournamentLevel.Practice).toList();
    }
  }

  public EventLogRecord save(EventLogRecord record) {
    if (!eventTypeRepository.existsById(record.eventType())) {
      throw new IllegalArgumentException("Invalid event type: " + record.eventType());
    }
    return eventLogRepository.save(record);
  }
}
