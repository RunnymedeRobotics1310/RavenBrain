package ca.team1310.ravenbrain.carson;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

/**
 * @author Tony Field
 * @since 2026-02-12 06:26
 */
@Controller("/api/carson") // Tells Micronaut that this is an API endpoint with prefix /api/carson
@Secured( // Tells Micronaut that all endpoints here require the user to be signed in
    SecurityRule.IS_AUTHENTICATED)
public class CarsonAPI {

  @Get("/hello") // specifies that this is for http GET requests and answers to /api/carson/hello
  @Produces(MediaType.TEXT_PLAIN) // Specifies that the response will be plain text
  @Secured( // Allows anonymous access to this endpoint, overriding line 15
      SecurityRule.IS_ANONYMOUS)
  public String hello() {
    return "Hello Carson!"; // returns a string which matches line 20
  }

  @Get("/secure")
  @Produces(MediaType.TEXT_PLAIN)
  public String helloSecure() {
    // Note we are not overriding @Secured, so the one on the class takes precedence (i.e. secure)
    return "Hello Logged In Carson!";
  }
}
