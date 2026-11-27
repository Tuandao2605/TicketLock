package com.ticketlock.event.service;

import com.ticketlock.event.api.ShowSeatLayout;
import com.ticketlock.event.dto.EventResponse;
import com.ticketlock.event.dto.ShowResponse;
import com.ticketlock.event.entity.Event;
import com.ticketlock.event.entity.Show;
import com.ticketlock.event.repository.EventRepository;
import com.ticketlock.event.repository.ShowRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
    private final EventRepository events;
    private final ShowRepository shows;
    private final ShowSeatLayout layouts;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public EventService(EventRepository events, ShowRepository shows, ShowSeatLayout layouts) {
        this(events, shows, layouts, Clock.systemUTC());
    }

    EventService(EventRepository events, ShowRepository shows, ShowSeatLayout layouts, Clock clock) {
        this.events = events;
        this.shows = shows;
        this.layouts = layouts;
        this.clock = clock;
    }

    @Transactional
    public EventResponse createEvent(String name, String description) {
        return EventResponse.from(events.save(new Event(name, description, clock.instant())));
    }

    @Transactional
    public ShowResponse createShow(UUID eventId, Instant startsAt, String venue) {
        if (eventId == null || !events.existsById(eventId)) throw new NoSuchElementException("Không tìm thấy sự kiện");
        return ShowResponse.from(shows.save(new Show(eventId, startsAt, venue, clock.instant())));
    }

    @Transactional
    public ShowResponse publishShow(UUID showId) {
        Show show = shows.findForUpdate(showId).orElseThrow(() -> new NoSuchElementException("Không tìm thấy show"));
        show.publish(clock.instant(), layouts.isReady(show.getId(), show.getEventId()));
        return ShowResponse.from(show);
    }
}
