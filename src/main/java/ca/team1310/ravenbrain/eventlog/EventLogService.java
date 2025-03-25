/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.eventlog;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Tony Field
 * @since 2025-03-23 22:59
 */
@Slf4j
public class EventLogService {
  void addEventLogRecord(EventLogRecord eventLogRecord) {
    log.info("Saving event log record: {}", eventLogRecord);
  }
}
