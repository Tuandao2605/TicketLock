package com.ticketlock.event.repository;

import com.ticketlock.event.entity.Seat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
    Optional<Seat> findByEventIdAndLabel(UUID eventId, String label);
}
