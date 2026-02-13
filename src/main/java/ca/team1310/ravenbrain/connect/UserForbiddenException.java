package ca.team1310.ravenbrain.connect;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

public class UserForbiddenException extends HttpStatusException {

  public UserForbiddenException(String message) {
    super(HttpStatus.FORBIDDEN, message);
  }
}
