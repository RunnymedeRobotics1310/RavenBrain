package ca.team1310.ravenbrain.statboticsapi.fetch;

/**
 * Thrown by the Statbotics fetch layer on HTTP failures, unexpected status codes, or network
 * errors. Mirrors {@link ca.team1310.ravenbrain.tbaapi.fetch.TbaClientException} but narrows to the
 * TTL-only caching shape (no conditional-request branches).
 */
public class StatboticsClientException extends RuntimeException {
  public StatboticsClientException() {}

  public StatboticsClientException(Throwable cause) {
    super(cause);
  }

  public StatboticsClientException(String message) {
    super(message);
  }

  public StatboticsClientException(String message, Throwable cause) {
    super(message, cause);
  }

  public StatboticsClientException(StatboticsRawResponse res) {
    super("Error returned: " + res.statuscode() + " for " + res.url() + ": " + res.body());
  }
}
