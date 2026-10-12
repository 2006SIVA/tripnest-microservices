CREATE TABLE payments(id BIGSERIAL PRIMARY KEY,booking_id BIGINT NOT NULL,amount NUMERIC(12,2) NOT NULL,status VARCHAR(20) NOT NULL,provider VARCHAR(40) NOT NULL DEFAULT 'MOCK',reference VARCHAR(100) NOT NULL UNIQUE,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE payment_booking_locks(booking_id BIGINT PRIMARY KEY,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE UNIQUE INDEX uq_one_successful_payment_per_booking ON payments(booking_id) WHERE status='SUCCEEDED';
