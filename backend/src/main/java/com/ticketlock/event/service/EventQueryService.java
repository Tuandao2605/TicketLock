package com.ticketlock.event.service;

import com.ticketlock.event.api.ShowSeatLayout;
import com.ticketlock.event.dto.*;
import com.ticketlock.event.entity.ShowStatus;
import com.ticketlock.event.repository.*;
import java.time.Instant;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventQueryService {
    private final EventRepository events;
    private final ShowRepository shows;
    private final SeatRepository seats;
    private final ShowSeatLayout inventory;
    public EventQueryService(EventRepository events, ShowRepository shows, SeatRepository seats, ShowSeatLayout inventory) {
        this.events = events; this.shows = shows; this.seats = seats; this.inventory = inventory;
    }
    private PageRequest page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Trang không hợp lệ");
        return PageRequest.of(page, size);
    }
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> events(int page, int size) {
        return PageResponse.from(events.findPublishedEvents(page(page, size)).map(EventResponse::from));
    }
    @Transactional(readOnly = true)
    public EventResponse event(UUID id) {
        return EventResponse.from(events.findPublishedById(id).orElseThrow(() -> new NoSuchElementException("Không tìm thấy sự kiện")));
    }
    @Transactional(readOnly = true)
    public PageResponse<ShowResponse> shows(UUID eventId, int page, int size) {
        if (events.findPublishedById(eventId).isEmpty()) throw new NoSuchElementException("Không tìm thấy sự kiện");
        return PageResponse.from(shows.findByEventIdAndStatusOrderByStartsAtAscIdAsc(eventId, ShowStatus.PUBLISHED, page(page, size)).map(ShowResponse::from));
    }
    @Transactional(readOnly = true)
    public ShowResponse show(UUID id) {
        return ShowResponse.from(shows.findByIdAndStatus(id, ShowStatus.PUBLISHED).orElseThrow(() -> new NoSuchElementException("Không tìm thấy show")));
    }
    @Transactional
    public SeatSnapshotResponse snapshot(UUID id) {
        var show = shows.findForUpdate(id).filter(s -> s.getStatus() == ShowStatus.PUBLISHED)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy show"));
        var rows = inventory.snapshot(id);
        if (rows.isEmpty() || rows.stream().anyMatch(s -> s.amount() == null || s.currency() == null))
            throw new IllegalStateException("Giá ghế chưa được cấu hình");
        var labels = seats.findAllById(rows.stream().map(ShowSeatLayout.SeatSnapshot::seatId).toList())
                .stream().collect(Collectors.toMap(s -> s.getId(), Function.identity()));
        if (labels.size() != rows.size()) throw new IllegalStateException("Sơ đồ ghế không hợp lệ");
        var values = rows.stream().map(s -> new SeatSnapshotResponse.Seat(s.showSeatId(), labels.get(s.seatId()).getLabel(), s.status(), new SeatSnapshotResponse.Money(s.amount(), s.currency())))
                .sorted(Comparator.comparing(SeatSnapshotResponse.Seat::label).thenComparing(SeatSnapshotResponse.Seat::showSeatId)).toList();
        return new SeatSnapshotResponse(id, Instant.now(), show.getSeatMapVersion(), values);
    }
}
