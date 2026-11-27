package com.ticketlock.event;

import com.ticketlock.event.entity.Event;
import com.ticketlock.event.entity.Show;
import com.ticketlock.event.entity.ShowStatus;
import com.ticketlock.event.repository.EventRepository;
import com.ticketlock.event.repository.ShowRepository;
import com.ticketlock.event.service.EventService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class EventSchemaTests {
    @Autowired EventRepository events;
    @Autowired ShowRepository shows;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired EventService eventService;

    @Test void createsEventAndShowInPostgresWithUtcInstant() {
        Instant now = Instant.parse("2027-01-01T00:00:00Z");
        Event event = events.saveAndFlush(new Event("Đêm nhạc", "Demo", now));
        Show show = shows.saveAndFlush(new Show(event.getId(), now.plusSeconds(3600), "Nhà hát", now));
        UUID id = show.getId();
        assertEquals(7, id.version());
        entityManager.clear();
        Show loaded = shows.findById(id).orElseThrow();
        assertEquals(event.getId(), loaded.getEventId());
        assertEquals(now.plusSeconds(3600), loaded.getStartsAt());
        assertEquals(ShowStatus.DRAFT, loaded.getStatus());
        assertFalse(loaded.isLayoutLocked());
    }
    @Test void configuredLayoutPolicyRefusesPublishingUntilImplemented() {
        Instant now = Instant.now();
        Event event = events.saveAndFlush(new Event("Event", "Demo", now));
        Show show = shows.saveAndFlush(new Show(event.getId(), now.plusSeconds(3600), "Venue", now));
        assertThrows(IllegalStateException.class, () -> eventService.publishShow(show.getId()));
        assertEquals(ShowStatus.DRAFT, show.getStatus());
        assertFalse(show.isLayoutLocked());
    }

    @Test void databaseRejectsShowWithoutEvent() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO shows (id,event_id,starts_at,venue,status,created_at) VALUES (?,?,?,?,'DRAFT',?)",
                UUID.randomUUID(), UUID.randomUUID(), java.sql.Timestamp.from(Instant.parse("2027-01-01T01:00:00Z")),
                "Demo", java.sql.Timestamp.from(Instant.parse("2027-01-01T00:00:00Z"))));
    }

    @Test void databaseRejectsPublishedShowWithoutLockTimestamp() {
        Event event = events.saveAndFlush(new Event("Event", "Demo", Instant.parse("2027-01-01T00:00:00Z")));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO shows (id,event_id,starts_at,venue,status,created_at) VALUES (?,?,?,?,'PUBLISHED',?)",
                UUID.randomUUID(), event.getId(), java.sql.Timestamp.from(Instant.parse("2027-01-01T01:00:00Z")),
                "Demo", java.sql.Timestamp.from(Instant.parse("2027-01-01T00:00:00Z"))));
    }
}
