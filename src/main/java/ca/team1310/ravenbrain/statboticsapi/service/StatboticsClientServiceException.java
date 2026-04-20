package ca.team1310.ravenbrain.statboticsapi.service;

/**
 * Thrown by {@link StatboticsClientService} on JSON parse failures or other service-layer errors.
 * Mirrors {@code TbaClientServiceException}.
 */
public class StatboticsClientServiceException extends RuntimeException {
  StatboticsClientServiceException(String message) {
    super(message);
  }

  StatboticsClientServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
