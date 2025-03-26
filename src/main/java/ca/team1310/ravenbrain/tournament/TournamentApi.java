/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.tournament;

import io.micronaut.http.annotation.*;

import java.util.List;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

/**
 * @author Tony Field
 * @since 2025-03-26 07:17
 */
@Controller("/api/tournament")
public class TournamentApi {
    private final TournamentService tournamentService;

    public TournamentApi(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
    }

    @Put
    @Consumes(APPLICATION_JSON)
    public void createTournament(TournamentRecord tournamentRecord) {
        tournamentService.addTournament(tournamentRecord);
    }

    @Get
    @Produces(APPLICATION_JSON)
    public List<TournamentRecord> getTournaments() {
        return tournamentService.listTournaments();
    }

    @Post
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public void updateTournament(TournamentRecord tournamentRecord) {
        tournamentService.updateTournament(tournamentRecord);
    }

}
