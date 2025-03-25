/*
 * Copyright 2025 The Kingsway Digital Company Limited. All rights reserved.
 */
package ca.team1310.ravenbrain.eventlog;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Put;

/**
 * @author Tony Field
 * @since 2025-03-24 20:00
 */
@Controller("/api/event")
public class EventApi {
    private final EventLogService eventLogService;

    public EventApi(EventLogService eventLogService) {
        this.eventLogService = eventLogService;
    }

    @Put
    public void putEventLogRecord(EventLogRecord eventLogRecord) {
        eventLogService.addEventLogRecord(eventLogRecord);
    }
}
