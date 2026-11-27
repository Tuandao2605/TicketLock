package com.ticketlock.event.dto;

import com.ticketlock.event.entity.Event;
import java.util.UUID;

public record EventResponse(UUID id, String name, String description) {
    public static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getName(), event.getDescription());
    }
}
