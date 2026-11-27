package com.ticketlock.event.controller;

import com.ticketlock.event.dto.*;
import com.ticketlock.event.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class EventController {
    private final EventService events;
    private final EventQueryService queries;
    private final SeatLayoutService layouts;
    public EventController(EventService events, EventQueryService queries, SeatLayoutService layouts) {
        this.events = events; this.queries = queries; this.layouts = layouts;
    }
    public record EventInput(@NotBlank @Size(max=200) String name, @NotNull @Size(max=5000) String description) { }
    public record ShowInput(@NotNull @Future Instant startsAt, @NotBlank @Size(max=200) String venue) { }
    public record LayoutInput(@NotEmpty @Size(max=1000) List<@NotBlank @Size(max=100) String> labels) { }
    public record PriceInput(@NotNull @Min(0) Long amount, @NotNull @Pattern(regexp="[A-Z]{3}") String currency) { }

    @GetMapping("/events")
    public PageResponse<EventResponse> events(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return queries.events(page, size); }
    @GetMapping("/events/{id}")
    public EventResponse event(@PathVariable UUID id) { return queries.event(id); }
    @GetMapping("/events/{id}/shows")
    public PageResponse<ShowResponse> shows(@PathVariable UUID id, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return queries.shows(id, page, size); }
    @GetMapping("/shows/{id}")
    public ShowResponse show(@PathVariable UUID id) { return queries.show(id); }
    @GetMapping("/shows/{id}/seats")
    public SeatSnapshotResponse seats(@PathVariable UUID id) { return queries.snapshot(id); }

    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/events") @ResponseStatus(HttpStatus.CREATED)
    public EventResponse createEvent(@Valid @RequestBody EventInput input) { return events.createEvent(input.name(), input.description()); }
    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/events/{id}/shows") @ResponseStatus(HttpStatus.CREATED)
    public ShowResponse createShow(@PathVariable UUID id, @Valid @RequestBody ShowInput input) { return events.createShow(id, input.startsAt(), input.venue()); }
    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/shows/{id}/seats/import") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void importSeats(@PathVariable UUID id, @Valid @RequestBody LayoutInput input) { layouts.importSeats(id, input.labels()); }
    @PreAuthorize("hasRole('ADMIN')") @PutMapping("/shows/{id}/price") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPrice(@PathVariable UUID id, @Valid @RequestBody PriceInput input) { layouts.setUniformPrice(id, input.amount(), input.currency()); }
    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/shows/{id}/publish")
    public ShowResponse publish(@PathVariable UUID id) { return events.publishShow(id); }
}
