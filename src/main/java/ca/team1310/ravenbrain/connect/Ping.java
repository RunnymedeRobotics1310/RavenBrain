/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.connect;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.rules.SecurityRule;


/**
 * @author Tony Field
 * @since 2025-03-25 08:02
 */
@Controller("/api")
@Secured(SecurityRule.IS_ANONYMOUS)
public class Ping {

    @Get("/ping")
    @Produces(MediaType.TEXT_PLAIN)
    public String ping() {
        return "pong";
    }

    @Post("/auth")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String auth(@Body UsernamePasswordCredentials credentials) {
        System.out.println("auth");
        return "hello " + credentials.getUsername();
    }
}
