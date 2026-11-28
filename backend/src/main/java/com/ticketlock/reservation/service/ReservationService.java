package com.ticketlock.reservation.service;

import java.sql.Timestamp;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Internal only: the caller must supply an authenticated owner's UUID. */
@Service
public class ReservationService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final int maxSeats;
    private final long holdSeconds;

    public ReservationService(JdbcTemplate jdbc, PlatformTransactionManager manager,
            @Value("${ticketlock.reservation.max-seats:6}") int maxSeats,
            @Value("${ticketlock.reservation.hold-seconds:600}") long holdSeconds) {
        if (maxSeats < 1 || maxSeats > 6 || holdSeconds < 1) throw new IllegalArgumentException("Invalid reservation configuration");
        this.jdbc = jdbc;
        this.maxSeats = maxSeats;
        this.holdSeconds = holdSeconds;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Hold hold(UUID showId, UUID ownerId, List<UUID> seatIds) {
        if (showId == null || ownerId == null || seatIds == null || seatIds.isEmpty()
                || seatIds.size() > maxSeats || seatIds.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(seatIds).size() != seatIds.size()) throw new IllegalArgumentException("Invalid seat selection");
        List<UUID> ordered = seatIds.stream().sorted().toList();
        return transaction.execute(status -> createHold(showId, ownerId, ordered));
    }

    private Hold createHold(UUID showId, UUID ownerId, List<UUID> ids) {
        var shows = jdbc.query("SELECT status FROM shows WHERE id = ? FOR UPDATE", (rs, row) -> rs.getString(1), showId);
        if (shows.size() != 1 || !shows.get(0).equals("PUBLISHED")) throw new IllegalStateException("Show is not available");
        var seats = jdbc.query(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, status, price_amount, currency FROM show_seats " +
                    "WHERE show_id = ? AND id = ANY(?) ORDER BY id FOR UPDATE");
            statement.setObject(1, showId);
            statement.setArray(2, connection.createArrayOf("uuid", ids.toArray()));
            return statement;
        }, (rs, row) -> new Seat(rs.getObject(1, UUID.class), rs.getString(2),
                (Long) rs.getObject(3), rs.getString(4)));
        if (seats.size() != ids.size()) throw new IllegalArgumentException("Seat does not belong to show");
        if (seats.stream().anyMatch(seat -> !seat.status().equals("AVAILABLE")
                || seat.amount() == null || seat.currency() == null))
            throw new IllegalStateException("Seat is not available");
        String currency = seats.get(0).currency();
        if (seats.stream().anyMatch(s -> !currency.equals(s.currency()))) throw new IllegalStateException("Mixed currencies");
        long total = 0;
        for (Seat seat : seats) total = Math.addExact(total, seat.amount());
        UUID reservationId = UUID.randomUUID();
        long totalAmount = total;
        var times = jdbc.queryForObject("""
                INSERT INTO reservations (id, show_id, owner_id, created_at, expires_at, currency, total_amount)
                SELECT ?, ?, ?, t.now, t.now + (? * interval '1 second'), ?, ?
                FROM (SELECT clock_timestamp() AS now) t
                RETURNING created_at, expires_at
                """, (rs, row) -> new HoldTimes(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()),
                reservationId, showId, ownerId, holdSeconds, currency, totalAmount);
        int inserted = jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO reservation_seats (reservation_id, show_id, show_seat_id, price_amount, currency)
                    SELECT ?, show_id, id, price_amount, currency FROM show_seats
                    WHERE show_id = ? AND id = ANY(?)
                    """);
            statement.setObject(1, reservationId);
            statement.setObject(2, showId);
            statement.setArray(3, connection.createArrayOf("uuid", ids.toArray()));
            return statement;
        });
        int updated = jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    UPDATE show_seats SET status='HELD', reservation_id=?, hold_expires_at=?, version=version+1
                    WHERE show_id=? AND id = ANY(?) AND status='AVAILABLE'
                    """);
            statement.setObject(1, reservationId);
            statement.setTimestamp(2, Timestamp.from(times.expiresAt()));
            statement.setObject(3, showId);
            statement.setArray(4, connection.createArrayOf("uuid", ids.toArray()));
            return statement;
        });
        if (inserted != ids.size() || updated != ids.size()) throw new IllegalStateException("Seat selection changed");
        jdbc.update("UPDATE shows SET seat_map_version=seat_map_version+1 WHERE id=?", showId);
        return new Hold(reservationId, showId, ownerId, times.createdAt(), times.expiresAt(), currency, total,
                seats.stream().map(s -> new HeldSeat(s.id(), s.amount(), s.currency())).toList());
    }

    private record HoldTimes(Instant createdAt, Instant expiresAt) { }
    private record Seat(UUID id, String status, Long amount, String currency) { }
    public record HeldSeat(UUID showSeatId, long priceAmount, String currency) { }
    public record Hold(UUID id, UUID showId, UUID ownerId, Instant createdAt, Instant expiresAt,
                       String currency, long totalAmount, List<HeldSeat> seats) { }
}
