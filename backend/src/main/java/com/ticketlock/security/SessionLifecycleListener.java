package com.ticketlock.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.stereotype.Component;

/** Observes Redis session events without logging credentials or session IDs. */
@Component
public class SessionLifecycleListener {
    private static final Logger log = LoggerFactory.getLogger(SessionLifecycleListener.class);

    @EventListener
    public void sessionCreated(SessionCreatedEvent event) {
        log.info("Redis session created");
    }

    @EventListener
    public void sessionDeleted(SessionDeletedEvent event) {
        log.info("Redis session deleted");
    }

    @EventListener
    public void sessionExpired(SessionExpiredEvent event) {
        log.info("Redis session expired");
    }
}
