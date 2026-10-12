CREATE TABLE hotel_reservations(
  id BIGSERIAL PRIMARY KEY,
  booking_id BIGINT NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  hotel_id BIGINT NOT NULL REFERENCES hotels(id),
  check_in DATE NOT NULL,
  check_out DATE NOT NULL,
  rooms INTEGER NOT NULL CHECK(rooms > 0),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK(check_out > check_in)
);
CREATE INDEX idx_hotel_reservations_availability ON hotel_reservations(hotel_id,check_in,check_out,status);
CREATE UNIQUE INDEX uq_hotel_reservation_active_booking ON hotel_reservations(booking_id) WHERE status='ACTIVE';
