package com.ticketlock.event.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "shows")
public class Show {
    @Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(nullable = false, length = 200)
    private String venue;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ShowStatus status;
    @Column(name = "sales_opened_at")
    private Instant salesOpenedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Show() { }

    public Show(UUID eventId, Instant startsAt, String venue, Instant now) {
        if (eventId == null || now == null) throw new IllegalArgumentException("Sự kiện hoặc thời điểm không hợp lệ");
        validate(startsAt, venue, now);
        this.eventId = eventId;
        this.startsAt = startsAt;
        this.venue = venue.strip();
        this.createdAt = now;
        this.status = ShowStatus.DRAFT;
    }

    public void publish(Instant now, boolean layoutReady) {
        if (status != ShowStatus.DRAFT) throw new IllegalStateException("Chỉ show nháp được publish");
        validate(startsAt, venue, now);
        if (!layoutReady) throw new IllegalStateException("Sơ đồ ghế chưa sẵn sàng");
        this.salesOpenedAt = now;
        this.status = ShowStatus.PUBLISHED;
    }

    public void cancel() {
        if (status == ShowStatus.CANCELLED) return;
        status = ShowStatus.CANCELLED;
    }

    private static void validate(Instant startsAt, String venue, Instant now) {
        if (now == null || startsAt == null || !startsAt.isAfter(now)) throw new IllegalArgumentException("Thời gian show phải ở tương lai");
        if (venue == null || venue.isBlank() || venue.length() > 200) throw new IllegalArgumentException("Địa điểm không hợp lệ");
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public Instant getStartsAt() { return startsAt; }
    public String getVenue() { return venue; }
    public ShowStatus getStatus() { return status; }
    public Instant getSalesOpenedAt() { return salesOpenedAt; }
    public boolean isLayoutLocked() { return salesOpenedAt != null; }
}
