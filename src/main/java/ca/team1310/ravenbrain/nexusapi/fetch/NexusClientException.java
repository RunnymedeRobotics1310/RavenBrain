package ca.team1310.ravenbrain.nexusapi.fetch;

public class NexusClientException extends RuntimeException {
  NexusClientException() {}

  NexusClientException(Throwable cause) {
    super(cause);
  }

  NexusClientException(String message) {
    super(message);
  }

  NexusClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
