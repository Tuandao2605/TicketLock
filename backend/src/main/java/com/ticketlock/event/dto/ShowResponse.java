package com.ticketlock.event.dto;

import com.ticketlock.event.entity.Show;
import com.ticketlock.event.entity.ShowStatus;
import java.time.Instant;
import java.util.UUID;

public record ShowResponse(UUID id, UUID eventId, Instant startsAt, String venue, ShowStatus status) {
    public static ShowResponse from(Show show) {
        return new ShowResponse(show.getId(), show.getEventId(), show.getStartsAt(), show.getVenue(), show.getStatus());
    }
}
