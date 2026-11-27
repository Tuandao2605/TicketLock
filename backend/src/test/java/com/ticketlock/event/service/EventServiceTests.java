package com.ticketlock.event.service;

import com.ticketlock.event.api.ShowSeatLayout;
import com.ticketlock.event.entity.Event;
import com.ticketlock.event.entity.Show;
import com.ticketlock.event.entity.ShowStatus;
import com.ticketlock.event.repository.EventRepository;
import com.ticketlock.event.repository.ShowRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventServiceTests {
    private final Instant now = Instant.parse("2027-01-01T00:00:00Z");
    private final EventRepository events = mock(EventRepository.class);
    private final ShowRepository shows = mock(ShowRepository.class);
    private final ShowSeatLayout layouts = mock(ShowSeatLayout.class);
    private final EventService service = new EventService(events, shows, layouts, Clock.fixed(now, ZoneOffset.UTC));

    @Test void validatesRequiredFieldsAndFutureTime() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new Event("   ", "demo", now));
        assertThrows(IllegalArgumentException.class, () -> new Show(id, now, "venue", now));
        assertThrows(IllegalArgumentException.class, () -> new Show(id, now.plusSeconds(60), " ", now));
        assertThrows(NoSuchElementException.class, () -> service.createShow(id, now.plusSeconds(60), "venue"));
    }

    @Test void publishFailsClosedThenLocksEvenWhenCancelled() {
        UUID id = UUID.randomUUID();
        Show show = new Show(UUID.randomUUID(), now.plusSeconds(3600), "venue", now);
        when(shows.findForUpdate(id)).thenReturn(Optional.of(show));
        assertThrows(IllegalStateException.class, () -> service.publishShow(id));
        assertEquals(ShowStatus.DRAFT, show.getStatus());
        assertFalse(show.isLayoutLocked());
        when(layouts.isReady(show.getId(), show.getEventId())).thenReturn(true);
        service.publishShow(id);
        assertEquals(ShowStatus.PUBLISHED, show.getStatus());
        assertTrue(show.isLayoutLocked());
        show.cancel();
        assertEquals(ShowStatus.CANCELLED, show.getStatus());
        assertTrue(show.isLayoutLocked());
        assertThrows(IllegalStateException.class, () -> service.publishShow(id));
    }

    @Test void refusesToPublishIfShowTimeHasPassed() {
        UUID id = UUID.randomUUID();
        Show show = new Show(UUID.randomUUID(), now.plusSeconds(1), "venue", now);
        EventService later = new EventService(events, shows, layouts, Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC));
        when(shows.findForUpdate(id)).thenReturn(Optional.of(show));
        when(layouts.isReady(show.getId(), show.getEventId())).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> later.publishShow(id));
        assertEquals(ShowStatus.DRAFT, show.getStatus());
    }
}
