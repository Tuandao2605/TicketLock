package com.ticketlock.event.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "events")
public class Event {
    @Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 5000)
    private String description;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Event() { }

    public Event(String name, String description, Instant now) {
        if (name == null || name.isBlank() || name.length() > 200) throw new IllegalArgumentException("Tên sự kiện không hợp lệ");
        if (description == null || description.length() > 5000) throw new IllegalArgumentException("Mô tả không hợp lệ");
        if (now == null) throw new IllegalArgumentException("Thời điểm tạo không hợp lệ");
        this.name = name.strip();
        this.description = description;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
