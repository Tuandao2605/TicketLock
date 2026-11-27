package com.ticketlock.event.entity;

import jakarta.persistence.*;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "seats")
public class Seat {
    @Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;
    @Column(nullable = false, length = 100, updatable = false)
    private String label;
    protected Seat() { }
    public Seat(UUID eventId, String label) {
        if (eventId == null || label == null || label.isBlank() || label.length() > 100)
            throw new IllegalArgumentException("Ghế không hợp lệ");
        this.eventId = eventId;
        this.label = label.strip();
    }
    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getLabel() { return label; }
}
