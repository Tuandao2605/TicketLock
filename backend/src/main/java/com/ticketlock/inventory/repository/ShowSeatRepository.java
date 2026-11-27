package com.ticketlock.inventory.repository;

import com.ticketlock.inventory.entity.ShowSeat;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, UUID> {
    boolean existsByShowIdAndSeatId(UUID showId, UUID seatId);
    long countByShowId(UUID showId);
    List<ShowSeat> findByShowId(UUID showId);
}
