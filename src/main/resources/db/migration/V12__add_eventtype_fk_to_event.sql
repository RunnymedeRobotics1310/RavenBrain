ALTER TABLE RB_EVENT
    ADD CONSTRAINT fk_event_eventtype
        FOREIGN KEY (eventtype) REFERENCES RB_EVENTTYPE (eventtype);
