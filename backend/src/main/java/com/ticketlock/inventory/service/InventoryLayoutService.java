package com.ticketlock.inventory.service;

import com.ticketlock.event.api.ShowSeatLayout;
import com.ticketlock.inventory.entity.ShowSeat;
import com.ticketlock.inventory.repository.ShowSeatRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryLayoutService implements ShowSeatLayout {
    private final ShowSeatRepository seats;
    public InventoryLayoutService(ShowSeatRepository seats) { this.seats = seats; }

    @Override @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void addSeats(UUID showId, UUID eventId, List<UUID> seatIds) {
        for (UUID seatId : seatIds) {
            if (!seats.existsByShowIdAndSeatId(showId, seatId)) seats.save(new ShowSeat(showId, seatId, eventId));
        }
        seats.flush();
    }

    @Override @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY, readOnly = true)
    public boolean isReady(UUID showId, UUID eventId) { return seats.countByShowId(showId) > 0; }
}
