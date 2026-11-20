package com.ticketlock.security;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Logs servlet session lifecycle events for the session learning lab. */
@Component
@Profile("!redis")
public class SessionLifecycleListener implements HttpSessionListener {
    private static final Logger log = LoggerFactory.getLogger(SessionLifecycleListener.class);

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        log.info("HTTP session created");
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        log.info("HTTP session destroyed");
    }
}
