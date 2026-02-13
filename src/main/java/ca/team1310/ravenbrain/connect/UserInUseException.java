package ca.team1310.ravenbrain.connect;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

public class UserInUseException extends HttpStatusException {

  public UserInUseException(String message) {
    super(HttpStatus.CONFLICT, message);
  }
}
