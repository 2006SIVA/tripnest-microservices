# TripNest — Tourism & Travel Booking System

A complete college-project implementation of the supplied SRS: a React travel-booking site backed by six Java 21 / Spring Boot REST microservices and service-owned PostgreSQL databases. Docker Compose runs the full stack locally. The payment provider is deliberately **mock-only**; no card data is accepted and no real charge is made.

## Quick start

**Requirements:** Docker Engine with the Compose plugin, 4 GB+ memory, and a free local port 8080. You do not need Java or Maven on the host; backend images build with Maven inside Docker.

```bash
cp .env.example .env
# For anything beyond a disposable local demo, replace every secret in .env.
docker compose up --build -d
```

Open **http://localhost:8080**. The first build compiles the frontend and each Spring Boot service, creates the service databases, and applies Flyway migrations. Startup may take several minutes. Check progress with:

```bash
docker compose ps
docker compose logs -f web user-service travel-service hotel-service transport-service booking-service payment-service
```

Health endpoint: `http://localhost:8080/health`.

### Local demo accounts and payments

- Traveller: register through the app with an email and a password of at least 8 characters.
- Administrator: use the `ADMIN_EMAIL` and `ADMIN_PASSWORD` values from your local `.env` (the example file has `admin@tripnest.local` / `DemoAdmin123!`). The administrator is seeded as a BCrypt hash when the user database is first initialized. Do not reuse those example credentials anywhere else.
- In **My trips**, select the success or failure simulation. The UI never collects card details. Only a `SUCCESS` outcome confirms the booking; a failure leaves it awaiting payment.

### Database lifecycle

- `docker compose down` stops the stack and **preserves** the named `postgres-data` volume.
- `docker compose up --build -d` restarts with the same database.
- `docker compose down -v` **permanently deletes the local database volume and all bookings/users**. Use it only when you intentionally want to reset a disposable demo. Flyway migrations run only on database creation/migration, not on every container rebuild.

## What is implemented

- Traveller registration, login, server-verified profile, profile editing, password changes, and booking history.
- Destination search/category filtering, details, packages, inclusions, prices, hotels and date-aware availability, and transport/seats by date.
- Bookings can contain a destination alone at its administrator-managed starting price, or combine that destination with optional packages, hotels and/or transportation. The server checks authoritative catalogue prices and availability; totals are calculated server-side.
- Inventory reservation, pending-booking date/guest edits with repricing, cancellation before/while the trip is eligible, payment attempts, booking confirmation codes, and history.
- Administrator-protected destination, package, hotel and transport create/update/remove endpoints and a browser administration workspace; admin booking listing and status management.
- Stateless signed 15-minute bearer JWTs held for the current browser session, BCrypt password hashes, owner checks for traveller resources, role checks for admin routes, a separate shared-service secret for internal inventory/payment callbacks, server-side validation, parameterized SQL, migrations, constraints, seed data, and consistent API errors.
- **Factory Pattern:** `TravelServiceFactory` creates optional hotel/transport components. **Builder Pattern:** `BookingBuilder` validates and assembles the booking and computes the trip price.

## Architecture

```text
Browser
  │ same-origin HTTPS/HTTP
  ▼
Nginx web container (static React SPA + REST reverse proxy)
  ├── /api/v1/auth/*, /api/v1/users/* ───── user-service ── userdb
  ├── /api/v1/destinations/*, /packages/* ─ travel-service ─ traveldb
  ├── /api/v1/hotels/* ──────────────────── hotel-service ─ hoteldb
  ├── /api/v1/transport/* ───────────────── transport-service ─ transportdb
  ├── /api/v1/bookings/* ────────────────── booking-service ─ bookingdb
  └── /api/v1/payments/* ────────────────── payment-service ─ paymentdb
                                                   │
                              private REST calls + service-token headers

PostgreSQL 16: one local server, six separate databases and six restricted roles.
```

This is a microservice *data ownership* setup: each service owns its own PostgreSQL database/role and schema, with cross-service business data obtained through REST, not cross-database joins. Compose keeps the services private; only Nginx publishes port 8080.

## Source layout

- `client/` — React user interface, responsive design, destination photography, and route manifest.
- `backend/shared/` — shared JWT principal/filter, security configuration, API errors, and internal service-token validation.
- `backend/user-service/` — accounts, BCrypt authentication, profiles, administrator bootstrap, and user migrations.
- `backend/travel-service/` — destinations and packages.
- `backend/hotel-service/` — hotel catalogue, availability, and reservations.
- `backend/transport-service/` — transport catalogue, seats, and reservations.
- `backend/booking-service/` — booking builder, pricing, ownership, lifecycle, and cross-service inventory orchestration.
- `backend/payment-service/` — payment-attempt persistence and the replaceable `PaymentProvider` interface.
- `infra/` — Java and frontend Dockerfiles, Nginx routing, and PostgreSQL role/database bootstrap.
- `docker-compose.yml` — full local stack; `.env.example` — demo-only configuration template.
- `docs/API.md` and `docs/ARCHITECTURE.md` — endpoint guide and service/data-flow details.

## Configuration and security notes

Copy `.env.example` to `.env`. Compose refuses to start when required keys are absent. Set independent strong, random values for `POSTGRES_PASSWORD`, every `*_DB_PASSWORD`, `JWT_SECRET` (at least 32 UTF-8 bytes), and `SERVICE_SECRET` (at least 32 UTF-8 bytes). Set a unique administrator email and a password of at least 12 characters. Keep `.env` private; it is ignored by Git and is not included in the source ZIP.

The sample values in `.env.example` are intentionally labelled **local demo only** and are public in the source. Compose binds the plaintext web port to `127.0.0.1` only. Real deployments additionally need TLS, secret storage/rotation, database backups, rate limiting, monitoring, and a real provider-specific payment/refund integration. Do not expose the mock success switch as a real payment function. Session storage reduces persistence but remains readable by JavaScript running on this origin; treat this demo as local-only.

## Development and validation

Frontend-only static preview (catalogue/demo content is available; authentication and booking APIs require the Compose stack):

```bash
pnpm install --frozen-lockfile
pnpm dev:static
```

Frontend checks/build:

```bash
pnpm check
pnpm test
pnpm build:static
```

Backend build/tests (Java 21 required locally):

```bash
cd backend
./mvnw -B verify
```

Full runtime smoke-test:

```bash
cp .env.example .env
docker compose up --build -d
curl -fsS http://localhost:8080/health
curl -fsS http://localhost:8080/api/v1/destinations
```

The container deployment is reproducible, but production billing is not in scope: payments are recorded as simulated attempts only.
