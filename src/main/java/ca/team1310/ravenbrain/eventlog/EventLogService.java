/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.eventlog;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-23 22:59
 */
@Slf4j
@Singleton
public class EventLogService {
  void addEventLogRecord(EventLogRecord eventLogRecord) {
    log.info("Saving event log record: {}", eventLogRecord);
    if (eventLogRecord.getTimestamp() == null) {
      throw new IllegalArgumentException("Event log record has no timestamp");
    }
    // todo: throw on fail
  }
}
