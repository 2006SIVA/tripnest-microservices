CREATE TABLE transport_reservations(
  id BIGSERIAL PRIMARY KEY,
  booking_id BIGINT NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  transport_id BIGINT NOT NULL REFERENCES transport_options(id),
  travel_date DATE NOT NULL,
  seats INTEGER NOT NULL CHECK(seats > 0),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_transport_reservations_availability ON transport_reservations(transport_id,travel_date,status);
CREATE UNIQUE INDEX uq_transport_reservation_active_booking ON transport_reservations(booking_id) WHERE status='ACTIVE';
