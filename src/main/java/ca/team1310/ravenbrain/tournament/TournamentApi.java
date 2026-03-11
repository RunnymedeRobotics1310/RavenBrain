package ca.team1310.ravenbrain.tournament;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import ca.team1310.ravenbrain.schedule.ScheduleRecord;
import ca.team1310.ravenbrain.schedule.ScheduleService;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-26 07:17
 */
@Controller("/api/tournament")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Slf4j
public class TournamentApi {
  private final TournamentService tournamentService;
  private final ScheduleService scheduleService;
  private final int teamNumber;

  public TournamentApi(
      TournamentService tournamentService,
      ScheduleService scheduleService,
      @Property(name = "raven-eye.team") int teamNumber) {
    this.tournamentService = tournamentService;
    this.scheduleService = scheduleService;
    this.teamNumber = teamNumber;
  }

  @Introspected
  @Serdeable
  record TournamentDTO(
      String id,
      int season,
      String code,
      String name,
      LocalDateTime startTime,
      LocalDateTime endTime) {}

  @Post
  @Consumes(APPLICATION_JSON)
  public void createTournament(@Body TournamentDTO tournamentRecord) {
    log.debug("Saving tournament record: {}", tournamentRecord);
    var t =
        new TournamentRecord(
            tournamentRecord.id(),
            tournamentRecord.code(),
            tournamentRecord.season(),
            tournamentRecord.name(),
            tournamentRecord.startTime().toInstant(ZoneOffset.UTC),
            tournamentRecord.endTime().toInstant(ZoneOffset.UTC));
    tournamentService.save(t);
  }

  @Get
  @Produces(APPLICATION_JSON)
  public List<TournamentRecord> getTournaments() {
    return tournamentService.findAllSortByStartTime();
  }

  @Get("/active-team")
  @Produces(APPLICATION_JSON)
  public List<TournamentRecord> getActiveTeamTournaments() {
    return tournamentService.findActiveTournaments().stream()
        .filter(t -> hasTeamInSchedule(t.id()))
        .toList();
  }

  private boolean hasTeamInSchedule(String tournamentId) {
    for (ScheduleRecord s :
        scheduleService.findAllByTournamentIdOrderByMatch(tournamentId)) {
      if (s.red1() == teamNumber
          || s.red2() == teamNumber
          || s.red3() == teamNumber
          || s.blue1() == teamNumber
          || s.blue2() == teamNumber
          || s.blue3() == teamNumber) {
        return true;
      }
    }
    return false;
  }
}
