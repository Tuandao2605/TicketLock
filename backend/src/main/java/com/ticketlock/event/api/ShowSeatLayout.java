package com.ticketlock.event.api;

import java.util.List;
import java.util.UUID;

/** Inventory implementation joins caller's transaction; caller holds the Show row lock. */
public interface ShowSeatLayout {
    void addSeats(UUID showId, UUID eventId, List<UUID> seatIds);
    boolean isReady(UUID showId, UUID eventId);
}
