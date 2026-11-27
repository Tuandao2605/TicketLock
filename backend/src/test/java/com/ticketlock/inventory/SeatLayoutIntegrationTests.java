package com.ticketlock.inventory;

import com.ticketlock.event.api.ShowSeatLayout;
import com.ticketlock.event.entity.Event;
import com.ticketlock.event.entity.Show;
import com.ticketlock.event.entity.ShowStatus;
import com.ticketlock.event.repository.EventRepository;
import com.ticketlock.event.repository.ShowRepository;
import com.ticketlock.event.repository.SeatRepository;
import com.ticketlock.event.service.EventService;
import com.ticketlock.event.service.SeatLayoutService;
import com.ticketlock.inventory.entity.SeatStatus;
import com.ticketlock.inventory.repository.ShowSeatRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SeatLayoutIntegrationTests {
    @Autowired EventRepository events;
    @Autowired ShowRepository shows;
    @Autowired SeatRepository seats;
    @Autowired ShowSeatRepository showSeats;
    @Autowired SeatLayoutService layouts;
    @Autowired EventService eventService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @Test @Transactional
    void reusesSeatsAcrossShowsButKeepsStateSeparateAndImportIdempotent() {
        Event event = events.saveAndFlush(new Event("Test", "", Instant.now()));
        Show first = shows.saveAndFlush(new Show(event.getId(), Instant.now().plusSeconds(3600), "Hall", Instant.now()));
        Show second = shows.saveAndFlush(new Show(event.getId(), Instant.now().plusSeconds(7200), "Hall", Instant.now()));
        layouts.importSeats(first.getId(), List.of("A1", "A2"));
        layouts.importSeats(first.getId(), List.of("A1", "A2"));
        layouts.importSeats(second.getId(), List.of("A1", "A2"));
        var firstSeats = showSeats.findByShowId(first.getId());
        var secondSeats = showSeats.findByShowId(second.getId());
        assertEquals(2, firstSeats.size());
        assertEquals(2, secondSeats.size());
        assertEquals(firstSeats.stream().map(x -> x.getSeatId()).collect(java.util.stream.Collectors.toSet()), secondSeats.stream().map(x -> x.getSeatId()).collect(java.util.stream.Collectors.toSet()));
        assertNotEquals(firstSeats.get(0).getId(), secondSeats.get(0).getId());
        assertTrue(firstSeats.stream().allMatch(x -> x.getStatus() == SeatStatus.AVAILABLE && x.getVersion() == 0));
        jdbc.update("UPDATE show_seats SET status='SOLD' WHERE id=?", firstSeats.get(0).getId());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM show_seats WHERE show_id=? AND status='AVAILABLE'", Integer.class, second.getId()));
        layouts.setUniformPrice(first.getId(), 100000, "VND");
        eventService.publishShow(first.getId());
        assertEquals(ShowStatus.PUBLISHED, first.getStatus());
        assertThrows(IllegalStateException.class, () -> layouts.importSeats(first.getId(), List.of("A3")));
        assertTrue(first.isLayoutLocked());
    }

    @Test @Transactional
    void rejectsDuplicateLabelsWithoutCreatingSeats() {
        Event event = events.saveAndFlush(new Event("Test", "", Instant.now()));
        Show show = shows.saveAndFlush(new Show(event.getId(), Instant.now().plusSeconds(3600), "Hall", Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> layouts.importSeats(show.getId(), List.of("A1", " A1 ")));
        assertTrue(seats.findByEventIdAndLabel(event.getId(), "A1").isEmpty());
        assertEquals(0, showSeats.countByShowId(show.getId()));
    }

    @Test @Transactional
    void databaseRejectsDuplicateShowSeat() {
        Event event = events.saveAndFlush(new Event("Test", "", Instant.now()));
        Show show = shows.saveAndFlush(new Show(event.getId(), Instant.now().plusSeconds(3600), "Hall", Instant.now()));
        layouts.importSeats(show.getId(), List.of("A1"));
        UUID seatId = seats.findByEventIdAndLabel(event.getId(), "A1").orElseThrow().getId();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO show_seats (id, show_id, seat_id, event_id, status, version) VALUES (?,?,?,?, 'AVAILABLE',0)", UUID.randomUUID(), show.getId(), seatId, event.getId()));
    }

    @Test @Transactional
    void databaseRejectsSeatFromAnotherEvent() {
        Event first = events.saveAndFlush(new Event("First", "", Instant.now()));
        Event second = events.saveAndFlush(new Event("Second", "", Instant.now()));
        Show show = shows.saveAndFlush(new Show(first.getId(), Instant.now().plusSeconds(3600), "Hall", Instant.now()));
        Show otherShow = shows.saveAndFlush(new Show(second.getId(), Instant.now().plusSeconds(3600), "Hall", Instant.now()));
        layouts.importSeats(otherShow.getId(), List.of("B1"));
        UUID seatId = seats.findByEventIdAndLabel(second.getId(), "B1").orElseThrow().getId();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO show_seats (id,show_id,seat_id,event_id,status,version) VALUES (?,?,?,?,'AVAILABLE',0)",
            UUID.randomUUID(), show.getId(), seatId, first.getId()));
    }

    @Test
    void failedInventoryWriteRollsBackNewSeatDefinitions() {
        UUID[] ids = transactions.execute(status -> {
            Event event = events.saveAndFlush(new Event("Rollback", "", Instant.now()));
            Show show = shows.saveAndFlush(new Show(event.getId(), Instant.now().plusSeconds(3600), "Hall", Instant.now()));
            return new UUID[]{event.getId(), show.getId()};
        });
        try {
            ShowSeatLayout failing = new ShowSeatLayout() {
                @Override public void addSeats(UUID showId, UUID eventId, List<UUID> seatIds) { throw new IllegalStateException("fixture failure"); }
                @Override public boolean isReady(UUID showId, UUID eventId) { return false; }
                @Override public int setPrice(UUID showId, long amount, String currency) { return 0; }
                @Override public List<SeatSnapshot> snapshot(UUID showId) { return List.of(); }
            };
            SeatLayoutService service = new SeatLayoutService(events, shows, seats, failing);
            assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> service.importSeats(ids[1], List.of("R1", "R2"))));
            assertTrue(seats.findByEventIdAndLabel(ids[0], "R1").isEmpty());
            assertTrue(seats.findByEventIdAndLabel(ids[0], "R2").isEmpty());
            assertEquals(0, showSeats.countByShowId(ids[1]));
        } finally {
            transactions.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM shows WHERE id = ?", ids[1]);
                jdbc.update("DELETE FROM events WHERE id = ?", ids[0]);
            });
        }
    }

    @Test
    void publishAndImportRaceUsesSameShowLock() throws Exception {
        UUID[] ids = transactions.execute(status -> {
            Event e = events.saveAndFlush(new Event("Race", "", Instant.now()));
            Show show = shows.saveAndFlush(new Show(e.getId(), Instant.now().plusSeconds(3600), "Hall", Instant.now()));
            return new UUID[]{e.getId(), show.getId()};
        });
        try {
            layouts.importSeats(ids[1], List.of("A1"));
            layouts.setUniformPrice(ids[1], 100000, "VND");
            var start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<Boolean> publish = pool.submit(() -> {
                    await(start);
                    try { eventService.publishShow(ids[1]); return true; }
                    catch (IllegalStateException expected) { return false; }
                });
                Future<Boolean> append = pool.submit(() -> {
                    await(start);
                    try { layouts.importSeats(ids[1], List.of("A2")); return true; }
                    catch (IllegalStateException expected) { return false; }
                });
                start.countDown();
                boolean published = publish.get(15, TimeUnit.SECONDS);
                boolean appended = append.get(15, TimeUnit.SECONDS);
                assertNotEquals(published, appended);
                assertEquals(published ? ShowStatus.PUBLISHED : ShowStatus.DRAFT, shows.findById(ids[1]).orElseThrow().getStatus());
                assertEquals(appended ? 2 : 1, showSeats.countByShowId(ids[1]));
                if (!published) {
                    layouts.setUniformPrice(ids[1], 100000, "VND");
                    eventService.publishShow(ids[1]);
                }
                assertThrows(IllegalStateException.class, () -> layouts.importSeats(ids[1], List.of("A3")));
            } finally { pool.shutdownNow(); }
        } finally {
            transactions.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM show_seats WHERE show_id = ?", ids[1]);
                jdbc.update("DELETE FROM seats WHERE event_id = ?", ids[0]);
                jdbc.update("DELETE FROM shows WHERE id = ?", ids[1]);
                jdbc.update("DELETE FROM events WHERE id = ?", ids[0]);
            });
        }
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
