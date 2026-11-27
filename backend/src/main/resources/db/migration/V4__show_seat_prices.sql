-- Existing show seats remain unpriced until an operator configures prices.
ALTER TABLE shows ADD COLUMN seat_map_version BIGINT NOT NULL DEFAULT 0 CHECK (seat_map_version >= 0);
ALTER TABLE show_seats ADD COLUMN price_amount BIGINT;
ALTER TABLE show_seats ADD COLUMN currency VARCHAR(3);
ALTER TABLE show_seats ADD CONSTRAINT ck_show_seat_price CHECK (
    (price_amount IS NULL AND currency IS NULL) OR
    (price_amount IS NOT NULL AND price_amount >= 0 AND currency IS NOT NULL AND currency ~ '^[A-Z]{3}$')
);
