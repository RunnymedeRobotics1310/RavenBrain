/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.frcapi.fetch;

/**
 * @author Tony Field
 * @since 2025-09-21 13:47
 */
public class FrcClientException extends RuntimeException {
  FrcClientException() {}

  FrcClientException(Throwable cause) {
    super(cause);
  }

  FrcClientException(String message) {
    super(message);
  }

  FrcClientException(String message, Throwable cause) {
    super(message, cause);
  }

  FrcClientException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  FrcClientException(FrcRawResponse res) {
    super("Error returned: " + res.statuscode + " for " + res.url + ": " + res.body);
  }
}
