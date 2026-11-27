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
    public boolean isReady(UUID showId, UUID eventId) {
        var rows = seats.findByShowId(showId);
        return !rows.isEmpty() && rows.stream().allMatch(s -> s.getPriceAmount() != null && s.getCurrency() != null)
                && rows.stream().map(ShowSeat::getCurrency).distinct().count() == 1;
    }

    @Override @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public int setPrice(UUID showId, long amount, String currency) {
        var rows = seats.findByShowId(showId);
        if (rows.isEmpty()) throw new IllegalStateException("Show chưa có ghế");
        if (rows.stream().anyMatch(s -> s.getStatus() != com.ticketlock.inventory.entity.SeatStatus.AVAILABLE))
            throw new IllegalStateException("Không thể đổi giá ghế đã được giữ hoặc bán");
        rows.forEach(s -> s.setPrice(amount, currency));
        seats.flush();
        return rows.size();
    }

    @Override @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY, readOnly = true)
    public List<SeatSnapshot> snapshot(UUID showId) {
        return seats.findByShowId(showId).stream().map(s -> new SeatSnapshot(
                s.getId(), s.getSeatId(), s.getStatus().name(), s.getPriceAmount(), s.getCurrency())).toList();
    }
}
