package ca.team1310.ravenbrain.tbaapi.service;

/**
 * Thrown by {@link TbaClientService} on JSON parse failures or other service-layer errors.
 * Mirrors {@code FrcClientServiceException}.
 */
public class TbaClientServiceException extends RuntimeException {
  TbaClientServiceException(String message) {
    super(message);
  }

  TbaClientServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
