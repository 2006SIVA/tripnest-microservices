#!/bin/sh
set -eu

create_service_database() {
  role_name="$1"
  database_name="$2"
  password="$3"

  psql --no-psqlrc --set=ON_ERROR_STOP=1 --set=role_name="$role_name" --set=db_password="$password" \
    --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role_name', :'db_password');
\gexec
SQL
  psql --no-psqlrc --set=ON_ERROR_STOP=1 --set=role_name="$role_name" --set=database_name="$database_name" \
    --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
SELECT format('CREATE DATABASE %I OWNER %I', :'database_name', :'role_name');
\gexec
SQL
}

create_service_database user userdb "$USER_DB_PASSWORD"
create_service_database travel traveldb "$TRAVEL_DB_PASSWORD"
create_service_database hotel hoteldb "$HOTEL_DB_PASSWORD"
create_service_database transport transportdb "$TRANSPORT_DB_PASSWORD"
create_service_database booking bookingdb "$BOOKING_DB_PASSWORD"
create_service_database payment paymentdb "$PAYMENT_DB_PASSWORD"
