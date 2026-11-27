ALTER TABLE shows ADD CONSTRAINT uq_shows_id_event UNIQUE (id, event_id);
CREATE TABLE seats (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    label VARCHAR(100) NOT NULL CHECK (length(btrim(label)) > 0),
    CONSTRAINT uq_seat_event_label UNIQUE (event_id, label),
    CONSTRAINT uq_seat_id_event UNIQUE (id, event_id)
);
CREATE TABLE show_seats (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL,
    seat_id UUID NOT NULL,
    event_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE', 'HELD', 'SOLD')),
    reservation_id UUID,
    hold_expires_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_show_seat UNIQUE (show_id, seat_id),
    CONSTRAINT fk_show_seat_show FOREIGN KEY (show_id, event_id) REFERENCES shows(id, event_id),
    CONSTRAINT fk_show_seat_seat FOREIGN KEY (seat_id, event_id) REFERENCES seats(id, event_id),
    CONSTRAINT ck_show_seat_hold CHECK (
      (status = 'AVAILABLE' AND reservation_id IS NULL AND hold_expires_at IS NULL) OR
      (status = 'HELD' AND reservation_id IS NOT NULL AND hold_expires_at IS NOT NULL) OR
      (status = 'SOLD' AND hold_expires_at IS NULL)
    )
);
