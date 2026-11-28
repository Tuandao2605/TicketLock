package com.ticketlock.reservation;

import com.ticketlock.event.entity.Event;
import com.ticketlock.event.entity.Show;
import com.ticketlock.event.repository.EventRepository;
import com.ticketlock.event.repository.ShowRepository;
import com.ticketlock.event.service.EventService;
import com.ticketlock.event.service.SeatLayoutService;
import com.ticketlock.inventory.repository.ShowSeatRepository;
import com.ticketlock.reservation.service.ReservationService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ReservationIntegrationTests {
    @Autowired EventRepository events;
    @Autowired ShowRepository shows;
    @Autowired SeatLayoutService layouts;
    @Autowired EventService eventService;
    @Autowired ShowSeatRepository seats;
    @Autowired ReservationService reservations;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    private record Fixture(UUID event, UUID show, List<UUID> seats) { }
    private Fixture fixture() { return fixture(List.of("A1", "A2")); }
    private Fixture fixture(List<String> labels) {
        UUID[] ids = transactions.execute(s -> {
            Event e = events.saveAndFlush(new Event("Hold test", "", Instant.now()));
            Show show = shows.saveAndFlush(new Show(e.getId(), Instant.now().plusSeconds(3600), "Hall", Instant.now()));
            return new UUID[]{e.getId(), show.getId()};
        });
        layouts.importSeats(ids[1], labels);
        layouts.setUniformPrice(ids[1], 100, "VND");
        eventService.publishShow(ids[1]);
        return new Fixture(ids[0], ids[1], seats.findByShowId(ids[1]).stream().map(s -> s.getId()).toList());
    }
    private void clean(Fixture f) {
        transactions.executeWithoutResult(s -> {
            jdbc.update("UPDATE show_seats SET status='AVAILABLE', reservation_id=NULL, hold_expires_at=NULL WHERE show_id=?", f.show());
            jdbc.update("DELETE FROM reservation_seats WHERE show_id=?", f.show());
            jdbc.update("DELETE FROM reservations WHERE show_id=?", f.show());
            jdbc.update("DELETE FROM show_seats WHERE show_id=?", f.show());
            jdbc.update("DELETE FROM seats WHERE event_id=?", f.event());
            jdbc.update("DELETE FROM shows WHERE id=?", f.show());
            jdbc.update("DELETE FROM events WHERE id=?", f.event());
        });
    }
    @Test void holdsAtomicallyAndPreservesPriceSnapshot() {
        Fixture f = fixture();
        try {
            UUID owner = UUID.randomUUID();
            long version = jdbc.queryForObject("SELECT seat_map_version FROM shows WHERE id=?", Long.class, f.show());
            long seatVersion = jdbc.queryForObject("SELECT min(version) FROM show_seats WHERE show_id=?", Long.class, f.show());
            var hold = reservations.hold(f.show(), owner, f.seats());
            assertEquals(200, hold.totalAmount());
            assertEquals(Duration.ofMinutes(10), Duration.between(hold.createdAt(), hold.expiresAt()));
            assertEquals(owner, jdbc.queryForObject("SELECT owner_id FROM reservations WHERE id=?", UUID.class, hold.id()));
            assertEquals(f.show(), jdbc.queryForObject("SELECT show_id FROM reservations WHERE id=?", UUID.class, hold.id()));
            assertEquals(version + 1, jdbc.queryForObject("SELECT seat_map_version FROM shows WHERE id=?", Long.class, f.show()));
            jdbc.update("UPDATE show_seats SET price_amount=200 WHERE show_id=?", f.show());
            assertEquals(200L, jdbc.queryForObject("SELECT sum(price_amount) FROM reservation_seats WHERE reservation_id=?", Long.class, hold.id()));
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM show_seats WHERE reservation_id=? AND status='HELD' AND version=?", Integer.class, hold.id(), seatVersion + 1));
        } finally { clean(f); }
    }
    @Test void holdsSixSeatsInOneGroup() {
        Fixture f = fixture(List.of("A1", "A2", "A3", "A4", "A5", "A6"));
        try {
            var selection = new java.util.ArrayList<>(f.seats());
            java.util.Collections.reverse(selection);
            var hold = reservations.hold(f.show(), UUID.randomUUID(), selection);
            assertEquals(600, hold.totalAmount());
            assertEquals(new java.util.HashSet<>(selection), hold.seats().stream()
                    .map(ReservationService.HeldSeat::showSeatId).collect(java.util.stream.Collectors.toSet()));
            assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM reservation_seats WHERE reservation_id=?", Integer.class, hold.id()));
            assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM show_seats WHERE reservation_id=? AND status='HELD'", Integer.class, hold.id()));
        } finally { clean(f); }
    }
    @Test void rejectsInvalidGroupWithoutPartialHold() {
        Fixture f = fixture();
        try {
            UUID owner = UUID.randomUUID();
            assertThrows(IllegalArgumentException.class, () -> reservations.hold(f.show(), owner, List.of(f.seats().get(0), f.seats().get(0))));
            assertThrows(IllegalArgumentException.class, () -> reservations.hold(f.show(), owner, List.of(f.seats().get(0), UUID.randomUUID())));
            reservations.hold(f.show(), owner, List.of(f.seats().get(0)));
            assertThrows(IllegalStateException.class, () -> reservations.hold(f.show(), owner, f.seats()));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM show_seats WHERE show_id=? AND status='AVAILABLE'", Integer.class, f.show()));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM reservations WHERE show_id=?", Integer.class, f.show()));
        } finally { clean(f); }
    }
    @Test void rejectsSeatFromAnotherShow() {
        Fixture first = fixture();
        Fixture second = fixture();
        try {
            assertThrows(IllegalArgumentException.class, () -> reservations.hold(first.show(), UUID.randomUUID(),
                    List.of(first.seats().get(0), second.seats().get(0))));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM reservations WHERE show_id=?", Integer.class, first.show()));
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM show_seats WHERE show_id=? AND status='AVAILABLE'", Integer.class, first.show()));
        } finally { clean(second); clean(first); }
    }

    @Test void failureAfterSeatWritesRollsBackEntireHold() {
        Fixture f = fixture();
        try {
            jdbc.update("UPDATE shows SET seat_map_version=? WHERE id=?", Long.MAX_VALUE, f.show());
            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> reservations.hold(f.show(), UUID.randomUUID(), f.seats()));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM reservations WHERE show_id=?", Integer.class, f.show()));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM reservation_seats WHERE show_id=?", Integer.class, f.show()));
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM show_seats WHERE show_id=? AND status='AVAILABLE' AND reservation_id IS NULL", Integer.class, f.show()));
        } finally { clean(f); }
    }

    @Test void commitFailureDoesNotReturnSuccess() {
        Fixture f = fixture();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String function = "test_hold_fail_" + suffix;
        String trigger = "test_hold_trigger_" + suffix;
        try {
            jdbc.execute("CREATE FUNCTION " + function + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                    + "IF NEW.show_id = '" + f.show() + "'::uuid THEN RAISE EXCEPTION 'test commit failure'; END IF; RETURN NEW; END $$");
            jdbc.execute("CREATE CONSTRAINT TRIGGER " + trigger + " AFTER INSERT ON reservations DEFERRABLE INITIALLY DEFERRED "
                    + "FOR EACH ROW EXECUTE FUNCTION " + function + "()");
            assertThrows(org.springframework.orm.jpa.JpaSystemException.class,
                    () -> reservations.hold(f.show(), UUID.randomUUID(), f.seats()));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM reservations WHERE show_id=?", Integer.class, f.show()));
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM show_seats WHERE show_id=? AND status='AVAILABLE'", Integer.class, f.show()));
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS " + trigger + " ON reservations");
            jdbc.execute("DROP FUNCTION IF EXISTS " + function + "()");
            clean(f);
        }
    }

    @Test void concurrentHoldsHaveOneWinner() throws Exception {
        Fixture f = fixture();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> attempt = () -> { start.await(); try { reservations.hold(f.show(), UUID.randomUUID(), List.of(f.seats().get(0))); return true; } catch (IllegalStateException e) { return false; } };
            var a = pool.submit(attempt); var b = pool.submit(attempt); start.countDown();
            assertNotEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM reservations WHERE show_id=?", Integer.class, f.show()));
        } finally { pool.shutdownNow(); clean(f); }
    }
}
