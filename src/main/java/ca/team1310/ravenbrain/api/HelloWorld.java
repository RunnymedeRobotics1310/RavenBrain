/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.api;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;

/**
 * @author Tony Field
 * @since 2025-03-22 23:35
 */
@Controller("/api")
@Secured("isAnonymous()")
public class HelloWorld {

  @Get("/hello")
  public String index() {
    return "Hello World";
  }
}
