package ca.team1310.ravenbrain.tbaapi.fetch;

/**
 * Thrown by the TBA fetch layer on HTTP failures, unexpected status codes, or network errors.
 * Mirrors FrcClientException.
 */
public class TbaClientException extends RuntimeException {
  public TbaClientException() {}

  public TbaClientException(Throwable cause) {
    super(cause);
  }

  public TbaClientException(String message) {
    super(message);
  }

  public TbaClientException(String message, Throwable cause) {
    super(message, cause);
  }

  public TbaClientException(TbaRawResponse res) {
    super("Error returned: " + res.statuscode() + " for " + res.url() + ": " + res.body());
  }
}
