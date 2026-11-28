CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows(id),
    owner_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > created_at),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    total_amount BIGINT NOT NULL CHECK (total_amount >= 0),
    UNIQUE (id, show_id)
);
CREATE TABLE reservation_seats (
    reservation_id UUID NOT NULL,
    show_id UUID NOT NULL,
    show_seat_id UUID NOT NULL REFERENCES show_seats(id),
    price_amount BIGINT NOT NULL CHECK (price_amount >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    PRIMARY KEY (reservation_id, show_seat_id),
    FOREIGN KEY (reservation_id, show_id) REFERENCES reservations(id, show_id)
);
ALTER TABLE show_seats ADD CONSTRAINT fk_show_seat_reservation
    FOREIGN KEY (reservation_id, show_id) REFERENCES reservations(id, show_id);
ALTER TABLE show_seats ADD CONSTRAINT uq_show_seat_id_show UNIQUE (id, show_id);
ALTER TABLE reservation_seats ADD CONSTRAINT fk_reservation_seat_show
    FOREIGN KEY (show_seat_id, show_id) REFERENCES show_seats(id, show_id);
