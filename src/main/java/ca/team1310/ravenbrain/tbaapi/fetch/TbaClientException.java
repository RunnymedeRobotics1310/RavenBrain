package ca.team1310.ravenbrain.tbaapi.fetch;

/**
 * Thrown by the TBA fetch layer on HTTP failures, unexpected status codes, or network errors.
 * Mirrors FrcClientException.
 */
public class TbaClientException extends RuntimeException {
  TbaClientException() {}

  TbaClientException(Throwable cause) {
    super(cause);
  }

  TbaClientException(String message) {
    super(message);
  }

  TbaClientException(String message, Throwable cause) {
    super(message, cause);
  }

  TbaClientException(TbaRawResponse res) {
    super("Error returned: " + res.statuscode() + " for " + res.url() + ": " + res.body());
  }
}
