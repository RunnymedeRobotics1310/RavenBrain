package ca.team1310.ravenbrain.connect;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

public class UserNotFoundException extends HttpStatusException {

  public UserNotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, message);
  }
}
