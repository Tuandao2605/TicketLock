package com.ticketlock.event.service;

import com.ticketlock.event.entity.Seat;
import com.ticketlock.event.entity.Show;
import com.ticketlock.event.entity.ShowStatus;
import com.ticketlock.event.repository.EventRepository;
import com.ticketlock.event.repository.SeatRepository;
import com.ticketlock.event.repository.ShowRepository;
import com.ticketlock.event.api.ShowSeatLayout;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Layout definitions are immutable; changing a show only appends seats while it is draft. */
@Service
public class SeatLayoutService {
    private final EventRepository events;
    private final ShowRepository shows;
    private final SeatRepository seats;
    private final ShowSeatLayout inventory;

    public SeatLayoutService(EventRepository events, ShowRepository shows, SeatRepository seats, ShowSeatLayout inventory) {
        this.events = events; this.shows = shows; this.seats = seats; this.inventory = inventory;
    }

    @Transactional
    public void importSeats(UUID showId, List<String> labels) {
        if (showId == null || labels == null || labels.isEmpty() || labels.size() > 1000)
            throw new IllegalArgumentException("Danh sách ghế không hợp lệ");
        if (labels.stream().anyMatch(label -> label == null || label.isBlank() || label.length() > 100))
            throw new IllegalArgumentException("Tên ghế không hợp lệ");
        List<String> clean = labels.stream().map(String::strip).toList();
        if (new HashSet<>(clean).size() != clean.size()) throw new IllegalArgumentException("Tên ghế bị trùng");
        UUID eventId = shows.findEventId(showId).orElseThrow(() -> new NoSuchElementException("Không tìm thấy show"));
        events.findForUpdate(eventId).orElseThrow(() -> new NoSuchElementException("Không tìm thấy sự kiện"));
        Show show = shows.findForUpdate(showId).orElseThrow(() -> new NoSuchElementException("Không tìm thấy show"));
        if (!eventId.equals(show.getEventId()) || show.getStatus() != ShowStatus.DRAFT || show.isLayoutLocked())
            throw new IllegalStateException("Sơ đồ show không thể thay đổi");
        List<UUID> seatIds = clean.stream().map(label -> seats.findByEventIdAndLabel(eventId, label)
                .orElseGet(() -> seats.saveAndFlush(new Seat(eventId, label))).getId()).toList();
        inventory.addSeats(showId, eventId, seatIds);
        show.markSeatMapChanged();
    }
    @Transactional
    public void setUniformPrice(UUID showId, long amount, String currency) {
        if (amount < 0 || currency == null || !currency.matches("[A-Z]{3}"))
            throw new IllegalArgumentException("Giá hoặc currency không hợp lệ");
        Show show = shows.findForUpdate(showId).orElseThrow(() -> new NoSuchElementException("Không tìm thấy show"));
        if (show.getStatus() != ShowStatus.DRAFT || show.isLayoutLocked())
            throw new IllegalStateException("Không thể đổi giá show đã mở bán");
        inventory.setPrice(showId, amount, currency);
        show.markSeatMapChanged();
    }
}
