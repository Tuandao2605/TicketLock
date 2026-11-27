package com.ticketlock.event.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SeatSnapshotResponse(UUID showId, Instant capturedAt, long version, List<Seat> seats) {
    public record Money(long amount, String currency) { }
    public record Seat(UUID showSeatId, String label, String status, Money price) { }
}
