package com.ticketlock.inventory.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "show_seats")
public class ShowSeat {
    @Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;
    @Column(name = "show_id", nullable = false, updatable = false) private UUID showId;
    @Column(name = "seat_id", nullable = false, updatable = false) private UUID seatId;
    @Column(name = "event_id", nullable = false, updatable = false) private UUID eventId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private SeatStatus status;
    @Column(name = "reservation_id") private UUID reservationId;
    @Column(name = "hold_expires_at") private Instant holdExpiresAt;
    @Column(name = "price_amount") private Long priceAmount;
    @Column(length = 3) private String currency;
    @Version @Column(nullable = false) private long version;
    protected ShowSeat() { }
    public ShowSeat(UUID showId, UUID seatId, UUID eventId) {
        if (showId == null || seatId == null || eventId == null) throw new IllegalArgumentException("Định danh ghế không hợp lệ");
        this.showId = showId; this.seatId = seatId; this.eventId = eventId; this.status = SeatStatus.AVAILABLE;
    }
    public UUID getId() { return id; }
    public UUID getShowId() { return showId; }
    public UUID getSeatId() { return seatId; }
    public SeatStatus getStatus() { return status; }
    public Long getPriceAmount() { return priceAmount; }
    public String getCurrency() { return currency; }
    public void setPrice(long amount, String currency) { this.priceAmount = amount; this.currency = currency; }
    public long getVersion() { return version; }
}
